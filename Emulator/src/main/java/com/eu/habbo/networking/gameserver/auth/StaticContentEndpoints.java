package com.eu.habbo.networking.gameserver.auth;

import com.eu.habbo.Emulator;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.applyCors;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.errorPayload;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.isKeepAlive;
import static com.eu.habbo.networking.gameserver.auth.AuthHttpUtil.sendJson;

final class StaticContentEndpoints {

    private static final Logger LOGGER = LoggerFactory.getLogger(StaticContentEndpoints.class);

    private static final long NEWS_CACHE_TTL_MS = 30_000L;
    private static final int NEWS_IMAGE_MAX_BYTES = 512 * 1024;
    private static volatile NewsCacheEntry NEWS_CACHE = null;

    private static final class NewsCacheEntry {
        final byte[] jsonBytes;
        final long expiresAt;

        NewsCacheEntry(byte[] j, long e) {
            jsonBytes = j;
            expiresAt = e;
        }
    }

    private StaticContentEndpoints() {
    }

    private static Connection openConnection() throws Exception {
        return Emulator.getDatabase().getDataSource().getConnection();
    }

    private static int contentLimit(String configKey, int fallback) {
        return Math.max(1, Math.min(20, Emulator.getConfig().getInt(configKey, fallback)));
    }

    static void handleRoomTemplates(ChannelHandlerContext ctx, FullHttpRequest req) {
        JsonArray templates = new JsonArray();
        try (Connection conn = openConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT template_id, title, description, thumbnail " +
                             "FROM room_templates WHERE enabled = '1' " +
                             "ORDER BY sort_order ASC, template_id ASC")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    JsonObject t = new JsonObject();
                    t.addProperty("templateId", rs.getInt("template_id"));
                    t.addProperty("title", rs.getString("title"));
                    t.addProperty("description", rs.getString("description"));
                    t.addProperty("thumbnail", rs.getString("thumbnail"));
                    templates.add(t);
                }
            }
        } catch (Exception e) {
            LOGGER.error("room-templates list failed", e);
            sendJson(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, errorPayload("Server error."));
            return;
        }
        JsonObject res = new JsonObject();
        res.add("templates", templates);
        sendJson(ctx, req, HttpResponseStatus.OK, res);
    }

    static void handleNews(ChannelHandlerContext ctx, FullHttpRequest req) {
        long now = System.currentTimeMillis();
        NewsCacheEntry cached = NEWS_CACHE;

        if (cached == null || cached.expiresAt < now) {
            JsonArray items = new JsonArray();
            int limit = contentLimit("login.news.limit", 5);
            try (Connection conn = openConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT id, title, body, image, link_text, link_url " +
                             "FROM ui_news WHERE enabled = 1 " +
                             "ORDER BY created_at DESC, id DESC LIMIT ?")) {
                stmt.setInt(1, limit);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int id = rs.getInt("id");
                        JsonObject n = new JsonObject();
                        n.addProperty("id", id);
                        n.addProperty("title", rs.getString("title"));
                        n.addProperty("body", rs.getString("body"));

                        String image = rs.getString("image");
                        if (image != null && image.length() > NEWS_IMAGE_MAX_BYTES) {
                            LOGGER.warn("ui_news id={} image is {} bytes (>{}KB cap), omitting in response",
                                    id, image.length(), NEWS_IMAGE_MAX_BYTES / 1024);
                            image = null;
                        }
                        n.addProperty("image", image);

                        n.addProperty("linkText", rs.getString("link_text"));
                        n.addProperty("linkUrl", rs.getString("link_url"));
                        items.add(n);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("ui_news list failed", e);
                sendJson(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, errorPayload("Server error."));
                return;
            }

            JsonObject res = new JsonObject();
            res.add("news", items);
            byte[] bytes = res.toString().getBytes(StandardCharsets.UTF_8);
            cached = new NewsCacheEntry(bytes, now + NEWS_CACHE_TTL_MS);
            NEWS_CACHE = cached;
        }

        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.wrappedBuffer(cached.jsonBytes));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, cached.jsonBytes.length);
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "public, max-age=30");
        applyCors(req, response);
        boolean keepAlive = isKeepAlive(req);
        if (keepAlive) response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        var future = ctx.writeAndFlush(response);
        if (!keepAlive) future.addListener(ChannelFutureListener.CLOSE);
    }

    static void handlePhotos(ChannelHandlerContext ctx, FullHttpRequest req) {
        JsonArray items = new JsonArray();
        int limit = contentLimit("login.photos.limit", 100);

        String sqlWithCaption =
                "SELECT camera_web.id, camera_web.user_id, camera_web.room_id, camera_web.timestamp, camera_web.url, " +
                        "COALESCE(camera_web.caption, '') AS caption, " +
                        "COALESCE(users.username, '') AS username, COALESCE(users.look, '') AS look, COALESCE(rooms.name, '') AS room_name " +
                        "FROM camera_web " +
                        "LEFT JOIN users ON users.id = camera_web.user_id " +
                        "LEFT JOIN rooms ON rooms.id = camera_web.room_id " +
                        "WHERE camera_web.url IS NOT NULL AND TRIM(camera_web.url) <> '' " +
                        "ORDER BY camera_web.timestamp DESC, camera_web.id DESC LIMIT ?";

        String sqlFallback =
                "SELECT camera_web.id, camera_web.user_id, camera_web.room_id, camera_web.timestamp, camera_web.url, " +
                        "'' AS caption, " +
                        "COALESCE(users.username, '') AS username, COALESCE(users.look, '') AS look, COALESCE(rooms.name, '') AS room_name " +
                        "FROM camera_web " +
                        "LEFT JOIN users ON users.id = camera_web.user_id " +
                        "LEFT JOIN rooms ON rooms.id = camera_web.room_id " +
                        "WHERE camera_web.url IS NOT NULL AND TRIM(camera_web.url) <> '' " +
                        "ORDER BY camera_web.timestamp DESC, camera_web.id DESC LIMIT ?";

        try (Connection conn = openConnection()) {
            boolean loaded = false;
            try (PreparedStatement stmt = conn.prepareStatement(sqlWithCaption)) {
                stmt.setInt(1, limit);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        JsonObject photo = new JsonObject();
                        photo.addProperty("id", rs.getInt("id"));
                        photo.addProperty("userId", rs.getInt("user_id"));
                        photo.addProperty("roomId", rs.getInt("room_id"));
                        photo.addProperty("timestamp", rs.getInt("timestamp"));
                        photo.addProperty("url", rs.getString("url"));
                        photo.addProperty("caption", rs.getString("caption"));
                        photo.addProperty("username", rs.getString("username"));
                        photo.addProperty("look", rs.getString("look"));
                        photo.addProperty("roomName", rs.getString("room_name"));
                        items.add(photo);
                    }
                    loaded = true;
                }
            } catch (SQLException ex) {
                LOGGER.warn("Failed querying camera_web with caption, trying fallback query: {}", ex.getMessage());
            }

            if (!loaded) {
                try (PreparedStatement stmt = conn.prepareStatement(sqlFallback)) {
                    stmt.setInt(1, limit);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            JsonObject photo = new JsonObject();
                            photo.addProperty("id", rs.getInt("id"));
                            photo.addProperty("userId", rs.getInt("user_id"));
                            photo.addProperty("roomId", rs.getInt("room_id"));
                            photo.addProperty("timestamp", rs.getInt("timestamp"));
                            photo.addProperty("url", rs.getString("url"));
                            photo.addProperty("caption", "");
                            photo.addProperty("username", rs.getString("username"));
                            photo.addProperty("look", rs.getString("look"));
                            photo.addProperty("roomName", rs.getString("room_name"));
                            items.add(photo);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("camera photos list failed", e);
            sendJson(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, errorPayload("Server error."));
            return;
        }

        JsonObject res = new JsonObject();
        res.add("photos", items);
        sendJson(ctx, req, HttpResponseStatus.OK, res);
    }

    static void handleInstaduckCaption(ChannelHandlerContext ctx, FullHttpRequest req, JsonObject body) {
        int photoId = AuthHttpUtil.readInt(body, "photoId", 0);
        String caption = AuthHttpUtil.readString(body, "caption").trim();

        if (photoId <= 0) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST, errorPayload("Invalid photo ID."));
            return;
        }

        if (caption.length() > 255) {
            caption = caption.substring(0, 255);
        }

        try (Connection conn = openConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE camera_web SET caption = ? WHERE id = ?")) {
            stmt.setString(1, caption);
            stmt.setInt(2, photoId);
            stmt.executeUpdate();

            JsonObject ok = new JsonObject();
            ok.addProperty("status", "ok");
            ok.addProperty("photoId", photoId);
            ok.addProperty("caption", caption);
            sendJson(ctx, req, HttpResponseStatus.OK, ok);
        } catch (Exception e) {
            LOGGER.error("instaduck caption update failed", e);
            sendJson(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, errorPayload("Server error."));
        }
    }

    static void handleServerKey(ChannelHandlerContext ctx, FullHttpRequest req) {
        try {
            JsonObject ok = new JsonObject();
            ok.addProperty("publicKey", com.eu.habbo.networking.gameserver.crypto.CryptoSigningKeyManager.publicKeyBase64());
            ok.addProperty("algorithm", "ECDSA-P256-SHA256");
            sendJson(ctx, req, HttpResponseStatus.OK, ok);
        } catch (Exception e) {
            LOGGER.error("server-key fetch failed", e);
            sendJson(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, errorPayload("Server error."));
        }
    }
}
