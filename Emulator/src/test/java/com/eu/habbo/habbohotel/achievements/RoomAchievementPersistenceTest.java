package com.eu.habbo.habbohotel.achievements;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.database.PersistenceExecutor;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.users.levels.AvatarLevelPlatform;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RoomAchievementPersistenceTest {
    @Test
    void roomSubmissionReturnsBeforePersistenceAndKeepsTheExactIncrement() {
        PersistenceExecutor persistence = mock(PersistenceExecutor.class);
        Achievement achievement = mock(Achievement.class);
        var pending = new AtomicReference<Runnable>();
        when(persistence.tryExecute(any())).thenAnswer(invocation -> {
            pending.set(invocation.getArgument(0));
            return true;
        });
        GameEnvironment environment = environment(new AchievementManager(persistence::tryExecute));
        try (var platform = mockStatic(AvatarLevelPlatform.class);
                var manager = mockStatic(AchievementManager.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            platform.when(AvatarLevelPlatform::game).thenReturn(environment);
            manager.when(() -> AchievementManager.progressAchievement(7, achievement, 3))
                    .thenAnswer(ignored -> null);

            assertTrue(AchievementManager.queueProgressAchievement(7, achievement, 3));
            manager.verify(
                    () -> AchievementManager.progressAchievement(7, achievement, 3), org.mockito.Mockito.never());
            pending.get().run();
            manager.verify(() -> AchievementManager.progressAchievement(7, achievement, 3));
        }
    }

    @Test
    void legacyRuntimeWithoutAnExecutorKeepsTheExistingPersistenceBehavior() {
        Achievement achievement = mock(Achievement.class);
        GameEnvironment environment = environment(new AchievementManager());
        try (var platform = mockStatic(AvatarLevelPlatform.class);
                var manager = mockStatic(AchievementManager.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            platform.when(AvatarLevelPlatform::game).thenReturn(environment);
            manager.when(() -> AchievementManager.progressAchievement(7, achievement, 2))
                    .thenAnswer(ignored -> null);
            assertTrue(AchievementManager.queueProgressAchievement(7, achievement, 2));
            manager.verify(() -> AchievementManager.progressAchievement(7, achievement, 2));
        }
    }

    @Test
    void saturationIsReportedToTimersAndFinalExitFallsBackWithoutDroppingMinutes() {
        PersistenceExecutor persistence = mock(PersistenceExecutor.class);
        Achievement achievement = mock(Achievement.class);
        GameEnvironment environment = environment(new AchievementManager(persistence::tryExecute));
        try (var platform = mockStatic(AvatarLevelPlatform.class);
                var manager = mockStatic(AchievementManager.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            platform.when(AvatarLevelPlatform::game).thenReturn(environment);
            manager.when(() -> AchievementManager.progressAchievement(7, achievement, 2))
                    .thenAnswer(ignored -> null);

            assertFalse(AchievementManager.queueProgressAchievement(7, achievement, 2));
            manager.verify(
                    () -> AchievementManager.progressAchievement(7, achievement, 2), org.mockito.Mockito.never());
            AchievementManager.flushProgressAchievement(7, achievement, 2);
            manager.verify(() -> AchievementManager.progressAchievement(7, achievement, 2));
            verify(persistence, org.mockito.Mockito.times(2)).tryExecute(any());
        }
    }

    private static GameEnvironment environment(AchievementManager manager) {
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getAchievementManager()).thenReturn(manager);
        return environment;
    }
}
