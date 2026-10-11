package com.eu.habbo.habbohotel.collectibles;

import java.util.List;

public record CollectibleReward(
        String title,
        String collection,
        String kind,
        int itemId,
        int currencyType,
        int amount,
        int xp,
        long expiresAt,
        List<Integer> recipients,
        Integer requiredLevel,
        String badgeCode) {
    public CollectibleReward(
            String title,
            String collection,
            String kind,
            int itemId,
            int currencyType,
            int amount,
            int xp,
            long expiresAt,
            List<Integer> recipients,
            Integer requiredLevel) {
        this(title, collection, kind, itemId, currencyType, amount, xp, expiresAt, recipients, requiredLevel, null);
    }

    public CollectibleReward(
            String title,
            String collection,
            String kind,
            int itemId,
            int currencyType,
            int amount,
            int xp,
            long expiresAt,
            List<Integer> recipients) {
        this(title, collection, kind, itemId, currencyType, amount, xp, expiresAt, recipients, null);
    }

    public void validate(long now) {
        if (title == null || title.isBlank() || title.length() > 100 || collection == null || collection.length() > 100)
            throw new IllegalArgumentException("Titolo o collezione non validi.");
        if (!List.of("item", "currency", "badge").contains(kind)
                || amount < 1
                || amount > ("item".equals(kind) ? 100 : 1000000)
                || xp < 0
                || xp > 1000000
                || (expiresAt != 0 && (expiresAt <= now || expiresAt > now + 315360000L)))
            throw new IllegalArgumentException("Quantità, XP o scadenza non validi.");
        if (("item".equals(kind) && itemId <= 0) || ("currency".equals(kind) && currencyType < -1))
            throw new IllegalArgumentException("Articolo o valuta non validi.");
        if ("badge".equals(kind) && (!validBadgeCode(badgeCode) || amount != 1))
            throw new IllegalArgumentException(
                    "Codice badge non valido: usa lettere, numeri, trattini e underscore (massimo 50 caratteri). Un badge per premio.");
        if (recipients == null
                || recipients.size() > 1000
                || recipients.stream().anyMatch(id -> id == null || id <= 0)
                || recipients.stream().distinct().count() != recipients.size())
            throw new IllegalArgumentException("Destinatari non validi.");
        if (requiredLevel != null && (requiredLevel < 0 || !recipients.isEmpty()))
            throw new IllegalArgumentException("Scegli un livello valido oppure utenti specifici.");
    }

    public static boolean validBadgeCode(String code) {
        return code != null && code.matches("[A-Za-z0-9_-]{1,50}");
    }

    public boolean availableTo(int userId, long now, boolean enabled, boolean claimed) {
        return availableTo(userId, 0, now, enabled, claimed);
    }

    public boolean availableTo(int userId, int userLevel, long now, boolean enabled, boolean claimed) {
        return enabled
                && !claimed
                && (expiresAt == 0 || now < expiresAt)
                && (recipients.isEmpty() || recipients.contains(userId))
                && (requiredLevel == null || requiredLevel == userLevel);
    }

    public static int level(long xp) {
        return (int) Math.floor((Math.sqrt(1 + 8.0 * Math.max(0, xp) / 100) - 1) / 2);
    }

    public static long nextLevelXp(long xp) {
        long next = level(xp) + 1L;
        return next * (next + 1) * 50;
    }
}
