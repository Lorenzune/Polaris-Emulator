package com.eu.habbo.messages.incoming.crackable;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.crackable.CrackableEditorResultComposer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.regex.Pattern;

public class CrackableEditorSaveEvent extends MessageHandler {
    private static final Pattern ACHIEVEMENT = Pattern.compile("[A-Za-z0-9_.:-]{0,64}");
    private static final Pattern PRIZE = Pattern.compile("\\d+(?::\\d+)?(?:;\\d+(?::\\d+)?)*");

    @Override
    public void handle() throws Exception {
        if (!CrackableEditorSupport.canEdit(this.client)) return;

        int objectId = this.packet.readInt();
        int count = this.packet.readInt();
        String prizes = this.packet.readString().trim();
        String achievementTick = this.packet.readString().trim();
        String achievementCracked = this.packet.readString().trim();
        int requiredEffect = this.packet.readInt();
        int subscriptionDuration = this.packet.readInt();
        String subscriptionType = this.packet.readString().trim().toLowerCase();

        HabboItem station = CrackableEditorSupport.getCrackable(this.client, objectId);
        if (station == null) {
            fail("Crackable non trovato");
            return;
        }

        if (count <= 0 || count > 2_000_000_000 || prizes.length() > 255 || (!prizes.isEmpty() && !PRIZE.matcher(prizes).matches())
                || !validAchievement(achievementTick) || !validAchievement(achievementCracked)
                || requiredEffect < 0 || requiredEffect > 10000 || subscriptionDuration < 0
                || (subscriptionType.length() > 0 && !subscriptionType.equals("hc") && !subscriptionType.equals("bc"))) {
            fail("Configurazione crackable non valida");
            return;
        }

        if (!prizes.isEmpty()) {
            for (String prize : prizes.split(";")) {
                int itemId = Integer.parseInt(prize.split(":")[0]);
                if (Emulator.getGameEnvironment().getItemManager().getItem(itemId) == null) {
                    fail("Premio non trovato: " + itemId);
                    return;
                }
            }
        }

        String subscriptionDb = subscriptionType.isEmpty() ? null : subscriptionType;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO items_crackable (item_id, item_name, count, prizes, achievement_tick, achievement_cracked, required_effect, subscription_duration, subscription_type) "
                             + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE item_name=VALUES(item_name), count=VALUES(count), prizes=VALUES(prizes), achievement_tick=VALUES(achievement_tick), achievement_cracked=VALUES(achievement_cracked), required_effect=VALUES(required_effect), subscription_duration=VALUES(subscription_duration), subscription_type=VALUES(subscription_type)")) {
            statement.setInt(1, station.getBaseItem().getId());
            statement.setString(2, station.getBaseItem().getName());
            statement.setInt(3, count);
            statement.setString(4, prizes);
            statement.setString(5, achievementTick);
            statement.setString(6, achievementCracked);
            statement.setInt(7, requiredEffect);
            if (subscriptionDuration == 0) statement.setNull(8, java.sql.Types.INTEGER);
            else statement.setInt(8, subscriptionDuration);
            if (subscriptionDb == null) statement.setNull(9, java.sql.Types.VARCHAR);
            else statement.setString(9, subscriptionDb);
            statement.executeUpdate();
        }

        Emulator.getGameEnvironment().getItemManager().loadCrackable();
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room != null) {
            station.needsUpdate(true);
            room.updateItem(station);
        }
        this.client.sendResponse(new CrackableEditorResultComposer(true, "Crackable salvato"));
        CrackableEditorSupport.sendState(this.client, objectId);
    }

    private static boolean validAchievement(String value) {
        return value != null && ACHIEVEMENT.matcher(value).matches();
    }

    private void fail(String message) {
        this.client.sendResponse(new CrackableEditorResultComposer(false, message));
    }
}
