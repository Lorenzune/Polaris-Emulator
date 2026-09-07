package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base for Phase-2 wired storage chests (config-based v1). A chest is a config-holder furni (it opens
 * a dialog via the {@link InteractionWiredExtra} channel, but is never applied as a stack add-on — the
 * engine only matches specific add-on classes) whose contents live as JSON in its own
 * {@code items.wired_data} ({@link ChestStorage}). The give effects + chest conditions read/mutate the
 * contents through {@link #getContents()} and persist with {@link #persistContents()}.
 */
public abstract class InteractionWiredChest extends InteractionWiredExtra {
    protected ChestStorage contents = new ChestStorage();
    private final Set<Integer> activeViewers = ConcurrentHashMap.newKeySet();

    protected InteractionWiredChest(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        try {
            String wiredData = set.getString("wired_data");
            if (wiredData != null && !wiredData.isEmpty()) {
                this.contents = ChestStorage.fromJson(wiredData);
                resolveLegacySpriteIds(this.contents);
            }
        } catch (SQLException ignored) {
            // Legacy rows/schema variants without wired_data retain the same empty-storage fallback.
            this.contents = new ChestStorage();
        }
    }

    protected InteractionWiredChest(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public boolean hasContents() {
        if (this instanceof InteractionWiredChestFurni) {
            return this.contents.furniItemCount() > 0 || !this.contents.entries().isEmpty();
        } else {
            for (ChestStorage.Entry entry : this.contents.entries()) {
                if (entry.quantity > 0) return true;
            }
            return false;
        }
    }

    public void addViewer(int habboId, Room room) {
        this.activeViewers.add(habboId);
        this.updateAppearanceState(room);
    }

    public void removeViewer(int habboId, Room room) {
        this.activeViewers.remove(habboId);
        this.updateAppearanceState(room);
    }

    public void updateAppearanceState(Room room) {
        if (room == null && this.getRoomId() > 0) {
            if (Emulator.getGameEnvironment() != null && Emulator.getGameEnvironment().getRoomManager() != null) {
                room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
            }
        }
        if (room == null) return;

        int state = this.contents.getAppearanceState();
        boolean isOpen;
        if (state == 1) {
            isOpen = true; // Always open
        } else if (state == 2) {
            isOpen = false; // Always closed
        } else {
            isOpen = !this.activeViewers.isEmpty(); // Open when someone looks inside
        }

        String targetExtra;
        if (!isOpen) {
            targetExtra = "0"; // Closed
        } else {
            targetExtra = this.hasContents() ? "2" : "1"; // 2 = Open with items/coins, 1 = Open empty
        }

        if (!targetExtra.equals(this.getExtradata())) {
            this.setExtradata(targetExtra);
            room.updateItem(this);
        }
    }

    @Override
    public void onPlace(Room room) {
        super.onPlace(room);
        this.updateAppearanceState(room);
        this.persistContents(room);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return this.contents.toJson();
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.contents = ChestStorage.fromJson(set.getString("wired_data"));
        resolveLegacySpriteIds(this.contents);
        this.updateAppearanceState(room);
    }

    /**
     * Payloads persisted before the sprite id was stored per row only carry the internal base item
     * id. The client needs the furnidata sprite id to render icons/names, so backfill it here — the
     * next persist writes it out permanently.
     */
    private static void resolveLegacySpriteIds(ChestStorage contents) {
        if (Emulator.getGameEnvironment() == null || Emulator.getGameEnvironment().getItemManager() == null) return;

        for (ChestFurniStoredItem stored : contents.furniItems()) {
            if (stored.spriteId > 0) continue;

            Item base = Emulator.getGameEnvironment().getItemManager().getItem(stored.baseItemId);
            if (base != null) stored.spriteId = base.getSpriteId();
        }
    }

    @Override
    public void onPickUp() {
        // KEEP CONTENTS! Clear temporary room viewers and persist state.
        this.activeViewers.clear();
        this.setExtradata("0");
        this.persistContents();
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    public ChestStorage getContents() {
        return this.contents;
    }

    public void persistContents(Room room) {
        this.updateAppearanceState(room);
        this.needsUpdate(true);
    }

    public void persistContents() {
        this.updateAppearanceState(null);
        this.needsUpdate(true);
    }
}
