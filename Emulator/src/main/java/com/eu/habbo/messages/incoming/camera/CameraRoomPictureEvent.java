package com.eu.habbo.messages.incoming.camera;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.camera.CameraURLComposer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufUtil;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CameraRoomPictureEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraRoomPictureEvent.class);

    public static int CAMERA_RENDER_DELAY = 15;
    public static int MAX_IMAGE_BYTES = 2 * 1024 * 1024; // 2 MB max upload
    public static int MAX_IMAGE_WIDTH = 1024;
    public static int MAX_IMAGE_HEIGHT = 1024;
    public static int MAX_DAILY_RENDERS = 50;

    private ByteBuf image = null;
    private boolean responseSent = false;

    @Override
    public void handle() {
        try {
            this.make();
        } finally {
            if (this.image != null) {
                this.image.release();
            }
            if (!this.responseSent) this.client.sendResponse(new CameraURLComposer(""));
        }
    }

    private void make() {
        Habbo habbo = this.client.getHabbo();
        if (!habbo.hasPermission("acc_camera")) {
            habbo.alert(Emulator.getTexts().getValue("camera.permission"));
            return;
        }

        HabboInfo habboInfo = habbo.getHabboInfo();
        HabboStats habboStats = habbo.getHabboStats();
        int timestamp = Emulator.getIntUnixTimestamp();

        if (habboStats.cache.containsKey("camera_render_cooldown")) {
            int cameraTimestamp = (Integer) habboStats.cache.get("camera_render_cooldown");
            if (timestamp - cameraTimestamp < CAMERA_RENDER_DELAY) {
                String alertMessage = Emulator.getTexts()
                        .getValue("camera.wait")
                        .replace("%seconds%", Integer.toString(CAMERA_RENDER_DELAY - (timestamp - cameraTimestamp)));
                habbo.alert(alertMessage);
                return;
            }
        }

        // Daily render quota check
        int dailyRenderCount = getDailyRenderCount(habboStats, timestamp);
        if (dailyRenderCount >= MAX_DAILY_RENDERS) {
            habbo.alert(Emulator.getTexts()
                    .getValue("camera.daily.limit", "You have reached the daily photo limit. Try again tomorrow."));
            return;
        }
        Room room = habboInfo.getCurrentRoom();
        if (room == null) return;
        habboStats.cache.put("camera_render_cooldown", timestamp);

        int count = this.packet.readInt();

        // Reject oversized payloads before reading
        if (count <= 0 || count > MAX_IMAGE_BYTES) {
            LOGGER.warn("User {} attempted camera upload with invalid size: {} bytes", habboInfo.getUsername(), count);
            habbo.alert(Emulator.getTexts().getValue("camera.error.creation"));
            return;
        }

        this.image = this.packet.getBuffer().readBytes(count);
        if (this.image == null) return;

        byte[] imageBytes = ByteBufUtil.getBytes(this.image, 0, 4, true);
        if (imageBytes == null || imageBytes.length < 4 || !isPNG(imageBytes)) {
            LOGGER.warn("User {} attempted camera upload with non-PNG data", habboInfo.getUsername());
            return;
        }

        // Validate image dimensions before fully decoding
        int[] dimensions;
        try {
            dimensions = readPNGDimensions(this.image);
        } catch (IOException e) {
            LOGGER.warn("User {} uploaded image with unreadable dimensions", habboInfo.getUsername());
            handleImageProcessingError(habbo);
            return;
        }

        if (dimensions == null
                || dimensions[0] <= 0
                || dimensions[1] <= 0
                || dimensions[0] > MAX_IMAGE_WIDTH
                || dimensions[1] > MAX_IMAGE_HEIGHT) {
            LOGGER.warn(
                    "User {} attempted camera upload with invalid dimensions: {}x{}",
                    habboInfo.getUsername(),
                    dimensions != null ? dimensions[0] : "null",
                    dimensions != null ? dimensions[1] : "null");
            habbo.alert(Emulator.getTexts().getValue("camera.error.creation"));
            return;
        }

        BufferedImage theImage;
        try (ByteBufInputStream in = new ByteBufInputStream(this.image)) {
            theImage = ImageIO.read(in);
        } catch (IOException e) {
            handleImageProcessingError(habbo);
            return;
        }

        if (theImage == null) {
            LOGGER.warn("User {} uploaded image that could not be decoded", habboInfo.getUsername());
            handleImageProcessingError(habbo);
            return;
        }

        // Double-check decoded dimensions match expectations
        if (theImage.getWidth() > MAX_IMAGE_WIDTH || theImage.getHeight() > MAX_IMAGE_HEIGHT) {
            LOGGER.warn(
                    "User {} decoded image exceeds dimension limits: {}x{}",
                    habboInfo.getUsername(),
                    theImage.getWidth(),
                    theImage.getHeight());
            handleImageProcessingError(habbo);
            return;
        }

        String fileName = habboInfo.getId() + "_" + timestamp;
        String URL = fileName + ".png";
        String URLsmall = fileName + "_small.png";
        String base = Emulator.getConfig().getValue("camera.url");
        String json = Emulator.getConfig()
                .getValue("camera.extradata")
                .replace("%timestamp%", Integer.toString(timestamp))
                .replace("%room_id%", Integer.toString(room.getId()))
                .replace("%url%", base + URL);
        File outputDirectory = new File(Emulator.getConfig().getValue("imager.location.output.camera"));
        try {
            writeImages(theImage, outputDirectory, URL, URLsmall);
        } catch (IOException e) {
            LOGGER.error("Unable to save camera image to {}", outputDirectory, e);
            handleImageProcessingError(habbo);
            return;
        }

        habboInfo.setPhotoURL(base + URL);
        habboInfo.setPhotoTimestamp(timestamp);
        habboInfo.setPhotoRoomId(room.getId());
        habboInfo.setPhotoJSON(json);
        incrementDailyRenderCount(habboStats, timestamp, dailyRenderCount);
        this.client.sendResponse(new CameraURLComposer(URL));
        this.responseSent = true;
    }

    static void writeImages(BufferedImage image, File directory, String fullName, String smallName) throws IOException {
        Files.createDirectories(directory.toPath());
        if (!ImageIO.write(image, "png", new File(directory, fullName)))
            throw new IOException("PNG writer unavailable");
        int width = Math.max(1, image.getWidth() / 2);
        int height = Math.max(1, image.getHeight() / 2);
        BufferedImage small = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = small.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(image, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        if (!ImageIO.write(small, "png", new File(directory, smallName)))
            throw new IOException("PNG writer unavailable");
    }

    private boolean isPNG(byte[] bytes) {
        return bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47;
    }

    /**
     * Read PNG dimensions from the IHDR chunk without fully decoding the image.
     * This prevents decompression bomb attacks by checking dimensions before allocation.
     */
    private int[] readPNGDimensions(ByteBuf buf) throws IOException {
        try (ByteBufInputStream in = new ByteBufInputStream(buf.duplicate())) {
            try (ImageInputStream iis = ImageIO.createImageInputStream(in)) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
                if (!readers.hasNext()) return null;

                ImageReader reader = readers.next();
                try {
                    reader.setInput(iis);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    return new int[] {width, height};
                } finally {
                    reader.dispose();
                }
            }
        }
    }

    private int getDailyRenderCount(HabboStats stats, int currentTimestamp) {
        if (!stats.cache.containsKey("camera_daily_count") || !stats.cache.containsKey("camera_daily_reset")) {
            return 0;
        }
        int resetTimestamp = (Integer) stats.cache.get("camera_daily_reset");
        // Reset counter if more than 24 hours have passed
        if (currentTimestamp - resetTimestamp >= 86400) {
            return 0;
        }
        return (Integer) stats.cache.get("camera_daily_count");
    }

    private void incrementDailyRenderCount(HabboStats stats, int currentTimestamp, int currentCount) {
        if (currentCount == 0) {
            stats.cache.put("camera_daily_reset", currentTimestamp);
        }
        stats.cache.put("camera_daily_count", currentCount + 1);
    }

    private void handleImageProcessingError(Habbo habbo) {
        habbo.alert(Emulator.getTexts().getValue("camera.error.creation"));
    }
}
