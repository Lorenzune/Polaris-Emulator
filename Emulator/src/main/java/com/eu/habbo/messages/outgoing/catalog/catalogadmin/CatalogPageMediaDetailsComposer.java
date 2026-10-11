package com.eu.habbo.messages.outgoing.catalog.catalogadmin;

import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class CatalogPageMediaDetailsComposer extends MessageComposer {
    private final CatalogPage page;
    private final CatalogPageType catalogType;
    private final int[] header;
    private final int[] teaser;
    private final int[] textPosition;
    private final boolean uploadEnabled;

    public CatalogPageMediaDetailsComposer(
            CatalogPage page,
            CatalogPageType catalogType,
            int[] header,
            int[] teaser,
            int[] textPosition,
            boolean uploadEnabled) {
        this.page = page;
        this.catalogType = catalogType;
        this.header = header;
        this.teaser = teaser;
        this.textPosition = textPosition;
        this.uploadEnabled = uploadEnabled;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CatalogPageMediaDetailsComposer);
        this.response.appendInt(this.page.getId());
        this.response.appendString(this.catalogType.toWireMode());
        this.response.appendString(this.page.getHeaderImage() == null ? "" : this.page.getHeaderImage());
        this.response.appendString(this.page.getTeaserImage() == null ? "" : this.page.getTeaserImage());
        this.response.appendString(this.page.getTextOne() == null ? "" : this.page.getTextOne());
        this.response.appendBoolean(this.uploadEnabled);
        for (int value : this.header) this.response.appendInt(value);
        for (int value : this.teaser) this.response.appendInt(value);
        for (int value : this.textPosition) this.response.appendInt(value);
        this.response.appendString(this.page.getTextTwo() == null ? "" : this.page.getTextTwo());
        this.response.appendString(this.page.getTextDetails() == null ? "" : this.page.getTextDetails());
        this.response.appendString(this.page.getTextTeaser() == null ? "" : this.page.getTextTeaser());
        return this.response;
    }
}
