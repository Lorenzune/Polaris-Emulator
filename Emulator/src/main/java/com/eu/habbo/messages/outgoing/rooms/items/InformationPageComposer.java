package com.eu.habbo.messages.outgoing.rooms.items;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
public final class InformationPageComposer extends MessageComposer {
    private final int itemId;
    private final boolean editable;
    private final String title, content, link, error;
    public InformationPageComposer(int itemId, boolean editable, String title, String content, String link, String error) {
        this.itemId=itemId; this.editable=editable; this.title=title; this.content=content; this.link=link; this.error=error;
    }
    @Override protected ServerMessage composeInternal() {
        this.response.init(4808);
        this.response.appendInt(itemId); this.response.appendBoolean(editable);
        this.response.appendString(title); this.response.appendString(content);
        this.response.appendString(link); this.response.appendString(error);
        return this.response;
    }
}
