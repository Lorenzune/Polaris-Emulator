package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class ImportRoomCommand extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImportRoomCommand.class);

    public ImportRoomCommand() {
        super("", new String[]{"importroom", "importwired"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient.getHabbo() == null || gameClient.getHabbo().getHabboInfo() == null) return false;
        Room room = gameClient.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) return false;

        if (room.getOwnerId() != gameClient.getHabbo().getHabboInfo().getId() && gameClient.getHabbo().getHabboInfo().getRank().getId() < 6) {
            gameClient.getHabbo().whisper("Puoi importare una stanza solo nelle tue stanze o con rango staff.", RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (params.length < 2) {
            gameClient.getHabbo().whisper("Uso: :importroom <nomefile> (es: :importroom test1)", RoomChatMessageBubbles.ALERT);
            return true;
        }

        String importDir = Emulator.getConfig().getValue("room.import.path", "d:/ducket");
        String scriptPath = Emulator.getConfig().getValue("room.import.script.path", "d:/ducket/tools/import_room.js");
        String nodeExecutable = Emulator.getConfig().getValue("room.import.node.executable", "node");

        String fileName = params[1].trim();
        if (!fileName.endsWith(".json")) {
            fileName += ".json";
        }

        File file = new File(fileName);
        if (!file.exists()) {
            file = new File(importDir, fileName);
        }
        if (!file.exists()) {
            file = new File("d:/ducket/" + fileName);
        }
        if (!file.exists()) {
            gameClient.getHabbo().whisper("File non trovato: " + fileName + " (cercato in: " + importDir + ")", RoomChatMessageBubbles.ALERT);
            return true;
        }

        gameClient.getHabbo().whisper("Importazione stanza in corso da " + file.getName() + "...", RoomChatMessageBubbles.WIRED);

        final String finalFilePath = file.getAbsolutePath();
        final int roomId = room.getId();
        final int userId = gameClient.getHabbo().getHabboInfo().getId();

        Emulator.getThreading().run(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(nodeExecutable, scriptPath, finalFilePath, String.valueOf(roomId), String.valueOf(userId), "--clean");
                pb.redirectErrorStream(true);
                Process process = pb.start();

                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }

                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    gameClient.getHabbo().whisper("Stanza importata con successo! Ricarico la stanza...", RoomChatMessageBubbles.WIRED);
                    Emulator.getThreading().run(room::dispose, 500);
                } else {
                    LOGGER.error("Import room failed:\n{}", output);
                    gameClient.getHabbo().whisper("Errore durante l'importazione: exit code " + exitCode, RoomChatMessageBubbles.ALERT);
                }
            } catch (Exception e) {
                LOGGER.error("Error executing import room", e);
                gameClient.getHabbo().whisper("Errore: " + e.getMessage(), RoomChatMessageBubbles.ALERT);
            }
        });

        return true;
    }
}
