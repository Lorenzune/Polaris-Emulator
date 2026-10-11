package com.eu.habbo.habbohotel.messenger;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FriendRoomPresenceContractTest {
    @Test
    void successfulEntryPublishesPresenceAfterAssigningTheRoom() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/RoomManager.java"));
        int assignment = source.indexOf("habbo.getHabboInfo().setCurrentRoom(room);");
        int notification = source.indexOf("habbo.getMessenger().connectionChanged(habbo, true, true);");
        assertTrue(
                assignment >= 0 && notification > assignment,
                "Friend packets resolve currentRoom, so an earlier notification reports not followable");
    }
}
