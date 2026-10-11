package com.eu.habbo.messages.incoming.catalog;

import com.eu.habbo.habbohotel.rooms.BuildersClubRecolorService;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;

public class BuildersClubRecolorFurniEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int itemId = this.packet.readInt();
        int colorIndex = this.packet.readInt();
        int scope = this.packet.readInt();

        BuildersClubRecolorService.Result result =
                BuildersClubRecolorService.recolor(this.client.getHabbo(), itemId, colorIndex, scope);

        if (result == BuildersClubRecolorService.Result.DONE || result == BuildersClubRecolorService.Result.TOO_FAST) {
            return;
        }

        this.client.sendResponse(new BubbleAlertComposer(
                BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key,
                switch (result) {
                    case NO_RIGHTS -> "Non hai i permessi Builders Club per modificare questa stanza.";
                    case NOT_FOUND -> "Il furni non è disponibile oppure il salvataggio non è riuscito.";
                    case NOT_RECOLORABLE -> "Questo furni non può essere ricolorato.";
                    case NOT_BUILDERS_CLUB -> "Puoi ricolorare soltanto i furni posati con Builders Club.";
                    case NO_SUCH_COLOR -> "Questo colore non è disponibile per il tuo rank o non è compatibile.";
                    case LIMIT_EXCEEDED ->
                        "Limite colori della stanza o limite di 500 furni per operazione raggiunto. Seleziona un singolo furni.";
                    default -> "Ricolora il furni tra qualche istante.";
                }));
    }
}
