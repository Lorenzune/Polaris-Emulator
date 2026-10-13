package com.eu.habbo.habbohotel.catalog.marketplace;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementInteractions;
import com.eu.habbo.habbohotel.economy.EconomyMutationResult;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.LedgerWalletMutation;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.catalog.marketplace.RequestOffersEvent;
import com.eu.habbo.messages.outgoing.catalog.marketplace.MarketplaceBuyErrorComposer;
import com.eu.habbo.messages.outgoing.catalog.marketplace.MarketplaceCancelSaleComposer;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.inventory.RemoveHabboItemComposer;
import com.eu.habbo.messages.outgoing.users.UserCreditsComposer;
import com.eu.habbo.messages.outgoing.users.UserPointsComposer;
import com.eu.habbo.plugin.events.marketplace.MarketPlaceItemCancelledEvent;
import com.eu.habbo.plugin.events.marketplace.MarketPlaceItemOfferedEvent;
import com.eu.habbo.plugin.events.marketplace.MarketPlaceItemSoldEvent;
import com.eu.habbo.plugin.events.users.UserCreditsEvent;
import com.eu.habbo.plugin.events.users.UserPointsEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MarketPlace {
    private static final Logger LOGGER = LoggerFactory.getLogger(MarketPlace.class);
    private static final String PURCHASE_OFFER_SQL = """
            SELECT item_id, user_id, price, state, currency_type,timestamp
            FROM marketplace_items
            WHERE id = ? LIMIT 1
            """;
    private static final String PURCHASE_ITEM_SQL = """
            SELECT id, user_id, room_id, item_id, wall_pos, x, y, z, rot,
                   extra_data, wired_data, limited_data, guild_id
            FROM items
            WHERE id = ? LIMIT 1
            """;
    public static final int MINIMUM_LISTING_PRICE = 1;
    public static final int MAXIMUM_LISTING_PRICE = 1_000_000_000;

    // Configuration. Loaded from database & updated accordingly.
    public static volatile boolean MARKETPLACE_ENABLED = true;

    // Currency to use.
    public static volatile int MARKETPLACE_CURRENCY = 0;

    public static int legacyCurrency() {
        return MARKETPLACE_CURRENCY == 0 ? -1 : MARKETPLACE_CURRENCY;
    }

    public static Set<MarketPlaceOffer> getOwnOffers(Habbo habbo) {
        Set<MarketPlaceOffer> offers = new HashSet<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT b.type AS type,COALESCE(i.item_id,h.base_item_id) AS base_item_id,COALESCE(i.limited_data,h.limited_data) AS ltd_data,m.* FROM marketplace_items m LEFT JOIN items i ON m.item_id=i.id LEFT JOIN rare_market_sales h ON h.offer_id=m.id INNER JOIN items_base b ON b.id=COALESCE(i.item_id,h.base_item_id) WHERE m.user_id=?")) {
            statement.setInt(1, habbo.getHabboInfo().getId());
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    offers.add(new MarketPlaceOffer(set, true));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return offers;
    }

    public static void takeBackItem(Habbo habbo, int offerId) {
        MarketPlaceOffer offer = habbo.getInventory().getOffer(offerId);

        if (offer == null) {
            return;
        }

        if (!Emulator.getPluginManager()
                .fireEvent(new MarketPlaceItemCancelledEvent(offer))
                .isCancelled()) {
            takeBackItem(habbo, offer);
        }
    }

    private static void takeBackItem(Habbo habbo, MarketPlaceOffer offer) {
        if (offer != null && habbo.getInventory().getMarketplaceItems().contains(offer)) {
            RequestOffersEvent.cachedResults.clear();
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
                try (PreparedStatement ownerCheck = connection.prepareStatement(
                        "SELECT user_id FROM marketplace_items WHERE id = ?",
                        ResultSet.TYPE_SCROLL_INSENSITIVE,
                        ResultSet.CONCUR_READ_ONLY)) {
                    ownerCheck.setInt(1, offer.getOfferId());
                    try (ResultSet ownerSet = ownerCheck.executeQuery()) {
                        ownerSet.last();

                        if (ownerSet.getRow() == 0) {
                            return;
                        }

                        ownerSet.first();
                        if (ownerSet.getInt("user_id") != habbo.getHabboInfo().getId()) {
                            LOGGER.warn(
                                    "User {} attempted to take back marketplace offer {} owned by user {}",
                                    habbo.getHabboInfo().getId(),
                                    offer.getOfferId(),
                                    ownerSet.getInt("user_id"));
                            return;
                        }

                        try (PreparedStatement statement = connection.prepareStatement(
                                "DELETE FROM marketplace_items WHERE id = ? AND state != 2")) {
                            statement.setInt(1, offer.getOfferId());
                            int count = statement.executeUpdate();

                            if (count != 0) {
                                habbo.getInventory().removeMarketplaceOffer(offer);
                                try (PreparedStatement updateItems = connection.prepareStatement(
                                        "UPDATE items SET user_id = ? WHERE id = ? LIMIT 1")) {
                                    updateItems.setInt(1, habbo.getHabboInfo().getId());
                                    updateItems.setInt(2, offer.getSoldItemId());
                                    updateItems.execute();

                                    try (PreparedStatement selectItem =
                                            connection.prepareStatement("SELECT * FROM items WHERE id = ? LIMIT 1")) {
                                        selectItem.setInt(1, offer.getSoldItemId());
                                        try (ResultSet set = selectItem.executeQuery()) {
                                            while (set.next()) {
                                                HabboItem item = Emulator.getGameEnvironment()
                                                        .getItemManager()
                                                        .loadHabboItem(set);
                                                habbo.getInventory()
                                                        .getItemsComponent()
                                                        .addItem(item);
                                                habbo.getClient()
                                                        .sendResponse(new MarketplaceCancelSaleComposer(offer, true));
                                                habbo.getClient().sendResponse(new AddHabboItemComposer(item));
                                                habbo.getClient().sendResponse(new InventoryRefreshComposer());
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
                habbo.getClient().sendResponse(new MarketplaceCancelSaleComposer(offer, false));
            }
        }
    }

    public static List<MarketPlaceOffer> getOffers(int minPrice, int maxPrice, String search, int sort) {
        return getOffers(minPrice, maxPrice, search, sort, legacyCurrency());
    }

    public static List<MarketPlaceOffer> getOffers(int minPrice, int maxPrice, String search, int sort, int currency) {
        List<MarketPlaceOffer> offers = new ArrayList<>(10);
        MarketplaceOffersQuery.Request request =
                new MarketplaceOffersQuery.Request(minPrice, maxPrice, search, sort, false, currency);

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(MarketplaceOffersQuery.sql(request))) {
            MarketplaceOffersQuery.bind(
                    statement, request, Emulator.getIntUnixTimestamp(), MINIMUM_LISTING_PRICE, MAXIMUM_LISTING_PRICE);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    offers.add(new MarketPlaceOffer(set, false));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return offers;
    }

    public static void serializeItemInfo(int itemId, ServerMessage message) {
        serializeItemInfo(itemId, message, legacyCurrency(), 1);
    }

    public static void serializeItemInfo(int itemId, ServerMessage message, int currency, int type) {
        String furnitureType = type == 2 ? "i" : "s";
        try (var c = Emulator.getDatabase().getDataSource().getConnection()) {
            int average = 0, count = 0;
            try (var s = c.prepareStatement(
                    "SELECT COALESCE(AVG(s.unit_price),0) avg FROM rare_market_sales s JOIN items_base b ON b.id=s.base_item_id WHERE b.sprite_id=? AND b.type=? AND s.currency_type=?")) {
                s.setInt(1, itemId);
                s.setString(2, furnitureType);
                s.setInt(3, currency);
                try (var r = s.executeQuery()) {
                    r.next();
                    average = r.getInt(1);
                }
            }
            try (var s = c.prepareStatement(
                    "SELECT COUNT(*) FROM marketplace_items m JOIN items i ON i.id=m.item_id JOIN items_base b ON b.id=i.item_id WHERE m.state=1 AND m.timestamp>? AND b.sprite_id=? AND b.type=? AND m.currency_type=?")) {
                s.setInt(1, Emulator.getIntUnixTimestamp() - 172800);
                s.setInt(2, itemId);
                s.setString(3, furnitureType);
                s.setInt(4, currency);
                try (var r = s.executeQuery()) {
                    r.next();
                    count = r.getInt(1);
                }
            }
            var days = new ArrayList<int[]>();
            int length = 1;
            try (var s = c.prepareStatement(
                    "SELECT FLOOR(AVG(s.unit_price)) price,COUNT(*) sold,DATEDIFF(NOW(),FROM_UNIXTIME(s.sold_at)) day FROM rare_market_sales s JOIN items_base b ON b.id=s.base_item_id WHERE b.sprite_id=? AND b.type=? AND s.currency_type=? GROUP BY DATE(FROM_UNIXTIME(s.sold_at)) ORDER BY day DESC")) {
                s.setInt(1, itemId);
                s.setString(2, furnitureType);
                s.setInt(3, currency);
                try (var r = s.executeQuery()) {
                    while (r.next()) {
                        int day = Math.max(0, r.getInt("day"));
                        length = Math.max(length, day + 1);
                        days.add(new int[] {day, r.getInt("price"), r.getInt("sold")});
                    }
                }
            }
            message.appendInt(calculateCommision(average));
            message.appendInt(count);
            message.appendInt(length);
            message.appendInt(days.size());
            for (var day : days) {
                message.appendInt(-day[0]);
                message.appendInt(calculateCommision(day[1]));
                message.appendInt(day[2]);
            }
            message.appendInt(type);
            message.appendInt(itemId);
        } catch (SQLException e) {
            LOGGER.error("Marketplace lifetime statistics failed", e);
            message.appendInt(0);
            message.appendInt(0);
            message.appendInt(1);
            message.appendInt(0);
            message.appendInt(type);
            message.appendInt(itemId);
        }
    }

    public static int itemsOnSale(int baseItemId) {
        try (var c = Emulator.getDatabase().getDataSource().getConnection();
                var s = c.prepareStatement(
                        "SELECT COUNT(*) FROM marketplace_items m JOIN items i ON i.id=m.item_id JOIN items_base b ON b.id=i.item_id WHERE m.state=1 AND m.timestamp>? AND b.sprite_id=? AND m.currency_type=?")) {
            s.setInt(1, Emulator.getIntUnixTimestamp() - 172800);
            s.setInt(2, baseItemId);
            s.setInt(3, legacyCurrency());
            try (var r = s.executeQuery()) {
                r.next();
                return r.getInt(1);
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    public static void buyItem(int offerId, GameClient client) {
        buyItem(offerId, client, legacyCurrency());
    }

    public static void buyItem(int offerId, GameClient client, int expectedCurrency) {
        RequestOffersEvent.cachedResults.clear();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(PURCHASE_OFFER_SQL)) {
                statement.setInt(1, offerId);
                try (ResultSet set = statement.executeQuery()) {
                    if (set.next()) {
                        try (PreparedStatement itemStatement = connection.prepareStatement(
                                PURCHASE_ITEM_SQL, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)) {
                            itemStatement.setInt(1, set.getInt("item_id"));
                            try (ResultSet itemSet = itemStatement.executeQuery()) {
                                itemSet.first();

                                if (itemSet.getRow() > 0) {
                                    int currency = set.getInt("currency_type");
                                    if (currency != expectedCurrency
                                            || Emulator.getIntUnixTimestamp() - set.getInt("timestamp") >= 172800
                                            || set.getInt("user_id")
                                                    == client.getHabbo()
                                                            .getHabboInfo()
                                                            .getId()) {
                                        sendErrorMessage(client, set.getInt("item_id"), offerId);
                                        return;
                                    }
                                    int rawPrice = set.getInt("price");
                                    if (!isValidListingPrice(rawPrice)) {
                                        sendErrorMessage(client, set.getInt("item_id"), offerId);
                                        return;
                                    }

                                    int price = MarketPlace.calculateCommision(rawPrice);
                                    if (set.getInt("state") != 1) {
                                        sendErrorMessage(client, set.getInt("item_id"), offerId);
                                    } else if ((currency == -1
                                                    && price
                                                            > client.getHabbo()
                                                                    .getHabboInfo()
                                                                    .getCredits())
                                            || (currency >= 0
                                                    && price
                                                            > client.getHabbo()
                                                                    .getHabboInfo()
                                                                    .getCurrencyAmount(currency))) {
                                        client.sendResponse(new MarketplaceBuyErrorComposer(
                                                MarketplaceBuyErrorComposer.NOT_ENOUGH_CREDITS, 0, offerId, price));
                                    } else {
                                        Habbo habbo = Emulator.getGameServer()
                                                .getGameClientManager()
                                                .getHabbo(set.getInt("user_id"));
                                        HabboItem item = Emulator.getGameEnvironment()
                                                .getItemManager()
                                                .loadHabboItem(itemSet);

                                        MarketPlaceItemSoldEvent event = new MarketPlaceItemSoldEvent(
                                                habbo, client.getHabbo(), item, set.getInt("price"));
                                        if (Emulator.getPluginManager()
                                                        .fireEvent(event)
                                                        .isCancelled()
                                                || !isValidListingPrice(event.price)) {
                                            return;
                                        }

                                        int soldTimestamp = Emulator.getIntUnixTimestamp();
                                        int agreedPrice = event.price;
                                        event.price = calculateCommision(event.price);

                                        PreparedCharge charge =
                                                prepareMarketplaceCharge(client.getHabbo(), event.price, currency);
                                        if (charge == null) return;

                                        EconomyMutationResult walletMutation =
                                                LedgerWalletMutation.coordinated(client.getHabbo(), () -> {
                                                    EconomyMutationResult mutation =
                                                            MarketPlacePurchaseTransaction.commit(
                                                                    offerId,
                                                                    item.getId(),
                                                                    client.getHabbo()
                                                                            .getHabboInfo()
                                                                            .getId(),
                                                                    charge.currencyType(),
                                                                    -charge.delta(),
                                                                    soldTimestamp,
                                                                    agreedPrice);
                                                    if (mutation != null) {
                                                        LedgerWalletMutation.applyCommitted(
                                                                client.getHabbo(),
                                                                charge.currencyType(),
                                                                mutation.balanceAfter());
                                                    }
                                                    return mutation;
                                                });
                                        if (walletMutation == null) {
                                            sendErrorMessage(
                                                    client, item.getBaseItem().getId(), offerId);
                                            return;
                                        }

                                        item.setUserId(
                                                client.getHabbo().getHabboInfo().getId());
                                        item.needsUpdate(true);
                                        Emulator.getThreading().run(item);

                                        client.getHabbo()
                                                .getInventory()
                                                .getItemsComponent()
                                                .addItem(item);

                                        publishMarketplaceCharge(client, charge);

                                        client.sendResponse(new AddHabboItemComposer(item));
                                        client.sendResponse(new InventoryRefreshComposer());
                                        client.sendResponse(new MarketplaceBuyErrorComposer(
                                                MarketplaceBuyErrorComposer.REFRESH, 0, offerId, price));

                                        AchievementInteractions.progressUser(
                                                set.getInt("user_id"), "HabboBuilder_TSale_");
                                        if (habbo != null) {
                                            MarketPlaceOffer offer =
                                                    habbo.getInventory().getOffer(offerId);
                                            if (offer != null) {
                                                offer.setState(MarketPlaceState.SOLD);
                                                offer.setSoldTimestamp(soldTimestamp);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public static void sendErrorMessage(GameClient client, int baseItemId, int offerId) {
        // A fresh search preserves currency, grouping and filters after a concurrent sale.
        client.sendResponse(new MarketplaceBuyErrorComposer(MarketplaceBuyErrorComposer.REFRESH, 0, offerId, 0));
    }

    public static boolean sellItem(GameClient client, HabboItem item, int price) {
        return sellItem(client, item, price, legacyCurrency());
    }

    public static boolean sellItem(GameClient client, HabboItem item, int price, int currency) {
        if (item == null || client == null) return false;
        try {
            if (!new com.eu.habbo.habbohotel.catalog.RareEconomyRepository(
                            Emulator.getDatabase().getDataSource())
                    .currencyEnabled(currency)) return false;
        } catch (SQLException e) {
            return false;
        }

        if (!item.getBaseItem().allowMarketplace() || !isValidListingPrice(price)) return false;

        MarketPlaceItemOfferedEvent event = new MarketPlaceItemOfferedEvent(client.getHabbo(), item, price);
        if (Emulator.getPluginManager().fireEvent(event).isCancelled()) {
            return false;
        }

        RequestOffersEvent.cachedResults.clear();

        client.sendResponse(new RemoveHabboItemComposer(event.item.getGiftAdjustedId()));
        client.sendResponse(new InventoryRefreshComposer());

        event.item.setFromGift(false);

        MarketPlaceOffer offer = new MarketPlaceOffer(event.item, event.price, client.getHabbo(), currency);
        if (!offer.isPersisted()) {
            LOGGER.warn(
                    "Marketplace offer insert failed for user {} item {}",
                    client.getHabbo().getHabboInfo().getId(),
                    event.item.getId());
            return false;
        }

        client.getHabbo().getInventory().addMarketplaceOffer(offer);
        client.getHabbo().getInventory().getItemsComponent().removeHabboItem(event.item);
        item.setUserId(-1);
        item.needsUpdate(true);
        Emulator.getThreading().run(item);

        return true;
    }

    public static void getCredits(GameClient client) {
        getCredits(client, legacyCurrency());
    }

    public static void getCredits(GameClient client, int currency) {
        try {
            var result = LedgerWalletMutation.coordinated(client.getHabbo(), () -> {
                var claim = MarketPlaceClaimTransaction.commit(
                        Emulator.getDatabase().getDataSource(),
                        client.getHabbo().getHabboInfo().getId(),
                        currency,
                        totals -> {
                            for (var entry : totals.entrySet()) {
                                if (entry.getKey() == -1) {
                                    var event = new UserCreditsEvent(client.getHabbo(), entry.getValue());
                                    if (Emulator.getPluginManager()
                                                    .fireEvent(event)
                                                    .isCancelled()
                                            || event.credits != entry.getValue()) return false;
                                } else {
                                    var event =
                                            new UserPointsEvent(client.getHabbo(), entry.getValue(), entry.getKey());
                                    if (Emulator.getPluginManager()
                                                    .fireEvent(event)
                                                    .isCancelled()
                                            || event.points != entry.getValue()
                                            || event.type != entry.getKey()) return false;
                                }
                            }
                            return true;
                        });
                for (var entry : claim.wallets().entrySet())
                    LedgerWalletMutation.applyCommitted(
                            client.getHabbo(), entry.getKey(), entry.getValue().balanceAfter());
                return claim;
            });
            for (int id : result.offerIds()) {
                var offer = client.getHabbo().getInventory().getOffer(id);
                if (offer != null) client.getHabbo().getInventory().removeMarketplaceOffer(offer);
            }
            for (var entry : result.totals().entrySet())
                publishMarketplaceCharge(client, new PreparedCharge(entry.getKey(), entry.getValue()));
        } catch (Exception e) {
            LOGGER.error("Atomic marketplace payout failed", e);
        }
    }

    public static int calculateCommision(int price) {
        long commission = price + (long) Math.ceil(price / 100.0);
        return commission > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) commission;
    }

    private static PreparedCharge prepareMarketplaceCharge(Habbo buyer, int price, int currency) {
        if (currency == -1) {
            UserCreditsEvent event = new UserCreditsEvent(buyer, -price);
            if (Emulator.getPluginManager().fireEvent(event).isCancelled() || event.credits != -price) return null;
            return new PreparedCharge(-1, event.credits);
        }

        UserPointsEvent event = new UserPointsEvent(buyer, -price, currency);
        if (Emulator.getPluginManager().fireEvent(event).isCancelled()
                || event.type != currency
                || event.points != -price) return null;
        return new PreparedCharge(event.type, event.points);
    }

    private static void publishMarketplaceCharge(GameClient client, PreparedCharge charge) {
        if (charge.currencyType() < 0) {
            client.sendResponse(new UserCreditsComposer(client.getHabbo()));
            return;
        }

        client.sendResponse(new UserPointsComposer(
                client.getHabbo().getHabboInfo().getCurrencyAmount(charge.currencyType()),
                charge.delta(),
                charge.currencyType()));
    }

    private record PreparedCharge(int currencyType, int delta) {}

    public static boolean isValidListingPrice(int price) {
        return price >= MINIMUM_LISTING_PRICE && price <= MAXIMUM_LISTING_PRICE;
    }
}
