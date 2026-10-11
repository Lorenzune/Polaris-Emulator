package com.eu.habbo.habbohotel.users.levels;

public record AvatarLevelSnapshot(int userId, int level, int highestScore, AvatarLevelScore score) {
    public static AvatarLevelSnapshot advance(int userId, int previousLevel, int previousHigh, AvatarLevelScore score) {
        return new AvatarLevelSnapshot(userId,
                Math.max(Math.clamp(previousLevel, 1, AvatarLevels.MAX_LEVEL), AvatarLevels.level(score.total())),
                Math.max(previousHigh, score.total()), score);
    }
    public int nextLevel() { return level < AvatarLevels.MAX_LEVEL ? level + 1 : 0; }
    public int nextScore() { return nextLevel() == 0 ? 0 : AvatarLevels.threshold(nextLevel()); }
    public int nextReward() { return nextLevel() == 0 ? 0 : AvatarLevels.reward(nextLevel()); }
}
