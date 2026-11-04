package com.campuslink.module.account.infrastructure.storage;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本地头像适配器（CR-191）：魔数嗅探、体积 / 像素上限、居中裁方重编码、路径穿越防护。
 *
 * <p>这几条都是"伪造扩展名"类攻击的防线：客户端给的 Content-Type 与文件名都不可信，
 * 只认字节头 + 解码后的真实像素。
 */
class LocalAvatarImageAdapterTest {

    private static final long USER_ID = 42L;

    @TempDir
    private Path tempDir;

    private AppProperties props;
    private LocalAvatarImageAdapter adapter;

    @BeforeEach
    void setUp() {
        props = new AppProperties();
        props.getAvatar().setDir(tempDir.toString());
        props.getAvatar().setMaxBytes(2L * 1024 * 1024);
        props.getAvatar().setMaxDimension(4096);
        props.getAvatar().setSize(512);
        props.getAvatar().setQuality(0.9f);
        adapter = new LocalAvatarImageAdapter(props);
    }

    @Test
    @DisplayName("合法 PNG：返回 /media/avatars 路径，落盘为 512×512 JPEG")
    void storesPngAsSquareJpeg() throws IOException {
        String url = adapter.store(USER_ID, png(40, 20));

        assertThat(url).startsWith(LocalAvatarImageAdapter.PUBLIC_PREFIX + USER_ID + "/").endsWith(".jpg");
        Path file = tempDir.resolve(url.substring(LocalAvatarImageAdapter.PUBLIC_PREFIX.length()));
        assertThat(file).exists();

        BufferedImage stored = ImageIO.read(file.toFile());
        assertThat(stored.getWidth()).isEqualTo(512);
        assertThat(stored.getHeight()).isEqualTo(512);
        byte[] head = Files.readAllBytes(file);
        assertThat(head[0]).isEqualTo((byte) 0xFF);
        assertThat(head[1]).isEqualTo((byte) 0xD8);
    }

    @Test
    @DisplayName("伪造 .png 的纯文本 → 400 / 2016（只认魔数，不信扩展名）")
    void rejectsTextMasqueradingAsPng() {
        assertThatThrownBy(() -> adapter.store(USER_ID, "not an image".getBytes()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.AVATAR_INVALID));
    }

    @Test
    @DisplayName("SVG → 400 / 2016（矢量图标可含脚本，本轮不接受）")
    void rejectsSvg() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes();

        assertThatThrownBy(() -> adapter.store(USER_ID, svg))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.AVATAR_INVALID));
    }

    @Test
    @DisplayName("像素超过上限 → 400 / 2016（先看头部再解码，防解压炸弹）")
    void rejectsOversizedDimension() throws IOException {
        props.getAvatar().setMaxDimension(8);

        assertThatThrownBy(() -> adapter.store(USER_ID, png(10, 10)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.AVATAR_INVALID));
    }

    @Test
    @DisplayName("字节数超过上限 → 400 / 2016")
    void rejectsOversizedBytes() throws IOException {
        props.getAvatar().setMaxBytes(64);

        assertThatThrownBy(() -> adapter.store(USER_ID, png(40, 20)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.AVATAR_INVALID));
    }

    @Test
    @DisplayName("删除只认本存储前缀：预置路径 / 越界路径一律不碰")
    void deleteIgnoresPresetAndTraversalPaths() throws IOException {
        Path outside = tempDir.resolveSibling("outside-avatar.txt");
        Files.writeString(outside, "keep me");

        adapter.delete("/avatars/avatar-fox-01-happy.png");
        adapter.delete("/media/avatars/../outside-avatar.txt");

        assertThat(outside).exists();
    }

    @Test
    @DisplayName("删除本存储写入的文件：按 URL 反解路径并删掉")
    void deleteRemovesStoredFile() throws IOException {
        String url = adapter.store(USER_ID, png(40, 20));
        Path file = tempDir.resolve(url.substring(LocalAvatarImageAdapter.PUBLIC_PREFIX.length()));
        assertThat(file).exists();

        adapter.delete(url);

        assertThat(file).doesNotExist();
    }

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.RED);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
