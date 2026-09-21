package com.eu.habbo.messages.outgoing.rooms.youtube;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

public class YouTubeRoomBroadcastComposer extends MessageComposer {
    private final String videoId;
    private final String senderName;
    private final List<String> playlist;
    private final int elapsedSeconds;
    private final boolean isAudioOnly;
    private final boolean isPaused;
    private final int revision;
    private final long positionMs;
    private final long serverTimeMs;
    private final int headerId;

    public YouTubeRoomBroadcastComposer(String videoId, String senderName, List<String> playlist) {
        this(videoId, senderName, playlist, 0, false, false);
    }

    public YouTubeRoomBroadcastComposer(String videoId, String senderName, List<String> playlist, int elapsedSeconds) {
        this(videoId, senderName, playlist, elapsedSeconds, false, false);
    }

    public YouTubeRoomBroadcastComposer(String videoId, String senderName, List<String> playlist, int elapsedSeconds, boolean isAudioOnly) {
        this(videoId, senderName, playlist, elapsedSeconds, isAudioOnly, false);
    }

    public YouTubeRoomBroadcastComposer(String videoId, String senderName, List<String> playlist, int elapsedSeconds, boolean isAudioOnly, boolean isPaused) {
        this(videoId, senderName, playlist, elapsedSeconds, isAudioOnly, isPaused, 0);
    }

    public YouTubeRoomBroadcastComposer(String videoId, String senderName, List<String> playlist, int elapsedSeconds, boolean isAudioOnly, boolean isPaused, int revision) {
        this(videoId, senderName, playlist, elapsedSeconds, isAudioOnly, isPaused, revision, (long) Math.max(0, elapsedSeconds) * 1000L);
    }

    public YouTubeRoomBroadcastComposer(String videoId, String senderName, List<String> playlist, int elapsedSeconds, boolean isAudioOnly, boolean isPaused, int revision, long positionMs) {
        this(videoId, senderName, playlist, elapsedSeconds, isAudioOnly, isPaused, revision, positionMs, false);
    }

    public YouTubeRoomBroadcastComposer(String videoId, String senderName, List<String> playlist, int elapsedSeconds, boolean isAudioOnly, boolean isPaused, int revision, long positionMs, boolean musicParty) {
        this.videoId = videoId;
        this.senderName = senderName;
        this.playlist = playlist;
        this.elapsedSeconds = Math.max(0, elapsedSeconds);
        this.isAudioOnly = isAudioOnly;
        this.isPaused = isPaused;
        this.revision = Math.max(0, revision);
        this.positionMs = Math.max(0, positionMs);
        this.serverTimeMs = System.currentTimeMillis();
        this.headerId = musicParty ? Outgoing.MusicPartyRoomBroadcastComposer : Outgoing.YouTubeRoomBroadcastComposer;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(this.headerId);
        this.response.appendString(this.videoId);
        this.response.appendString(this.senderName);
        this.response.appendInt(this.playlist.size());
        for (String id : this.playlist) {
            this.response.appendString(id);
        }
        this.response.appendInt(this.elapsedSeconds);
        this.response.appendBoolean(this.isAudioOnly);
        this.response.appendBoolean(this.isPaused);
        this.response.appendInt(this.revision);
        appendLong(this.positionMs);
        appendLong(this.serverTimeMs);
        return this.response;
    }

    private void appendLong(long value) {
        this.response.appendInt((int) (value >>> 32));
        this.response.appendInt((int) value);
    }
}
