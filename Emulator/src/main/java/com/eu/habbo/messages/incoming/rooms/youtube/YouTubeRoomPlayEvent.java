package com.eu.habbo.messages.incoming.rooms.youtube;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.youtube.YouTubeRoomBroadcastComposer;

import java.util.ArrayList;
import java.util.List;

public class YouTubeRoomPlayEvent extends MessageHandler {

    private static final int MAX_PLAYLIST_ITEM_LENGTH = 200;
    private static final int MAX_PLAYLIST_SIZE = 50;
    private static final int MAX_START_OFFSET_SECONDS = 86_400;

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

        boolean isAudioOnly = false;
        if (this.packet.getBuffer().readableBytes() >= 1) {
            isAudioOnly = this.packet.readBoolean();
        }

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

        synchronized (room) {
            if (expectedRevision >= 0 && expectedRevision != room.getYoutubeRevision()) {
                this.client.sendResponse(currentState(room));
                return;
            }

            if (isEnded) {
                if (!videoId.equals(room.getYoutubeCurrentVideo())) return;

                List<String> currentPlaylist = new ArrayList<>(room.getYoutubePlaylist());
                if (currentPlaylist.isEmpty()) {
                    room.clearYoutubeVideo();
                    room.sendComposer(new YouTubeRoomBroadcastComposer("", "", List.of(), 0, room.isYoutubeAudioOnly(), false, room.getYoutubeRevision(), room.getYoutubePositionMs()).compose());
                    return;
                }

                String nextItem = currentPlaylist.remove(0);
                String nextVideo = normalizeVideoId(nextItem.split(":::", 2)[0]);
                if (nextVideo == null || nextVideo.isEmpty()) return;

                room.setYoutubeVideo(nextVideo, room.getYoutubeSenderName(), currentPlaylist, room.isYoutubeAudioOnly(), 0L, false);
                room.sendComposer(new YouTubeRoomBroadcastComposer(
                    nextVideo,
                    room.getYoutubeSenderName(),
                    currentPlaylist,
                    0,
                    room.isYoutubeAudioOnly(),
                    false,
                    room.getYoutubeRevision(),
                    room.getYoutubePositionMs()
                ).compose());
                return;
            }

            // Store the current video + playlist on the room, or clear if empty
            if (videoId.isEmpty()) {
                // Closing the player is a room-wide action. Room rights alone are
                // enough to control playback, but only the room owner or staff
                // may stop it for everybody in the room.
                if (!isRoomOwner && !isStaff) return;
                room.clearYoutubeVideo();
                room.sendComposer(
                    new YouTubeRoomBroadcastComposer("", "", List.of(), 0, false, false, room.getYoutubeRevision(), room.getYoutubePositionMs()).compose()
                );
                return;
            }

            // Check if this is a pause / resume toggle, playlist update, or seeking for current video
            if (videoId.equals(room.getYoutubeCurrentVideo())) {
                boolean keepCurrentPosition = preservePosition || (expectedRevision < 0 && startOffset == 0);
                long positionMs = keepCurrentPosition ? room.getYoutubePositionMs() : ((long) startOffset * 1000L);
                int offset = (int) Math.min(Integer.MAX_VALUE, positionMs / 1000L);
                boolean paused = keepCurrentPosition ? room.isYoutubePaused() : isPaused;
                room.setYoutubeVideo(videoId, room.getYoutubeSenderName(), playlist, isAudioOnly, positionMs, paused);
                room.sendComposer(
                    new YouTubeRoomBroadcastComposer(videoId, room.getYoutubeSenderName(), playlist, offset, isAudioOnly, paused, room.getYoutubeRevision(), room.getYoutubePositionMs()).compose()
                );
                return;
            }

            room.setYoutubeVideo(videoId, habbo.getHabboInfo().getUsername(), playlist, isAudioOnly, (long) startOffset * 1000L, isPaused);

            // Broadcast to everyone in the room
            room.sendComposer(
                new YouTubeRoomBroadcastComposer(videoId, habbo.getHabboInfo().getUsername(), playlist, startOffset, isAudioOnly, isPaused, room.getYoutubeRevision(), room.getYoutubePositionMs()).compose()
            );
        }
    }

    private static com.eu.habbo.messages.ServerMessage currentState(Room room) {
        return new YouTubeRoomBroadcastComposer(
            room.getYoutubeCurrentVideo(),
            room.getYoutubeSenderName(),
            room.getYoutubePlaylist(),
            room.getYoutubeElapsedSeconds(),
            room.isYoutubeAudioOnly(),
            room.isYoutubePaused(),
            room.getYoutubeRevision(),
            room.getYoutubePositionMs()
        ).compose();
    }

    private static String normalizeVideoId(String value) {
        if (value == null) return null;

        String videoId = value.trim();
        if (videoId.isEmpty()) return "";

        return videoId.matches("[A-Za-z0-9_-]{11}") ? videoId : null;
    }

    private static String normalizePlaylistItem(String value) {
        if (value == null || value.length() > MAX_PLAYLIST_ITEM_LENGTH) return null;

        int separator = value.indexOf(":::");
        String rawVideoId = separator >= 0 ? value.substring(0, separator) : value;
        String videoId = normalizeVideoId(rawVideoId);
        if (videoId == null || videoId.isEmpty()) return null;

        if (separator < 0) return videoId;

        String addedBy = value.substring(separator + 3).replaceAll("[\\p{Cntrl}]", "").trim();
        if (addedBy.length() > 64) addedBy = addedBy.substring(0, 64);
        return addedBy.isEmpty() ? videoId : videoId + ":::" + addedBy;
    }
}
