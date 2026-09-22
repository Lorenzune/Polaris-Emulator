package com.eu.habbo.habbohotel.guilds;

import com.eu.habbo.Emulator;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GuildCustomBadgeRequestManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuildCustomBadgeRequestManager.class);
    public static final int PENDING = 0;
    public static final int APPROVED = 1;
    public static final int REJECTED = 2;
    private static final int MAX_PENDING_PER_REQUESTER = 3;
    private static final Pattern BADGE_CODE_PATTERN = Pattern.compile("^custom_[A-Za-z0-9_-]{1,57}$");

    private GuildCustomBadgeRequestManager() {}

    public static final class Request {
        public final int id;
        public final int guildId;
        public final int requesterId;
        public final String requesterName;
        public final String guildName;
        public final String badgeCode;
        public final int createdAt;

        private Request(ResultSet resultSet) throws SQLException {
            this.id = resultSet.getInt("id");
            this.guildId = resultSet.getInt("guild_id");
            this.requesterId = resultSet.getInt("requester_id");
            this.requesterName = resultSet.getString("requester_name");
            this.guildName = resultSet.getString("guild_name");
            this.badgeCode = resultSet.getString("badge_code");
            this.createdAt = resultSet.getInt("created_at");
        }
    }

    public static final class ReviewResult {
        public final Request request;
        public final boolean approved;

        private ReviewResult(Request request, boolean approved) {
            this.request = request;
            this.approved = approved;
        }
    }

    public static boolean createPending(int guildId, int requesterId, String badgeCode) {
        if (guildId <= 0 || requesterId <= 0 || !isSafeBadgeCode(badgeCode)) return false;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            connection.setAutoCommit(false);

            try (PreparedStatement lock = connection.prepareStatement(
                    "SELECT COUNT(*) FROM group_custom_badge_requests WHERE status = ? AND (requester_id = ? OR guild_id = ?) FOR UPDATE")) {
                lock.setInt(1, PENDING);
                lock.setInt(2, requesterId);
                lock.setInt(3, guildId);
                try (ResultSet resultSet = lock.executeQuery()) {
                    if (resultSet.next() && resultSet.getInt(1) >= MAX_PENDING_PER_REQUESTER) {
                        connection.rollback();
                        return false;
                    }
                }
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO group_custom_badge_requests (guild_id, requester_id, badge_code, status, created_at) VALUES (?, ?, ?, ?, ?)")) {
                statement.setInt(1, guildId);
                statement.setInt(2, requesterId);
                statement.setString(3, badgeCode);
                statement.setInt(4, PENDING);
                statement.setInt(5, Emulator.getIntUnixTimestamp());
                statement.executeUpdate();
            }

            connection.commit();
            return true;
        } catch (SQLException e) {
            LOGGER.error("Failed to create pending custom group badge request", e);
            return false;
        }
    }

    public static boolean createApproved(int guildId, int requesterId, String badgeCode) {
        if (guildId <= 0 || requesterId <= 0 || !isSafeBadgeCode(badgeCode)) return false;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO group_custom_badge_requests (guild_id, requester_id, badge_code, status, reviewer_id, created_at, reviewed_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            int timestamp = Emulator.getIntUnixTimestamp();
            statement.setInt(1, guildId);
            statement.setInt(2, requesterId);
            statement.setString(3, badgeCode);
            statement.setInt(4, APPROVED);
            statement.setInt(5, requesterId);
            statement.setInt(6, timestamp);
            statement.setInt(7, timestamp);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            LOGGER.error("Failed to register approved custom group badge", e);
            return false;
        }
    }

    public static boolean isApprovedBadge(String badgeCode) {
        if (!isSafeBadgeCode(badgeCode)) return false;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT 1 FROM group_custom_badge_requests WHERE badge_code = ? AND status = ? LIMIT 1")) {
            statement.setString(1, badgeCode);
            statement.setInt(2, APPROVED);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to validate approved custom group badge {}", badgeCode, e);
            return false;
        }
    }

    public static List<Request> listPending() {
        List<Request> requests = new ArrayList<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT r.id, r.guild_id, r.requester_id, u.username AS requester_name, g.name AS guild_name, r.badge_code, r.created_at "
                                + "FROM group_custom_badge_requests r "
                                + "LEFT JOIN users u ON u.id = r.requester_id "
                                + "LEFT JOIN guilds g ON g.id = r.guild_id "
                                + "WHERE r.status = ? ORDER BY r.created_at ASC LIMIT 100")) {
            statement.setInt(1, PENDING);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) requests.add(new Request(resultSet));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to list pending custom group badge requests", e);
        }
        return requests;
    }

    public static ReviewResult review(int requestId, int reviewerId, boolean approve) {
        Request request;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT r.id, r.guild_id, r.requester_id, u.username AS requester_name, g.name AS guild_name, r.badge_code, r.created_at "
                            + "FROM group_custom_badge_requests r "
                            + "LEFT JOIN users u ON u.id = r.requester_id "
                            + "LEFT JOIN guilds g ON g.id = r.guild_id "
                            + "WHERE r.id = ? AND r.status = ? FOR UPDATE")) {
                statement.setInt(1, requestId);
                statement.setInt(2, PENDING);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        connection.rollback();
                        return null;
                    }
                    request = new Request(resultSet);
                }
            }

            if (approve
                    && (Emulator.getGameEnvironment().getGuildManager().getGuild(request.guildId) == null
                            || !badgeFilesExist(request.badgeCode))) {
                connection.rollback();
                return null;
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE group_custom_badge_requests SET status = ?, reviewer_id = ?, reviewed_at = ? WHERE id = ? AND status = ?")) {
                statement.setInt(1, approve ? APPROVED : REJECTED);
                statement.setInt(2, reviewerId);
                statement.setInt(3, Emulator.getIntUnixTimestamp());
                statement.setInt(4, request.id);
                statement.setInt(5, PENDING);
                if (statement.executeUpdate() != 1) {
                    connection.rollback();
                    return null;
                }
            }

            connection.commit();
        } catch (SQLException e) {
            LOGGER.error("Failed to review custom group badge request " + requestId, e);
            return null;
        }

        if (!approve) deleteBadgeFiles(request.badgeCode);
        return new ReviewResult(request, approve);
    }

    public static void saveBadgeFiles(String badgeCode, BufferedImage badgeImage1x, BufferedImage badgeImage2x)
            throws IOException {
        if (!isSafeBadgeCode(badgeCode)) throw new IOException("Invalid badge code");
        Path directory = getBadgeDirectory();
        Files.createDirectories(directory);
        Path standard = directory.resolve(badgeCode + ".png");
        Path large = directory.resolve(badgeCode + "_big.png");
        try {
            if (!ImageIO.write(badgeImage1x, "png", standard.toFile()))
                throw new IOException("No PNG writer available");
            if (!ImageIO.write(badgeImage2x, "png", large.toFile())) throw new IOException("No PNG writer available");
        } catch (IOException e) {
            Files.deleteIfExists(standard);
            Files.deleteIfExists(large);
            throw e;
        }
    }

    public static void deleteBadgeFiles(String badgeCode) {
        if (!isSafeBadgeCode(badgeCode)) return;
        try {
            Path directory = getBadgeDirectory();
            Files.deleteIfExists(directory.resolve(badgeCode + ".png"));
            Files.deleteIfExists(directory.resolve(badgeCode + "_big.png"));
        } catch (Exception e) {
            LOGGER.warn("Could not remove custom group badge files for {}", badgeCode, e);
        }
    }

    public static boolean badgeFilesExist(String badgeCode) {
        if (!isSafeBadgeCode(badgeCode)) return false;
        try {
            Path directory = getBadgeDirectory();
            return Files.isRegularFile(directory.resolve(badgeCode + ".png"))
                    && Files.isRegularFile(directory.resolve(badgeCode + "_big.png"));
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isSafeBadgeCode(String badgeCode) {
        return badgeCode != null && BADGE_CODE_PATTERN.matcher(badgeCode).matches();
    }

    private static Path getBadgeDirectory() throws IOException {
        String configuredPath = Emulator.getConfig().getValue("imager.location.output.badges", "");
        if (configuredPath == null || configuredPath.trim().isEmpty()) {
            throw new IOException("imager.location.output.badges is not configured");
        }
        return Paths.get(configuredPath).toAbsolutePath().normalize();
    }
}
