package com.eu.habbo.habbohotel.quests;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QuestRulesTest {
    QuestDefinition quest(String code, int target) {
        var d = new QuestDefinition();
        d.code = code;
        d.title = code;
        d.target = target;
        return d;
    }

    @Test
    void giveChangeRemoveAndRestartCannotGrantTheRewardTwice() {
        var d = quest("welcome", 3);
        var definitions = List.of(d);
        var start = QuestRules.apply(definitions, Map.of(), d.code, "give", 0, false, 10);
        assertTrue(start.progress().get(d.code).active);
        assertTrue(start.completed().isEmpty());
        var increment = QuestRules.apply(definitions, start.progress(), d.code, "change", 1, true, 20);
        assertEquals(1, increment.progress().get(d.code).value);
        assertEquals(0, start.progress().get(d.code).value);
        var complete = QuestRules.apply(definitions, increment.progress(), d.code, "change", 9, true, 30);
        assertEquals(3, complete.progress().get(d.code).value);
        assertEquals(List.of(d), complete.completed());
        var reset = QuestRules.apply(definitions, complete.progress(), d.code, "remove", 0, true, 40);
        assertFalse(reset.progress().get(d.code).active);
        assertTrue(reset.progress().get(d.code).rewarded);
        var retry = QuestRules.apply(definitions, reset.progress(), d.code, "give", 3, false, 50);
        assertTrue(retry.completed().isEmpty());
        assertTrue(retry.progress().get(d.code).rewarded);
    }

    @Test
    void unassignedAndAlreadyAssignedVariablesFollowNormalWiredSemantics() {
        var d = quest("welcome", 3);
        var ds = List.of(d);
        assertFalse(
                QuestRules.apply(ds, Map.of(), d.code, "change", 1, true, 10).changed());
        var p = QuestRules.apply(ds, Map.of(), d.code, "give", 1, false, 10);
        assertFalse(
                QuestRules.apply(ds, p.progress(), d.code, "give", 2, false, 20).changed());
        assertEquals(
                2,
                QuestRules.apply(ds, p.progress(), d.code, "give", 2, true, 20)
                        .progress()
                        .get(d.code)
                        .value);
        assertEquals(
                0,
                QuestRules.apply(ds, p.progress(), d.code, "change", -5, true, 20)
                        .progress()
                        .get(d.code)
                        .value);
    }

    @Test
    void chainGatesLaterMissionsAndAutomaticallyStartsTheNext() {
        var chain = quest("tour", 1);
        chain.chain = true;
        var one = quest("first", 2);
        one.chainCode = chain.code;
        var two = quest("second", 3);
        two.chainCode = chain.code;
        two.position = 1;
        var ds = List.of(chain, one, two);
        assertFalse(
                QuestRules.apply(ds, Map.of(), two.code, "give", 3, false, 10).changed());
        var start = QuestRules.apply(ds, Map.of(), chain.code, "give", 0, false, 10);
        assertTrue(start.progress().get(one.code).active);
        assertFalse(start.progress().get(two.code).active);
        var first = QuestRules.apply(ds, start.progress(), one.code, "change", 2, true, 20);
        assertEquals(1, first.progress().get(chain.code).value);
        assertTrue(first.progress().get(two.code).active);
        assertEquals(List.of(one), first.completed());
        var last = QuestRules.apply(ds, first.progress(), two.code, "change", 3, true, 30);
        assertEquals(2, last.progress().get(chain.code).value);
        assertEquals(Set.of(chain, two), Set.copyOf(last.completed()));
        var reset = QuestRules.apply(ds, last.progress(), chain.code, "remove", 0, true, 40);
        assertTrue(reset.progress().values().stream().noneMatch(p -> p.active));
        assertTrue(QuestRules.apply(ds, reset.progress(), chain.code, "give", 2, false, 50)
                .completed()
                .isEmpty());
    }

    @Test
    void disabledAndInvalidConfigurationAreRejected() {
        var d = quest("welcome", 1);
        d.enabled = false;
        assertThrows(
                IllegalArgumentException.class,
                () -> QuestRules.apply(List.of(d), Map.of(), d.code, "give", 0, false, 10));
        d.title = null;
        assertThrows(IllegalArgumentException.class, d::validate);
        d.title = "Welcome";
        d.reward.kind = "currency";
        d.reward.currencyType = 88;
        assertThrows(IllegalArgumentException.class, d::validate);
        d.reward.currencyType = 5;
        assertDoesNotThrow(d::validate);
    }

    @Test
    void expiryBlocksChangesAndRewardsAtTheExactBoundary() {
        var d = quest("timed", 1);
        d.expiresAt = 20;
        var started = QuestRules.apply(List.of(d), Map.of(), d.code, "give", 0, false, 19);
        assertThrows(
                IllegalArgumentException.class,
                () -> QuestRules.apply(List.of(d), started.progress(), d.code, "change", 1, true, 20));
        assertFalse(started.progress().get(d.code).rewarded);
        assertTrue(QuestRules.apply(List.of(d), started.progress(), d.code, "change", 1, true, 19)
                .completed()
                .contains(d));
    }

    @Test
    void expiredChainBlocksItsChildrenAndExpiredStepsCannotBeCompletedThroughTheChain() {
        var chain = quest("chain", 1);
        chain.chain = true;
        chain.expiresAt = 20;
        var step = quest("step", 1);
        step.chainCode = chain.code;
        assertFalse(QuestRules.available(step, List.of(chain, step), 20));
        assertThrows(
                IllegalArgumentException.class,
                () -> QuestRules.apply(List.of(chain, step), Map.of(), step.code, "give", 1, false, 20));
        chain.expiresAt = 0;
        step.expiresAt = 20;
        assertThrows(
                IllegalArgumentException.class,
                () -> QuestRules.apply(List.of(chain, step), Map.of(), chain.code, "give", 1, false, 20));
    }

    @Test
    void validatesImageUrlsAndKeepsUnscheduledDefinitionsAvailable() {
        var d = quest("image", 1);
        assertTrue(d.available(Long.MAX_VALUE));
        d.imageUrl = "https://images.habbo.com/c_images/Quests/quest_arrow1.png";
        assertDoesNotThrow(d::validate);
        d.imageUrl = "/assets/mission.png";
        assertDoesNotThrow(d::validate);
        d.imageUrl = "javascript:alert(1)";
        assertThrows(IllegalArgumentException.class, d::validate);
        d.imageUrl = "";
        d.expiresAt = -1;
        assertThrows(IllegalArgumentException.class, d::validate);
    }
}
