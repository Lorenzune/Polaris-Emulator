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
import java.util.Set;
import java.util.HashSet;
import java.util.IdentityHashMap;
import com.eu.habbo.habbohotel.users.HabboItem;

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
        if (!stack.hasConditions() && referencedGroups(stack, context).isEmpty()) {
            return new EvaluationResult(!negateConditions, List.of());
        }

        EvaluationResult rawResult = evaluate(stack, context);
        boolean conditionsPassed = rawResult.passed();
        diagnostics.log(context.room(), "Conditions result: {}", conditionsPassed ? "PASSED" : "FAILED");
        return new EvaluationResult(negateConditions ? !conditionsPassed : conditionsPassed, rawResult.conditions());
    }

    private EvaluationResult evaluate(WiredStack stack, WiredContext context) {
        List<WiredExtraOrEval> groups = referencedGroups(stack, context);
        if (!groups.isEmpty()) return evaluateGroups(stack, context, groups);
        return evaluateByMode(
                stack.conditions(), context, stack.conditionEvaluationMode(), stack.conditionEvaluationValue());
    }

    private List<WiredExtraOrEval> referencedGroups(WiredStack stack, WiredContext context) {
        HabboItem trigger = stack.triggerItem();
        if (trigger == null || context.room().getRoomSpecialTypes() == null) return List.of();
        var extras = context.room().getRoomSpecialTypes().getExtras(trigger.getX(), trigger.getY());
        if (extras == null) return List.of();
        return extras.stream().filter(WiredExtraOrEval.class::isInstance)
                .map(WiredExtraOrEval.class::cast).filter(WiredExtraOrEval::hasReferencedConditions)
                .sorted(java.util.Comparator.comparingInt(HabboItem::getId)).toList();
    }

    private EvaluationResult evaluateGroups(WiredStack stack, WiredContext context, List<WiredExtraOrEval> groups) {
        GroupEvaluation graph = new GroupEvaluation(context);
        Set<HabboItem> referenced = new HashSet<>();
        for (WiredExtraOrEval group : groups) graph.collect(group, referenced, new HashSet<>());
        List<WiredExtraOrEval> roots = groups.stream().filter(group -> !referenced.contains(group)).toList();
        boolean passed = !roots.isEmpty();
        for (WiredExtraOrEval root : roots) passed &= graph.evaluate(root, new HashSet<>());
        List<IWiredCondition> remaining = stack.conditions().stream()
                .filter(condition -> !(condition instanceof HabboItem item) || !referenced.contains(item)).toList();
        EvaluationResult outside = evaluateByMode(remaining, context, stack.conditionEvaluationMode(), stack.conditionEvaluationValue());
        graph.results.addAll(outside.conditions());
        return new EvaluationResult(passed && outside.passed() && graph.valid, List.copyOf(graph.results));
    }

    /** Selected AND/OR groups form a graph; referenced children are not independent stack requirements. */
    private final class GroupEvaluation {
        private final WiredContext context;
        private final Map<HabboItem, Boolean> cache = new IdentityHashMap<>();
        private final Map<WiredExtraOrEval, List<HabboItem>> children = new IdentityHashMap<>();
        private final List<ConditionResult> results = new ArrayList<>();
        private boolean valid = true;
        private final Set<HabboItem> collected = new HashSet<>();

        private GroupEvaluation(WiredContext context) { this.context = context; }

        private List<HabboItem> children(WiredExtraOrEval group) {
            return children.computeIfAbsent(group, key -> List.copyOf(key.resolveConditions(context)));
        }

        private void collect(WiredExtraOrEval group, Set<HabboItem> referenced, Set<HabboItem> path) {
            if (path.size() >= 64 || path.contains(group)) { valid = false; return; }
            if (!collected.add(group)) return;
            path.add(group);
            for (HabboItem child : children(group)) {
                referenced.add(child);
                if (child instanceof WiredExtraOrEval nested) collect(nested, referenced, path);
                else if (!(child instanceof IWiredCondition)) valid = false;
            }
            path.remove(group);
        }

        private boolean evaluate(HabboItem item, Set<HabboItem> path) {
            if (path.size() >= 64 || !path.add(item)) { valid = false; return false; }
            Boolean previous = cache.get(item);
            if (previous != null) { path.remove(item); return previous; }
            context.state().step();
            boolean passed = false;
            if (item instanceof WiredExtraOrEval group) {
                int matched = 0;
                List<HabboItem> selected = children(group);
                for (HabboItem child : selected) if (evaluate(child, path)) matched++;
                passed = !selected.isEmpty() && WiredExtraOrEval.matchesMode(
                        group.getEvaluationMode(), matched, selected.size(), group.getCompareValue());
            } else if (item instanceof IWiredCondition condition) {
                passed = condition.evaluate(context);
                results.add(new ConditionResult(condition, passed));
            } else valid = false;
            path.remove(item);
            cache.put(item, passed);
            return passed;
        }
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
