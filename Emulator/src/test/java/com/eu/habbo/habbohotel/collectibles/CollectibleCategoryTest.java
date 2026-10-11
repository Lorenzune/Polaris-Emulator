package com.eu.habbo.habbohotel.collectibles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CollectibleCategoryTest {
    @Test
    void completionRequiresEveryDistinctItemCurrentlyOwned() {
        var category = new CollectibleCategory("Ottobre", List.of(42, 43));
        assertDoesNotThrow(category::validate);
        assertFalse(category.complete(Set.of(42)));
        assertTrue(category.complete(Set.of(42, 43, 99)));
        assertFalse(category.complete(Set.of(43)));
    }

    @Test
    void rejectsEmptyAndDuplicateCategoryItems() {
        assertThrows(IllegalArgumentException.class, () -> new CollectibleCategory("C", List.of()).validate());
        assertThrows(IllegalArgumentException.class, () -> new CollectibleCategory("C", List.of(42, 42)).validate());
        assertThrows(IllegalArgumentException.class, () -> new CollectibleCategory("C", List.of(-1)).validate());
    }
}
