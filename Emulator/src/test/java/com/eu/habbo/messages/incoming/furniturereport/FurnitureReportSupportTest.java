package com.eu.habbo.messages.incoming.furniturereport;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FurnitureReportSupportTest {

    @Test
    void reportMessageKeepsTheVersionedValuesAndTrimmedReason() {
        JsonObject current = JsonParser.parseString("{\"item_name\":\"chair_rare\",\"sprite_id\":91}").getAsJsonObject();
        JsonObject proposed = JsonParser.parseString("{\"width\":2,\"allowSit\":true}").getAsJsonObject();

        String message = FurnitureReportSupport.buildMessage(current, proposed, "  It should be wider  ");
        JsonObject parsed = FurnitureReportSupport.parseMessage(message);

        assertNotNull(parsed);
        assertEquals("furniture_report", parsed.get("kind").getAsString());
        assertEquals(1, parsed.get("version").getAsInt());
        assertEquals("It should be wider", parsed.get("reason").getAsString());
        assertEquals(current, parsed.getAsJsonObject("currentValues"));
        assertEquals(proposed, parsed.getAsJsonObject("proposedValues"));
    }

    @Test
    void parserRejectsLegacyAndMalformedMessages() {
        assertNull(FurnitureReportSupport.parseMessage("ordinary ticket text"));
        assertNull(FurnitureReportSupport.parseMessage(FurnitureReportSupport.PREFIX + "{"));
    }
}
