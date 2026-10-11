package com.eu.habbo.habbohotel.quests;

public class QuestProgress {
    public boolean active, rewarded;
    public int value, createdAt, updatedAt;

    public QuestProgress copy() {
        var p = new QuestProgress();
        p.active = active;
        p.rewarded = rewarded;
        p.value = value;
        p.createdAt = createdAt;
        p.updatedAt = updatedAt;
        return p;
    }
}
