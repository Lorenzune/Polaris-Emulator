package com.eu.habbo.habbohotel.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SetRoomTemplateCommandTest {

    @Test
    void acceptsLocalizedAndItalianConfirmations() {
        assertTrue(SetRoomTemplateCommand.isConfirmation("Yes", "Yes"));
        assertTrue(SetRoomTemplateCommand.isConfirmation("yes", "Yes"));
        assertTrue(SetRoomTemplateCommand.isConfirmation("si", "Yes"));
        assertTrue(SetRoomTemplateCommand.isConfirmation("sì", "Yes"));
        assertTrue(SetRoomTemplateCommand.isConfirmation("  SÌ  ", "Yes"));
    }

    @Test
    void rejectsMissingOrUnrelatedConfirmations() {
        assertFalse(SetRoomTemplateCommand.isConfirmation(null, "Yes"));
        assertFalse(SetRoomTemplateCommand.isConfirmation("", "Yes"));
        assertFalse(SetRoomTemplateCommand.isConfirmation("no", "Yes"));
    }
}
