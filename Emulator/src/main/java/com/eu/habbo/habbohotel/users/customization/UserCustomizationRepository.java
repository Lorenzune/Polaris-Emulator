package com.eu.habbo.habbohotel.users.customization;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UserCustomizationRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserCustomizationRepository.class);
    private final DataSource dataSource;

    public UserCustomizationRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Map<String, Integer> loadPrefixSettings() {
        Map<String, Integer> settings = new HashMap<>();
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT key_name, `value` FROM custom_prefix_settings");
                ResultSet set = statement.executeQuery()) {
            while (set.next()) {
                try {
                    settings.put(set.getString("key_name"), Integer.parseInt(set.getString("value")));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (SQLException exception) {
            LOGGER.error("Error reading prefix settings", exception);
        }
        return Map.copyOf(settings);
    }

    public Map<String, Integer> loadNickSettings() {
        Map<String, Integer> settings = new HashMap<>();
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT key_name, `value` FROM custom_nick_settings");
                ResultSet set = statement.executeQuery()) {
            while (set.next()) {
                try {
                    settings.put(set.getString("key_name"), Integer.parseInt(set.getString("value")));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (SQLException exception) {
            LOGGER.error("Error reading nick settings", exception);
        }
        return Map.copyOf(settings);
    }

    public Optional<CatalogPrefixOffer> findCatalogPrefix(int id) throws SQLException {
        String sql = "SELECT display_name, text, color, icon, effect, font, credits, points, points_type, enabled "
                + "FROM custom_prefixes_catalog WHERE id = ? AND enabled = 1 LIMIT 1";
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) {
                    return Optional.empty();
                }
                return Optional.of(new CatalogPrefixOffer(
                        id,
                        set.getString("display_name"),
                        set.getString("text"),
                        set.getString("color"),
                        set.getString("icon"),
                        set.getString("effect"),
                        set.getString("font"),
                        set.getInt("credits"),
                        set.getInt("points"),
                        set.getInt("points_type"),
                        set.getBoolean("enabled")));
            }
        }
    }

    public Optional<CatalogPrefixOffer> findCatalogPrefixByEffect(String effect) throws SQLException {
        if (effect == null || effect.isEmpty()) {
            return Optional.empty();
        }
        String sql = "SELECT id, display_name, text, color, icon, effect, font, credits, points, points_type, enabled "
                + "FROM custom_prefixes_catalog WHERE effect = ? ORDER BY id ASC LIMIT 1";
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, effect);
            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) {
                    return Optional.empty();
                }
                return Optional.of(new CatalogPrefixOffer(
                        set.getInt("id"),
                        set.getString("display_name"),
                        set.getString("text"),
                        set.getString("color"),
                        set.getString("icon"),
                        set.getString("effect"),
                        set.getString("font"),
                        set.getInt("credits"),
                        set.getInt("points"),
                        set.getInt("points_type"),
                        set.getBoolean("enabled")));
            }
        }
    }

    public Optional<CatalogNickOffer> findCatalogNick(int id) throws SQLException {
        String sql = "SELECT id, category_id, display_name, color, effect, font, credits, points, points_type, enabled "
                + "FROM custom_nicks_catalog WHERE id = ? AND enabled = 1 LIMIT 1";
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) {
                    return Optional.empty();
                }
                return Optional.of(new CatalogNickOffer(
                        id,
                        set.getInt("category_id"),
                        set.getString("display_name"),
                        set.getString("color"),
                        set.getString("effect"),
                        set.getString("font"),
                        set.getInt("credits"),
                        set.getInt("points"),
                        set.getInt("points_type"),
                        set.getBoolean("enabled")));
            }
        }
    }

    public Optional<CatalogNickOffer> findCatalogNickByEffect(String effect) throws SQLException {
        if (effect == null || effect.isEmpty()) {
            return Optional.empty();
        }
        String sql = "SELECT id, category_id, display_name, color, effect, font, credits, points, points_type, enabled "
                + "FROM custom_nicks_catalog WHERE effect = ? AND enabled = 1 ORDER BY id ASC LIMIT 1";
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, effect);
            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) {
                    return Optional.empty();
                }
                return Optional.of(new CatalogNickOffer(
                        set.getInt("id"),
                        set.getInt("category_id"),
                        set.getString("display_name"),
                        set.getString("color"),
                        set.getString("effect"),
                        set.getString("font"),
                        set.getInt("credits"),
                        set.getInt("points"),
                        set.getInt("points_type"),
                        set.getBoolean("enabled")));
            }
        }
    }

    public Optional<NickIconOffer> findNickIcon(String iconKey) throws SQLException {
        if (iconKey == null || iconKey.isBlank()) {
            return Optional.empty();
        }
        String cleanKey = iconKey.trim();
        String baseKey = cleanKey;
        if (baseKey.toLowerCase().endsWith(".gif") || baseKey.toLowerCase().endsWith(".png")) {
            baseKey = baseKey.substring(0, baseKey.length() - 4);
        }
        String withGif = baseKey + ".gif";
        String withPng = baseKey + ".png";

        String sql =
                "SELECT icon_key, points, points_type, enabled " +
                "FROM custom_nick_icons_catalog " +
                "WHERE icon_key = ? OR icon_key = ? OR icon_key = ? OR icon_key = ? " +
                "ORDER BY CASE WHEN icon_key = ? THEN 0 WHEN icon_key = ? THEN 1 ELSE 2 END LIMIT 1";
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, cleanKey);
            statement.setString(2, baseKey);
            statement.setString(3, withGif);
            statement.setString(4, withPng);
            statement.setString(5, cleanKey);
            statement.setString(6, baseKey);
            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) {
                    return Optional.empty();
                }
                String matchedKey = set.getString("icon_key");
                return Optional.of(new NickIconOffer(
                        matchedKey != null && !matchedKey.isEmpty() ? matchedKey : cleanKey,
                        set.getInt("points"),
                        set.getInt("points_type"),
                        set.getBoolean("enabled")));
            }
        }
    }

    public record CatalogPrefixOffer(
            int id,
            String displayName,
            String text,
            String color,
            String icon,
            String effect,
            String font,
            int credits,
            int points,
            int pointsType,
            boolean enabled) {

        public CatalogPrefixOffer(
                int id,
                String displayName,
                String text,
                String color,
                String icon,
                String effect,
                String font,
                int points,
                int pointsType) {
            this(id, displayName, text, color, icon, effect, font, 0, points, pointsType, true);
        }
    }

    public record CatalogNickOffer(
            int id,
            int categoryId,
            String displayName,
            String color,
            String effect,
            String font,
            int credits,
            int points,
            int pointsType,
            boolean enabled) {}

    public record NickIconOffer(String iconKey, int points, int pointsType, boolean enabled) {}
}
