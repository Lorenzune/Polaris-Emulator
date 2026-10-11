package com.eu.habbo.messages.outgoing.catalog.catalogadmin;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class CatalogPageAssetUploadResultComposer extends MessageComposer {
    private final int pageId;
    private final CatalogPageType catalogType;
    private final String slot;
    private final boolean success;
    private final String fileName;
    private final String message;

    public CatalogPageAssetUploadResultComposer(
            int pageId, CatalogPageType catalogType, String slot, boolean success, String fileName, String message) {
        this.pageId = pageId;
        this.catalogType = catalogType;
        this.slot = slot;
        this.success = success;
        this.fileName = fileName == null ? "" : fileName;
        this.message = message == null ? "" : message;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CatalogPageAssetUploadResultComposer);
        this.response.appendInt(this.pageId);
        this.response.appendString(this.catalogType.toWireMode());
        this.response.appendString(this.slot);
        this.response.appendBoolean(this.success);
        this.response.appendString(this.fileName);
        this.response.appendString(this.message);
        return this.response;
    }
}
