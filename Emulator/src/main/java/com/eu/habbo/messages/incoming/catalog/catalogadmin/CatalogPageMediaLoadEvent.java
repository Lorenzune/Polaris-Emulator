package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogPageMediaDetailsComposer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class CatalogPageMediaLoadEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        int pageId = this.packet.readInt();
        CatalogPageType catalogType = CatalogPageType.fromString(this.packet.readString());
        if (catalogType == CatalogPageType.BOTH) catalogType = CatalogPageType.NORMAL;

        CatalogPage page = Emulator.getGameEnvironment().getCatalogManager().getCatalogPage(pageId, catalogType);
        if (page == null) return;

        int[] header = new int[4];
        int[] teaser = new int[4];
        int[] textPosition = new int[2];
        String pageTable = catalogType == CatalogPageType.BUILDER ? "catalog_pages_bc" : "catalog_pages";
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT page_headline_x, page_headline_y, page_headline_width, page_headline_height, "
                             + "page_teaser_x, page_teaser_y, page_teaser_width, page_teaser_height, page_text1_x, page_text1_y FROM " + pageTable + " WHERE id = ?")) {
            statement.setInt(1, pageId);
            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next()) {
                    header[0] = rows.getInt("page_headline_x");
                    header[1] = rows.getInt("page_headline_y");
                    header[2] = rows.getInt("page_headline_width");
                    header[3] = rows.getInt("page_headline_height");
                    teaser[0] = rows.getInt("page_teaser_x");
                    teaser[1] = rows.getInt("page_teaser_y");
                    teaser[2] = rows.getInt("page_teaser_width");
                    teaser[3] = rows.getInt("page_teaser_height");
                    textPosition[0] = rows.getInt("page_text1_x");
                    textPosition[1] = rows.getInt("page_text1_y");
                }
            }
        }

        boolean uploadEnabled = !Emulator.getConfig().getValue("catalog.admin.asset.upload.path", "").trim().isEmpty();
        this.client.sendResponse(new CatalogPageMediaDetailsComposer(page, catalogType, header, teaser, textPosition, uploadEnabled));
    }
}
