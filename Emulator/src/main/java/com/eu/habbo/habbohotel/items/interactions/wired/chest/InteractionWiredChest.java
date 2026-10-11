package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.items.ChestDataComposer;
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
    private ChestStorage.LogEntry lastNotifiedLog;

    protected InteractionWiredChest(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        try {
            String wiredData = set.getString("wired_data");
            if (wiredData != null && !wiredData.isEmpty()) {
                this.contents = ChestStorage.fromJson(wiredData);
                resolveLegacySpriteIds(this.contents);
                this.lastNotifiedLog = this.contents.getLog().isEmpty()
                        ? null
                        : this.contents.getLog().get(0);
            }
        } catch (SQLException ignored) {
            // Legacy rows/schema variants without wired_data retain the same empty-storage fallback.
            this.contents = new ChestStorage();
        }
    }

    protected InteractionWiredChest(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public boolean canManage(Habbo habbo) {
        return habbo != null
                && (getUserId() == habbo.getHabboInfo().getId() || habbo.hasPermission("acc_anyroomowner"));
    }

    public boolean canEdit(Habbo habbo, Room room) {
        return canManage(habbo) || (habbo != null && room != null && room.canModifyWired(habbo));
    }

    public boolean canRead(Habbo habbo, Room room) {
        return canManage(habbo) || (habbo != null && room != null && room.canInspectWired(habbo));
    }

    public boolean canWithdraw(Habbo habbo, Room room) {
        return canEdit(habbo, room) && (!contents.isLocked() || canManage(habbo));
    }

    public boolean canDeposit(Habbo habbo, Room room) {
        return canManage(habbo) || (!contents.isLocked() && (canEdit(habbo, room) || contents.isAccessDonate()));
    }

    private void refreshViewers(Room room) {
        if (room == null) return;
        for (Integer id : activeViewers) {
            Habbo viewer = room.getHabbo(id);
            if (viewer == null || viewer.getClient() == null) {
                activeViewers.remove(id);
                continue;
            }
            viewer.getClient().sendResponse(new ChestDataComposer(this, viewer));
            if (this instanceof InteractionWiredChestFurni) {
                ChestFurniPackets.sendFullChunks(viewer.getClient(), getId(), contents);
            }
        }
    }

    private synchronized void notifyOwner(Room room) {
        var log = contents.getLog();
        if (log.isEmpty() || log.get(0) == lastNotifiedLog) return;
        var entry = log.get(0);
        lastNotifiedLog = entry;
        if (contents.getNotifyMode() == 2 || (entry.withdrawn <= 0 && entry.deposited <= 0)) return;
        int used = this instanceof InteractionWiredChestFurni
                ? contents.furniItemCount()
                : contents.total(ChestStorage.KIND_CURRENCY);
        boolean enabled = (entry.deposited > 0 && contents.isNotifyDonation())
                || (entry.withdrawn > 0 && contents.isNotifyWithdraw())
                || (entry.type != null && entry.type.startsWith("wired") && contents.isNotifyWired())
                || (entry.deposited > 0 && used >= contents.getCapacity() && contents.isNotifyFull())
                || (entry.withdrawn > 0 && used == 0 && contents.isNotifyEmpty());
        if (!enabled) return;
        var environment = Emulator.getGameEnvironment();
        if (environment == null || environment.getHabboManager() == null) return;
        Habbo owner = environment.getHabboManager().getHabbo(getUserId());
        if (owner == null || owner.getClient() == null) return;
        if (contents.getNotifyMode() == 1 && owner.getHabboInfo().getCurrentRoom() != room) return;
        String label = contents.getName().isBlank() ? "Scrigno #" + getId() : contents.getName();
        String text = label + ": " + entry.userName
                + (entry.deposited > 0 ? " ha depositato " + entry.deposited : " ha prelevato " + entry.withdrawn);
        if (entry.deposited > 0 && contents.isNotifyDonation()) ChestNotifications.send(owner, "donation", text);
        if (entry.withdrawn > 0 && contents.isNotifyWithdraw()) ChestNotifications.send(owner, "withdraw", text);
        if (entry.type != null && entry.type.startsWith("wired") && contents.isNotifyWired())
            ChestNotifications.send(owner, "wired_transaction", text);
        if (entry.deposited > 0 && used >= contents.getCapacity() && contents.isNotifyFull())
            ChestNotifications.send(owner, "full", label + ": lo scrigno è pieno.");
        if (entry.withdrawn > 0 && used == 0 && contents.isNotifyEmpty())
            ChestNotifications.send(owner, "empty", label + ": lo scrigno è vuoto.");
    }

    public boolean hasContents() {
        if (this instanceof InteractionWiredChestFurni) {
            return this.contents.furniItemCount() > 0
                    || !this.contents.entries().isEmpty();
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
            var environment = Emulator.getGameEnvironment();
            if (environment != null && environment.getRoomManager() != null) {
                room = environment.getRoomManager().getRoom(this.getRoomId());
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
            if (this instanceof InteractionWiredChestCurrency) {
                int credits = this.contents.total(ChestStorage.KIND_CURRENCY);
                // Native currency assets: closed, empty, low, medium, high.
                targetExtra = credits >= 100 ? "4" : credits >= 20 ? "3" : credits >= 1 ? "2" : "1";
            } else {
                // Every native furni chest variant has only closed/open states.
                targetExtra = "1";
            }
        }

        if (!targetExtra.equals(this.getExtradata()) || this instanceof InteractionWiredChestFurni) {
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
        this.lastNotifiedLog =
                this.contents.getLog().isEmpty() ? null : this.contents.getLog().get(0);
        this.updateAppearanceState(room);
    }

    /**
     * Payloads persisted before the sprite id was stored per row only carry the internal base item
     * id. The client needs the furnidata sprite id to render icons/names, so backfill it here — the
     * next persist writes it out permanently.
     */
    private static void resolveLegacySpriteIds(ChestStorage contents) {
        var environment = Emulator.getGameEnvironment();
        if (environment == null || environment.getItemManager() == null) return;

        for (ChestFurniStoredItem stored : contents.furniItems()) {
            if (stored.spriteId > 0) continue;

            Item base = environment.getItemManager().getItem(stored.baseItemId);
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
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

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
        this.refreshViewers(room);
        this.notifyOwner(room);
    }

    public void persistContents() {
        var environment = Emulator.getGameEnvironment();
        Room room = environment == null || environment.getRoomManager() == null
                ? null
                : environment.getRoomManager().getRoom(getRoomId());
        this.persistContents(room);
    }
}
