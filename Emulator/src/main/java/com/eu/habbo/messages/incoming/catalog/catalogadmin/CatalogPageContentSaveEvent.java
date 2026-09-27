package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

public class CatalogPageContentSaveEvent extends MessageHandler {
    private static final int MAX_IMAGE_NAME_LENGTH = 512;
    private static final int MAX_TEXT_LENGTH = 8192;
    private static final int MAX_OFFSET = 2000;
    private static final int MAX_DIMENSION = 2000;
    private static final Safelist PAGE_TEXT_SAFE_LIST = new Safelist()
            .addTags("b", "i", "u", "br", "span", "div", "p", "a", "strong", "em")
            .addAttributes("a", "href", "target", "class", "style")
            .addAttributes(":all", "class", "style")
            .addProtocols("a", "href", "http", "https", "mailto", "#");

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() == null || !this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "No permission"));
            return;
        }

        int pageId = this.packet.readInt();
        CatalogPageType catalogType = CatalogPageType.fromString(this.packet.readString());
        if (catalogType == CatalogPageType.BOTH) catalogType = CatalogPageType.NORMAL;
        String headerImage = this.packet.readString().trim();
        String teaserImage = this.packet.readString().trim();
        String text = this.packet.readString();
        if (this.packet.bytesAvailable() < 10 * Integer.BYTES) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Image position and size values are missing"));
            return;
        }
        int[] header = readSlot();
        int[] teaser = readSlot();
        int textX = this.packet.readInt();
        int textY = this.packet.readInt();
        String textTwo = this.packet.readString();
        String textDetails = this.packet.readString();
        String textTeaser = this.packet.readString();

        if (pageId <= 0 || headerImage.length() > MAX_IMAGE_NAME_LENGTH || teaserImage.length() > MAX_IMAGE_NAME_LENGTH
                || text.length() > MAX_TEXT_LENGTH || textTwo.length() > MAX_TEXT_LENGTH
                || textDetails.length() > MAX_TEXT_LENGTH || textTeaser.length() > MAX_TEXT_LENGTH) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Catalog page content is invalid or exceeds the size limit"));
            return;
        }

        if (!validSlot(header) || !validSlot(teaser)) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Image position or size is out of range"));
            return;
        }
        if (textX < -MAX_OFFSET || textX > MAX_OFFSET || textY < -MAX_OFFSET || textY > MAX_OFFSET) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Text position is out of range"));
            return;
        }

        CatalogPage page = Emulator.getGameEnvironment().getCatalogManager().getCatalogPage(pageId, catalogType);
        if (page == null) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Page not found: " + pageId));
            return;
        }

        text = sanitizeText(text);
        textTwo = sanitizeText(textTwo);
        textDetails = sanitizeText(textDetails);
        textTeaser = sanitizeText(textTeaser);
        String pageTable = catalogType == CatalogPageType.BUILDER ? "catalog_pages_bc" : "catalog_pages";
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE " + pageTable + " SET page_headline = ?, page_teaser = ?, page_text1 = ?, "
                                + "page_text2 = ?, page_text_details = ?, page_text_teaser = ?, "
                                + "page_headline_x = ?, page_headline_y = ?, page_headline_width = ?, page_headline_height = ?, "
                                + "page_teaser_x = ?, page_teaser_y = ?, page_teaser_width = ?, page_teaser_height = ?, "
                                + "page_text1_x = ?, page_text1_y = ? WHERE id = ?")) {
                    update.setString(1, headerImage);
                    update.setString(2, teaserImage);
                    update.setString(3, text);
                    update.setString(4, textTwo);
                    update.setString(5, textDetails);
                    update.setString(6, textTeaser);
                    for (int i = 0; i < header.length; i++) update.setInt(7 + i, header[i]);
                    for (int i = 0; i < teaser.length; i++) update.setInt(11 + i, teaser[i]);
                    update.setInt(15, textX);
                    update.setInt(16, textY);
                    update.setInt(17, pageId);
                    // A zero update count can mean that the submitted values are unchanged.
                    update.executeUpdate();
                }

                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }

        page.setHeaderImage(headerImage);
        page.setTeaserImage(teaserImage);
        page.setTextOne(text);
        page.setTextTwo(textTwo);
        page.setTextDetails(textDetails);
        page.setTextTeaser(textTeaser);
        this.client.sendResponse(new CatalogAdminResultComposer(true, "Page content saved"));
    }

    private int[] readSlot() {
        return new int[] { this.packet.readInt(), this.packet.readInt(), this.packet.readInt(), this.packet.readInt() };
    }

    private static boolean validSlot(int[] values) {
        return values[0] >= -MAX_OFFSET && values[0] <= MAX_OFFSET
                && values[1] >= -MAX_OFFSET && values[1] <= MAX_OFFSET
                && values[2] >= 0 && values[2] <= MAX_DIMENSION
                && values[3] >= 0 && values[3] <= MAX_DIMENSION;
    }

    private static String sanitizeText(String text) {
        if (text == null || text.isEmpty()) return "";
        return Jsoup.clean(text, PAGE_TEXT_SAFE_LIST);
    }
}
