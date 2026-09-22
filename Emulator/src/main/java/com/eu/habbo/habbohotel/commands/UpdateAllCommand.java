package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateAllCommand extends Command {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateAllCommand.class);

    private final AtomicBoolean updateInProgress = new AtomicBoolean();

    public UpdateAllCommand() {
        super(
                "cmd_update_all",
                Emulator.getTexts().getValue("commands.keys.cmd_update_all").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        if (!this.updateInProgress.compareAndSet(false, true)) {
            gameClient.getHabbo().whisper("All updates are already in progress.", RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (Emulator.getThreading().run(() -> this.reloadAll(gameClient)) == null) {
            this.updateInProgress.set(false);
        }

        return true;
    }

    private void reloadAll(GameClient gameClient) {
        LOGGER.info("[UpdateAll] Reloading all subsystems...");

        try {
            // Achievements
            Emulator.getGameEnvironment().getAchievementManager().reload();

            // Bots
            Emulator.getGameEnvironment().getBotManager().reload();

            // Catalog and crafting are rebuilt as isolated snapshots, then
            // the unchanged client refresh sequence is sent to everyone.
            if (!Emulator.getGameEnvironment().reloadCatalogAndCraftingNow(() -> {
                Emulator.getGameEnvironment().broadcastCatalogReload();
                Emulator.getGameServer()
                        .getGameClientManager()
                        .sendBroadcastResponse(new com.eu.habbo.messages.outgoing.furniture.FurnitureDataReloadComposer(
                                com.eu.habbo.messages.outgoing.furniture.FurnitureDataReloadComposer.MODE_RELOAD_HINT,
                                null));
            })) {
                LOGGER.warn("[UpdateAll] Catalog update already in progress; its own notification will follow.");
            }

            // Config
            Emulator.getConfig().reload();

            // Guild Parts
            Emulator.getGameEnvironment().getGuildManager().loadGuildParts();
            Emulator.getBadgeImager().reload();

            // Hotel View
            Emulator.getGameEnvironment().getHotelViewManager().getNewsList().reload();
            Emulator.getGameEnvironment().getHotelViewManager().getHallOfFame().reload();

            // Items and active rooms
            if (!Emulator.getGameEnvironment().reloadItemsNow(null)) {
                LOGGER.warn("[UpdateAll] Item update already in progress; its own room refresh will follow.");
            }

            // Navigator
            Emulator.getGameEnvironment().getNavigatorManager().loadNavigator();

            // Room Models
            Emulator.getGameEnvironment().getRoomManager().loadRoomModels();
            Emulator.getGameEnvironment().getRoomManager().loadPublicRooms();

            // Permissions: rebind and notify every online Habbo so the new
            // server-side rank and Nitro's live permission state stay aligned.
            if (Emulator.getGameEnvironment().reloadPermissionsAndBroadcast() < 0) {
                LOGGER.warn("[UpdateAll] Permissions update already in progress; its own live refresh will follow.");
            }

            // Pet Data
            Emulator.getGameEnvironment().getPetManager().reloadPetData();

            // Polls
            Emulator.getGameEnvironment().getPollManager().loadPolls();

            // Texts & Commands
            try {
                Emulator.getTexts().reload();
                Emulator.getGameEnvironment().getCommandHandler().reloadCommands();
                com.eu.habbo.habbohotel.commands.roleplay.RoleplayCommandManager.getInstance()
                        .reload();
                Emulator.getGameServer()
                        .getGameClientManager()
                        .sendBroadcastResponse(new com.eu.habbo.messages.outgoing.roleplay.RoleplayCommandsComposer(
                                com.eu.habbo.habbohotel.commands.roleplay.RoleplayCommandManager.getInstance()
                                        .getCommandData()));
            } catch (Exception e) {
                if (gameClient.getChannel().isOpen() && gameClient.getHabbo() != null) {
                    gameClient
                            .getHabbo()
                            .whisper(
                                    Emulator.getTexts().getValue("commands.error.cmd_update_texts.failed"),
                                    RoomChatMessageBubbles.ALERT);
                }
            }

            // Word Filter
            Emulator.getGameEnvironment().getWordFilter().reload();

            // YouTube
            Emulator.getGameEnvironment().getItemManager().getYoutubeManager().load();

            // Client catalogs which are otherwise refreshed by their dedicated
            // update commands. These packets are user-specific (owned items,
            // permissions and active selections), so send one set to each
            // online client after the server-side catalogs have been reloaded.
            RoomChatMessageBubbles.removeDynamicBubbles();
            Emulator.getGameEnvironment().getRoomChatBubbleManager().reload();
            Emulator.getGameEnvironment().getInfostandBackgroundManager().reload();

            long externalTextsTimestamp = System.currentTimeMillis();
            com.eu.habbo.messages.outgoing.generic.ExternalTextsReloadComposer externalTextsReload =
                    new com.eu.habbo.messages.outgoing.generic.ExternalTextsReloadComposer(externalTextsTimestamp);

            for (Habbo habbo : Emulator.getGameEnvironment()
                    .getHabboManager()
                    .getOnlineHabbos()
                    .values()) {
                if (habbo == null || habbo.getClient() == null) {
                    continue;
                }

                habbo.getClient()
                        .sendResponse(new com.eu.habbo.messages.outgoing.inventory.chatbubbles.UserChatBubblesComposer(
                                habbo));
                habbo.getClient()
                        .sendResponse(
                                new com.eu.habbo.messages.outgoing.inventory.habbicons.UserHabbiconsComposer(habbo));
                habbo.getClient()
                        .sendResponse(
                                new com.eu.habbo.messages.outgoing.inventory.nickicons.UserNickIconsComposer(habbo));
                habbo.getClient()
                        .sendResponse(new com.eu.habbo.messages.outgoing.inventory.nicks.UserNickStylesComposer(habbo));
                habbo.getClient()
                        .sendResponse(
                                new com.eu.habbo.messages.outgoing.inventory.cosmetics.UserProfileCosmeticsComposer(
                                        habbo));
                habbo.getClient().sendResponse(externalTextsReload);
            }

            LOGGER.info("[UpdateAll] All subsystems reloaded successfully!");
            if (gameClient.getChannel().isOpen() && gameClient.getHabbo() != null) {
                gameClient
                        .getHabbo()
                        .whisper(
                                Emulator.getTexts().getValue("commands.succes.cmd_update_all"),
                                RoomChatMessageBubbles.ALERT);
            }
        } catch (Throwable throwable) {
            LOGGER.error("[UpdateAll] Reload failed", throwable);
        } finally {
            this.updateInProgress.set(false);
        }
    }
}
