package com.eu.habbo.messages.incoming.mentions;

import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.mentions.MentionSettingsComposer;
import java.util.Collections;
import org.slf4j.LoggerFactory;

public class MentionSettingsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client == null || this.client.getHabbo() == null) return;
        int ownerId = this.client.getHabbo().getHabboInfo().getId();
        if (!com.eu.habbo.habbohotel.mentions.MentionManager.runtime().tryAcquireSettings(ownerId)) {
            this.client.sendResponse(
                    new MentionSettingsComposer(Collections.emptyList(), "Attendi un momento e riprova."));
            return;
        }
        int action = this.packet.readInt();
        String error = "";
        try {
            if (action == 1 || action == 2)
                error = com.eu.habbo.habbohotel.mentions.MentionManager.runtime()
                        .mutePreferences()
                        .update(ownerId, this.packet.readString(), action == 1);
            else if (action != 0) error = "Operazione non valida.";
            this.client.sendResponse(new MentionSettingsComposer(
                    com.eu.habbo.habbohotel.mentions.MentionManager.runtime()
                            .mutePreferences()
                            .list(ownerId),
                    error));
            if (error.isEmpty()) {
                this.client.sendResponse(new com.eu.habbo.messages.outgoing.mentions.MentionsListComposer(
                        com.eu.habbo.habbohotel.mentions.MentionManager.runtime()
                                .getMentions(
                                        ownerId,
                                        com.eu.habbo.habbohotel.mentions.MentionManager.runtime()
                                                .storeLimit())));
            }
        } catch (java.sql.SQLException e) {
            LoggerFactory.getLogger(MentionSettingsEvent.class).error("Failed to update mention preferences", e);
            this.client.sendResponse(new MentionSettingsComposer(
                    Collections.emptyList(), "Impossibile caricare le impostazioni. Riprova."));
        }
    }
}
