package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogPageAssetUploadResultComposer;
import io.netty.buffer.ByteBuf;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public class CatalogPageAssetUploadEvent extends MessageHandler {
    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_IMAGE_DIMENSION = 4096;
    private static final long MAX_IMAGE_PIXELS = 16_000_000L;
    private static final int UPLOADS_PER_MINUTE = 10;
    private static final long UPLOAD_WINDOW_MS = 60_000L;
    private static final Map<Integer, long[]> UPLOAD_BUCKETS = new ConcurrentHashMap<>();

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int pageId = this.packet.readInt();
        CatalogPageType catalogType = CatalogPageType.fromString(this.packet.readString());
        if (catalogType == CatalogPageType.BOTH) catalogType = CatalogPageType.NORMAL;
        String slot = this.packet.readString();
        String originalName = this.packet.readString();
        int byteCount = this.packet.readInt();

        if (this.client.getHabbo() == null || !this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
            respond(pageId, catalogType, slot, false, "", "No permission");
            return;
        }

        if (pageId <= 0 || !("header".equals(slot) || "teaser".equals(slot))) {
            respond(pageId, catalogType, slot, false, "", "Invalid catalog page or image slot");
            return;
        }

        CatalogPage page = Emulator.getGameEnvironment().getCatalogManager().getCatalogPage(pageId, catalogType);
        if (page == null) {
            respond(pageId, catalogType, slot, false, "", "Catalog page not found");
            return;
        }

        if (!tryConsumeUpload(this.client.getHabbo().getHabboInfo().getId())) {
            respond(pageId, catalogType, slot, false, "", "Upload limit reached. Please try again in a minute.");
            return;
        }

        if (byteCount <= 0 || byteCount > MAX_IMAGE_BYTES || this.packet.bytesAvailable() != byteCount) {
            respond(pageId, catalogType, slot, false, "", "Image must be smaller than 5 MB");
            return;
        }

        ByteBuf imageBuffer = this.packet.getBuffer().readBytes(byteCount);
        byte[] imageBytes = new byte[byteCount];
        try {
            imageBuffer.readBytes(imageBytes);
        } finally {
            imageBuffer.release();
        }

        try {
            String requestedExtension = extensionFromName(originalName);
            String actualExtension = inspectImage(imageBytes);
            if (!extensionMatches(requestedExtension, actualExtension)) {
                respond(pageId, catalogType, slot, false, "", "File extension does not match the image format");
                return;
            }

            String configuredPath = Emulator.getConfig().getValue("catalog.admin.asset.upload.path", "").trim();
            if (configuredPath.isEmpty()) {
                respond(pageId, catalogType, slot, false, "", "Catalog image upload folder is not configured");
                return;
            }

            Path root = Paths.get(configuredPath).toAbsolutePath().normalize();
            Files.createDirectories(root);
            root = root.toRealPath();
            String fileName = "catalog_" + pageId + "_" + UUID.randomUUID().toString().replace("-", "") + "." + actualExtension;
            Path destination = root.resolve(fileName).normalize();
            if (!destination.startsWith(root)) {
                respond(pageId, catalogType, slot, false, "", "Invalid upload folder");
                return;
            }

            Files.write(destination, imageBytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            respond(pageId, catalogType, slot, true, fileName, "Image uploaded");
        } catch (IOException | RuntimeException e) {
            respond(pageId, catalogType, slot, false, "", "Image could not be validated or saved");
        }
    }

    private void respond(int pageId, CatalogPageType type, String slot, boolean success, String fileName, String message) {
        String safeSlot = "header".equals(slot) ? "header" : "teaser";
        this.client.sendResponse(new CatalogPageAssetUploadResultComposer(pageId, type, safeSlot, success, fileName, message));
    }

    private static String extensionFromName(String name) throws IOException {
        if (name == null || name.length() > 255) throw new IOException("Invalid file name");
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) throw new IOException("Missing file extension");
        String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!("png".equals(extension) || "jpg".equals(extension) || "jpeg".equals(extension) || "gif".equals(extension) || "webp".equals(extension))) {
            throw new IOException("Unsupported image extension");
        }
        return "jpeg".equals(extension) ? "jpg" : extension;
    }

    private static String inspectImage(byte[] imageBytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes))) {
            if (input == null) return inspectWebp(imageBytes);
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return inspectWebp(imageBytes);
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION
                        || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new IOException("Image dimensions are outside the allowed range");
                }
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if ("jpeg".equals(format)) return "jpg";
                if ("jpg".equals(format) || "png".equals(format) || "gif".equals(format) || "webp".equals(format)) return format;
                throw new IOException("Unsupported image format");
            } finally {
                reader.dispose();
            }
        }
    }

    private static String inspectWebp(byte[] bytes) throws IOException {
        if (bytes.length < 30 || !ascii(bytes, 0, "RIFF") || !ascii(bytes, 8, "WEBP")) {
            throw new IOException("Unsupported image data");
        }

        long declaredSize = unsignedInt32Le(bytes, 4) + 8L;
        if (declaredSize != bytes.length) throw new IOException("Invalid WebP container length");

        int offset = 12;
        int width = 0;
        int height = 0;
        boolean hasFrame = false;
        while (offset + 8 <= bytes.length) {
            String chunk = new String(bytes, offset, 4, StandardCharsets.US_ASCII);
            long chunkLength = unsignedInt32Le(bytes, offset + 4);
            long dataEnd = offset + 8L + chunkLength;
            if (dataEnd > bytes.length || chunkLength > Integer.MAX_VALUE) throw new IOException("Invalid WebP chunk");
            int data = offset + 8;

            if ("VP8X".equals(chunk) && chunkLength >= 10) {
                width = 1 + unsignedInt24Le(bytes, data + 4);
                height = 1 + unsignedInt24Le(bytes, data + 7);
            } else if ("VP8L".equals(chunk) && chunkLength >= 5 && (bytes[data] & 0xFF) == 0x2F) {
                width = 1 + (bytes[data + 1] & 0xFF) + ((bytes[data + 2] & 0x3F) << 8);
                height = 1 + ((bytes[data + 2] & 0xC0) >> 6) + ((bytes[data + 3] & 0xFF) << 2) + ((bytes[data + 4] & 0x0F) << 10);
                hasFrame = true;
            } else if ("VP8 ".equals(chunk) && chunkLength >= 10
                    && (bytes[data + 3] & 0xFF) == 0x9D && (bytes[data + 4] & 0xFF) == 0x01 && (bytes[data + 5] & 0xFF) == 0x2A) {
                width = unsignedInt16Le(bytes, data + 6) & 0x3FFF;
                height = unsignedInt16Le(bytes, data + 8) & 0x3FFF;
                hasFrame = true;
            }

            offset = (int) dataEnd + ((int) chunkLength & 1);
        }

        if (!hasFrame || offset != bytes.length || width <= 0 || height <= 0
                || width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION
                || (long) width * height > MAX_IMAGE_PIXELS) {
            throw new IOException("Invalid WebP image dimensions or frame");
        }
        return "webp";
    }

    private static boolean ascii(byte[] bytes, int offset, String expected) {
        if (offset < 0 || offset + expected.length() > bytes.length) return false;
        for (int i = 0; i < expected.length(); i++) {
            if (bytes[offset + i] != (byte) expected.charAt(i)) return false;
        }
        return true;
    }

    private static int unsignedInt16Le(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
    }

    private static int unsignedInt24Le(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8) | ((bytes[offset + 2] & 0xFF) << 16);
    }

    private static long unsignedInt32Le(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFFL)
                | ((bytes[offset + 1] & 0xFFL) << 8)
                | ((bytes[offset + 2] & 0xFFL) << 16)
                | ((bytes[offset + 3] & 0xFFL) << 24);
    }

    private static boolean extensionMatches(String requested, String actual) {
        if ("jpeg".equals(requested)) requested = "jpg";
        return requested.equals(actual);
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
