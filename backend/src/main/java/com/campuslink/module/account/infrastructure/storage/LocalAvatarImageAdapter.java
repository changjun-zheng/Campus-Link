package com.campuslink.module.account.infrastructure.storage;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.config.AppProperties;
import com.campuslink.module.account.domain.gateway.AvatarImageGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.UUID;

/**
 * 头像本地磁盘适配器（CR-191）：格式嗅探 → 像素上限 → 居中裁方 → 512×512 JPEG 重编码。
 *
 * <p>只接受 JPEG / PNG 魔数：GIF / WebP / SVG 一律拒绝（SVG 可含脚本；WebP 需额外解码库）。
 * 原始文件名不参与落盘路径，路径由 {@code userId + UUID} 生成，避免路径穿越与重名覆盖。
 *
 * <p>生产部署须把 {@code campuslink.avatar.dir} 指向持久卷，并由 Nginx 代理 {@code /media/}；
 * 本轮不改部署正文（CR-079 实施时补两条）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalAvatarImageAdapter implements AvatarImageGateway {

    /** 对外静态映射的前缀（与 MediaResourceConfig 一致） */
    static final String PUBLIC_PREFIX = "/media/avatars/";

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private final AppProperties props;

    @Override
    public String store(long userId, byte[] raw) {
        AppProperties.Avatar config = props.getAvatar();
        if (raw == null || raw.length == 0 || raw.length > config.getMaxBytes()
                || !looksLikeSupportedImage(raw)) {
            throw new ApiException(ResultCode.AVATAR_INVALID);
        }
        BufferedImage source = decode(raw, config.getMaxDimension());
        BufferedImage square = centerCropAndScale(source, config.getSize());

        String relative = userId + "/" + UUID.randomUUID() + ".jpg";
        Path root = rootDir();
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new ApiException(ResultCode.AVATAR_INVALID);
        }
        // relative 恒为 "userId/uuid.jpg"，target 必有父目录；显式判掉根路径写（SpotBugs NP_NULL）
        Path parent = target.getParent();
        if (parent == null) {
            throw new ApiException(ResultCode.AVATAR_INVALID);
        }
        try {
            Files.createDirectories(parent);
            writeJpeg(square, target, config.getQuality());
        } catch (IOException e) {
            throw new IllegalStateException("头像写入失败", e);
        }
        return PUBLIC_PREFIX + relative;
    }

    @Override
    public void delete(String avatarUrl) {
        if (avatarUrl == null || !avatarUrl.startsWith(PUBLIC_PREFIX)) {
            // 预置头像（/avatars/...）与外部 URL 不归本存储管
            return;
        }
        Path root = rootDir();
        Path target = root.resolve(avatarUrl.substring(PUBLIC_PREFIX.length())).normalize();
        if (!target.startsWith(root)) {
            log.warn("拒绝删除越界头像路径：{}", avatarUrl);
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            // 旧文件删除失败不影响本次头像切换结果；孤儿文件清理另开 CR
            log.warn("旧头像删除失败：{}", avatarUrl, e);
        }
    }

    private static BufferedImage decode(byte[] raw, int maxDimension) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(raw))) {
            if (in == null) {
                throw new ApiException(ResultCode.AVATAR_INVALID);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new ApiException(ResultCode.AVATAR_INVALID);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > maxDimension || height > maxDimension) {
                    throw new ApiException(ResultCode.AVATAR_INVALID);
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new ApiException(ResultCode.AVATAR_INVALID);
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new ApiException(ResultCode.AVATAR_INVALID);
        }
    }

    private static BufferedImage centerCropAndScale(BufferedImage source, int size) {
        int width = source.getWidth();
        int height = source.getHeight();
        int side = Math.min(width, height);
        int x = (width - side) / 2;
        int y = (height - side) / 2;

        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = out.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, size, size);
            graphics.drawImage(source, 0, 0, size, size, x, y, x + side, y + side, null);
        } finally {
            graphics.dispose();
        }
        return out;
    }

    private static void writeJpeg(BufferedImage image, Path target, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(target.toFile())) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(Math.max(0f, Math.min(1f, quality)));
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    /** 只认 JPEG / PNG 魔数，扩展名不可信（伪造 .png 的文本 / SVG 在此被拦） */
    static boolean looksLikeSupportedImage(byte[] raw) {
        if (raw == null || raw.length < 3) {
            return false;
        }
        if (raw[0] == (byte) 0xFF && raw[1] == (byte) 0xD8 && raw[2] == (byte) 0xFF) {
            return true;
        }
        if (raw.length < PNG_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (raw[i] != PNG_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    private Path rootDir() {
        return Paths.get(props.getAvatar().getDir()).toAbsolutePath().normalize();
    }
}
