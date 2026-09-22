package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.YoutubeManager;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.items.youtube.YoutubeVideoComposer;
import com.eu.habbo.messages.outgoing.rooms.youtube.EmbeddedMediaStateComposer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

public class InteractionYoutubeTV extends HabboItem {
    private static final Set<String> ROOM_YOUTUBE_DISPLAY_CLASSES =
            Set.of("yttv3", "yttv1", "yttv2b", "yttv2", "ytprojector");

    public YoutubeManager.YoutubePlaylist currentPlaylist = null;
    public YoutubeManager.YoutubeVideo currentVideo = null;
    public int startedWatchingAt = 0;
    public int offset = 0;
    public boolean playing = true;
    public ScheduledFuture<?> autoAdvance = null;
    private String embeddedUrl = "";
    private int embeddedState = 0;
    private int embeddedStartedAt = 0;
    private int embeddedOffset = 0;
    private int embeddedRevision = 0;
    private int embeddedBroadcasterId = 0;
    private String roomYoutubeVideoId = "";

    public InteractionYoutubeTV(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.loadEmbeddedUrl();
    }

    public InteractionYoutubeTV(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.loadEmbeddedUrl();
    }

    private void loadEmbeddedUrl() {
        if (this.isEmbeddedPlayer() && this.getExtradata() != null)
            this.embeddedUrl = this.getExtradata().trim();
    }

    public boolean isEmbeddedPlayer() {
        return "ads_videoplayer".equalsIgnoreCase(this.getBaseItem().getName())
                || "ads_videoplayer".equalsIgnoreCase(this.getBaseItem().getFullName());
    }

    public boolean isRoomYoutubeDisplay() {
        if (this.getBaseItem() == null) return false;

        String className = this.getBaseItem().getName();
        String fullName = this.getBaseItem().getFullName();
        return (className != null
                        && ROOM_YOUTUBE_DISPLAY_CLASSES.contains(className.toLowerCase(java.util.Locale.ROOT)))
                || (fullName != null
                        && ROOM_YOUTUBE_DISPLAY_CLASSES.contains(fullName.toLowerCase(java.util.Locale.ROOT)));
    }

    public boolean setRoomYoutubeVideo(String videoId) {
        String normalizedVideoId = videoId == null ? "" : videoId.trim();
        if (normalizedVideoId.equals(this.roomYoutubeVideoId)) return false;

        this.roomYoutubeVideoId = normalizedVideoId;
        return true;
    }

    public String getEmbeddedUrl() {
        return this.embeddedUrl;
    }

    public int getEmbeddedState() {
        return this.embeddedState;
    }

    public int getEmbeddedStartedAt() {
        return this.embeddedStartedAt;
    }

    public int getEmbeddedOffset() {
        return this.embeddedOffset;
    }

    public int getEmbeddedRevision() {
        return this.embeddedRevision;
    }

    public int getEmbeddedBroadcasterId() {
        return this.embeddedBroadcasterId;
    }

    private static int currentUnixTimestamp() {
        return Emulator.getIntUnixTimestamp();
    }

    public void setEmbeddedUrl(String url) {
        this.embeddedUrl = (url == null) ? "" : url;
        this.setExtradata(this.embeddedUrl);
        this.embeddedOffset = 0;
        this.embeddedStartedAt = currentUnixTimestamp();
        this.embeddedState = this.embeddedUrl.isEmpty() ? 0 : 1;
        this.embeddedBroadcasterId = 0;
        this.embeddedRevision++;
    }

    public void startEmbeddedShare(int broadcasterId) {
        this.embeddedUrl = "";
        this.embeddedState = 3;
        this.embeddedStartedAt = currentUnixTimestamp();
        this.embeddedOffset = 0;
        this.embeddedBroadcasterId = broadcasterId;
        this.embeddedRevision++;
    }

    public void playEmbedded() {
        if (this.embeddedUrl.isEmpty()) return;
        this.embeddedStartedAt = currentUnixTimestamp();
        this.embeddedState = 1;
        this.embeddedRevision++;
    }

    public void pauseEmbedded() {
        if (this.embeddedState == 1)
            this.embeddedOffset += Math.max(0, currentUnixTimestamp() - this.embeddedStartedAt);
        this.embeddedState = 2;
        this.embeddedRevision++;
    }

    public void reloadEmbedded() {
        this.embeddedStartedAt = currentUnixTimestamp();
        this.embeddedRevision++;
    }

    public void stopEmbedded() {
        this.embeddedState = 0;
        this.embeddedOffset = 0;
        this.embeddedStartedAt = 0;
        this.embeddedBroadcasterId = 0;
        this.embeddedRevision++;
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return false;
    }

    @Override
    public boolean isWalkable() {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        if (this.getExtradata().length() == 0) this.setExtradata("");

        serverMessage.appendInt(1 + (this.isLimited() ? 256 : 0));
        serverMessage.appendInt(1);
        serverMessage.appendString("THUMBNAIL_URL");
        if (!this.roomYoutubeVideoId.isEmpty()) {
            serverMessage.appendString("https://img.youtube.com/vi/" + this.roomYoutubeVideoId + "/hqdefault.jpg");
        } else if (this.currentVideo == null) {
            serverMessage.appendString("");
        } else {
            serverMessage.appendString("https://img.youtube.com/vi/" + this.currentVideo.getId() + "/hqdefault.jpg");
        }

        super.serializeExtradata(serverMessage);
    }

    @Override
    public void onPickUp(Room room) {
        if (this.isEmbeddedPlayer() && this.embeddedState != 0) {
            this.stopEmbedded();
            room.sendComposer(new EmbeddedMediaStateComposer(this, 5).compose());
        }

        super.onPickUp(room);

        if (this.autoAdvance != null) {
            this.cancelAdvancement();
        }

        this.currentVideo = null;
        this.currentPlaylist = null;
        this.roomYoutubeVideoId = "";
        this.startedWatchingAt = 0;
        this.offset = 0;
    }

    public void cancelAdvancement() {
        if (this.autoAdvance == null) return;

        this.autoAdvance.cancel(true);
        this.autoAdvance = null;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        super.onClick(client, room, objects);

        if (this.isEmbeddedPlayer()) {
            client.sendResponse(new EmbeddedMediaStateComposer(this, 0));
            return;
        }

        if (this.currentVideo != null) {
            int startTime = this.offset;
            if (this.playing) startTime += currentUnixTimestamp() - this.startedWatchingAt;
            client.sendResponse(new YoutubeVideoComposer(this.getId(), this.currentVideo, this.playing, startTime));
        }
    }
}
