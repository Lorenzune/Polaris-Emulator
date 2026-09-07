package com.eu.habbo.messages.incoming.inventory.nicks;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.UserNick;
import com.eu.habbo.habbohotel.users.customization.UserCustomizationPurchaseService;
import com.eu.habbo.habbohotel.users.customization.UserCustomizationRepository;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.inventory.nicks.ActiveNickStyleUpdatedComposer;
import com.eu.habbo.messages.outgoing.inventory.nicks.CustomNickPurchaseFailedComposer;
import com.eu.habbo.messages.outgoing.inventory.nicks.UserNickStylesComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDataComposer;
import com.eu.habbo.messages.outgoing.users.UserCreditsComposer;
import com.eu.habbo.messages.outgoing.users.UserPointsComposer;

public class PurchaseNickStyleEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String color = this.packet.readString();
        String effect = this.packet.readString();
        String font = this.packet.readString();

        UserCustomizationPurchaseService service = new UserCustomizationPurchaseService(
            new UserCustomizationRepository(Emulator.getDatabase().getDataSource()),
            java.util.List.of()
        );

        UserCustomizationPurchaseService.PurchaseResult result = service.purchaseCustomNick(
            this.client.getHabbo(),
            new UserCustomizationPurchaseService.CustomNickRequest(color, effect, font)
        );

        if (result.status() == UserCustomizationPurchaseService.Status.SUCCESS) {
            UserNick nick = result.nick();
            nick.run();
            this.client.getHabbo().getInventory().getUserNicksComponent().addNick(nick);
            this.client.getHabbo().getInventory().getUserNicksComponent().setActive(nick.getId());

            if (result.creditsChanged()) {
                this.client.sendResponse(new UserCreditsComposer(this.client.getHabbo()));
            }
            if (result.currencyChanged()) {
                this.client.sendResponse(new UserPointsComposer(
                    this.client.getHabbo().getHabboInfo().getCurrencyAmount(5),
                    0,
                    5
                ));
            }
            this.client.sendResponse(new ActiveNickStyleUpdatedComposer(nick.getId(), nick.getColor(), nick.getEffect(), nick.getFont()));
            this.client.sendResponse(new UserNickStylesComposer(this.client.getHabbo()));

            if (this.client.getHabbo().getHabboInfo().getCurrentRoom() != null) {
                this.client.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new RoomUserDataComposer(this.client.getHabbo()).compose());
            }
        } else {
            this.client.sendResponse(new CustomNickPurchaseFailedComposer(result.message()));
        }
    }
}
