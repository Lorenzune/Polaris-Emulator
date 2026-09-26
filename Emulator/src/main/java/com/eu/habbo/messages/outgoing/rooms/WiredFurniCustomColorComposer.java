package com.eu.habbo.messages.outgoing.rooms;

import com.eu.habbo.habbohotel.rooms.WiredOpacityState;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dictionary-compressed per-layer color updates; each packet is bounded by the broadcaster. */
public class WiredFurniCustomColorComposer extends MessageComposer {
    private static final int HEADER = 5112;
    private final int roomId;
    private final List<WiredOpacityState> updates;
    private final List<String> colors;
    private final Map<String, Integer> colorIndexes;

    public WiredFurniCustomColorComposer(int roomId, Collection<WiredOpacityState> updates) {
        this.roomId = Math.max(0, roomId);
        this.updates = updates == null
                ? List.of()
                : updates.stream()
                        .filter(update -> update != null && update.itemId() > 0)
                        .sorted(Comparator.comparingInt(WiredOpacityState::itemId))
                        .toList();

        LinkedHashMap<String, Integer> dictionary = new LinkedHashMap<>();
        for (WiredOpacityState update : this.updates) {
            dictionary.computeIfAbsent(
                    update.customColor() == null ? "" : update.customColor(), ignored -> dictionary.size());
        }
        this.colors = List.copyOf(dictionary.keySet());
        this.colorIndexes = Map.copyOf(dictionary);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(HEADER);
        this.response.appendInt(this.roomId);
        this.response.appendInt(this.colors.size());
        for (String color : this.colors) {
            this.response.appendString(color);
        }
        this.response.appendInt(this.updates.size());
        for (WiredOpacityState update : this.updates) {
            this.response.appendInt(update.itemId());
            this.response.appendInt(this.colorIndexes.get(update.customColor() == null ? "" : update.customColor()));
        }
        return this.response;
    }
}
