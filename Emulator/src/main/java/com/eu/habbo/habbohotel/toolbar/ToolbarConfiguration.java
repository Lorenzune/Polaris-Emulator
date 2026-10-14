package com.eu.habbo.habbohotel.toolbar;

import com.google.gson.Gson;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Public presentation settings only. Currency balances and voice permissions remain server-owned. */
public record ToolbarConfiguration(
        int version, List<Currency> currencies, Map<String, Boolean> widgets, List<Bubble> bubbles) {
    public static final Gson JSON = new Gson();
    public static final int MAX_BYTES = 24000;
    public static final Set<String> WIDGETS =
            Set.of("radio", "voice", "hc", "earnings", "features", "groups", "mysterybox", "offers", "roompromotes");

    public record Currency(int type, String label, String icon, String color, boolean visible, String placement) {}

    public record Bubble(
            String id,
            String title,
            String image,
            String link,
            String color,
            boolean visible,
            Boolean dismissible,
            Long startsAt,
            Long endsAt,
            Integer width,
            Integer height,
            String linkLabel) {}

    public static ToolbarConfiguration parse(String json) {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("configuration too large");
        ToolbarConfiguration config = JSON.fromJson(json, ToolbarConfiguration.class);
        if (config == null) throw new IllegalArgumentException("missing configuration");
        config.validate();
        return config;
    }

    public void validate() {
        if (version != 1
                || currencies == null
                || currencies.size() > 30
                || widgets == null
                || bubbles == null
                || bubbles.size() > 20) throw new IllegalArgumentException("invalid configuration");
        if (!WIDGETS.containsAll(widgets.keySet()) || widgets.values().stream().anyMatch(v -> v == null))
            throw new IllegalArgumentException("invalid widget");
        Set<Integer> types = new HashSet<>();
        for (Currency currency : currencies) {
            if (currency == null
                    || currency.type < -1
                    || !types.add(currency.type)
                    || !Set.of("purse", "bubble").contains(currency.placement))
                throw new IllegalArgumentException("invalid currency");
            text(currency.label, 80);
            color(currency.color);
            url(currency.icon, false);
        }
        Set<String> ids = new HashSet<>();
        for (Bubble bubble : bubbles) {
            if (bubble == null || bubble.id == null || !bubble.id.matches("[a-zA-Z0-9_-]{1,40}") || !ids.add(bubble.id))
                throw new IllegalArgumentException("invalid bubble");
            text(bubble.title, 240);
            if (bubble.title.isBlank()) throw new IllegalArgumentException("missing title");
            color(bubble.color);
            url(bubble.image, false);
            url(bubble.link, true);
            if (bubble.linkLabel != null) text(bubble.linkLabel, 80);
            dimension(bubble.width, 120);
            dimension(bubble.height, 48);
            timestamp(bubble.startsAt);
            timestamp(bubble.endsAt);
            if (bubble.startsAt != null
                    && bubble.startsAt > 0
                    && bubble.endsAt != null
                    && bubble.endsAt > 0
                    && bubble.endsAt <= bubble.startsAt) throw new IllegalArgumentException("end must follow start");
        }
        if (JSON.toJson(this).getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("configuration too large");
    }

    private static void text(String value, int max) {
        if (value == null || value.length() > max || value.chars().anyMatch(c -> c < 32))
            throw new IllegalArgumentException("invalid text");
    }

    private static void dimension(Integer value, int min) {
        if (value != null && value != 0 && (value < min || value > 320))
            throw new IllegalArgumentException("invalid bubble size");
    }

    private static void timestamp(Long value) {
        if (value != null && (value < 0 || value > 253402300799L))
            throw new IllegalArgumentException("invalid bubble schedule");
    }

    private static void color(String value) {
        if (value == null || !value.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("invalid color");
    }

    private static void url(String value, boolean link) {
        text(value, 512);
        if (value.isEmpty()) return;
        if (value.chars().anyMatch(c -> c <= 32)) throw new IllegalArgumentException("invalid URL");
        // Root-relative assets and a small set of existing UI routes; never arbitrary schemes.
        if (value.startsWith("/") && !value.startsWith("//") && !value.contains("\\")) return;
        if (link
                && value.matches(
                        "(?:catalog|navigator|achievements|habboUI|help|user-settings|questengine)/[a-zA-Z0-9_/#?=&%.+-]+"))
            return;
        try {
            URI uri = URI.create(value);
            if (("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && !value.contains("\\")) return;
        } catch (IllegalArgumentException ignored) {
            // Rejected below.
        }
        throw new IllegalArgumentException("invalid URL");
    }
}
