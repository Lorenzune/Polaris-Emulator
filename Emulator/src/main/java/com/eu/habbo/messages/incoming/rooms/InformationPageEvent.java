package com.eu.habbo.messages.incoming.rooms;

import com.eu.habbo.habbohotel.items.InformationPageRepository;
import com.eu.habbo.habbohotel.items.interactions.InteractionInformationTerminal;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.items.InformationPageComposer;

public final class InformationPageEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 300;
    }

    @Override
    public String getRatelimitGroup() {
        return "information-page";
    }

    @Override
    public void handle() throws Exception {
        int itemId = this.packet.readInt();
        int action = this.packet.readInt();
        var habbo = this.client.getHabbo();
        var room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null || !(room.getHabboItem(itemId) instanceof InteractionInformationTerminal)) return;
        var terminal = (InteractionInformationTerminal) room.getHabboItem(itemId);
        boolean editable = habbo.hasPermission("acc_information_terminal_edit");
        if (action != 0 && action != 1) return;
        if (action == 1) {
            if (!editable) {
                this.client.sendResponse(new InformationPageComposer(
                        itemId, false, "", "", "", "Non hai il permesso di modificare questo terminale."));
                return;
            }
            String title = this.packet.readString().trim();
            String content = this.packet.readString();
            if (title.isEmpty()
                    || title.length() > 120
                    || content.length() > 24000
                    || content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 60000) {
                this.client.sendResponse(new InformationPageComposer(
                        itemId,
                        true,
                        "",
                        "",
                        "",
                        "Titolo obbligatorio (max 120 caratteri), contenuto max 24000 caratteri."));
                return;
            }
            content = InformationPageRepository.clean(content);
            if (content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 60000) {
                this.client.sendResponse(new InformationPageComposer(
                        itemId, true, "", "", "", "Il contenuto formattato è troppo lungo."));
                return;
            }
            try {
                com.eu.habbo.habbohotel.items.ItemManager.informationPages()
                        .save(itemId, title, content, habbo.getHabboInfo().getId());
            } catch (java.sql.SQLException exception) {
                this.client.sendResponse(new InformationPageComposer(
                        itemId, editable, "", "", "", "Salvataggio non riuscito. Riprova."));
                throw exception;
            }
        }
        InformationPageRepository.Page page;
        try {
            page = com.eu.habbo.habbohotel.items.ItemManager.informationPages().find(itemId);
        } catch (java.sql.SQLException exception) {
            this.client.sendResponse(new InformationPageComposer(
                    itemId, editable, "", "", "", "Impossibile caricare la pagina del terminale."));
            throw exception;
        }
        this.client.sendResponse(new InformationPageComposer(
                itemId,
                editable,
                page == null ? "Informazioni" : page.title(),
                page == null ? "" : page.content(),
                page == null ? terminal.values.getOrDefault("internalLink", "") : "",
                ""));
    }
}
