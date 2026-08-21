package com.eu.habbo.habbohotel.earnings;

import java.util.Arrays;
import java.util.Optional;

public enum EarningsCategory {
    DAILY_GIFT("daily_gift"),
    GAMES("games"),
    ACHIEVEMENTS("achievements"),
    MARKETPLACE("marketplace"),
    /** Retained for plugins compiled against the released Polaris API. */
    @Deprecated
    CLUB_JOB("club_job"),
    HC_PAYDAY("hc_payday"),
    LEVEL_PROGRESS("level_progress"),
    DONATIONS("donations"),
    BONUS_BAG("bonus_bag"),
    MYSTERY_BOXES("mystery_boxes");

    private final String key;

    EarningsCategory(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public static Optional<EarningsCategory> fromKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }

        String normalized = key.trim().toLowerCase();
        return Arrays.stream(values())
                .filter(category -> category.key.equals(normalized))
                .findFirst();
    }
}
