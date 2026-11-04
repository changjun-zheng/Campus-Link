package com.campuslink.module.forum.infrastructure.storage;
import com.campuslink.common.exception.ApiException;
import com.campuslink.config.AppProperties;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;
import static org.assertj.core.api.Assertions.*;
class LocalPostImageStorageTest {
    @TempDir(cleanup = CleanupMode.NEVER) Path dir;
    private final AppProperties props = new AppProperties();
    private final LocalPostImageStorage storage = new LocalPostImageStorage(props);
    private byte[] png(int w, int h) throws Exception {
        var image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0x40112233);
        var out = new ByteArrayOutputStream(); ImageIO.write(image, "png", out); return out.toByteArray();
    }
    @Test void pngPreservesDimensionsAndTransparencyAndRoundTrips() throws Exception {
        props.getPostImages().setDir(dir.toString());
        var clean = storage.prepare(png(13, 7));
        assertThat(clean.width()).isEqualTo(13); assertThat(clean.height()).isEqualTo(7);
        assertThat(ImageIO.read(new ByteArrayInputStream(clean.bytes())).getRGB(0, 0) >>> 24).isEqualTo(64);
        String key = UUID.randomUUID() + ".png";
        storage.store(key, clean.bytes()); assertThat(storage.read(key)).isEqualTo(clean.bytes());
        assertThatThrownBy(() -> storage.store(key, clean.bytes())).isInstanceOf(ApiException.class);
    }
    @Test void rejectsMagicDamagePixelAndByteLimits() throws Exception {
        for (byte[] raw : new byte[][]{new byte[0], "<svg/>".getBytes(), "GIF89a".getBytes(), new byte[]{(byte)255,(byte)216,(byte)255}}) {
            assertThatThrownBy(() -> storage.prepare(raw)).isInstanceOf(ApiException.class);
        }
        assertThatThrownBy(() -> storage.prepare(new byte[2 * 1024 * 1024 + 1])).isInstanceOf(ApiException.class);
        byte[] wide = png(4097, 1);
        assertThatThrownBy(() -> storage.prepare(wide)).isInstanceOf(ApiException.class);
    }
    @Test void rejectsTraversalAndMissingFileAndUnwritableDirectory() throws Exception {
        props.getPostImages().setDir(dir.toString());
        assertThatThrownBy(() -> storage.read("../secret.png")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> storage.read(UUID.randomUUID() + ".png")).isInstanceOf(ApiException.class);
        Path file = java.nio.file.Files.writeString(dir.resolve("blocked"), "fixture");
        props.getPostImages().setDir(file.toString());
        assertThatThrownBy(() -> storage.store(UUID.randomUUID() + ".png", png(2, 2))).isInstanceOf(ApiException.class);
    }
}
