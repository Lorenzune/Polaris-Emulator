package com.eu.habbo.messages.outgoing.achievements;

import com.eu.habbo.habbohotel.achievements.Achievement;
import com.eu.habbo.habbohotel.achievements.AchievementLevel;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.Collection;

/** Rank-gated metadata used by the in-client Traguardi editor. */
public class AchievementEditorStateComposer extends MessageComposer {
    private final boolean canEdit;
    private final Collection<Achievement> achievements;

    public AchievementEditorStateComposer(boolean canEdit, Collection<Achievement> achievements) {
        this.canEdit = canEdit;
        this.achievements = achievements;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.AchievementEditorStateComposer);
        this.response.appendBoolean(this.canEdit);
        this.response.appendInt(this.canEdit && this.achievements != null ? this.achievements.size() : 0);

        if (!this.canEdit || this.achievements == null) return this.response;

        for (Achievement achievement : this.achievements) {
            this.response.appendInt(achievement.id);
            this.response.appendString(achievement.name);
            this.response.appendString(achievement.categoryCode);
            this.response.appendShort(achievement.state);
            this.response.appendInt(achievement.levels.size());

            achievement.levels.values().stream()
                    .sorted(java.util.Comparator.comparingInt(level -> level.level))
                    .forEach(level -> appendLevel(level));
        }

        return this.response;
    }

    private void appendLevel(AchievementLevel level) {
        this.response.appendInt(level.level);
        this.response.appendInt(level.progress);
        this.response.appendInt(level.rewardAmount);
        this.response.appendInt(level.rewardType);
        this.response.appendInt(level.points);
    }
}
