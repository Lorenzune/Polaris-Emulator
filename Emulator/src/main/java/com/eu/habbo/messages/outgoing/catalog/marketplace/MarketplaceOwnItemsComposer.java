package com.eu.habbo.messages.outgoing.catalog.marketplace;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.marketplace.MarketPlaceOffer;
import com.eu.habbo.habbohotel.catalog.marketplace.MarketPlaceState;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MarketplaceOwnItemsComposer extends MessageComposer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MarketplaceOwnItemsComposer.class);

    private final Habbo habbo;
    private final boolean multi;

    public MarketplaceOwnItemsComposer(Habbo habbo) {
        this(habbo, false);
    }

    public MarketplaceOwnItemsComposer(Habbo habbo, boolean multi) {
        this.habbo = habbo;
        this.multi = multi;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.MarketplaceOwnItemsComposer);
        var offers = com.eu.habbo.habbohotel.catalog.marketplace.MarketPlace.getOwnOffers(this.habbo).stream()
                .filter(offer -> multi
                        || offer.getCurrencyType()
                                == com.eu.habbo.habbohotel.catalog.marketplace.MarketPlace.legacyCurrency())
                .toList();
        long waiting = offers.stream()
                .filter(o -> o.getState() == MarketPlaceState.SOLD
                        && o.getCurrencyType()
                                == com.eu.habbo.habbohotel.catalog.marketplace.MarketPlace.legacyCurrency())
                .mapToLong(MarketPlaceOffer::getPrice)
                .sum();
        this.response.appendInt((int) Math.min(Integer.MAX_VALUE, waiting));
        this.response.appendInt(offers.size());

        for (MarketPlaceOffer offer : offers) {
            try {
                if (offer.getState() == MarketPlaceState.OPEN) {
                    if ((offer.getTimestamp() + 172800) - Emulator.getIntUnixTimestamp() <= 0) {
                        offer.setState(MarketPlaceState.CLOSED);
                        Emulator.getThreading().run(offer);
                    }
                }

                this.response.appendInt(offer.getOfferId());
                this.response.appendInt(offer.getState().getState());
                this.response.appendInt(offer.getType());
                this.response.appendInt(offer.getItemId());

                if (offer.getType() == 3) {
                    this.response.appendInt(offer.getLimitedNumber());
                    this.response.appendInt(offer.getLimitedStack());
                } else if (offer.getType() == 2) {
                    this.response.appendString("");
                } else {
                    this.response.appendInt(0);
                    this.response.appendString("");
                }

                this.response.appendInt(offer.getPrice());

                if (offer.getState() == MarketPlaceState.OPEN)
                    this.response.appendInt((((offer.getTimestamp() + 172800) - Emulator.getIntUnixTimestamp()) / 60));
                else this.response.appendInt(0);

                this.response.appendInt(0);
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
            }
        }

        var currencies = new java.util.HashMap<Integer, Integer>();
        for (var offer : offers) currencies.put(offer.getOfferId(), offer.getCurrencyType());
        this.response.appendString(new com.google.gson.Gson().toJson(currencies));
        return this.response;
    }

    public Habbo getHabbo() {
        return habbo;
    }
}
