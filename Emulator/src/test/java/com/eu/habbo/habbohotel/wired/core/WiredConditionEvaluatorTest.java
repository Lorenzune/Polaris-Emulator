package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraOrEval;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionOperator;
import com.eu.habbo.habbohotel.wired.api.IWiredCondition;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WiredConditionEvaluatorTest {

    @Test
    void preservesPhysicalOrderOrGroupingStepAccountingAndNegation() {
        Room room = mock(Room.class);
        WiredServices services = mock(WiredServices.class);
        List<String> evaluationOrder = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();

        WiredStack stack = stack(List.of(
                new TestCondition("and", true, WiredConditionOperator.AND, evaluationOrder),
                new TestCondition("or-false", false, WiredConditionOperator.OR, evaluationOrder),
                new TestCondition("or-true", true, WiredConditionOperator.OR, evaluationOrder)));

        WiredState normalState = new WiredState(10);
        WiredContext normalContext = context(room, services, stack, normalState);
        WiredConditionEvaluator evaluator =
                new WiredConditionEvaluator((ignoredRoom, format, arguments) -> diagnostics.add(format));

        WiredConditionEvaluator.EvaluationResult result = evaluator.evaluateForExecution(stack, normalContext, false);

        assertTrue(result.passed());
        assertEquals(
                List.of(true, false, true),
                result.conditions().stream()
                        .map(WiredConditionEvaluator.ConditionResult::passed)
                        .toList());
        assertEquals(List.of("and", "or-false", "or-true"), evaluationOrder);
        assertEquals(3, normalState.steps());
        assertTrue(diagnostics.contains("Conditions result: {}"));

        evaluationOrder.clear();
        WiredState negatedState = new WiredState(10);
        assertFalse(evaluator.outcomeForExecution(stack, context(room, services, stack, negatedState), true));
        assertEquals(List.of("and", "or-false", "or-true"), evaluationOrder);
        assertEquals(3, negatedState.steps());
    }

    @Test
    void preservesNoConditionNegationWithoutConsumingSteps() {
        Room room = mock(Room.class);
        WiredServices services = mock(WiredServices.class);
        WiredStack stack = stack(List.of());
        WiredState state = new WiredState(10);
        WiredConditionEvaluator evaluator = new WiredConditionEvaluator((ignoredRoom, format, arguments) -> {});

        assertFalse(evaluator.outcomeForExecution(stack, context(room, services, stack, state), true));
        assertEquals(0, state.steps());
    }

    @Test
    void clickTurnGroupsRespectBothSeatsAndBothTurnStates() {
        for (int bits = 0; bits < 16; bits++) {
            Room room = mock(Room.class);
            RoomSpecialTypes types = mock(RoomSpecialTypes.class);
            when(room.getRoomSpecialTypes()).thenReturn(types);
            HabboItem trigger = mock(HabboItem.class);
            InteractionWiredCondition leftTurn = mock(InteractionWiredCondition.class);
            InteractionWiredCondition rightTurn = mock(InteractionWiredCondition.class);
            InteractionWiredCondition leftSeat = mock(InteractionWiredCondition.class);
            InteractionWiredCondition rightSeat = mock(InteractionWiredCondition.class);
            boolean lt = (bits & 1) != 0, ls = (bits & 2) != 0;
            boolean rt = (bits & 4) != 0, rs = (bits & 8) != 0;
            WiredStack stack =
                    new WiredStack(trigger, null, List.of(leftTurn, rightTurn, leftSeat, rightSeat), List.of());
            WiredContext context = context(room, mock(WiredServices.class), stack, new WiredState(100));
            when(leftTurn.evaluate(context)).thenReturn(lt);
            when(leftSeat.evaluate(context)).thenReturn(ls);
            when(rightTurn.evaluate(context)).thenReturn(rt);
            when(rightSeat.evaluate(context)).thenReturn(rs);
            WiredExtraOrEval left = group(1, WiredExtraOrEval.MODE_ALL, context, List.of(leftTurn, leftSeat));
            WiredExtraOrEval right = group(2, WiredExtraOrEval.MODE_ALL, context, List.of(rightTurn, rightSeat));
            WiredExtraOrEval either = group(3, WiredExtraOrEval.MODE_AT_LEAST_ONE, context, List.of(left, right));
            when(types.getExtras(0, 0)).thenReturn(java.util.Set.of(left, right, either));
            WiredConditionEvaluator evaluator = new WiredConditionEvaluator((r, f, a) -> {});
            var result = evaluator.evaluateForExecution(stack, context, false);
            assertEquals((lt && ls) || (rt && rs), result.passed(), "seat/turn combination " + bits);
            assertEquals(4, result.conditions().size());
        }
    }

    @Test
    void cyclicGroupsFailWithoutRecursionOverflowEvenInNoneMode() {
        Room room = mock(Room.class);
        RoomSpecialTypes types = mock(RoomSpecialTypes.class);
        when(room.getRoomSpecialTypes()).thenReturn(types);
        HabboItem trigger = mock(HabboItem.class);
        WiredStack stack = new WiredStack(trigger, null, List.of(), List.of());
        WiredContext context = context(room, mock(WiredServices.class), stack, new WiredState(100));
        WiredExtraOrEval cycle = group(1, WiredExtraOrEval.MODE_NONE, context, List.of());
        when(cycle.resolveConditions(context)).thenReturn(List.of(cycle));
        when(types.getExtras(0, 0)).thenReturn(java.util.Set.of(cycle));
        assertFalse(new WiredConditionEvaluator((r, f, a) -> {}).outcomeForExecution(stack, context, false));
    }

    @Test
    void independentStackConditionStillBlocksPassingNestedGroup() {
        Room room = mock(Room.class);
        RoomSpecialTypes types = mock(RoomSpecialTypes.class);
        when(room.getRoomSpecialTypes()).thenReturn(types);
        HabboItem trigger = mock(HabboItem.class);
        InteractionWiredCondition selected = mock(InteractionWiredCondition.class);
        List<String> order = new ArrayList<>();
        WiredStack stack = new WiredStack(
                trigger,
                null,
                List.of(selected, new TestCondition("outside", false, WiredConditionOperator.AND, order)),
                List.of());
        WiredContext context = context(room, mock(WiredServices.class), stack, new WiredState(100));
        when(selected.evaluate(context)).thenReturn(true);
        WiredExtraOrEval group = group(1, WiredExtraOrEval.MODE_AT_LEAST_ONE, context, List.of(selected));
        when(types.getExtras(0, 0)).thenReturn(java.util.Set.of(group));
        var result = new WiredConditionEvaluator((r, f, a) -> {}).evaluateForExecution(stack, context, false);
        assertFalse(result.passed());
        assertEquals(2, result.conditions().size());
        assertEquals(List.of("outside"), order);
    }

    private static WiredExtraOrEval group(int id, int mode, WiredContext context, List<HabboItem> children) {
        WiredExtraOrEval group = mock(WiredExtraOrEval.class);
        when(group.getId()).thenReturn(id);
        when(group.hasReferencedConditions()).thenReturn(true);
        when(group.getEvaluationMode()).thenReturn(mode);
        when(group.getCompareValue()).thenReturn(1);
        when(group.resolveConditions(context)).thenReturn(children);
        return group;
    }

    private static WiredStack stack(List<IWiredCondition> conditions) {
        return new WiredStack(null, null, conditions, List.of(), WiredExtraOrEval.MODE_ALL, 1, false, false, false);
    }

    private static WiredContext context(Room room, WiredServices services, WiredStack stack, WiredState state) {
        WiredEvent event = WiredEvent.builder(WiredEvent.Type.CUSTOM, room).build();
        return new WiredContext(event, null, stack, services, state, null);
    }

    private static final class TestCondition implements IWiredCondition {
        private final String name;
        private final boolean result;
        private final WiredConditionOperator operator;
        private final List<String> evaluationOrder;

        private TestCondition(
                String name, boolean result, WiredConditionOperator operator, List<String> evaluationOrder) {
            this.name = name;
            this.result = result;
            this.operator = operator;
            this.evaluationOrder = evaluationOrder;
        }

        @Override
        public boolean evaluate(WiredContext context) {
            this.evaluationOrder.add(this.name);
            return this.result;
        }

        @Override
        public WiredConditionOperator operator() {
            return this.operator;
        }
    }
}
