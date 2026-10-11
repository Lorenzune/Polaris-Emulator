package com.eu.habbo.messages.incoming.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CameraImageStorageTest {
    @TempDir
    Path temp;

    @Test
    void createsMissingOutputDirectoryAndWritesBothPhotosWithoutTrailingSeparator() throws Exception {
        Path output = temp.resolve("camera/photos");
        BufferedImage image = new BufferedImage(320, 320, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(10, 10, 0xffff0000);
        CameraRoomPictureEvent.writeImages(image, output.toFile(), "photo.png", "photo_small.png");
        BufferedImage saved = ImageIO.read(output.resolve("photo.png").toFile());
        assertEquals(320, saved.getWidth());
        assertEquals(0xffff0000, saved.getRGB(10, 10));
        assertEquals(
                160, ImageIO.read(output.resolve("photo_small.png").toFile()).getWidth());
    }

    @Test
    void reportsInvalidOutputPathInsteadOfAcceptingAnUnsavedPhoto() throws Exception {
        Path output = temp.resolve("file");
        Files.writeString(output, "occupied");
        assertThrows(
                IOException.class,
                () -> CameraRoomPictureEvent.writeImages(
                        new BufferedImage(320, 320, BufferedImage.TYPE_INT_ARGB),
                        output.toFile(),
                        "photo.png",
                        "small.png"));
    }
}
