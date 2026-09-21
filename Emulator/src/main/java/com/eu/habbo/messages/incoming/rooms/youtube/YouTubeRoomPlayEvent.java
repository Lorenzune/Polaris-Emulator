package com.eu.habbo.messages.incoming.rooms.youtube;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.incoming.navigator.SearchRoomsEvent;
import com.eu.habbo.messages.outgoing.rooms.youtube.YouTubeRoomBroadcastComposer;

import java.util.ArrayList;
import java.util.List;

public class YouTubeRoomPlayEvent extends MessageHandler {

    private static final int MAX_PLAYLIST_ITEM_LENGTH = 200;
    private static final int MAX_PLAYLIST_SIZE = 50;
    private static final int MAX_START_OFFSET_SECONDS = 86_400;

    /** Dedicated incoming handler for the independent :play Music Party channel. */
    public static class MusicPartyRoomPlayEvent extends YouTubeRoomPlayEvent {
        @Override
        protected boolean isMusicParty() {
            return true;
        }
    }

    protected boolean isMusicParty() {
        return false;
    }

    @Override
    public int getRatelimit() {
        // Responsive seeking / actions for room host
        return 400;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null) return;
        if (!room.isYoutubeEnabled()) return;
        boolean isRoomOwner = room.isOwner(habbo);
        boolean isStaff = habbo.hasPermission(Permission.ACC_ANYROOMOWNER);
        if (!isRoomOwner && !room.hasRights(habbo) && !isStaff) return;

        String videoId = normalizeVideoId(this.packet.readString());
        if (videoId == null) return;

        int playlistCount = this.packet.readInt();
        if (playlistCount > MAX_PLAYLIST_SIZE) playlistCount = MAX_PLAYLIST_SIZE;
        if (playlistCount < 0) playlistCount = 0;

        List<String> playlist = new ArrayList<>();
        for (int i = 0; i < playlistCount; i++) {
            String item = normalizePlaylistItem(this.packet.readString());
            if (item != null) {
                playlist.add(item);
            }
        }

        int startOffset = 0;
        if (this.packet.getBuffer().readableBytes() >= 4) {
            startOffset = Math.min(MAX_START_OFFSET_SECONDS, Math.max(0, this.packet.readInt()));
        }

        boolean packetAudioOnly = this.packet.getBuffer().readableBytes() >= 1 && this.packet.readBoolean();
        boolean isAudioOnly = this.isMusicParty() || packetAudioOnly;

        boolean isPaused = false;
        if (this.packet.getBuffer().readableBytes() >= 1) {
            isPaused = this.packet.readBoolean();
        }

        int expectedRevision = -1;
        if (this.packet.getBuffer().readableBytes() >= 4) {
            expectedRevision = this.packet.readInt();
        }

        boolean isEnded = false;
        if (this.packet.getBuffer().readableBytes() >= 1) {
            isEnded = this.packet.readBoolean();
        }

        boolean preservePosition = false;
        if (this.packet.getBuffer().readableBytes() >= 1) {
            preservePosition = this.packet.readBoolean();
        }

        boolean preservePlaylist = false;
        if (this.packet.getBuffer().readableBytes() >= 1) {
            preservePlaylist = this.packet.readBoolean();
        }

        synchronized (room) {
            if (expectedRevision >= 0 && expectedRevision != revision(room)) {
                this.client.sendResponse(currentState(room));
                return;
            }

            if (isEnded) {
                if (!videoId.equals(currentVideo(room))) return;

                List<String> currentPlaylist = new ArrayList<>(playlist(room));
                if (currentPlaylist.isEmpty()) {
                    clearVideo(room);
                    room.sendComposer(broadcast("", "", List.of(), 0, isMusicParty(), false, revision(room), positionMs(room)));
                    return;
                }

                String nextItem = currentPlaylist.remove(0);
                String[] nextParts = nextItem.split(":::", 3);
                String nextVideo = normalizeVideoId(nextParts[0]);
                if (nextVideo == null || nextVideo.isEmpty()) return;

                String nextSender = nextParts.length > 1 && !nextParts[1].isBlank()
                    ? nextParts[1]
                    : senderName(room);
                setVideo(room, nextVideo, nextSender, currentPlaylist, isAudioOnly, 0L, false);
                room.sendComposer(broadcast(
                    nextVideo,
                    nextSender,
                    currentPlaylist,
                    0,
                    isAudioOnly,
                    false,
                    revision(room),
                    positionMs(room)));
                return;
            }

            // Store the current video + playlist on the room, or clear if empty
            if (videoId.isEmpty()) {
                // Closing the player is a room-wide action. Room rights alone are
                // enough to control playback, but only the room owner or staff
                // may stop it for everybody in the room.
                if (!isRoomOwner && !isStaff) return;
                clearVideo(room);
                room.sendComposer(broadcast("", "", List.of(), 0, isMusicParty(), false, revision(room), positionMs(room)));
                return;
            }

            // Check if this is a pause / resume toggle, playlist update, or seeking for current video
            if (videoId.equals(currentVideo(room))) {
                boolean keepCurrentPosition = preservePosition || (expectedRevision < 0 && startOffset == 0);
                long currentPositionMs = keepCurrentPosition ? positionMs(room) : ((long) startOffset * 1000L);
                int offset = (int) Math.min(Integer.MAX_VALUE, currentPositionMs / 1000L);
                boolean paused = keepCurrentPosition ? isPaused(room) : isPaused;
                List<String> effectivePlaylist = preservePlaylist ? new ArrayList<>(playlist(room)) : playlist;
                setVideo(room, videoId, senderName(room), effectivePlaylist, isAudioOnly, currentPositionMs, paused);
                room.sendComposer(broadcast(videoId, senderName(room), effectivePlaylist, offset, isAudioOnly, paused, revision(room), positionMs(room)));
                return;
            }

            String senderName = habbo.getHabboInfo().getUsername();
            List<String> currentPlaylist = playlist(room);
            for (String queuedItem : currentPlaylist) {
                String[] nextParts = queuedItem.split(":::", 3);
                String queuedVideoId = normalizeVideoId(nextParts[0]);
                if (videoId.equals(queuedVideoId) && nextParts.length > 1 && !nextParts[1].isBlank()) {
                    senderName = nextParts[1];
                    break;
                }
            }

            setVideo(room, videoId, senderName, playlist, isAudioOnly, (long) startOffset * 1000L, isPaused);

            // Broadcast to everyone in the room
            room.sendComposer(broadcast(videoId, senderName, playlist, startOffset, isAudioOnly, isPaused, revision(room), positionMs(room)));
        }
    }

    private String currentVideo(Room room) {
        return this.isMusicParty() ? room.getMusicPartyCurrentVideo() : room.getYoutubeCurrentVideo();
    }

    private String senderName(Room room) {
        return this.isMusicParty() ? room.getMusicPartySenderName() : room.getYoutubeSenderName();
    }

    private List<String> playlist(Room room) {
        return this.isMusicParty() ? room.getMusicPartyPlaylist() : room.getYoutubePlaylist();
    }

    private int revision(Room room) {
        return this.isMusicParty() ? room.getMusicPartyRevision() : room.getYoutubeRevision();
    }

    private long positionMs(Room room) {
        return this.isMusicParty() ? room.getMusicPartyPositionMs() : room.getYoutubePositionMs();
    }

    private boolean isPaused(Room room) {
        return this.isMusicParty() ? room.isMusicPartyPaused() : room.isYoutubePaused();
    }

    private void setVideo(Room room, String videoId, String senderName, List<String> playlist, boolean audioOnly, long positionMs, boolean paused) {
        if (this.isMusicParty()) {
            room.setMusicPartyVideo(videoId, senderName, playlist, positionMs, paused);
            SearchRoomsEvent.invalidateCachedResults();
        } else {
            room.setYoutubeVideo(videoId, senderName, playlist, audioOnly, positionMs, paused);
        }
    }

    private void clearVideo(Room room) {
        if (this.isMusicParty()) {
            room.clearMusicPartyVideo();
            SearchRoomsEvent.invalidateCachedResults();
        } else {
            room.clearYoutubeVideo();
        }
    }

    private com.eu.habbo.messages.ServerMessage broadcast(String videoId, String senderName, List<String> playlist,
                                                           int elapsedSeconds, boolean audioOnly, boolean paused,
                                                           int revision, long positionMs) {
        if (this.isMusicParty()) {
            return new YouTubeRoomBroadcastComposer(
                videoId, senderName, playlist, elapsedSeconds, audioOnly, paused, revision, positionMs, true).compose();
        }
        return new YouTubeRoomBroadcastComposer(videoId, senderName, playlist, elapsedSeconds, audioOnly, paused, revision, positionMs).compose();
    }

    private com.eu.habbo.messages.ServerMessage currentState(Room room) {
        return broadcast(currentVideo(room), senderName(room), playlist(room),
            this.isMusicParty() ? room.getMusicPartyElapsedSeconds() : room.getYoutubeElapsedSeconds(),
            this.isMusicParty() || room.isYoutubeAudioOnly(), isPaused(room), revision(room), positionMs(room));
    }

    private static String normalizeVideoId(String value) {
        if (value == null) return null;

        String videoId = value.trim();
        if (videoId.isEmpty()) return "";

        return videoId.matches("[A-Za-z0-9_-]{11}") ? videoId : null;
    }

    private static String normalizePlaylistItem(String value) {
        if (value == null || value.length() > MAX_PLAYLIST_ITEM_LENGTH) return null;

        String[] parts = value.split(":::", 3);
        String rawVideoId = parts[0];
        String videoId = normalizeVideoId(rawVideoId);
        if (videoId == null || videoId.isEmpty()) return null;

        if (parts.length < 2) return videoId;

        String addedBy = parts[1].replaceAll("[\\p{Cntrl}]", "").trim();
        if (addedBy.length() > 64) addedBy = addedBy.substring(0, 64);
        if (addedBy.isEmpty()) return videoId;

        if (parts.length < 3) return videoId + ":::" + addedBy;
        String title = parts[2].replaceAll("[\\p{Cntrl}]", "").trim();
        if (title.length() > 110) title = title.substring(0, 110);
        return title.isEmpty() ? videoId + ":::" + addedBy : videoId + ":::" + addedBy + ":::" + title;
    }
}
