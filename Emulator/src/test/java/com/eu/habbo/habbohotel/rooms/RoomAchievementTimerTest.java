package com.eu.habbo.habbohotel.rooms;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.achievements.Achievement;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.users.Habbo;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class RoomAchievementTimerTest {
    @Test
    void rejectedTimerSubmissionRetainsItsMinutesAndSuccessfulRetryIsNotCountedAgainOnExit() throws Exception {
        Room room = mock(Room.class);
        Habbo visitor = mock(Habbo.class, RETURNS_DEEP_STUBS);
        when(visitor.getHabboInfo().getId()).thenReturn(7);
        visitor.getHabboStats().roomEnterTimestamp = System.currentTimeMillis() / 1000 - 125;
        var visitors = new ConcurrentHashMap<Integer, Habbo>();
        visitors.put(7, visitor);
        when(room.getCurrentHabbos()).thenReturn(visitors);
        when(room.isPublicRoom()).thenReturn(true);
        GameEnvironment environment = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        Achievement achievement = mock(Achievement.class);
        when(environment.getAchievementManager().getAchievement("TimeSpentRoomTypeChat"))
                .thenReturn(achievement);
        var cycle = new RoomCycleManager(room, (task, delay) -> null);
        var timers = RoomCycleManager.class.getDeclaredMethod("processTimeSpentRoomAchievements");
        timers.setAccessible(true);
        try (var emulator = mockStatic(Emulator.class);
                var progress = mockStatic(AchievementManager.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            progress.when(() -> AchievementManager.queueProgressAchievement(7, achievement, 2))
                    .thenReturn(false, true);

            timers.invoke(cycle);
            timers.invoke(cycle);
            cycle.flushTimeSpentVisitor(visitor);

            progress.verify(() -> AchievementManager.queueProgressAchievement(7, achievement, 2), times(2));
            progress.verify(
                    () -> AchievementManager.flushProgressAchievement(eq(7), eq(achievement), anyInt()), never());
        }
    }
}
