package com.eu.habbo.messages.outgoing.rooms.youtube;

import com.eu.habbo.habbohotel.items.YoutubeManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

public class YouTubeSearchResultsComposer extends MessageComposer {
    private final String query;
    private final String error;
    private final List<YoutubeManager.YoutubeSearchResult> results;

    public YouTubeSearchResultsComposer(String query, String error, List<YoutubeManager.YoutubeSearchResult> results) {
        this.query = query;
        this.error = error;
        this.results = results;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.YouTubeSearchResultsComposer);
        this.response.appendString(this.query);
        this.response.appendString(this.error);
        this.response.appendInt(this.results.size());
        for (YoutubeManager.YoutubeSearchResult result : this.results) {
            this.response.appendString(result.getId());
            this.response.appendString(result.getTitle());
            this.response.appendString(result.getThumbnail());
            this.response.appendString(result.getChannel());
        }
        return this.response;
    }
}
