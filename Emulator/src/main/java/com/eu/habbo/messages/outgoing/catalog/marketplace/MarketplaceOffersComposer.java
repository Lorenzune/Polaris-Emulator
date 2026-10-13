package com.eu.habbo.messages.outgoing.catalog.marketplace;

import com.eu.habbo.habbohotel.catalog.marketplace.MarketPlace;
import com.eu.habbo.habbohotel.catalog.marketplace.MarketPlaceOffer;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

public class MarketplaceOffersComposer extends MessageComposer {
    private final List<MarketPlaceOffer> offers;
    private final int currency;

    public MarketplaceOffersComposer(List<MarketPlaceOffer> offers) {this(offers,MarketPlace.legacyCurrency());}
    public MarketplaceOffersComposer(List<MarketPlaceOffer> offers,int currency){this.offers=offers;this.currency=currency;}

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.MarketplaceOffersComposer);
        this.response.appendInt(this.offers.size());

        for (MarketPlaceOffer offer : this.offers) {
            this.response.appendInt(offer.getOfferId());
            this.response.appendInt(1);
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
            this.response.appendInt(MarketPlace.calculateCommision(offer.getPrice()));
            this.response.appendInt(0);
            this.response.appendInt(MarketPlace.calculateCommision(offer.avarage));
            this.response.appendInt(offer.count);
        }
        this.response.appendInt(this.offers.size());
        var currencies=new java.util.HashMap<Integer,Integer>();for(var offer:this.offers)currencies.put(offer.getOfferId(),offer.getCurrencyType());
        var sales=new java.util.HashMap<Integer,Integer>();for(var offer:this.offers)sales.put(offer.getOfferId(),offer.salesCount);
        this.response.appendString(new com.google.gson.Gson().toJson(java.util.Map.of("currencies",currencies,"currency",currency,"sales",sales)));
        return this.response;
    }

    public List<MarketPlaceOffer> getOffers() {
        return offers;
    }
}
