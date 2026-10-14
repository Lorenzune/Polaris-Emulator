package com.eu.habbo.habbohotel.toolbar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ToolbarConfigurationTest {
    private static final String VALID = """
        {"version":1,"currencies":[{"type":42,"label":"Monete evento","icon":"/icons/42.webp","color":"#bb77cc","visible":true,"placement":"bubble"}],"widgets":{"radio":false,"voice":true},"bubbles":[{"id":"event","title":"Evento","image":"https://example.com/icon.png","link":"navigator/show","color":"#336699","visible":true}]}
        """;

    @Test
    void roundTripsCustomCurrenciesAndVisibility() {
        var configuration = ToolbarConfiguration.parse(VALID);
        assertEquals(42, configuration.currencies().getFirst().type());
        assertFalse(configuration.widgets().get("radio"));
        assertEquals(configuration, ToolbarConfiguration.parse(ToolbarConfiguration.JSON.toJson(configuration)));
    }

    @Test
    void roundTripsBubbleScheduleSizeAndClosePolicy() {
        var config = ToolbarConfiguration.parse(
                VALID.replace(
                        "\"visible\":true}]}",
                        "\"visible\":true,\"dismissible\":false,\"startsAt\":1800000000,\"endsAt\":1800003600,\"width\":232,\"height\":100,\"linkLabel\":\"Scopri evento\"}]}"));
        String stored = ToolbarConfiguration.JSON.toJson(config);
        org.junit.jupiter.api.Assertions.assertTrue(stored.contains("\"dismissible\":false"));
        org.junit.jupiter.api.Assertions.assertTrue(stored.contains("\"startsAt\":1800000000"));
        org.junit.jupiter.api.Assertions.assertTrue(stored.contains("\"width\":232"));
        assertEquals("Scopri evento", config.bubbles().getFirst().linkLabel());
        assertEquals(config, ToolbarConfiguration.parse(stored));
    }

    @Test
    void rejectsInvalidBubbleScheduleAndDimensions() {
        for (String fields : new String[] {
            "\"startsAt\":200,\"endsAt\":100",
            "\"startsAt\":-1",
            "\"width\":119",
            "\"height\":47",
            "\"width\":321",
            "\"height\":321",
            "\"linkLabel\":\"" + "x".repeat(81) + "\""
        }) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ToolbarConfiguration.parse(
                            VALID.replace("\"visible\":true}]}", "\"visible\":true," + fields + "}]}")));
        }
    }

    @Test
    void rejectsExecutableAndProtocolRelativeUrls() {
        for (String url :
                new String[] {"javascript:alert(1)", "data:text/html,test", "//example.com/test", "file:///tmp/x"}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ToolbarConfiguration.parse(VALID.replace("/icons/42.webp", url)));
        }
    }

    @Test
    void rejectsDuplicateCurrencyIdsInvalidVersionAndUnknownWidgets() {
        var configuration = ToolbarConfiguration.parse(VALID);
        assertThrows(
                IllegalArgumentException.class,
                () -> new ToolbarConfiguration(
                                1,
                                java.util.List.of(
                                        configuration.currencies().getFirst(),
                                        configuration.currencies().getFirst()),
                                configuration.widgets(),
                                configuration.bubbles())
                        .validate());
        assertThrows(
                IllegalArgumentException.class,
                () -> ToolbarConfiguration.parse(VALID.replace("\"version\":1", "\"version\":2")));
        assertThrows(
                IllegalArgumentException.class,
                () -> ToolbarConfiguration.parse(VALID.replace("\"radio\"", "\"unknown\"")));
        assertThrows(IllegalArgumentException.class, () -> ToolbarConfiguration.parse(" ".repeat(24001)));
    }
}
