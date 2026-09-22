package com.eu.habbo.messages.incoming.rooms.youtube;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.YoutubeManager;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.youtube.YouTubeSearchResultsComposer;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class YouTubeSearchEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(YouTubeSearchEvent.class);
    private static final int MAX_QUERY_LENGTH = 100;

    @Override
    public int getRatelimit() {
        // YouTube Data API search requests consume a significant portion of the daily quota.
        return 2500;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null || !room.isYoutubeEnabled()) return;
        if (!room.isOwner(habbo) && !room.hasRights(habbo) && !habbo.hasPermission(Permission.ACC_ANYROOMOWNER)) return;

        String query = this.packet.readString().trim();
        if (query.length() < 2 || query.length() > MAX_QUERY_LENGTH) return;

        final var client = this.client;
        final String searchQuery = query;
        Emulator.getThreading().run(() -> {
            try {
                YoutubeManager manager =
                        Emulator.getGameEnvironment().getItemManager().getYoutubeManager();
                List<YoutubeManager.YoutubeSearchResult> results = manager.searchVideos(searchQuery);
                if (client.getHabbo() != null) {
                    client.sendResponse(new YouTubeSearchResultsComposer(searchQuery, "", results).compose());
                }
            } catch (Exception e) {
                LOGGER.warn("YouTube search failed for query '{}': {}", searchQuery, e.getMessage());
                if (client.getHabbo() != null) {
                    client.sendResponse(new YouTubeSearchResultsComposer(
                                    searchQuery,
                                    "Ricerca YouTube non disponibile: verifica youtube.apikey e la quota API.",
                                    List.of())
                            .compose());
                }
            }
        });
    }
}
