package com.eu.habbo.habbohotel.collectibles;

import java.util.List;
import java.util.Set;

public record CollectibleCategory(String title, List<Integer> itemIds) {
    public void validate() {
        if (title == null
                || title.isBlank()
                || title.length() > 100
                || itemIds == null
                || itemIds.isEmpty()
                || itemIds.size() > 500
                || itemIds.stream().anyMatch(id -> id == null || id <= 0)
                || itemIds.stream().distinct().count() != itemIds.size())
            throw new IllegalArgumentException("Nome categoria o articoli non validi (da 1 a 500 articoli distinti).");
    }

    public boolean complete(Set<Integer> owned) {
        return !itemIds.isEmpty() && owned.containsAll(itemIds);
    }
}
