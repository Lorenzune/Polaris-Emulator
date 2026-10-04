package com.eu.habbo.habbohotel.items;

import javax.sql.DataSource;
import java.sql.SQLException;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

public final class InformationPageRepository {
    private final DataSource dataSource;
    public InformationPageRepository(DataSource dataSource) { this.dataSource = java.util.Objects.requireNonNull(dataSource); }
    public record Page(String title, String content) {}

    public Page find(int itemId) throws SQLException {
        try (var connection = this.dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT title, content FROM item_information_pages WHERE item_id = ?")) {
            statement.setInt(1, itemId);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? new Page(rows.getString(1), rows.getString(2)) : null;
            }
        }
    }

    public static String clean(String html) {
        String cleaned = Jsoup.clean(html, new Safelist()
                .addTags("p", "div", "br", "span", "b", "strong", "i", "em", "u", "ul", "ol", "li", "h2", "h3", "blockquote", "a", "img")
                .addAttributes(":all", "style")
                .addAttributes("a", "href", "title").addProtocols("a", "href", "http", "https")
                .addAttributes("img", "src", "alt").addProtocols("img", "src", "https")
                .addEnforcedAttribute("a", "rel", "noopener noreferrer"));
        var document = Jsoup.parseBodyFragment(cleaned);
        for (var element : document.select("[style]")) {
            String style = cleanStyle(element.attr("style"));
            if (style.isEmpty()) element.removeAttr("style"); else element.attr("style", style);
        }
        document.outputSettings().prettyPrint(false);
        return document.body().html();
    }

    public static String cleanStyle(String style) {
        var result = new java.util.ArrayList<String>();
        for (String declaration : style.split(";")) {
            int separator = declaration.indexOf(':');
            if (separator < 0) continue;
            String property = declaration.substring(0, separator).trim().toLowerCase(java.util.Locale.ROOT);
            String value = declaration.substring(separator + 1).trim().toLowerCase(java.util.Locale.ROOT);
            boolean allowed = switch (property) {
                case "color", "background-color" -> value.matches("(#[0-9a-f]{3,8}|rgba?\\([\\d.,%\\s]+\\)|transparent|black|white)");
                case "font-size" -> value.matches("(?:[89]|[1-6]\\d|7[0-2])px");
                case "font-family" -> value.replace("'", "").replace("\"", "").matches("arial|verdana|tahoma|georgia|monospace");
                case "text-align" -> value.matches("left|center|right|justify");
                case "font-weight" -> value.matches("bold|normal|[1-9]00");
                case "font-style" -> value.matches("italic|normal");
                case "text-decoration" -> value.matches("underline|line-through|none");
                case "width" -> value.matches("(?:[1-9]\\d{0,2}|1[0-9]{3}|2000)px|(?:[1-9]\\d?|100)%");
                case "height" -> value.equals("auto");
                case "float" -> value.matches("left|right|none");
                case "clear" -> value.equals("both");
                case "display" -> value.matches("block|inline|inline-block");
                case "margin-left", "margin-right", "padding-left" -> value.matches("auto|0|(?:[0-9]|[1-9][0-9]|1[0-5][0-9]|160)px");
                default -> false;
            };
            if (allowed) result.add(property + ": " + value);
        }
        return String.join("; ", result);
    }

    public void save(int itemId, String title, String content, int userId) throws SQLException {
        try (var connection = this.dataSource.getConnection();
             var statement = connection.prepareStatement("INSERT INTO item_information_pages (item_id, title, content, updated_by) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE title=VALUES(title), content=VALUES(content), updated_by=VALUES(updated_by), updated_at=CURRENT_TIMESTAMP")) {
            statement.setInt(1, itemId); statement.setString(2, title);
            statement.setString(3, clean(content)); statement.setInt(4, userId);
            statement.executeUpdate();
        }
    }
}
