package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementInteractions;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.wired.core.WiredUserMovementHelper;
import com.eu.habbo.plugin.PluginManager;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class RoomUserWalkTimingTest {
    private MockedStatic<Emulator> emulator;
    private MockedStatic<AchievementInteractions> achievements;
    private Room room;
    private RoomLayout layout;
    private Habbo habbo;
    private HabboInfo info;
    private RoomUnit unit;
    private TestManager manager;
    private final List<Runnable> pending = new ArrayList<>();
    private final List<Long> delays = new ArrayList<>();

    @BeforeEach
    void setUp() {
        this.emulator = mockStatic(Emulator.class);
        this.emulator.when(Emulator::getPluginManager).thenReturn(mock(PluginManager.class));
        this.achievements = mockStatic(AchievementInteractions.class);
        this.room = mock(Room.class, RETURNS_DEEP_STUBS);
        this.layout = this.room.getLayout();
        when(this.room.isLoaded()).thenReturn(true);
        when(this.room.tileWalkable(any())).thenReturn(true);
        this.habbo = mock(Habbo.class);
        this.info = mock(HabboInfo.class);
        when(this.habbo.getHabboInfo()).thenReturn(this.info);
        when(this.info.getCurrentRoom()).thenReturn(this.room);
        this.unit = new RoomUnit();
        this.unit.setId(9);
        this.unit.setPathFinderRoom(this.room);
        this.unit.setLocation(tile(0));
        this.unit.setInRoom(true);
        when(this.habbo.getRoomUnit()).thenReturn(this.unit);
        when(this.room.getHabbo(this.unit)).thenReturn(null);
        // Real RoomUnit movement and posture; only the empty-room geometry is stubbed.
        when(this.layout.getTile(anyShort(), anyShort())).thenAnswer(i -> tile(i.getArgument(0, Short.class)));
        when(this.layout.getPathfinder().findPath(any(), any(), any(), same(this.unit)))
                .thenAnswer(i -> {
                    RoomTile target = i.getArgument(1);
                    ArrayDeque<RoomTile> path = new ArrayDeque<>();
                    int direction = Integer.compare(target.x, this.unit.getX());
                    for (int x = this.unit.getX() + direction;
                            direction != 0 && x != target.x + direction;
                            x += direction) path.add(tile(x));
                    return path;
                });
        this.manager = new TestManager();
    }

    @AfterEach
    void tearDown() {
        this.achievements.close();
        this.emulator.close();
    }

    @Test
    void startsAndPublishesFirstStepBeforeAnyScheduledTaskRuns() {
        this.manager.startUserWalk(this.habbo, tile(3));
        assertEquals(1, this.unit.getX());
        assertEquals("1,0,0.0", this.unit.getStatus(RoomUnitStatus.MOVE));
        assertEquals(1, this.manager.published);
        assertEquals(List.of(500L), this.delays);
    }

    @Test
    void roomHeartbeatCannotAddAStepBetweenClickDrivenSteps() {
        this.manager.startUserWalk(this.habbo, tile(3));
        assertFalse(this.manager.cycleRoomUnit(this.unit, RoomUnitType.USER, this.habbo));
        assertEquals(1, this.unit.getX());
        this.pending.removeFirst().run();
        assertEquals(2, this.unit.getX());
        assertEquals(List.of(500L, 500L), this.delays);
    }

    @Test
    void rapidDirectionChangesUseLatestGoalWithoutExtraStepsOrTimers() {
        this.manager.startUserWalk(this.habbo, tile(5));
        this.manager.startUserWalk(this.habbo, tile(3));
        this.manager.startUserWalk(this.habbo, tile(0));
        assertEquals(1, this.unit.getX());
        assertEquals(1, this.pending.size());
        this.pending.removeFirst().run();
        assertEquals(0, this.unit.getX());
        assertEquals(0, this.unit.getGoal().x);
    }

    @Test
    void singleTileWalkSettlesAtItsOwnBoundaryAndReleasesHeartbeat() {
        this.manager.startUserWalk(this.habbo, tile(1));
        assertTrue(this.unit.hasStatus(RoomUnitStatus.MOVE));
        assertFalse(this.manager.cycleRoomUnit(this.unit, RoomUnitType.USER, this.habbo));
        assertTrue(this.unit.hasStatus(RoomUnitStatus.MOVE));
        this.pending.removeFirst().run();
        assertFalse(this.unit.hasStatus(RoomUnitStatus.MOVE));
        assertEquals(2, this.manager.published);
        assertTrue(this.pending.isEmpty());
        this.manager.startUserWalk(this.habbo, tile(2));
        assertEquals(2, this.unit.getX());
    }

    @Test
    void leavingRoomStopsPendingMovement() {
        this.manager.startUserWalk(this.habbo, tile(3));
        when(this.info.getCurrentRoom()).thenReturn(null);
        this.pending.removeFirst().run();
        assertEquals(1, this.unit.getX());
        assertTrue(this.pending.isEmpty());
        assertEquals(1, this.manager.published);
    }

    @Test
    void blockedAvatarDoesNotStartOrScheduleSteps() {
        this.unit.setCanWalk(false);
        this.manager.startUserWalk(this.habbo, tile(3));
        assertEquals(0, this.unit.getX());
        assertTrue(this.pending.isEmpty());
        assertEquals(0, this.manager.published);
    }

    @Test
    void invalidPathDoesNotStartOrScheduleSteps() {
        when(this.layout.getPathfinder().findPath(any(), any(), any(), same(this.unit)))
                .thenReturn(new ArrayDeque<>());
        this.manager.startUserWalk(this.habbo, tile(3));
        assertEquals(0, this.unit.getX());
        assertTrue(this.pending.isEmpty());
        assertEquals(0, this.manager.published);
    }

    @Test
    void wiredMovementTakesOwnershipWithoutAnExtraWalkingStep() {
        this.manager.startUserWalk(this.habbo, tile(3));
        try (var movements = mockStatic(WiredUserMovementHelper.class)) {
            movements
                    .when(() -> WiredUserMovementHelper.shouldSuppressStatusComposer(this.unit))
                    .thenReturn(true);
            this.pending.removeFirst().run();
            assertEquals(1, this.unit.getX());
            assertTrue(this.pending.isEmpty());
            assertEquals(1, this.manager.published);
        }
    }

    @Test
    void lostPathSettlesWithoutSchedulingForever() {
        this.manager.startUserWalk(this.habbo, tile(3));
        this.unit.setPath(new java.util.concurrent.ConcurrentLinkedDeque<>());
        this.pending.removeFirst().run();
        assertEquals(1, this.unit.getX());
        assertTrue(this.pending.isEmpty());
        assertFalse(this.unit.hasStatus(RoomUnitStatus.MOVE));
    }

    private static RoomTile tile(int x) {
        return new RoomTile((short) x, (short) 0, (short) 0, RoomTileState.OPEN, true);
    }

    private final class TestManager extends RoomCycleManager {
        private int published;

        private TestManager() {
            super(room, (task, delay) -> {
                pending.add(task);
                delays.add(delay);
                return mock(ScheduledFuture.class);
            });
        }

        @Override
        protected void publishUnitUpdates(Set<RoomUnit> updates) {
            this.published++;
            assertEquals(Set.of(unit), updates);
        }
    }
}
