package com.eu.habbo.messages.incoming.rooms.bots;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eu.habbo.habbohotel.bots.BotManager;
import org.junit.jupiter.api.Test;

class BotChatSpeedTest {
    @Test
    void extremeInputsStayWithinPositiveShortRangeAndConfiguredBounds() {
        int minimum = BotManager.MINIMUM_CHAT_SPEED;
        int maximum = BotManager.MAXIMUM_CHAT_SPEED;
        try {
            BotManager.MINIMUM_CHAT_SPEED = 7;
            BotManager.MAXIMUM_CHAT_SPEED = 100;
            assertEquals(7, BotSaveSettingsEvent.clampChatSpeed(Integer.MIN_VALUE));
            assertEquals(100, BotSaveSettingsEvent.clampChatSpeed(Integer.MAX_VALUE));
            assertEquals(20, BotSaveSettingsEvent.clampChatSpeed(20));
            BotManager.MAXIMUM_CHAT_SPEED = Integer.MAX_VALUE;
            assertEquals(Short.MAX_VALUE, BotSaveSettingsEvent.clampChatSpeed(Integer.MAX_VALUE));
        } finally {
            BotManager.MINIMUM_CHAT_SPEED = minimum;
            BotManager.MAXIMUM_CHAT_SPEED = maximum;
        }
    }
}
