package com.eu.habbo.messages.incoming.rooms.bots;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.bots.BotManager;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.users.DanceType;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.BotErrorComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDanceComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserNameChangedComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUsersComposer;
import com.eu.habbo.plugin.events.bots.BotSavedChatEvent;
import com.eu.habbo.plugin.events.bots.BotSavedLookEvent;
import com.eu.habbo.plugin.events.bots.BotSavedNameEvent;
import org.jsoup.Jsoup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class BotSaveSettingsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null)
            return;

        int botId = this.packet.readInt();
        Bot bot = room.getBot(Math.abs(botId));
        if (bot == null)
            return;

        int settingId = this.packet.readInt();
        int userId = this.client.getHabbo().getHabboInfo().getId();
        boolean roomOwnerOrStaff = room.getOwnerId() == userId || this.client.getHabbo().hasPermission(Permission.ACC_ANYROOMOWNER);
        boolean botOwnerCosmetic = bot.getOwnerId() == userId && settingId >= 13 && settingId <= 16;
        if (!roomOwnerOrStaff && !botOwnerCosmetic)
            return;

        boolean allowed = false;
        for (short a : bot.getOwnerActionIds()) {
            if (a == settingId) {
                allowed = true;
                break;
            }
        }
        if (!allowed) return;

        if (!bot.tryAcquireOwnerActionSlot()) return;

        switch (settingId) {
                case 1:
                    BotSavedLookEvent lookEvent = new BotSavedLookEvent(bot,
                            this.client.getHabbo().getHabboInfo().getGender(),
                            this.client.getHabbo().getHabboInfo().getLook(),
                            this.client.getHabbo().getRoomUnit().getEffectId());
                    Emulator.getPluginManager().fireEvent(lookEvent);

                    if (lookEvent.isCancelled())
                        break;

                    bot.setFigure(lookEvent.newLook);
                    bot.setGender(lookEvent.gender);
                    bot.setEffect(lookEvent.effect, -1);
                    bot.needsUpdate(true);
                    break;

                case 2:
                    String messageString = this.packet.readString();

                    if (messageString.length() > 5112)
                        break;

                    String[] data = messageString.split(";#;");

                    ArrayList<String> chat = new ArrayList<>();
                    int totalChatLength = 0;
                    for (int i = 0; i < data.length - 3 && totalChatLength <= 120; i++) {
                        for (String s : data[i].split("\r")) {
                            String filtered = Jsoup.parse(s).text();
                            int count = 0;
                            while (!filtered.equalsIgnoreCase(s)) {
                                if (count >= 5) {
                                    bot.clearChat();
                                    return;
                                }
                                s = filtered;
                                filtered = Jsoup.parse(s).text();
                                count++;
                            }

                            String result = Emulator.getGameEnvironment().getWordFilter().filter(s, null);

                            if (!result.isEmpty()) {
                                if (!this.client.getHabbo().hasPermission(Permission.ACC_CHAT_NO_FILTER)) {
                                    result = Emulator.getGameEnvironment().getWordFilter().filter(result, this.client.getHabbo());
                                }

                                result = result.substring(0, Math.min(BotManager.MAXIMUM_CHAT_LENGTH - totalChatLength, result.length()));
                                chat.add(result);
                                totalChatLength += result.length();
                            }
                        }
                    }

                    int chatSpeed = 7;

                    try {
                        chatSpeed = Integer.parseInt(data[data.length - 2]);
                        if (chatSpeed < BotManager.MINIMUM_CHAT_SPEED) {
                            chatSpeed = BotManager.MINIMUM_CHAT_SPEED;
                        }
                    } catch (Exception e) {
                        //Invalid chatspeed. Use 7.
                    }

                    BotSavedChatEvent chatEvent = new BotSavedChatEvent(bot, Boolean.parseBoolean(data[data.length - 3]), Boolean.parseBoolean(data[data.length - 1]), chatSpeed, chat);
                    Emulator.getPluginManager().fireEvent(chatEvent);

                    if (chatEvent.isCancelled())
                        break;

                    bot.setChatAuto(chatEvent.autoChat);
                    bot.setChatRandom(chatEvent.randomChat);
                    bot.setChatDelay((short) chatEvent.chatDelay);
                    bot.clearChat();
                    bot.addChatLines(chat);
                    bot.needsUpdate(true);
                    break;

                case 3:
                    bot.setCanWalk(!bot.canWalk());
                    bot.needsUpdate(true);
                    break;

                case 4:
                    bot.getRoomUnit().setDanceType(DanceType.values()[(bot.getRoomUnit().getDanceType().getType() + 1) % DanceType.values().length]);
                    room.sendComposer(new RoomUserDanceComposer(bot.getRoomUnit()).compose());
                    bot.needsUpdate(true);
                    break;

                case 5:
                    String name = this.packet.readString();
                    boolean invalidName = name.length() > BotManager.MAXIMUM_NAME_LENGTH || name.contains("<") || name.contains(">");
                    if (!invalidName) {
                        String filteredName = Emulator.getGameEnvironment().getWordFilter().filter(name, null);
                        invalidName = !name.equalsIgnoreCase(filteredName);
                        if (!invalidName) {
                            BotSavedNameEvent nameEvent = new BotSavedNameEvent(bot, name);

                            Emulator.getPluginManager().fireEvent(nameEvent);

                            if (nameEvent.isCancelled())
                                break;

                            bot.setName(nameEvent.name);
                            bot.needsUpdate(true);
                            room.sendComposer(new RoomUserNameChangedComposer(bot.getRoomUnit().getId(), bot.getRoomUnit().getId(), nameEvent.name).compose());
                        }
                    }

                    if (invalidName) {
                        this.client.sendResponse(new BotErrorComposer(BotErrorComposer.ROOM_ERROR_BOTS_NAME_NOT_ACCEPT));
                    }
                    break;
                case 9:
                    String motto = this.packet.readString().replace('\r', ' ').replace('\n', ' ').trim();

                    if(motto.length() > Math.min(Emulator.getConfig().getInt("motto.max_length", 38), 38)) break;

                    bot.setMotto(motto);
                    bot.needsUpdate(true);
                    room.sendComposer(new RoomUsersComposer(bot).compose());
                    break;
                case 13:
                    String[] requestedBadges = this.packet.readString().split(",", -1);
                    if (requestedBadges.length > 7) break;

                    String[] badges = new String[7];
                    Set<String> uniqueBadges = new HashSet<>();
                    for (int i = 0; i < requestedBadges.length; i++) {
                        String badge = requestedBadges[i].trim();
                        if (badge.isEmpty()) continue;
                        if (badge.length() > 50 || !uniqueBadges.add(badge) || !ownsBadge(bot.getOwnerId(), badge)) return;
                        badges[i] = badge;
                    }

                    bot.setBadges(badges);
                    break;
                case 14:
                    int borderId;
                    try {
                        borderId = Integer.parseInt(this.packet.readString());
                    } catch (NumberFormatException e) {
                        break;
                    }

                    if (borderId < 0 || (borderId > 0 && !ownsBorder(bot.getOwnerId(), borderId))) break;

                    bot.setInfostandBorderId(borderId);
                    break;
                case 15:
                    String[] backgroundValues = this.packet.readString().split(",", -1);
                    if (backgroundValues.length > 3) break;

                    int backgroundId;
                    int backgroundStandId;
                    int backgroundCardId;
                    try {
                        backgroundId = parseCosmeticId(backgroundValues, 0);
                        backgroundStandId = parseCosmeticId(backgroundValues, 1);
                        backgroundCardId = parseCosmeticId(backgroundValues, 2);
                    } catch (NumberFormatException e) {
                        break;
                    }

                    if (!ownsCosmetic(bot.getOwnerId(), backgroundId, "background")
                            || !ownsCosmetic(bot.getOwnerId(), backgroundStandId, "stand")
                            || !ownsCosmetic(bot.getOwnerId(), backgroundCardId, "card")) break;

                    bot.setBackgroundCosmetics(backgroundId, backgroundStandId, backgroundCardId);
                    break;
                case 16:
                    String vipSlotsValue = this.packet.readString();
                    boolean vipSlotsActive = "1".equals(vipSlotsValue) || Boolean.parseBoolean(vipSlotsValue);
                    if (vipSlotsActive && !ownsCosmetic(bot.getOwnerId(), 2001, "slot")) break;

                    bot.setVipSlotsActive(vipSlotsActive);
                    break;
                case Bot.ACTION_ROTATE:
                    if (bot.getRoomUnit() == null) break;
                    int next = (bot.getRoomUnit().getBodyRotation().getValue() + 2) % 8;
                    RoomUserRotation rotation = RoomUserRotation.fromValue(next);
                    bot.getRoomUnit().setRotation(rotation);
                    bot.getRoomUnit().setPreviousLocation(bot.getRoomUnit().getCurrentLocation());
                    bot.getRoomUnit().setPreviousLocationZ(bot.getRoomUnit().getZ());
                    bot.needsUpdate(true);
                    room.sendComposer(new RoomUserStatusComposer(bot.getRoomUnit()).compose());
                    break;
            }

        bot.onPostOwnerAction(settingId);

        if (bot.needsUpdate()) {
            Emulator.getThreading().run(bot);
        }
    }

    private int parseCosmeticId(String[] values, int index) throws NumberFormatException {
        if (index >= values.length || values[index].trim().isEmpty()) return 0;

        int value = Integer.parseInt(values[index].trim());
        if (value < 0 || value > 9999) throw new NumberFormatException("Invalid cosmetic id");

        return value;
    }

    private boolean ownsCosmetic(int userId, int itemId, String... categories) throws Exception {
        if (itemId == 0) return true;

        String placeholders = String.join(",", Collections.nCopies(categories.length, "?"));
        String query = "SELECT 1 FROM custom_profile_cosmetics_catalog c "
                + "WHERE c.id = ? AND c.enabled = 1 AND LOWER(c.category) IN (" + placeholders + ") "
                + "AND (c.is_free = 1 OR EXISTS (SELECT 1 FROM users_profile_cosmetics u "
                + "WHERE u.user_id = ? AND u.item_id = c.id AND LOWER(u.category) = LOWER(c.category))) LIMIT 1";

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            int parameter = 1;
            statement.setInt(parameter++, itemId);
            for (String category : categories) statement.setString(parameter++, category);
            statement.setInt(parameter, userId);

            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean ownsBadge(int userId, String badgeCode) throws Exception {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM users_badges WHERE user_id = ? AND badge_code = ? LIMIT 1")) {
            statement.setInt(1, userId);
            statement.setString(2, badgeCode);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean ownsBorder(int userId, int borderId) throws Exception {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM users_profile_cosmetics WHERE user_id = ? AND item_id = ? AND category IN ('border', 'borders') LIMIT 1")) {
            statement.setInt(1, userId);
            statement.setInt(2, borderId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }
}
