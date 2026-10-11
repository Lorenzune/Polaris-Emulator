package com.eu.habbo.habbohotel.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.permissions.PermissionsManager;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

class PermissionHierarchyTest {
    @Test
    void preservesExistingIdHierarchyEvenWhenRankLevelsDiffer() {
        try (var emulator = mockStatic(Emulator.class)) {
            GameEnvironment environment = mock(GameEnvironment.class);
            PermissionsManager permissions = mock(PermissionsManager.class);
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            when(environment.getPermissionsManager()).thenReturn(permissions);
            Rank lower = rank(6, 9), staff = rank(7, 8), higher = rank(8, 1), core = rank(101, 1);
            when(permissions.getAllRanks()).thenReturn(List.of(lower, staff, higher, core));
            Habbo actor = mock(Habbo.class);
            HabboInfo info = info(99, staff);
            when(actor.getHabboInfo()).thenReturn(info);
            assertTrue(CommandTargetGuard.canTarget(actor, info(42, lower)));
            assertFalse(CommandTargetGuard.canTarget(actor, info(42, staff)));
            assertFalse(CommandTargetGuard.canTarget(actor, info(42, higher)));
            assertFalse(CommandTargetGuard.canTarget(actor, info(99, lower)));
            when(info.getRank()).thenReturn(core);
            assertTrue(CommandTargetGuard.canTarget(actor, info(42, core)));
            assertFalse(CommandTargetGuard.canTarget(actor, info(99, core)));
        }
    }

    private static Rank rank(int id, int level) {
        Rank rank = mock(Rank.class);
        when(rank.getId()).thenReturn(id);
        when(rank.getLevel()).thenReturn(level);
        return rank;
    }

    private static HabboInfo info(int id, Rank rank) {
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(id);
        when(info.getRank()).thenReturn(rank);
        return info;
    }
}
