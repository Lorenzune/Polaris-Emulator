package com.eu.habbo.habbohotel.commands.roleplay;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.Achievement;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessage;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserTalkComposer;
import com.eu.habbo.util.pathfinding.Rotation;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GenericRoleplayCommand extends Command {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_COOLDOWN_ENTRIES = 1024;

    private final RoleplayCommandData data;
    private final Map<Integer, Long> cooldowns = new ConcurrentHashMap<>();

    public GenericRoleplayCommand(RoleplayCommandData data) {
        super(data.getPermission(), data.getKeys());
        this.data = data;
    }

    public RoleplayCommandData getData() {
        return data;
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient == null || gameClient.getHabbo() == null) {
            return true;
        }

        Habbo sender = gameClient.getHabbo();
        Room currentRoom = sender.getHabboInfo().getCurrentRoom();
        if (currentRoom == null || sender.getRoomUnit() == null) {
            return true;
        }

        if (sender.getHabboInfo().getRank().getId() < data.getMinRank()) {
            return true;
        }

        Habbo target = null;

        if (data.isRequiresTarget()) {
            if (params.length < 2) {
                sender.whisper(data.getErrorForgotTarget(), RoomChatMessageBubbles.ALERT);
                return true;
            }

            target = currentRoom.getHabbo(params[1]);

            if (target == null || target.getRoomUnit() == null) {
                sender.whisper(
                        data.getErrorNotFound().replace("%target%", params[1]).replace("%user%", params[1]),
                        RoomChatMessageBubbles.ALERT);
                return true;
            }

            if (!data.isAllowSelf() && target == sender) {
                sender.whisper(data.getErrorSelf(), RoomChatMessageBubbles.ALERT);
                return true;
            }

            // Check privacy block
            if (target.getHabboStats() != null && target.getHabboStats().blockRoleplay) {
                sender.whisper(
                        data.getErrorDisabled()
                                .replace("%target%", target.getHabboInfo().getUsername())
                                .replace("%user%", target.getHabboInfo().getUsername()),
                        RoomChatMessageBubbles.ALERT);
                return true;
            }

            // Check distance
            if (data.getMaxDistance() >= 0) {
                int distanceX = Math.abs(
                        target.getRoomUnit().getX() - sender.getRoomUnit().getX());
                int distanceY = Math.abs(
                        target.getRoomUnit().getY() - sender.getRoomUnit().getY());
                int maxDist = Math.max(distanceX, distanceY);

                if (maxDist > data.getMaxDistance()) {
                    sender.whisper(
                            data.getErrorCantReach()
                                    .replace("%target%", target.getHabboInfo().getUsername())
                                    .replace("%user%", target.getHabboInfo().getUsername()),
                            RoomChatMessageBubbles.ALERT);
                    return true;
                }
            }

            if (!tryStartCooldown(sender)) {
                return true;
            }

            // Face to face look direction
            if (data.isFaceToFace() && target != sender) {
                sender.getRoomUnit().lookAtPoint(target.getRoomUnit().getCurrentLocation());
                target.getRoomUnit().lookAtPoint(sender.getRoomUnit().getCurrentLocation());
                currentRoom.sendComposer(new RoomUserStatusComposer(sender.getRoomUnit()).compose());
                currentRoom.sendComposer(new RoomUserStatusComposer(target.getRoomUnit()).compose());
            }

            // Knockback / push
            if (data.getKnockbackTiles() > 0 && target != sender) {
                int rot = Rotation.Calculate(
                        sender.getRoomUnit().getX(),
                        sender.getRoomUnit().getY(),
                        target.getRoomUnit().getX(),
                        target.getRoomUnit().getY());
                RoomTile currTile = target.getRoomUnit().getCurrentLocation();

                for (int i = 0; i < data.getKnockbackTiles(); i++) {
                    RoomTile nextTile = currentRoom.getLayout().getTileInFront(currTile, rot);
                    if (nextTile != null
                            && nextTile.isWalkable()
                            && currentRoom.getLayout().getDoorTile() != nextTile) {
                        currTile = nextTile;
                    } else {
                        break;
                    }
                }

                if (currTile != null && currTile != target.getRoomUnit().getCurrentLocation()) {
                    target.getRoomUnit().setGoalLocation(currTile);
                }
            }

            // Target effects & handitems & postures
            if (data.getTargetEffect() > 0) {
                currentRoom.giveEffect(target, data.getTargetEffect(), data.getTargetEffectDuration());
            }

            if (data.getTargetHanditem() > 0) {
                currentRoom.giveHandItem(target, data.getTargetHanditem());
            }

            if ("sit".equalsIgnoreCase(data.getTargetPosture())) {
                currentRoom.makeSit(target);
            }
        }

        if (!data.isRequiresTarget() && !tryStartCooldown(sender)) {
            return true;
        }

        // Sender effects & handitems & postures
        if (data.getSenderEffect() > 0) {
            currentRoom.giveEffect(sender, data.getSenderEffect(), data.getSenderEffectDuration());
        }

        if (data.getSenderHanditem() > 0) {
            currentRoom.giveHandItem(sender, data.getSenderHanditem());
        }

        if ("sit".equalsIgnoreCase(data.getSenderPosture())) {
            currentRoom.makeSit(sender);
        }

        RoomChatMessageBubbles bubble = RoomChatMessageBubbles.getBubble(data.getBubbleId());

        // Format and send sender message
        String rawSenderMsg = data.getRandomSenderMessage();
        if (rawSenderMsg != null && !rawSenderMsg.isEmpty()) {
            String formattedSender = formatMessage(rawSenderMsg, sender, target, currentRoom);
            RoomChatMessage senderMsg = new RoomChatMessage(formattedSender, sender, bubble);
            currentRoom.sendComposer(new RoomUserTalkComposer(senderMsg).compose());
        }

        // Format and send target message
        if (target != null) {
            String rawTargetMsg = data.getRandomTargetMessage();
            if (rawTargetMsg != null && !rawTargetMsg.isEmpty()) {
                String formattedTarget = formatMessage(rawTargetMsg, sender, target, currentRoom);
                RoomChatMessage targetMsg = new RoomChatMessage(formattedTarget, target, bubble);
                currentRoom.sendComposer(new RoomUserTalkComposer(targetMsg).compose());
            }
        }

        // Progress linked Achievements (Sender / Chi esegue l'azione)
        if (data.getSenderAchievement() != null
                && !data.getSenderAchievement().trim().isEmpty()) {
            Achievement senderAch = Emulator.getGameEnvironment()
                    .getAchievementManager()
                    .getAchievement(data.getSenderAchievement().trim());
            if (senderAch != null) {
                AchievementManager.progressAchievement(sender, senderAch, data.getSenderAchievementProgress());
            }
        }

        // Progress linked Achievements (Receiver / Chi riceve l'azione)
        if (target != null
                && data.getTargetAchievement() != null
                && !data.getTargetAchievement().trim().isEmpty()) {
            Achievement targetAch = Emulator.getGameEnvironment()
                    .getAchievementManager()
                    .getAchievement(data.getTargetAchievement().trim());
            if (targetAch != null) {
                AchievementManager.progressAchievement(target, targetAch, data.getTargetAchievementProgress());
            }
        }

        return true;
    }

    private String formatMessage(String template, Habbo sender, Habbo target, Room room) {
        String result = template;
        if (sender != null && sender.getHabboInfo() != null) {
            result = result.replace("%sender%", sender.getHabboInfo().getUsername());
        }
        if (target != null && target.getHabboInfo() != null) {
            result = result.replace("%target%", target.getHabboInfo().getUsername())
                    .replace("%user%", target.getHabboInfo().getUsername());
        }
        if (room != null) {
            result = result.replace("%room%", room.getName());
        }
        result = result.replace("%time%", LocalTime.now().format(TIME_FORMAT));
        return result;
    }

    private boolean tryStartCooldown(Habbo sender) {
        if (data.getCooldownSeconds() <= 0) return true;

        final long now = System.currentTimeMillis();
        final long cooldownMillis = data.getCooldownSeconds() * 1000L;
        synchronized (cooldowns) {
            if (cooldowns.size() >= MAX_COOLDOWN_ENTRIES) {
                cooldowns.entrySet().removeIf(entry -> now - entry.getValue() >= cooldownMillis);

                if (cooldowns.size() >= MAX_COOLDOWN_ENTRIES) {
                    cooldowns.entrySet().stream()
                            .min(Map.Entry.comparingByValue())
                            .ifPresent(oldest -> cooldowns.remove(oldest.getKey()));
                }
            }

            Long lastUsed = cooldowns.get(sender.getHabboInfo().getId());
            if (lastUsed != null) {
                long elapsedSeconds = Math.max(0L, (now - lastUsed) / 1000L);
                if (elapsedSeconds < data.getCooldownSeconds()) {
                    long remaining = data.getCooldownSeconds() - elapsedSeconds;
                    sender.whisper(
                            "Attendi ancora " + remaining + "s prima di riutilizzare questo comando!",
                            RoomChatMessageBubbles.ALERT);
                    return false;
                }
            }

            cooldowns.put(sender.getHabboInfo().getId(), now);
            return true;
        }
    }
}
