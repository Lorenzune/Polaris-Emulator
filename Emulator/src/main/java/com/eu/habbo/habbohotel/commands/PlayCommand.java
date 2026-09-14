package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.outgoing.rooms.youtube.YouTubeRoomBroadcastComposer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlayCommand extends Command {

    private static final Pattern YOUTUBE_PATTERN = Pattern.compile(
        "(?:youtube\\.com\\/(?:watch\\?v=|embed\\/|v\\/|shorts\\/)|youtu\\.be\\/)([a-zA-Z0-9_-]{11})|^([a-zA-Z0-9_-]{11})$"
    );

    public PlayCommand() {
        super(null, Emulator.getTexts().getValue("commands.keys.cmd_play", "play;youtube;yt").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient == null || gameClient.getHabbo() == null) return true;

        Room room = gameClient.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) {
            gameClient.getHabbo().whisper("Devi essere in una stanza per usare questo comando.");
            return true;
        }

        if (!room.isOwner(gameClient.getHabbo()) && !room.hasRights(gameClient.getHabbo())) {
            gameClient.getHabbo().whisper("Puoi usare questo comando solo se sei il proprietario della stanza o hai i diritti.");
            return true;
        }

        if (params.length < 2) {
            gameClient.getHabbo().whisper("Uso: :play <link o id youtube>");
            return true;
        }

        String input = params[1].trim();
        Matcher matcher = YOUTUBE_PATTERN.matcher(input);
        String videoId = null;
        if (matcher.find()) {
            videoId = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        }

        if (videoId == null || videoId.isEmpty()) {
            gameClient.getHabbo().whisper("Link o ID YouTube non valido.");
            return true;
        }

        // If audio is already playing in the room, add it to the playlist queue
        if (!room.getYoutubeCurrentVideo().isEmpty() && room.isYoutubeAudioOnly()) {
            room.getYoutubePlaylist().add(videoId + ":::" + gameClient.getHabbo().getHabboInfo().getUsername());
            room.sendComposer(
                new YouTubeRoomBroadcastComposer(room.getYoutubeCurrentVideo(), room.getYoutubeSenderName(), room.getYoutubePlaylist(), room.getYoutubeElapsedSeconds(), true, room.isYoutubePaused()).compose()
            );
            gameClient.getHabbo().whisper("Traccia aggiunta alla coda di riproduzione (" + room.getYoutubePlaylist().size() + " in coda).");
            return true;
        }

        // Start new audio track
        room.setYoutubeVideo(videoId, gameClient.getHabbo().getHabboInfo().getUsername(), room.getYoutubePlaylist(), true);
        room.sendComposer(
            new YouTubeRoomBroadcastComposer(videoId, gameClient.getHabbo().getHabboInfo().getUsername(), room.getYoutubePlaylist(), 0, true).compose()
        );
        gameClient.getHabbo().whisper("Hai avviato la riproduzione dell'audio.");
        return true;
    }
}
