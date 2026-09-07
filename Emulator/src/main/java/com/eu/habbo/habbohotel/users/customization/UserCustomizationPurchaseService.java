package com.eu.habbo.habbohotel.users.customization;

import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import com.eu.habbo.habbohotel.economy.EconomyOperationId;
import com.eu.habbo.habbohotel.modtool.WordFilterWord;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.LedgerWalletMutation;
import com.eu.habbo.habbohotel.users.UserNick;
import com.eu.habbo.habbohotel.users.UserNickIcon;
import com.eu.habbo.habbohotel.users.UserPrefix;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UserCustomizationPurchaseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserCustomizationPurchaseService.class);
    private static final String[] ALLOWED_FONTS = {"", "pixel", "cherry", "vampiro"};
    private static final String[] ALLOWED_EFFECTS = {
        "",
        "glow",
        "shadow",
        "italic",
        "outline",
        "underline",
        "pulse",
        "bounce",
        "wave",
        "shake",
        "discord-neon",
        "pop",
        "bold-glow",
        "rainbow",
        "frost",
        "gold",
        "glitch",
        "fire",
        "matrix",
        "sparkle",
        "cosmic",
        "ice",
        "cyberpunk",
        "electric"
    };
    private static final String[] ALLOWED_NICK_EFFECTS = {
        "",
        "shimmer",
        "glitter",
        "sparkle",
        "neon",
        "glow",
        "rainbow",
        "wave",
        "pulse",
        "bounce",
        "fire",
        "ice",
        "electric",
        "toxic",
        "matrix",
        "cyberpunk",
        "glitch",
        "vaporwave",
        "hologram",
        "cosmic",
        "chrome",
        "darkness"
    };
    private static final int MAX_ICON_LENGTH = 16;

    private final UserCustomizationRepository repository;
    private final Collection<WordFilterWord> filteredWords;

    public UserCustomizationPurchaseService(
            UserCustomizationRepository repository, Collection<WordFilterWord> filteredWords) {
        this.repository = repository;
        this.filteredWords = List.copyOf(filteredWords);
    }

    public PurchaseResult purchaseCustomPrefix(Habbo habbo, CustomPrefixRequest request) throws SQLException {
        Map<String, Integer> settings = this.repository.loadPrefixSettings();
        int maxLength = setting(settings, "max_length", 15);
        int minRank = setting(settings, "min_rank_to_buy", 1);
        int priceCredits = setting(settings, "price_credits", 5);
        int pricePoints = setting(settings, "price_points", 0);
        int pointsType = setting(settings, "points_type", 0);
        int fontPriceCredits = setting(settings, "font_price_credits", 10);
        int fontPricePoints = setting(settings, "font_price_points", 0);
        int fontPointsType = setting(settings, "font_points_type", pointsType);
        int maxPrefixes = setting(settings, "max_prefixes", 60);

        if (maxPrefixes > 0
                && habbo.getInventory().getPrefixesComponent().getPrefixes().size() >= maxPrefixes) {
            return PurchaseResult.failure("You already own the maximum number of prefixes (" + maxPrefixes + ").");
        }

        String text = request.text().trim();
        if (text.isEmpty() || text.length() > maxLength) {
            return PurchaseResult.failure("Prefix text is invalid or too long (max " + maxLength + " characters).");
        }
        if (containsControlChars(text)) {
            return PurchaseResult.failure("Prefix text contains invalid characters.");
        }
        if (this.containsFilteredWord(text)) {
            return PurchaseResult.failure("This prefix contains a blocked word.");
        }

        String rawColor = request.color() != null ? request.color().trim() : "#000000";
        if (rawColor.isEmpty()) {
            rawColor = "#000000";
        }
        String[] colorParts = rawColor.split(",");
        if (colorParts.length > text.length()) {
            return PurchaseResult.failure("Invalid color format.");
        }
        for (String part : colorParts) {
            if (!part.matches("^#[0-9A-Fa-f]{6}$")) {
                return PurchaseResult.failure("Invalid color format.");
            }
        }
        if (habbo.getHabboInfo().getRank().getId() < minRank) {
            return PurchaseResult.failure("Your rank is too low to purchase prefixes.");
        }

        String icon = normalizeNullable(request.icon());
        if (!isValidIcon(icon)) {
            return PurchaseResult.failure("Invalid prefix icon.");
        }
        String effect = normalizeNullable(request.effect()).toLowerCase();
        if (!isAllowedEffect(effect)) {
            return PurchaseResult.failure("Invalid prefix effect.");
        }

        // Limit: max 1 custom prefix per style/effect
        for (UserPrefix existingPrefix : habbo.getInventory().getPrefixesComponent().getPrefixes()) {
            if (existingPrefix.getEffect().equalsIgnoreCase(effect)) {
                return PurchaseResult.failure("Possiedi già un prefisso con questo stile (" + (effect.isEmpty() ? "Standard" : effect) + "). Elimina quello vecchio nei tuoi prefissi per crearne uno nuovo.");
            }
        }

        String font = normalizeNullable(request.font()).toLowerCase();
        if (!isAllowedFont(font)) {
            return PurchaseResult.failure("Invalid font format.");
        }

        int effectPriceCredits = 0;
        int effectPricePoints = 0;
        int effectPointsType = pointsType;

        if (!effect.isEmpty()) {
            Optional<UserCustomizationRepository.CatalogPrefixOffer> catalogEffectOffer = Optional.empty();
            try {
                catalogEffectOffer = this.repository.findCatalogPrefixByEffect(effect);
            } catch (SQLException e) {
                LOGGER.error("Error reading effect offer from catalog", e);
            }

            if (catalogEffectOffer.isPresent()) {
                UserCustomizationRepository.CatalogPrefixOffer offer = catalogEffectOffer.get();
                if (!offer.enabled()) {
                    return PurchaseResult.failure("Questo effetto prefisso è attualmente disabilitato.");
                }
                effectPriceCredits = offer.credits() > 0 ? offer.credits() : (offer.pointsType() == -1 ? offer.points() : 0);
                effectPricePoints = offer.pointsType() != -1 ? offer.points() : 0;
                effectPointsType = offer.pointsType();
            } else {
                int effectEnabled = setting(settings, "effect_enabled_" + effect, 1);
                if (effectEnabled == 0) {
                    return PurchaseResult.failure("This prefix effect is currently disabled.");
                }
                effectPriceCredits = setting(settings, "effect_price_credits_" + effect, getDefaultEffectCredits(effect));
                effectPricePoints = setting(settings, "effect_price_points_" + effect, getDefaultEffectPoints(effect));
                effectPointsType = setting(settings, "effect_points_type_" + effect, getDefaultEffectPointsType(effect));
            }
        }

        int priceColorCredits = setting(settings, "price_color_credits", 5);
        int priceColorPoints = setting(settings, "price_color_points", 0);
        int colorPointsType = setting(settings, "color_points_type", 5);

        boolean isCustomColor = !request.color().equalsIgnoreCase("#000000") && !request.color().equalsIgnoreCase("#FFFFFF");

        int totalCredits = priceCredits + effectPriceCredits;
        if (isCustomColor) {
            totalCredits += priceColorCredits;
        }

        Map<Integer, Integer> pointsToDebit = new HashMap<>();
        if (pricePoints > 0) {
            pointsToDebit.put(pointsType, pointsToDebit.getOrDefault(pointsType, 0) + pricePoints);
        }
        if (effectPricePoints > 0) {
            pointsToDebit.put(effectPointsType, pointsToDebit.getOrDefault(effectPointsType, 0) + effectPricePoints);
        }
        if (isCustomColor && priceColorPoints > 0) {
            pointsToDebit.put(colorPointsType, pointsToDebit.getOrDefault(colorPointsType, 0) + priceColorPoints);
        }
        if (!font.isEmpty()) {
            totalCredits += fontPriceCredits;
            if (fontPricePoints > 0) {
                pointsToDebit.put(fontPointsType, pointsToDebit.getOrDefault(fontPointsType, 0) + fontPricePoints);
            }
        }

        if (totalCredits > 0 && habbo.getHabboInfo().getCredits() < totalCredits) {
            return PurchaseResult.failure("Not enough credits.");
        }

        for (Map.Entry<Integer, Integer> entry : pointsToDebit.entrySet()) {
            if (entry.getValue() > 0 && habbo.getHabboInfo().getCurrencyAmount(entry.getKey()) < entry.getValue()) {
                return PurchaseResult.failure("Not enough points.");
            }
        }

        List<EconomyOperation> operations = new ArrayList<>();
        if (totalCredits > 0) {
            operations.add(debit(
                    EconomyOperationId.create("custom-prefix:" + habbo.getHabboInfo().getId() + ":credits"),
                    habbo,
                    -1,
                    totalCredits,
                    "custom_prefix_purchase",
                    "inventory.custom_prefix.purchase",
                    "text=" + text));
        }

        for (Map.Entry<Integer, Integer> entry : pointsToDebit.entrySet()) {
            if (entry.getValue() > 0) {
                operations.add(debit(
                        EconomyOperationId.create(
                                "custom-prefix:" + habbo.getHabboInfo().getId() + ":points:" + entry.getKey()),
                        habbo,
                        entry.getKey(),
                        entry.getValue(),
                        "custom_prefix_purchase",
                        "inventory.custom_prefix.purchase",
                        "text=" + text));
            }
        }

        if (!operations.isEmpty()) {
            try {
                LedgerWalletMutation.executeBatch(habbo, operations);
            } catch (IllegalArgumentException exception) {
                return PurchaseResult.failure("Payment failed.");
            }
        }

        UserPrefix prefix = new UserPrefix(
                habbo.getHabboInfo().getId(),
                text,
                request.color(),
                icon,
                effect,
                font,
                0,
                text,
                0,
                0,
                true);
        return PurchaseResult.prefix(prefix, totalCredits > 0, !pointsToDebit.isEmpty());
    }

    public PurchaseResult purchaseCatalogPrefix(Habbo habbo, int catalogPrefixId) throws SQLException {
        if (habbo.getInventory().getPrefixesComponent().getPrefixByCatalogId(catalogPrefixId) != null) {
            return PurchaseResult.failure("You already own this prefix.");
        }
        var offer = this.repository.findCatalogPrefix(catalogPrefixId).orElse(null);
        if (offer == null || !offer.enabled()) {
            return PurchaseResult.unavailable();
        }
        if (offer.credits() > 0 && habbo.getHabboInfo().getCredits() < offer.credits()) {
            return PurchaseResult.failure("Not enough credits.");
        }
        if (offer.points() > 0 && habbo.getHabboInfo().getCurrencyAmount(offer.pointsType()) < offer.points()) {
            return PurchaseResult.failure("Not enough points.");
        }
        List<EconomyOperation> operations = new ArrayList<>();
        if (offer.credits() > 0) {
            operations.add(debit(
                    EconomyOperationId.create(
                            "catalog-prefix:" + habbo.getHabboInfo().getId() + ":credits:" + catalogPrefixId),
                    habbo,
                    -1,
                    offer.credits(),
                    "catalog_prefix_purchase",
                    "inventory.catalog_prefix.purchase",
                    "catalogPrefixId=" + catalogPrefixId));
        }
        if (offer.points() > 0) {
            operations.add(debit(
                    EconomyOperationId.create(
                            "catalog-prefix:" + habbo.getHabboInfo().getId() + ":points:" + catalogPrefixId),
                    habbo,
                    offer.pointsType(),
                    offer.points(),
                    "catalog_prefix_purchase",
                    "inventory.catalog_prefix.purchase",
                    "catalogPrefixId=" + catalogPrefixId));
        }
        if (!operations.isEmpty()) {
            try {
                LedgerWalletMutation.executeBatch(habbo, operations);
            } catch (IllegalArgumentException exception) {
                return PurchaseResult.failure("Payment failed.");
            }
        }
        UserPrefix prefix = new UserPrefix(
                habbo.getHabboInfo().getId(),
                offer.text(),
                offer.color(),
                offer.icon(),
                offer.effect(),
                offer.font(),
                offer.id(),
                offer.displayName(),
                offer.points(),
                offer.pointsType(),
                false);
        return PurchaseResult.prefix(prefix, offer.credits() > 0, offer.points() > 0);
    }

    public PurchaseResult purchaseCustomNick(Habbo habbo, CustomNickRequest request) throws SQLException {
        Map<String, Integer> settings = this.repository.loadNickSettings();
        int minRank = setting(settings, "min_rank_to_buy", 1);
        int priceCredits = setting(settings, "price_custom_credits", 10);
        int pricePoints = setting(settings, "price_custom_points", 2);
        int pointsType = setting(settings, "custom_points_type", 5);
        int fontPriceCredits = setting(settings, "font_price_credits", 10);
        int fontPricePoints = setting(settings, "font_price_points", 0);
        int fontPointsType = setting(settings, "font_points_type", pointsType);
        int maxNicks = setting(settings, "max_nicks", 60);

        if (maxNicks > 0 && habbo.getInventory().getUserNicksComponent().getNicks().size() >= maxNicks) {
            return PurchaseResult.failure("You already own the maximum number of nickname styles (" + maxNicks + ").");
        }

        if (habbo.getHabboInfo().getRank().getId() < minRank) {
            return PurchaseResult.failure("Your rank is too low to purchase nickname styles.");
        }

        String color = request.color() != null ? request.color().trim() : "#000000";
        if (color.isEmpty()) {
            color = "#000000";
        }
        if (!color.matches("^#[0-9A-Fa-f]{6}$")) {
            return PurchaseResult.failure("Invalid color format.");
        }

        String effect = normalizeNullable(request.effect()).toLowerCase();
        if (!isAllowedNickEffect(effect)) {
            return PurchaseResult.failure("Invalid nickname effect.");
        }

        // Limit: max 1 custom nickname per style/effect
        for (UserNick existingNick : habbo.getInventory().getUserNicksComponent().getNicks()) {
            if (existingNick.getEffect().equalsIgnoreCase(effect)) {
                return PurchaseResult.failure("Possiedi già uno stile nickname con questo effetto (" + (effect.isEmpty() ? "Standard" : effect) + "). Elimina quello vecchio per acquistarne uno nuovo.");
            }
        }

        String font = normalizeNullable(request.font()).toLowerCase();
        if (!isAllowedFont(font)) {
            return PurchaseResult.failure("Invalid font format.");
        }

        int effectPriceCredits = 0;
        int effectPricePoints = 0;
        int effectPointsType = pointsType;

        if (!effect.isEmpty()) {
            Optional<UserCustomizationRepository.CatalogNickOffer> catalogOffer = Optional.empty();
            try {
                catalogOffer = this.repository.findCatalogNickByEffect(effect);
            } catch (SQLException e) {
                LOGGER.error("Error reading nick effect offer", e);
            }

            if (catalogOffer.isPresent()) {
                var offer = catalogOffer.get();
                if (!offer.enabled()) {
                    return PurchaseResult.failure("Questo effetto nickname è disabilitato.");
                }
                effectPriceCredits = offer.credits();
                effectPricePoints = offer.points();
                effectPointsType = offer.pointsType();
            } else {
                effectPriceCredits = setting(settings, "effect_price_credits_" + effect, 8);
                effectPricePoints = setting(settings, "effect_price_points_" + effect, 1);
                effectPointsType = 5;
            }
        }

        int priceColorCredits = setting(settings, "price_color_credits", 5);
        int totalCredits = priceCredits + effectPriceCredits;
        if (!color.equalsIgnoreCase("#000000") && !color.equalsIgnoreCase("#FFFFFF")) {
            totalCredits += priceColorCredits;
        }

        Map<Integer, Integer> pointsToDebit = new HashMap<>();
        if (pricePoints > 0) pointsToDebit.put(pointsType, pointsToDebit.getOrDefault(pointsType, 0) + pricePoints);
        if (effectPricePoints > 0) pointsToDebit.put(effectPointsType, pointsToDebit.getOrDefault(effectPointsType, 0) + effectPricePoints);
        if (!font.isEmpty()) {
            totalCredits += fontPriceCredits;
            if (fontPricePoints > 0) pointsToDebit.put(fontPointsType, pointsToDebit.getOrDefault(fontPointsType, 0) + fontPricePoints);
        }

        if (totalCredits > 0 && habbo.getHabboInfo().getCredits() < totalCredits) {
            return PurchaseResult.failure("Not enough credits.");
        }

        for (Map.Entry<Integer, Integer> entry : pointsToDebit.entrySet()) {
            if (entry.getValue() > 0 && habbo.getHabboInfo().getCurrencyAmount(entry.getKey()) < entry.getValue()) {
                return PurchaseResult.failure("Not enough points.");
            }
        }

        List<EconomyOperation> operations = new ArrayList<>();
        if (totalCredits > 0) {
            operations.add(debit(
                    EconomyOperationId.create("custom-nick:" + habbo.getHabboInfo().getId() + ":credits"),
                    habbo,
                    -1,
                    totalCredits,
                    "custom_nick_purchase",
                    "inventory.custom_nick.purchase",
                    "color=" + color + ";effect=" + effect));
        }

        for (Map.Entry<Integer, Integer> entry : pointsToDebit.entrySet()) {
            if (entry.getValue() > 0) {
                operations.add(debit(
                        EconomyOperationId.create("custom-nick:" + habbo.getHabboInfo().getId() + ":points:" + entry.getKey()),
                        habbo,
                        entry.getKey(),
                        entry.getValue(),
                        "custom_nick_purchase",
                        "inventory.custom_nick.purchase",
                        "color=" + color + ";effect=" + effect));
            }
        }

        if (!operations.isEmpty()) {
            try {
                LedgerWalletMutation.executeBatch(habbo, operations);
            } catch (IllegalArgumentException exception) {
                return PurchaseResult.failure("Payment failed.");
            }
        }

        UserNick nick = new UserNick(habbo.getHabboInfo().getId(), color, effect, font, 0);
        return PurchaseResult.nick(nick, totalCredits > 0, !pointsToDebit.isEmpty());
    }

    public PurchaseResult purchaseCatalogNick(Habbo habbo, int catalogNickId) throws SQLException {
        if (habbo.getInventory().getUserNicksComponent().getNickByCatalogId(catalogNickId) != null) {
            return PurchaseResult.failure("You already own this nickname style.");
        }
        var offer = this.repository.findCatalogNick(catalogNickId).orElse(null);
        if (offer == null || !offer.enabled()) {
            return PurchaseResult.failure("This nickname style is not available.");
        }
        if (offer.credits() > 0 && habbo.getHabboInfo().getCredits() < offer.credits()) {
            return PurchaseResult.failure("Not enough credits.");
        }
        if (offer.points() > 0 && habbo.getHabboInfo().getCurrencyAmount(offer.pointsType()) < offer.points()) {
            return PurchaseResult.failure("Not enough points.");
        }

        List<EconomyOperation> operations = new ArrayList<>();
        if (offer.credits() > 0) {
            operations.add(debit(
                    EconomyOperationId.create("catalog-nick:" + habbo.getHabboInfo().getId() + ":credits:" + catalogNickId),
                    habbo,
                    -1,
                    offer.credits(),
                    "catalog_nick_purchase",
                    "inventory.catalog_nick.purchase",
                    "catalogNickId=" + catalogNickId));
        }
        if (offer.points() > 0) {
            operations.add(debit(
                    EconomyOperationId.create("catalog-nick:" + habbo.getHabboInfo().getId() + ":points:" + catalogNickId),
                    habbo,
                    offer.pointsType(),
                    offer.points(),
                    "catalog_nick_purchase",
                    "inventory.catalog_nick.purchase",
                    "catalogNickId=" + catalogNickId));
        }

        if (!operations.isEmpty()) {
            try {
                LedgerWalletMutation.executeBatch(habbo, operations);
            } catch (IllegalArgumentException exception) {
                return PurchaseResult.failure("Payment failed.");
            }
        }

        UserNick nick = new UserNick(habbo.getHabboInfo().getId(), offer.color(), offer.effect(), offer.font(), offer.id());
        return PurchaseResult.nick(nick, offer.credits() > 0, offer.points() > 0);
    }

    public PurchaseResult purchaseNickIcon(Habbo habbo, String iconKey) throws SQLException {
        var offer = this.repository.findNickIcon(iconKey).orElse(null);
        if (offer == null || !offer.enabled()) {
            return PurchaseResult.failure("This nick icon is not available.");
        }
        String finalIconKey = offer.iconKey();
        if (habbo.getInventory().getNickIconsComponent().getNickIconByKey(finalIconKey) != null
                || habbo.getInventory().getNickIconsComponent().getNickIconByKey(iconKey) != null) {
            return PurchaseResult.failure("You already own this nick icon.");
        }
        if (offer.points() > 0 && habbo.getHabboInfo().getCurrencyAmount(offer.pointsType()) < offer.points()) {
            return PurchaseResult.failure("Not enough points.");
        }
        if (offer.points() > 0) {
            try {
                LedgerWalletMutation.execute(
                        habbo,
                        debit(
                                EconomyOperationId.create(
                                        "nick-icon:" + habbo.getHabboInfo().getId() + ":" + finalIconKey),
                                habbo,
                                offer.pointsType(),
                                offer.points(),
                                "nick_icon_purchase",
                                "inventory.nick_icon.purchase",
                                finalIconKey));
            } catch (IllegalArgumentException exception) {
                return PurchaseResult.failure("Not enough points.");
            }
        }
        UserNickIcon nickIcon = new UserNickIcon(habbo.getHabboInfo().getId(), finalIconKey);
        return PurchaseResult.nickIcon(nickIcon, offer.points() > 0);
    }

    public static boolean containsControlChars(String text) {
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character < 0x20 || character == 0x7F) {
                return true;
            }
        }
        return false;
    }

    public static boolean isValidIcon(String icon) {
        if (icon.isEmpty()) {
            return true;
        }
        if (icon.length() > MAX_ICON_LENGTH) {
            return false;
        }
        for (int index = 0; index < icon.length(); index++) {
            char character = icon.charAt(index);
            if (character < 0x20 || character == 0x7F || character == '<' || character == '>') {
                return false;
            }
        }
        return true;
    }

    public static boolean isAllowedEffect(String effect) {
        for (String candidate : ALLOWED_EFFECTS) {
            if (candidate.equalsIgnoreCase(effect)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAllowedNickEffect(String effect) {
        for (String candidate : ALLOWED_NICK_EFFECTS) {
            if (candidate.equalsIgnoreCase(effect)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAllowedFont(String font) {
        for (String candidate : ALLOWED_FONTS) {
            if (candidate.equalsIgnoreCase(font)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsFilteredWord(String text) {
        String sanitized = text.toLowerCase();
        for (WordFilterWord word : this.filteredWords) {
            if (sanitized.contains(word.key.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static int setting(Map<String, Integer> settings, String key, int fallback) {
        return settings.getOrDefault(key, fallback);
    }

    private static String normalizeNullable(String value) {
        return value == null ? "" : value.trim();
    }

    private static EconomyOperation debit(
            String operationId,
            Habbo habbo,
            int currency,
            int amount,
            String operation,
            String reason,
            String metadata) {
        return new EconomyOperation(
                operationId,
                habbo.getHabboInfo().getId(),
                habbo.getHabboInfo().getId(),
                operation,
                reason,
                currency,
                -amount,
                null,
                metadata);
    }

    public record CustomPrefixRequest(String text, String color, String icon, String effect, String font) {}
    public record CustomNickRequest(String color, String effect, String font) {}

    public record PurchaseResult(
            Status status,
            String message,
            UserPrefix prefix,
            UserNickIcon nickIcon,
            UserNick nick,
            boolean creditsChanged,
            boolean currencyChanged) {

        public static PurchaseResult failure(String message) {
            return new PurchaseResult(Status.FAILURE, message, null, null, null, false, false);
        }

        public static PurchaseResult unavailable() {
            return new PurchaseResult(Status.UNAVAILABLE, "", null, null, null, false, false);
        }

        public static PurchaseResult refresh() {
            return new PurchaseResult(Status.REFRESH, "", null, null, null, false, false);
        }

        public static PurchaseResult prefix(UserPrefix prefix, boolean creditsChanged, boolean currencyChanged) {
            return new PurchaseResult(Status.SUCCESS, "", prefix, null, null, creditsChanged, currencyChanged);
        }

        public static PurchaseResult nickIcon(UserNickIcon nickIcon, boolean currencyChanged) {
            return new PurchaseResult(Status.SUCCESS, "", null, nickIcon, null, false, currencyChanged);
        }

        public static PurchaseResult nick(UserNick nick, boolean creditsChanged, boolean currencyChanged) {
            return new PurchaseResult(Status.SUCCESS, "", null, null, nick, creditsChanged, currencyChanged);
        }
    }

    public enum Status {
        SUCCESS,
        FAILURE,
        REFRESH,
        UNAVAILABLE
    }

    private static int getDefaultEffectCredits(String effect) {
        return switch (effect) {
            case "italic", "underline" -> 1;
            case "glow", "shadow", "outline" -> 2;
            case "pulse" -> 3;
            case "bounce", "wave", "shake" -> 4;
            case "bold-glow", "discord-neon", "cartoon", "toon" -> 5;
            case "pop" -> 6;
            case "frost" -> 7;
            case "sparkle", "matrix" -> 8;
            case "ice", "gold" -> 10;
            case "fire", "glitch", "cyberpunk" -> 12;
            case "electric" -> 14;
            case "rainbow", "cosmic" -> 15;
            default -> 0;
        };
    }

    private static int getDefaultEffectPoints(String effect) {
        return switch (effect) {
            case "frost", "sparkle" -> 1;
            case "matrix", "ice", "gold" -> 2;
            case "fire", "glitch", "cyberpunk" -> 3;
            case "electric" -> 4;
            case "rainbow", "cosmic" -> 5;
            default -> 0;
        };
    }

    private static int getDefaultEffectPointsType(String effect) {
        return 5;
    }
}
