package com.eu.habbo.messages.incoming.guilds;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.guilds.GuildCustomBadgeRequestManager;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.guilds.GuildCustomBadgeSavedComposer;
import io.netty.buffer.ByteBuf;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GuildUploadCustomBadgeEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuildUploadCustomBadgeEvent.class);
    private static final int MAX_BADGE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_IMAGE_WIDTH = 1024;
    private static final int MAX_IMAGE_HEIGHT = 1024;
    private static final long MAX_IMAGE_PIXELS = 4_000_000L;
    private static final int UPLOADS_PER_MINUTE = 5;
    private static final long UPLOAD_WINDOW_MS = 60_000L;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<Integer, long[]> UPLOAD_BUCKETS = new ConcurrentHashMap<>();

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        if (this.packet.getBuffer().readableBytes() < 8) return;

        int guildId = this.packet.readInt();
        int byteCount = this.packet.readInt();
        Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);

        boolean hasCustomPermission = habbo.hasPermission(Permission.ACC_CUSTOM_GROUP_BADGE);
        boolean isGuildAdmin = habbo.hasPermission(Permission.ACC_GUILD_ADMIN);
        boolean isOwner =
                guild != null && guild.getOwnerId() == habbo.getHabboInfo().getId();

        // Custom group badges are only valid for an existing group. This also
        // prevents the creator flow from generating orphaned files.
        if (guild == null || guildId <= 0) {
            habbo.alert("Il gruppo non è valido o non è ancora stato creato.");
            return;
        }

        if ((!hasCustomPermission && !isGuildAdmin) || (!isOwner && !isGuildAdmin)) {
            habbo.alert("Non hai il permesso di caricare stemmi personalizzati per questo gruppo.");
            return;
        }

        if (!tryConsumeUpload(habbo.getHabboInfo().getId())) {
            habbo.alert("Hai effettuato troppi caricamenti. Riprova tra poco.");
            return;
        }

        if (byteCount <= 0
                || byteCount > MAX_BADGE_BYTES
                || this.packet.getBuffer().readableBytes() < byteCount) {
            LOGGER.warn(
                    "User {} attempted custom group badge upload with invalid size: {} bytes",
                    habbo.getHabboInfo().getUsername(),
                    byteCount);
            habbo.alert("L'immagine non è valida o supera il limite consentito.");
            return;
        }

        ByteBuf imageBuf = this.packet.getBuffer().readBytes(byteCount);
        byte[] imageBytes = new byte[byteCount];
        imageBuf.readBytes(imageBytes);
        imageBuf.release();

        int imageWidth;
        int imageHeight;
        try (ImageInputStream peek = ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes))) {
            if (peek == null) throw new IOException("No image input stream");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(peek);
            if (!readers.hasNext()) throw new IOException("Unknown image format");
            ImageReader reader = readers.next();
            try {
                reader.setInput(peek, true, true);
                imageWidth = reader.getWidth(0);
                imageHeight = reader.getHeight(0);
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            LOGGER.warn(
                    "Failed to read custom group badge image header uploaded by {}",
                    habbo.getHabboInfo().getUsername(),
                    e);
            habbo.alert("Non è stato possibile leggere l'immagine.");
            return;
        }

        if (imageWidth <= 0
                || imageHeight <= 0
                || imageWidth > MAX_IMAGE_WIDTH
                || imageHeight > MAX_IMAGE_HEIGHT
                || (long) imageWidth * imageHeight > MAX_IMAGE_PIXELS) {
            LOGGER.warn(
                    "User {} uploaded an image with invalid dimensions for group badge: {}x{}",
                    habbo.getHabboInfo().getUsername(),
                    imageWidth,
                    imageHeight);
            habbo.alert("Le dimensioni dell'immagine non sono valide.");
            return;
        }

        BufferedImage originalImage;
        try {
            originalImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
        } catch (Exception e) {
            LOGGER.warn(
                    "Failed to decode custom group badge image uploaded by {}",
                    habbo.getHabboInfo().getUsername(),
                    e);
            habbo.alert("Non è stato possibile leggere l'immagine.");
            return;
        }

        if (originalImage == null
                || originalImage.getWidth() != imageWidth
                || originalImage.getHeight() != imageHeight) {
            LOGGER.warn(
                    "User {} uploaded an image with invalid dimensions for group badge",
                    habbo.getHabboInfo().getUsername());
            habbo.alert("Le dimensioni dell'immagine non sono valide.");
            return;
        }

        BufferedImage badgeImage1x = renderBadge(originalImage, 39);
        BufferedImage badgeImage2x = renderBadge(originalImage, 78);
        String badgeCode = createBadgeCode(guildId);

        try {
            GuildCustomBadgeRequestManager.saveBadgeFiles(badgeCode, badgeImage1x, badgeImage2x);
        } catch (IOException e) {
            LOGGER.error("Failed to save custom group badge {}", badgeCode, e);
            GuildCustomBadgeRequestManager.deleteBadgeFiles(badgeCode);
            habbo.alert("Non è stato possibile salvare lo stemma.");
            return;
        }

        boolean requiresApproval = habbo.getHabboInfo().getRank() == null
                || habbo.getHabboInfo().getRank().getId() < 7;
        if (requiresApproval) {
            if (!GuildCustomBadgeRequestManager.createPending(
                    guildId, habbo.getHabboInfo().getId(), badgeCode)) {
                GuildCustomBadgeRequestManager.deleteBadgeFiles(badgeCode);
                habbo.alert("Esiste già una richiesta pendente per questo gruppo oppure hai raggiunto il limite.");
                return;
            }

            this.client.sendResponse(new GuildCustomBadgeSavedComposer(guildId, badgeCode, "pending"));
            return;
        }

        if (!GuildCustomBadgeRequestManager.createApproved(
                guildId, habbo.getHabboInfo().getId(), badgeCode)) {
            GuildCustomBadgeRequestManager.deleteBadgeFiles(badgeCode);
            habbo.alert("Non Ã¨ stato possibile registrare lo stemma.");
            return;
        }

        applyBadge(guild, badgeCode, habbo);
        this.client.sendResponse(new GuildCustomBadgeSavedComposer(guildId, badgeCode, "approved"));
    }

    private static BufferedImage renderBadge(BufferedImage originalImage, int size) {
        BufferedImage badgeImage = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = badgeImage.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(
                    RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
            graphics.drawImage(originalImage, 0, 0, size, size, null);
        } finally {
            graphics.dispose();
        }
        return badgeImage;
    }

    private static String createBadgeCode(int guildId) {
        return "custom_" + System.currentTimeMillis() + "_" + guildId + "_"
                + Long.toUnsignedString(RANDOM.nextLong(), 36);
    }

    public static void applyBadge(Guild guild, String badgeCode, Habbo habbo) {
        guild.setBadge(badgeCode);
        guild.needsUpdate = true;

        Room guildRoom = Emulator.getGameEnvironment().getRoomManager().getRoom(guild.getRoomId());
        if (guildRoom != null) {
            guildRoom.refreshGuild(guild);
            guildRoom.sendComposer(
                    new com.eu.habbo.messages.outgoing.rooms.users.RoomUsersAddGuildBadgeComposer(guild).compose());
        }

        Room currentRoom = habbo.getHabboInfo().getCurrentRoom();
        if (currentRoom != null) {
            currentRoom.sendComposer(
                    new com.eu.habbo.messages.outgoing.rooms.users.RoomUsersAddGuildBadgeComposer(guild).compose());
            if (habbo.getHabboStats().guild == guild.getId() && habbo.getRoomUnit() != null) {
                currentRoom.sendComposer(new com.eu.habbo.messages.outgoing.guilds.GuildFavoriteRoomUserUpdateComposer(
                                habbo.getRoomUnit(), guild)
                        .compose());
            }
        }

        Emulator.getThreading().run(guild);
    }

    private static boolean tryConsumeUpload(int userId) {
        long now = System.currentTimeMillis();
        long[] bucket = UPLOAD_BUCKETS.computeIfAbsent(userId, ignored -> new long[UPLOADS_PER_MINUTE]);
        synchronized (bucket) {
            int oldestIndex = 0;
            long oldest = Long.MAX_VALUE;
            for (int i = 0; i < bucket.length; i++) {
                if (bucket[i] < oldest) {
                    oldest = bucket[i];
                    oldestIndex = i;
                }
            }
            if (oldest > now - UPLOAD_WINDOW_MS) return false;
            bucket[oldestIndex] = now;
            return true;
        }
    }
}
