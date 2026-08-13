package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraOrEval;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredConditionOperator;
import com.eu.habbo.habbohotel.wired.api.IWiredCondition;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Internal owner of WIRED condition grouping, evaluation modes and negation.
 *
 * <p>The engine remains the public execution surface. This collaborator deliberately receives the
 * existing mutable execution context and evaluates conditions in the same physical order so step
 * accounting, plugin-visible condition instances and diagnostics remain behavior-compatible.
 */
final class WiredConditionEvaluator {

    @FunctionalInterface
    interface DiagnosticSink {
        void log(Room room, String format, Object... arguments);
    }

    private final DiagnosticSink diagnostics;

    WiredConditionEvaluator(DiagnosticSink diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    boolean outcomeForExecution(WiredStack stack, WiredContext context, boolean negateConditions) {
        return evaluateForExecution(stack, context, negateConditions).passed();
    }

    EvaluationResult evaluateForExecution(WiredStack stack, WiredContext context, boolean negateConditions) {
        if (!stack.hasConditions()) {
            return new EvaluationResult(!negateConditions, List.of());
        }

        EvaluationResult rawResult = evaluate(stack, context);
        boolean conditionsPassed = rawResult.passed();
        diagnostics.log(context.room(), "Conditions result: {}", conditionsPassed ? "PASSED" : "FAILED");
        return new EvaluationResult(negateConditions ? !conditionsPassed : conditionsPassed, rawResult.conditions());
    }

    private EvaluationResult evaluate(WiredStack stack, WiredContext context) {
        return evaluateByMode(
                stack.conditions(), context, stack.conditionEvaluationMode(), stack.conditionEvaluationValue());
    }

    private EvaluationResult evaluateByMode(
            List<IWiredCondition> conditions, WiredContext context, int evaluationMode, int evaluationValue) {
        if (conditions == null || conditions.isEmpty()) {
            return new EvaluationResult(true, List.of());
        }

        Room room = context.room();
        Map<String, List<Boolean>> groupedOrResults = new LinkedHashMap<>();
        List<ConditionResult> conditionResults = new ArrayList<>(conditions.size());
        int matchedRequirements = 0;
        int totalRequirements = 0;

        for (IWiredCondition condition : conditions) {
            context.state().step();

            boolean result = condition.evaluate(context);
            conditionResults.add(new ConditionResult(condition, result));
            String conditionKey = conditionGroupKey(condition);

            if (condition.operator() == WiredConditionOperator.OR) {
                groupedOrResults
                        .computeIfAbsent(conditionKey, ignored -> new ArrayList<>())
                        .add(result);
                diagnostics.log(
                        room,
                        "  Condition (OR group {}) {}: {}",
                        conditionKey,
                        condition.getClass().getSimpleName(),
                        result ? "PASS" : "FAIL");
                continue;
            }

            totalRequirements++;

            if (result) {
                matchedRequirements++;
            }

            diagnostics.log(room, "  Condition {}: {}", condition.getClass().getSimpleName(), result ? "PASS" : "FAIL");
        }

        for (Map.Entry<String, List<Boolean>> entry : groupedOrResults.entrySet()) {
            totalRequirements++;

            boolean groupPassed = entry.getValue().stream().anyMatch(Boolean::booleanValue);
            if (groupPassed) {
                matchedRequirements++;
            }

            diagnostics.log(room, "  Condition (OR result {}) : {}", entry.getKey(), groupPassed ? "PASS" : "FAIL");
        }

        boolean matches =
                WiredExtraOrEval.matchesMode(evaluationMode, matchedRequirements, totalRequirements, evaluationValue);

        diagnostics.log(
                room,
                "Condition eval mode {} value {} matched {}/{} logical requirements => {}",
                evaluationMode,
                evaluationValue,
                matchedRequirements,
                totalRequirements,
                matches ? "PASS" : "FAIL");
        return new EvaluationResult(matches, List.copyOf(conditionResults));
    }

    private static String conditionGroupKey(IWiredCondition condition) {
        if (condition instanceof InteractionWiredCondition interactionCondition) {
            return String.valueOf(interactionCondition.getType());
        }

        return condition.getClass().getName();
    }

    record ConditionResult(IWiredCondition condition, boolean passed) {}

    record EvaluationResult(boolean passed, List<ConditionResult> conditions) {}
}
