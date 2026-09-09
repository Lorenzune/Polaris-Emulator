package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import java.util.HashMap;
import java.util.Map;

public class EventCommand extends Command {
    public EventCommand() {
        super("cmd_event", Emulator.getTexts().getValue("commands.keys.cmd_event").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        if (gameClient == null || gameClient.getHabbo() == null) {
            return false;
        }

        Room currentRoom = gameClient.getHabbo().getHabboInfo().getCurrentRoom();
        if (currentRoom == null) {
            return true;
        }

        StringBuilder extraMessage = new StringBuilder();
        if (params.length >= 2) {
            for (int i = 1; i < params.length; i++) {
                if (i > 1) {
                    extraMessage.append(" ");
                }
                extraMessage.append(params[i]);
            }
        }

        String username = gameClient.getHabbo().getHabboInfo().getUsername();
        String roomName = currentRoom.getName();
        int roomId = currentRoom.getId();
        String customText = extraMessage.toString().trim();

        StringBuilder formattedMessage = new StringBuilder();
        formattedMessage.append("<b>").append(username).append("</b> ha appena aperto un nuovo evento: <b>").append(roomName).append("</b>");
        if (!customText.isEmpty()) {
            formattedMessage.append("\r\r").append(customText);
        }

        Map<String, String> codes = new HashMap<>();
        codes.put("title", "Nuovo Evento!");
        codes.put("message", formattedMessage.toString());
        codes.put("MESSAGE", formattedMessage.toString());
        codes.put("username", username);
        codes.put("USERNAME", username);
        codes.put("roomName", roomName);
        codes.put("ROOMNAME", roomName);
        codes.put("roomId", String.valueOf(roomId));
        codes.put("ROOMID", String.valueOf(roomId));
        codes.put("customMessage", customText);
        codes.put("linkUrl", "navigator/goto/" + roomId);
        codes.put("linkTitle", "Entra in stanza");
        codes.put("LOOK", gameClient.getHabbo().getHabboInfo().getLook());
        codes.put("TIME", Emulator.getDate().toString());

        ServerMessage msg = new BubbleAlertComposer("hotel.event", codes).compose();

        for (Habbo habbo : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().values()) {
            if (habbo == null || !habbo.isOnline() || habbo.getHabboStats().blockStaffAlerts) {
                continue;
            }

            if (habbo.getClient() != null) {
                habbo.getClient().sendResponse(msg);
            }
        }

        return true;
    }
}
