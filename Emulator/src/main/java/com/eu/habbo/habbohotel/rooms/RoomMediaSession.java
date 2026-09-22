package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.youtube.YouTubeRoomWatchersComposer;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

final class RoomMediaSession {

    private final Room room;
    private final List<String> playlist = new CopyOnWriteArrayList<>();
    private final Set<Integer> watchers = ConcurrentHashMap.newKeySet();
    private boolean youtubeEnabled = true;
    private boolean soundboardEnabled;
    private boolean audioOnly = false;
    private boolean paused = false;
    private long pausedOffsetMs = 0;
    private String currentVideo = "";
    private String senderName = "";
    private long startedAtMs = 0;
    private int revision = 0;

    RoomMediaSession(Room room) {
        this.room = room;
    }

    synchronized boolean isAudioOnly() {
        return this.audioOnly;
    }

    synchronized void isAudioOnly(boolean audioOnly) {
        this.audioOnly = audioOnly;
    }

    synchronized boolean isPaused() {
        return this.paused;
    }

    synchronized void setPaused(boolean paused, int offset) {
        this.paused = paused;
        if (paused) {
            this.pausedOffsetMs = (long) Math.max(0, offset) * 1000L;
        } else {
            this.startedAtMs = System.currentTimeMillis() - ((long) Math.max(0, offset) * 1000L);
        }
        this.revision++;
    }

    synchronized boolean youtubeEnabled() {
        return this.youtubeEnabled;
    }

    synchronized void youtubeEnabled(boolean enabled) {
        this.youtubeEnabled = enabled;
    }

    synchronized boolean soundboardEnabled() {
        return this.soundboardEnabled;
    }

    synchronized void soundboardEnabled(boolean enabled) {
        this.soundboardEnabled = enabled;
    }

    synchronized String currentVideo() {
        return this.currentVideo;
    }

    synchronized String senderName() {
        return this.senderName;
    }

    synchronized long startedAtMs() {
        return this.startedAtMs;
    }

    synchronized int elapsedSeconds() {
        return (int) Math.min(Integer.MAX_VALUE, this.positionMs() / 1000L);
    }

    synchronized long positionMs() {
        if (this.currentVideo.isEmpty()) {
            return 0;
        }
        if (this.paused) {
            return this.pausedOffsetMs;
        }
        if (this.startedAtMs <= 0) {
            return 0;
        }
        return Math.max(0, System.currentTimeMillis() - this.startedAtMs);
    }

    synchronized List<String> playlist() {
        return this.playlist;
    }

    synchronized Set<Integer> watchers() {
        return this.watchers;
    }

    synchronized int revision() {
        return this.revision;
    }

    synchronized void setVideo(
            String videoId,
            String senderName,
            List<String> playlist,
            boolean audioOnly,
            int startOffset,
            boolean isPaused) {
        this.setVideo(videoId, senderName, playlist, audioOnly, (long) Math.max(0, startOffset) * 1000L, isPaused);
    }

    synchronized void setVideo(
            String videoId,
            String senderName,
            List<String> playlist,
            boolean audioOnly,
            long positionMs,
            boolean isPaused) {
        this.currentVideo = videoId;
        this.senderName = senderName;
        this.audioOnly = audioOnly;
        this.paused = isPaused;
        this.pausedOffsetMs = isPaused ? Math.max(0, positionMs) : 0;
        this.startedAtMs = (videoId.isEmpty() || isPaused) ? 0 : (System.currentTimeMillis() - Math.max(0, positionMs));
        this.playlist.clear();
        if (playlist != null) {
            this.playlist.addAll(playlist);
        }
        this.revision++;
    }

    void setVideo(String videoId, String senderName, List<String> playlist, boolean audioOnly, int startOffset) {
        this.setVideo(videoId, senderName, playlist, audioOnly, startOffset, false);
    }

    void setVideo(String videoId, String senderName, List<String> playlist, boolean audioOnly) {
        this.setVideo(videoId, senderName, playlist, audioOnly, 0);
    }

    void setVideo(String videoId, String senderName, List<String> playlist) {
        this.setVideo(videoId, senderName, playlist, false, 0);
    }

    synchronized void clearVideo() {
        this.currentVideo = "";
        this.senderName = "";
        this.audioOnly = false;
        this.paused = false;
        this.pausedOffsetMs = 0;
        this.startedAtMs = 0;
        this.playlist.clear();
        this.revision++;
    }

    void removeWatcher(Habbo habbo) {
        if (habbo == null) {
            return;
        }

        int userId = habbo.getHabboInfo().getId();
        if (this.watchers.remove(userId)) {
            this.room.sendComposer(new YouTubeRoomWatchersComposer(this.watchers).compose());
        }
    }
}
