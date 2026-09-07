package com.eu.habbo.habbohotel;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.CreditsScheduler;
import com.eu.habbo.core.GotwPointsScheduler;
import com.eu.habbo.core.PixelScheduler;
import com.eu.habbo.core.PointsScheduler;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.bots.BotManager;
import com.eu.habbo.habbohotel.campaign.calendar.CalendarManager;
import com.eu.habbo.habbohotel.catalog.CatalogManager;
import com.eu.habbo.habbohotel.commands.CommandHandler;
import com.eu.habbo.habbohotel.crafting.CraftingManager;
import com.eu.habbo.habbohotel.dailytasks.DailyTaskManager;
import com.eu.habbo.habbohotel.guides.GuideManager;
import com.eu.habbo.habbohotel.gameclients.GameClientFlushBatch;
import com.eu.habbo.habbohotel.gameclients.GameClientManager;
import com.eu.habbo.habbohotel.guilds.GuildManager;
import com.eu.habbo.habbohotel.hotelview.HotelViewManager;
import com.eu.habbo.habbohotel.items.FurnitureTextProvider;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.mentions.MentionManager;
import com.eu.habbo.habbohotel.modtool.ModToolManager;
import com.eu.habbo.habbohotel.modtool.ModToolSanctions;
import com.eu.habbo.habbohotel.modtool.WordFilter;
import com.eu.habbo.habbohotel.navigation.NavigatorManager;
import com.eu.habbo.habbohotel.permissions.PermissionsManager;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.pets.PetManager;
import com.eu.habbo.habbohotel.polls.PollManager;
import com.eu.habbo.habbohotel.rewardtrack.RewardTrackManager;
import com.eu.habbo.habbohotel.rooms.RoomChatBubbleManager;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomItemLiveRefreshTask;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.soundboard.SoundboardManager;
import com.eu.habbo.habbohotel.translations.GoogleTranslateManager;
import com.eu.habbo.habbohotel.traxeditor.TraxEditorManager;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.custombadge.CustomBadgeManager;
import com.eu.habbo.habbohotel.users.infostand.InfostandBackgroundManager;
import com.eu.habbo.habbohotel.users.subscriptions.SubscriptionManager;
import com.eu.habbo.habbohotel.users.subscriptions.SubscriptionScheduler;
import com.eu.habbo.habbohotel.wheel.WheelManager;
import com.eu.habbo.messages.outgoing.catalog.CatalogModeComposer;
import com.eu.habbo.messages.outgoing.catalog.CatalogUpdatedComposer;
import com.eu.habbo.messages.outgoing.catalog.DiscountComposer;
import com.eu.habbo.messages.outgoing.catalog.GiftConfigurationComposer;
import com.eu.habbo.messages.outgoing.catalog.RecyclerLogicComposer;
import com.eu.habbo.messages.outgoing.catalog.marketplace.MarketplaceConfigComposer;
import com.eu.habbo.messages.outgoing.commands.AvailableCommandsComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomRelativeMapComposer;
import com.eu.habbo.messages.outgoing.users.UserPermissionsComposer;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GameEnvironment {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameEnvironment.class);

    private record CatalogCraftingSnapshot(CatalogManager catalogManager, CraftingManager craftingManager) {}

    private final HotelServiceRegistry services = new HotelServiceRegistry();
    private final Executor persistenceExecutor;

    public CreditsScheduler creditsScheduler;
    public PixelScheduler pixelScheduler;
    public PointsScheduler pointsScheduler;
    public GotwPointsScheduler gotwPointsScheduler;
    public SubscriptionScheduler subscriptionScheduler;

    private HabboManager habboManager;
    private NavigatorManager navigatorManager;
    private GuildManager guildManager;
    private ItemManager itemManager;
    private FurnitureTextProvider furnitureTextProvider;
    private volatile CatalogManager catalogManager;
    private HotelViewManager hotelViewManager;
    private RoomManager roomManager;
    private CommandHandler commandHandler;
    private PermissionsManager permissionsManager;
    private BotManager botManager;
    private ModToolManager modToolManager;
    private ModToolSanctions modToolSanctions;
    private PetManager petManager;
    private AchievementManager achievementManager;
    private GuideManager guideManager;
    private WordFilter wordFilter;
    private volatile CraftingManager craftingManager;
    private volatile CatalogCraftingSnapshot catalogCraftingSnapshot;
    private final AtomicBoolean catalogReloadInProgress = new AtomicBoolean();
    private final AtomicBoolean itemReloadInProgress = new AtomicBoolean();
    private final AtomicBoolean permissionsReloadInProgress = new AtomicBoolean();
    private PollManager pollManager;
    private SubscriptionManager subscriptionManager;
    private CalendarManager calendarManager;
    private RoomChatBubbleManager roomChatBubbleManager;
    private GoogleTranslateManager googleTranslateManager;
    private CustomBadgeManager customBadgeManager;
    private InfostandBackgroundManager infostandBackgroundManager;
    private RewardTrackManager rewardTrackManager;
    private DailyTaskManager dailyTaskManager;
    private WheelManager wheelManager;
    private SoundboardManager soundboardManager;
    private TraxEditorManager traxEditorManager;
    private MentionManager mentionManager;

    public GameEnvironment() {
        this(Runnable::run);
    }

    public GameEnvironment(Executor persistenceExecutor) {
        this.persistenceExecutor = Objects.requireNonNull(persistenceExecutor, "persistenceExecutor");
    }

    public void load() throws Exception {
        LOGGER.info("GameEnvironment -> Loading...");

        this.permissionsManager = this.services.create("permissions manager", PermissionsManager::new);
        this.habboManager = this.services.create(
                "habbo manager", () -> new HabboManager(this.persistenceExecutor), HabboManager::dispose);
        this.hotelViewManager =
                this.services.create("hotel view manager", HotelViewManager::new, HotelViewManager::dispose);
        this.itemManager = this.services.create("item manager", ItemManager::new, ItemManager::dispose);
        this.itemManager.load();
        this.furnitureTextProvider = this.services.create("furniture text provider", FurnitureTextProvider::new);
        this.furnitureTextProvider.init();
        this.botManager = this.services.create("bot manager", BotManager::new, BotManager::dispose);
        this.petManager = this.services.create("pet manager", PetManager::new);
        this.guildManager = this.services.create("guild manager", GuildManager::new, GuildManager::dispose);
        this.catalogManager = this.services.create(
                "catalog manager",
                CatalogManager::new,
                ignored -> {
                    CatalogManager current = this.getCatalogManager();
                    if (current != null) {
                        current.dispose();
                    }
                });
        this.roomManager = this.services.create(
                "room manager", () -> new RoomManager(this.persistenceExecutor), RoomManager::dispose);
        this.services.beforeDispose("room cycles", () -> this.roomManager.quiesceRoomCycles());
        this.navigatorManager = this.services.create("navigator manager", NavigatorManager::new);
        this.commandHandler = this.services.create("command handler", CommandHandler::new, CommandHandler::dispose);
        this.modToolManager = this.services.create("moderation tools", ModToolManager::new);
        this.modToolSanctions = this.services.create("moderation sanctions", ModToolSanctions::new);
        this.achievementManager = this.services.create("achievement manager", AchievementManager::new);
        this.achievementManager.reload();
        this.guideManager = this.services.create("guide manager", GuideManager::new);
        this.wordFilter = this.services.create("word filter", WordFilter::new);
        this.craftingManager = this.services.create(
                "crafting manager",
                CraftingManager::new,
                ignored -> {
                    CraftingManager current = this.getCraftingManager();
                    if (current != null) {
                        current.dispose();
                    }
                });
        this.catalogCraftingSnapshot = new CatalogCraftingSnapshot(this.catalogManager, this.craftingManager);
        this.pollManager = this.services.create("poll manager", PollManager::new);
        this.calendarManager = this.services.create("calendar manager", CalendarManager::new, CalendarManager::dispose);
        this.roomChatBubbleManager = this.services.create("room chat bubbles", RoomChatBubbleManager::new);
        this.googleTranslateManager = this.services.create(
                "Google Translate cache", GoogleTranslateManager::new, GoogleTranslateManager::clearCache);
        this.customBadgeManager = this.services.create("custom badge manager", CustomBadgeManager::new);
        this.infostandBackgroundManager =
                this.services.create("infostand backgrounds", InfostandBackgroundManager::new);
        this.rewardTrackManager = this.services.create("reward track manager", RewardTrackManager::new);
        this.dailyTaskManager = this.services.create("daily task manager", DailyTaskManager::new);
        this.wheelManager = this.services.create("wheel manager", WheelManager::new);
        this.soundboardManager = this.services.create("soundboard manager", SoundboardManager::new);
        this.traxEditorManager = this.services.create("trax editor manager", TraxEditorManager::new);
        this.mentionManager = this.services.create("mention manager", MentionManager::new);

        this.roomManager.loadPublicRooms();
        this.navigatorManager.loadNavigator();

        this.creditsScheduler = this.services.create("credits scheduler", CreditsScheduler::new);
        this.services.onDispose("credits scheduler", () -> {
            if (this.creditsScheduler != null) {
                this.creditsScheduler.setDisposed(true);
            }
        });
        Emulator.getThreading().run(this.creditsScheduler);
        this.pixelScheduler = this.services.create("pixel scheduler", PixelScheduler::new);
        this.services.onDispose("pixel scheduler", () -> {
            if (this.pixelScheduler != null) {
                this.pixelScheduler.setDisposed(true);
            }
        });
        Emulator.getThreading().run(this.pixelScheduler);
        this.pointsScheduler = this.services.create("points scheduler", PointsScheduler::new);
        this.services.onDispose("points scheduler", () -> {
            if (this.pointsScheduler != null) {
                this.pointsScheduler.setDisposed(true);
            }
        });
        Emulator.getThreading().run(this.pointsScheduler);
        this.gotwPointsScheduler = this.services.create("gotw points scheduler", GotwPointsScheduler::new);
        this.services.onDispose("gotw points scheduler", () -> {
            if (this.gotwPointsScheduler != null) {
                this.gotwPointsScheduler.setDisposed(true);
            }
        });
        Emulator.getThreading().run(this.gotwPointsScheduler);

        this.subscriptionManager =
                this.services.create("subscription manager", SubscriptionManager::new, SubscriptionManager::dispose);
        this.subscriptionManager.init();

        this.subscriptionScheduler = this.services.create("subscription scheduler", SubscriptionScheduler::new);
        this.services.onDispose("subscription scheduler", () -> {
            if (this.subscriptionScheduler != null) {
                this.subscriptionScheduler.setDisposed(true);
            }
        });
        Emulator.getThreading().run(this.subscriptionScheduler);

        LOGGER.info("GameEnvironment -> Loaded!");
    }

    public void dispose() {
        if (this.services.hasServices()) {
            this.services.dispose();
            LOGGER.info("GameEnvironment -> Disposed!");
            return;
        }

        Map<String, Runnable> steps = new LinkedHashMap<>();
        steps.put(
                "points scheduler",
                this.pointsScheduler == null
                        ? null
                        : () -> {
                            this.pointsScheduler.setDisposed(true);
                        });
        steps.put(
                "pixel scheduler",
                this.pixelScheduler == null
                        ? null
                        : () -> {
                            this.pixelScheduler.setDisposed(true);
                        });
        steps.put(
                "credits scheduler",
                this.creditsScheduler == null
                        ? null
                        : () -> {
                            this.creditsScheduler.setDisposed(true);
                        });
        steps.put(
                "gotw points scheduler",
                this.gotwPointsScheduler == null
                        ? null
                        : () -> {
                            this.gotwPointsScheduler.setDisposed(true);
                        });
        steps.put(
                "subscription scheduler",
                this.subscriptionScheduler == null
                        ? null
                        : () -> {
                            this.subscriptionScheduler.setDisposed(true);
                        });
        steps.put("room cycles", this.roomManager == null ? null : () -> this.roomManager.quiesceRoomCycles());
        steps.put("crafting manager", this.craftingManager == null ? null : () -> this.craftingManager.dispose());
        steps.put("habbo manager", this.habboManager == null ? null : () -> this.habboManager.dispose());
        steps.put("command handler", this.commandHandler == null ? null : () -> this.commandHandler.dispose());
        steps.put("guild manager", this.guildManager == null ? null : () -> this.guildManager.dispose());
        steps.put("catalog manager", this.catalogManager == null ? null : () -> this.catalogManager.dispose());
        steps.put("room manager", this.roomManager == null ? null : () -> this.roomManager.dispose());
        steps.put("bot manager", this.botManager == null ? null : () -> this.botManager.dispose());
        steps.put("item manager", this.itemManager == null ? null : () -> this.itemManager.dispose());
        steps.put("hotel view manager", this.hotelViewManager == null ? null : () -> this.hotelViewManager.dispose());
        steps.put(
                "subscription manager",
                this.subscriptionManager == null ? null : () -> this.subscriptionManager.dispose());
        steps.put("calendar manager", this.calendarManager == null ? null : () -> this.calendarManager.dispose());
        steps.put(
                "Google Translate cache",
                this.googleTranslateManager == null ? null : () -> this.googleTranslateManager.clearCache());

        disposeAll(steps);
        LOGGER.info("GameEnvironment -> Disposed!");
    }

    static void disposeAll(Map<String, Runnable> steps) {
        for (Map.Entry<String, Runnable> step : steps.entrySet()) {
            if (step.getValue() == null) {
                continue;
            }

            try {
                step.getValue().run();
            } catch (Exception exception) {
                LOGGER.error("GameEnvironment -> Failed to dispose {}", step.getKey(), exception);
            }
        }
    }

    public HabboManager getHabboManager() {
        return this.habboManager;
    }

    public NavigatorManager getNavigatorManager() {
        return this.navigatorManager;
    }

    public GuildManager getGuildManager() {
        return this.guildManager;
    }

    public ItemManager getItemManager() {
        return this.itemManager;
    }

    public FurnitureTextProvider getFurnitureTextProvider() {
        return this.furnitureTextProvider;
    }

    public CatalogManager getCatalogManager() {
        CatalogCraftingSnapshot snapshot = this.catalogCraftingSnapshot;
        return snapshot != null ? snapshot.catalogManager() : this.catalogManager;
    }

    /**
     * Rebuilds catalog and crafting data away from the incoming-packet thread,
     * then publishes both fully populated snapshots in one volatile swap.
     *
     * @param afterPublish invoked only after both snapshots are live
     * @return false when a reload is already running or cannot be scheduled
     */
    public boolean reloadCatalogAndCraftingAsync(Runnable afterPublish) {
        if (!this.catalogReloadInProgress.compareAndSet(false, true)) {
            return false;
        }

        try {
            ScheduledFuture<?> scheduled = Emulator.getThreading().run(() -> this.reloadCatalogAndCrafting(afterPublish));
            if (scheduled != null) {
                return true;
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Catalog reload -> Could not schedule reload", exception);
        }

        this.catalogReloadInProgress.set(false);
        return false;
    }

    /**
     * Rebuilds catalog and crafting snapshots on the caller's current thread.
     * Callers use this only from a background task when a larger update must
     * keep its existing ordering around the catalog publication.
     */
    public boolean reloadCatalogAndCraftingNow(Runnable afterPublish) {
        if (!this.catalogReloadInProgress.compareAndSet(false, true)) {
            return false;
        }

        this.reloadCatalogAndCrafting(afterPublish);
        return true;
    }

    /** Reloads only catalog data, preserving the catalog-admin publish contract. */
    public boolean reloadCatalogAsync(Runnable afterPublish) {
        if (!this.catalogReloadInProgress.compareAndSet(false, true)) {
            return false;
        }

        try {
            ScheduledFuture<?> scheduled = Emulator.getThreading().run(() -> {
                long startedAt = System.currentTimeMillis();
                try {
                    if (Emulator.isShuttingDown) {
                        return;
                    }

                    CatalogManager catalogSnapshot = CatalogManager.loadSnapshot();
                    CraftingManager currentCraftingManager = this.getCraftingManager();
                    this.catalogManager = catalogSnapshot;
                    this.catalogCraftingSnapshot = new CatalogCraftingSnapshot(catalogSnapshot, currentCraftingManager);

                    if (afterPublish != null) {
                        afterPublish.run();
                    }

                    LOGGER.info("Catalog reload -> Published in {} ms", System.currentTimeMillis() - startedAt);
                } catch (Throwable throwable) {
                    LOGGER.error("Catalog reload -> Failed; the previous snapshot remains active", throwable);
                } finally {
                    this.catalogReloadInProgress.set(false);
                }
            });
            if (scheduled != null) {
                return true;
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Catalog reload -> Could not schedule reload", exception);
        }

        this.catalogReloadInProgress.set(false);
        return false;
    }

    private void reloadCatalogAndCrafting(Runnable afterPublish) {
        long startedAt = System.currentTimeMillis();
        try {
            if (Emulator.isShuttingDown) {
                return;
            }

            CraftingManager currentCraftingManager = this.getCraftingManager();
            if (currentCraftingManager != null) {
                currentCraftingManager.persistLimitedRecipeRemaining();
            }

            long catalogStartedAt = System.currentTimeMillis();
            CatalogManager catalogSnapshot = CatalogManager.loadSnapshot();
            long catalogLoadedAt = System.currentTimeMillis();
            CraftingManager craftingSnapshot = CraftingManager.loadSnapshot();
            long craftingLoadedAt = System.currentTimeMillis();

            this.catalogManager = catalogSnapshot;
            this.craftingManager = craftingSnapshot;
            this.catalogCraftingSnapshot = new CatalogCraftingSnapshot(catalogSnapshot, craftingSnapshot);

            if (afterPublish != null) {
                afterPublish.run();
            }

            LOGGER.info(
                    "Catalog reload -> Published in {} ms (catalog: {} ms, crafting: {} ms)",
                    System.currentTimeMillis() - startedAt,
                    catalogLoadedAt - catalogStartedAt,
                    craftingLoadedAt - catalogLoadedAt);
        } catch (Throwable throwable) {
            LOGGER.error("Catalog reload -> Failed; the previous snapshots remain active", throwable);
        } finally {
            this.catalogReloadInProgress.set(false);
        }
    }

    /**
     * Runs the existing item reload routines outside the incoming-packet
     * thread. The room refresh happens only after all three data sets are
     * available, so active users never receive an early update packet.
     */
    public boolean reloadItemsAsync(Runnable afterPublish) {
        if (!this.itemReloadInProgress.compareAndSet(false, true)) {
            return false;
        }

        try {
            ScheduledFuture<?> scheduled = Emulator.getThreading().run(() -> this.reloadItems(afterPublish));
            if (scheduled != null) {
                return true;
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Item reload -> Could not schedule reload", exception);
        }

        this.itemReloadInProgress.set(false);
        return false;
    }

    /** Same reload as {@link #reloadItemsAsync(Runnable)}, for an existing background workflow. */
    public boolean reloadItemsNow(Runnable afterPublish) {
        if (!this.itemReloadInProgress.compareAndSet(false, true)) {
            return false;
        }

        this.reloadItems(afterPublish);
        return true;
    }

    private void reloadItems(Runnable afterPublish) {
        long startedAt = System.currentTimeMillis();
        boolean refreshStarted = false;
        try {
            if (Emulator.isShuttingDown) {
                return;
            }

            ItemManager currentItemManager = this.getItemManager();
            currentItemManager.loadItems();
            currentItemManager.loadCrackable();
            currentItemManager.loadSoundTracks();

            refreshStarted = true;
            this.refreshActiveRoomItems(() -> {
                try {
                    if (afterPublish != null) {
                        afterPublish.run();
                    }

                    LOGGER.info("Item reload -> Published in {} ms", System.currentTimeMillis() - startedAt);
                } finally {
                    this.itemReloadInProgress.set(false);
                }
            });
        } catch (Throwable throwable) {
            LOGGER.error("Item reload -> Failed", throwable);
        } finally {
            if (!refreshStarted) {
                this.itemReloadInProgress.set(false);
            }
        }
    }

    /**
     * Updates the item representation in occupied rooms without making users
     * re-enter. The task deliberately uses incremental update packets rather
     * than a second room-item snapshot, which clients can interpret as adds.
     */
    private void refreshActiveRoomItems(Runnable afterRefresh) {
        List<Room> activeRooms = this.roomManager.getActiveRooms();
        if (!RoomItemLiveRefreshTask.schedule(activeRooms, afterRefresh) && afterRefresh != null) {
            afterRefresh.run();
        }
    }

    /** Re-sends the same room map composer to every currently occupied loaded room. */
    public void broadcastRoomRelativeMaps() {
        List<Room> activeRooms = this.roomManager.getActiveRooms();
        try (GameClientFlushBatch ignored = GameClientFlushBatch.open()) {
            for (Room room : activeRooms) {
                if (room.isLoaded() && room.getUserCount() > 0 && room.getLayout() != null) {
                    room.sendComposer(new RoomRelativeMapComposer(room).compose());
                }
            }
        }
    }

    /**
     * Reloads ranks and immediately pushes the new resolved permissions to
     * every connected user. This preserves live staff/member changes without
     * requiring a relogin.
     *
     * @return number of online users refreshed, or -1 when another reload is active
     */
    public int reloadPermissionsAndBroadcast() {
        if (!this.permissionsReloadInProgress.compareAndSet(false, true)) {
            return -1;
        }

        try {
            this.permissionsManager.reload();

            int refreshed = 0;
            try (GameClientFlushBatch ignored = GameClientFlushBatch.open()) {
                for (Habbo habbo : this.habboManager.getOnlineHabbos().values()) {
                    if (habbo == null || habbo.getHabboInfo() == null || habbo.getClient() == null) {
                        continue;
                    }

                    Rank currentRank = habbo.getHabboInfo().getRank();
                    int currentRankId = currentRank == null ? 1 : currentRank.getId();
                    Rank freshRank = this.permissionsManager.rankExists(currentRankId)
                            ? this.permissionsManager.getRank(currentRankId)
                            : this.permissionsManager.getRank(1);

                    habbo.getHabboInfo().setRank(freshRank);
                    habbo.getClient().sendResponse(new UserPermissionsComposer(habbo));
                    habbo.getClient().sendResponse(new AvailableCommandsComposer(
                            this.commandHandler.getCommandsForRank(freshRank == null ? currentRankId : freshRank.getId()),
                            this.permissionsManager.getAllRanks()));
                    refreshed++;
                }
            }

            return refreshed;
        } finally {
            this.permissionsReloadInProgress.set(false);
        }
    }

    /** Sends the existing catalog-refresh packet sequence as one coalesced broadcast. */
    public void broadcastCatalogReload() {
        GameClientManager clientManager = Emulator.getGameServer().getGameClientManager();
        try (GameClientFlushBatch ignored = GameClientFlushBatch.open()) {
            clientManager.sendBroadcastResponse(new CatalogUpdatedComposer());
            clientManager.sendBroadcastResponse(new CatalogModeComposer(0));
            clientManager.sendBroadcastResponse(new DiscountComposer());
            clientManager.sendBroadcastResponse(new MarketplaceConfigComposer());
            clientManager.sendBroadcastResponse(new GiftConfigurationComposer());
            clientManager.sendBroadcastResponse(new RecyclerLogicComposer());
        }
    }

    public WheelManager getWheelManager() {
        return this.wheelManager;
    }

    public SoundboardManager getSoundboardManager() {
        return this.soundboardManager;
    }

    public TraxEditorManager getTraxEditorManager() {
        return this.traxEditorManager;
    }

    public HotelViewManager getHotelViewManager() {
        return this.hotelViewManager;
    }

    public RewardTrackManager getRewardTrackManager() {
        return this.rewardTrackManager;
    }

    public DailyTaskManager getDailyTaskManager() {
        return this.dailyTaskManager;
    }

    public RoomManager getRoomManager() {
        return this.roomManager;
    }

    public CommandHandler getCommandHandler() {
        return this.commandHandler;
    }

    public PermissionsManager getPermissionsManager() {
        return this.permissionsManager;
    }

    public BotManager getBotManager() {
        return this.botManager;
    }

    public ModToolManager getModToolManager() {
        return this.modToolManager;
    }

    public ModToolSanctions getModToolSanctions() {
        return this.modToolSanctions;
    }

    public PetManager getPetManager() {
        return this.petManager;
    }

    public MentionManager getMentionManager() {
        return this.mentionManager;
    }

    public AchievementManager getAchievementManager() {
        return this.achievementManager;
    }

    public GuideManager getGuideManager() {
        return this.guideManager;
    }

    public WordFilter getWordFilter() {
        return this.wordFilter;
    }

    public CraftingManager getCraftingManager() {
        CatalogCraftingSnapshot snapshot = this.catalogCraftingSnapshot;
        return snapshot != null ? snapshot.craftingManager() : this.craftingManager;
    }

    public PollManager getPollManager() {
        return this.pollManager;
    }

    public CreditsScheduler getCreditsScheduler() {
        return this.creditsScheduler;
    }

    public PixelScheduler getPixelScheduler() {
        return this.pixelScheduler;
    }

    public PointsScheduler getPointsScheduler() {
        return this.pointsScheduler;
    }

    public GotwPointsScheduler getGotwPointsScheduler() {
        return this.gotwPointsScheduler;
    }

    public SubscriptionManager getSubscriptionManager() {
        return this.subscriptionManager;
    }

    public CalendarManager getCalendarManager() {
        return this.calendarManager;
    }

    public RoomChatBubbleManager getRoomChatBubbleManager() {
        return roomChatBubbleManager;
    }

    public GoogleTranslateManager getGoogleTranslateManager() {
        return this.googleTranslateManager;
    }

    public CustomBadgeManager getCustomBadgeManager() {
        return this.customBadgeManager;
    }

    public InfostandBackgroundManager getInfostandBackgroundManager() {
        return this.infostandBackgroundManager;
    }
}
