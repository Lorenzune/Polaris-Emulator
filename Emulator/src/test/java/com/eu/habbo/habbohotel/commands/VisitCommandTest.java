package com.eu.habbo.habbohotel.commands;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class VisitCommandTest {

    @Test
    void isAvailableToAllRanksWithVisitKey() {
        VisitCommand command = new VisitCommand();
        assertNull(command.permission, "VisitCommand must be available to all ranks (null permission)");
        assertArrayEquals(new String[] {"visit"}, command.keys);
    }

    @Test
    void returnsFalseForNullClientOrHabbo() {
        VisitCommand command = new VisitCommand();
        assertFalse(command.handle(null, new String[] {":visit"}));
    }
}
