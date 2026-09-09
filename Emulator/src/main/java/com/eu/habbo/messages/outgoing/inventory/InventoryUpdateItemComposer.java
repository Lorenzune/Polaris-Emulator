package com.eu.habbo.messages.outgoing.inventory;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.interactions.InteractionGift;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.Arrays;
import java.util.List;

public class InventoryUpdateItemComposer extends MessageComposer {
    private final HabboItem habboItem;

    public InventoryUpdateItemComposer(HabboItem item) {
        this.habboItem = item;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.InventoryItemUpdateComposer);
        this.response.appendInt(this.habboItem.getGiftAdjustedId());
        this.response.appendString(this.habboItem.getBaseItem().getType().code);
        this.response.appendInt(this.habboItem.getId());
        this.response.appendInt(this.habboItem.getBaseItem().getSpriteId());

        if (this.habboItem.getBaseItem().getName().equals("floor") || this.habboItem.getBaseItem().getName().equals("landscape") || this.habboItem.getBaseItem().getName().equals("song_disk") || this.habboItem.getBaseItem().getName().equals("wallpaper") || this.habboItem.getBaseItem().getName().equals("poster")) {
            switch (this.habboItem.getBaseItem().getName()) {
                case "landscape":
                    this.response.appendInt(4);
                    break;
                case "floor":
                    this.response.appendInt(3);
                    break;
                case "wallpaper":
                    this.response.appendInt(2);
                    break;
                case "poster":
                    this.response.appendInt(6);
                    break;
                case "song_disk":
                    this.response.appendInt(8);
                    break;
            }
            this.response.appendInt(0);
            this.response.appendString(this.habboItem.getExtradata());
        } else {
            if (this.habboItem.getBaseItem().getName().equals("gnome_box"))
                this.response.appendInt(13);
            else
                this.response.appendInt(this.habboItem instanceof InteractionGift ? ((((InteractionGift) this.habboItem).getColorId() * 1000) + ((InteractionGift) this.habboItem).getRibbonId()) : 1);

            this.habboItem.serializeExtradata(this.response);
        }

        this.response.appendBoolean(this.habboItem.getBaseItem().allowRecyle());
        this.response.appendBoolean(this.habboItem.getBaseItem().allowTrade());
        this.response.appendBoolean(!this.habboItem.isLimited() && this.habboItem.getBaseItem().allowInventoryStack());
        this.response.appendBoolean(this.habboItem.getBaseItem().allowMarketplace());
        this.response.appendInt(-1);
        this.response.appendBoolean(true);
        this.response.appendInt(-1);

        if (this.habboItem.getBaseItem().getType() == FurnitureType.FLOOR) {
            this.response.appendString("");
            if (this.habboItem.getBaseItem().getName().equals("song_disk")) {
                List<String> extraDataAsList = Arrays.asList(this.habboItem.getExtradata().split("\n"));
                this.response.appendInt(Integer.valueOf(extraDataAsList.get(extraDataAsList.size() - 1)));
                return this.response;
            }
            this.response.appendInt(this.habboItem instanceof InteractionGift ? ((((InteractionGift) this.habboItem).getColorId() * 1000) + ((InteractionGift) this.habboItem).getRibbonId()) : 1);
        }

        return this.response;
    }

    public HabboItem getHabboItem() {
        return habboItem;
    }
}
