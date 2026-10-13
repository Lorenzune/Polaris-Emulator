package com.eu.habbo.networking.gameserver.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectibleManager;
import com.eu.habbo.networking.gameserver.auth.AccessTokenService;
import com.eu.habbo.networking.gameserver.auth.CorsOriginGate;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.ReferenceCountUtil;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CollectibleHttpHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(CollectibleHttpHandler.class);
    private static final String BASE_PATH = "/api/collectibles";

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof FullHttpRequest req)) {
            super.channelRead(ctx, msg);
            return;
        }

        String path = new QueryStringDecoder(req.uri()).path();
        if (!path.equals(BASE_PATH) && !path.startsWith(BASE_PATH + "/")) {
            super.channelRead(ctx, msg);
            return;
        }

        try {
            handle(ctx, req);
        } finally {
            ReferenceCountUtil.release(req);
        }
    }

    private void handle(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (req.method() == HttpMethod.OPTIONS) {
            sendCors(ctx, req);
            return;
        }

        int userId = authenticate(req);
        if (userId <= 0) {
            sendJson(ctx, req, HttpResponseStatus.UNAUTHORIZED, error("Unauthorized."));
            return;
        }

        try {
            String path = new QueryStringDecoder(req.uri()).path();
            CollectibleManager manager = new CollectibleManager(
                    "rare-collections-v1".equals(req.headers().get("X-Nitro-Api")));
            if (req.method() == HttpMethod.POST && path.equals(BASE_PATH + "/manage")) {
                sendJson(ctx, req, HttpResponseStatus.OK, manager.manage(userId, parseBody(req)));
                return;
            }
            if (req.method() == HttpMethod.POST && path.equals(BASE_PATH + "/shop/create")) {
                sendJson(ctx, req, HttpResponseStatus.OK, manager.saveShop(userId, parseBody(req)));
                return;
            }
            if (req.method() == HttpMethod.POST && path.equals(BASE_PATH + "/shop/disable")) {
                sendJson(
                        ctx,
                        req,
                        HttpResponseStatus.OK,
                        manager.disableShop(userId, parseBody(req).get("id").getAsInt()));
                return;
            }
            if (req.method() == HttpMethod.POST && path.equals(BASE_PATH + "/shop/buy")) {
                sendJson(
                        ctx,
                        req,
                        HttpResponseStatus.OK,
                        manager.buyShop(userId, parseBody(req).get("id").getAsInt()));
                return;
            }

            if (req.method() == HttpMethod.GET && path.equals(BASE_PATH)) {
                sendJson(ctx, req, HttpResponseStatus.OK, manager.state(userId));
                return;
            }

            if (req.method() == HttpMethod.POST && path.equals(BASE_PATH + "/create")) {
                sendJson(ctx, req, HttpResponseStatus.OK, manager.create(userId, parseBody(req)));
                return;
            }
            if (req.method() == HttpMethod.POST && path.equals(BASE_PATH + "/categories/create")) {
                sendJson(ctx, req, HttpResponseStatus.OK, manager.createCategory(userId, parseBody(req)));
                return;
            }
            if (req.method() == HttpMethod.POST && path.equals(BASE_PATH + "/disable")) {
                sendJson(
                        ctx,
                        req,
                        HttpResponseStatus.OK,
                        manager.disable(userId, parseBody(req).get("id").getAsInt()));
                return;
            }
            if (req.method() == HttpMethod.POST && path.equals(BASE_PATH + "/claim")) {
                var ids = new java.util.ArrayList<Integer>();
                for (var value : parseBody(req).getAsJsonArray("ids")) ids.add(value.getAsInt());
                sendJson(ctx, req, HttpResponseStatus.OK, manager.claim(userId, ids));
                return;
            }
            sendJson(ctx, req, HttpResponseStatus.NOT_FOUND, error("Unknown collectibles endpoint."));
        } catch (SecurityException e) {
            sendJson(ctx, req, HttpResponseStatus.FORBIDDEN, error(e.getMessage()));
        } catch (IllegalArgumentException | NullPointerException | IllegalStateException e) {
            sendJson(
                    ctx,
                    req,
                    HttpResponseStatus.BAD_REQUEST,
                    error("Richiesta non valida: " + (e.getMessage() == null ? "dati mancanti" : e.getMessage())));
        } catch (Exception e) {
            LOGGER.error("[collectibles] unexpected error", e);
            sendJson(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, error("Server error."));
        }
    }

    private int authenticate(FullHttpRequest req) {
        String authorization = req.headers().get(HttpHeaderNames.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) return 0;

        return AccessTokenService.verify(
                authorization.substring("Bearer ".length()).trim());
    }

    private JsonObject parseBody(FullHttpRequest req) {
        if (!req.content().isReadable()) return new JsonObject();

        String text = req.content().toString(StandardCharsets.UTF_8);
        if (text == null || text.isBlank()) return new JsonObject();

        return JsonParser.parseString(text).getAsJsonObject();
    }

    private void sendCors(ChannelHandlerContext ctx, FullHttpRequest req) {
        FullHttpResponse response = new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.NO_CONTENT);
        addCors(response, req);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void sendJson(
            ChannelHandlerContext ctx, FullHttpRequest req, HttpResponseStatus status, JsonObject payload) {
        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response =
                new DefaultFullHttpResponse(req.protocolVersion(), status, Unpooled.wrappedBuffer(bytes));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        addCors(response, req);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void addCors(FullHttpResponse response, FullHttpRequest req) {
        String origin = req.headers().get(HttpHeaderNames.ORIGIN);
        if (origin != null && !origin.isEmpty() && CorsOriginGate.isAllowed(req)) {
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
            response.headers()
                    .set(HttpHeaderNames.VARY, "Origin, Access-Control-Request-Headers, Access-Control-Request-Method");
        }

        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, OPTIONS");
        response.headers()
                .set(
                        HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS,
                        "Authorization, Content-Type, X-Requested-With, X-Nitro-Api");
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE, "600");
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-store");
    }

    private static JsonObject error(String message) {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        return error;
    }
}
