package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.navigation.NavigatorFilterComparator;
import com.eu.habbo.habbohotel.navigation.NavigatorFilterField;
import org.junit.jupiter.api.Test;

class RoomManagerSearchQueryTest {

    @Test
    void descriptionFilterSearchesRoomDescriptionOnly() {
        RoomManager.RoomSearchQuery query = query("desc", "garden", -1, true);

        assertTrue(query.sql().contains("rooms.description LIKE ?"));
        assertEquals("%garden%", query.parameters().getFirst());
    }

    @Test
    void promotionFilterSearchesActivePromotionTitleAndDescription() {
        RoomManager.RoomSearchQuery query = query("promo", "summer", -1, true);

        assertTrue(query.sql().contains("rp.room_id = rooms.id"));
        assertTrue(query.sql().contains("rp.end_timestamp > UNIX_TIMESTAMP()"));
        assertTrue(query.sql().contains("rp.title"));
        assertTrue(query.sql().contains("rp.description"));
        assertEquals("%summer%", query.parameters().getFirst());
    }

    @Test
    void groupFilterSearchesNameAndDescription() {
        RoomManager.RoomSearchQuery query = query("group", "builders", -1, true);

        assertTrue(query.sql().contains("g.room_id = rooms.id"));
        assertTrue(query.sql().contains("g.name"));
        assertTrue(query.sql().contains("g.description"));
        assertEquals("%builders%", query.parameters().getFirst());
    }

    @Test
    void visibilityAndCategoryRestrictionsAreAppliedAfterFilterPredicate() {
        RoomManager.RoomSearchQuery query = query("desc", "secret", 12, false);

        assertTrue(query.sql().contains("WHERE rooms.description LIKE ? AND rooms.state != 'invisible'"));
        assertTrue(query.sql().contains("AND rooms.category = 12"));
    }

    @Test
    void tagFilterMatchesCompleteSemicolonDelimitedTags() {
        RoomManager.RoomSearchQuery query = query("tag", "retro", -1, true);

        assertTrue(query.sql().contains("rooms.tags = ?"));
        assertTrue(query.sql().contains("CONCAT(?, ';%')"));
        assertTrue(query.sql().contains("CONCAT('%;', ?, ';%')"));
        assertTrue(query.sql().contains("CONCAT('%;', ?)"));
        assertEquals(4, query.parameters().size());
    }

    private static RoomManager.RoomSearchQuery query(String key, String value, int category, boolean showInvisible) {
        NavigatorFilterField field = new NavigatorFilterField(
                key, null, "", NavigatorFilterComparator.CONTAINS);
        return RoomManager.buildRoomSearchQuery(field, value, category, showInvisible);
    }
}
