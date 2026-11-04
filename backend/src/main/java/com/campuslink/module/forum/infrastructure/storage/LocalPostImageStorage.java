package com.campuslink.module.forum.infrastructure.storage;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.result.ResultCode;
import com.campuslink.config.AppProperties;
import com.campuslink.module.forum.domain.gateway.PostImageStorage;
import com.campuslink.module.forum.domain.model.EncodedPostImage;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalPostImageStorage implements PostImageStorage {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private final AppProperties props;

    @Override
    public EncodedPostImage prepare(byte[] raw) {
        if (raw == null || raw.length == 0) {
            throw new ApiException(ResultCode.POST_IMAGE_INVALID);
        }
        if (raw.length > MAX_BYTES) {
            throw new ApiException(ResultCode.FILE_TOO_LARGE);
        }
        boolean png = raw.length >= 8 && raw[0] == (byte) 0x89 && raw[1] == 0x50 && raw[2] == 0x4e
                && raw[3] == 0x47 && raw[4] == 0x0d && raw[5] == 0x0a && raw[6] == 0x1a && raw[7] == 0x0a;
        boolean jpeg = raw.length >= 3 && raw[0] == (byte) 0xff && raw[1] == (byte) 0xd8 && raw[2] == (byte) 0xff;
        if (!png && !jpeg) {
            throw new ApiException(ResultCode.POST_IMAGE_INVALID);
        }
        try (var input = new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(raw))) {
            if (input == null) {
                throw new ApiException(ResultCode.POST_IMAGE_INVALID);
            }
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new ApiException(ResultCode.POST_IMAGE_INVALID);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName();
                if (!(png && "png".equalsIgnoreCase(format) || jpeg && "jpeg".equalsIgnoreCase(format))) {
                    throw new ApiException(ResultCode.POST_IMAGE_INVALID);
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > 4096 || height > 4096) {
                    throw new ApiException(ResultCode.POST_IMAGE_INVALID);
                }
                BufferedImage source = reader.read(0, new ImageReadParam());
                if (source == null) {
                    throw new ApiException(ResultCode.POST_IMAGE_INVALID);
                }
                BufferedImage clean = new BufferedImage(width, height,
                        png ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = clean.createGraphics();
                try {
                    graphics.drawImage(source, 0, 0, null);
                } finally {
                    graphics.dispose();
                }
                try (var bytes = new ByteArrayOutputStream(); var output = new javax.imageio.stream.MemoryCacheImageOutputStream(bytes)) {
                    ImageWriter writer = ImageIO.getImageWritersByFormatName(png ? "png" : "jpeg").next();
                    try {
                        writer.setOutput(output);
                        writer.write(null, new IIOImage(clean, null, null), writer.getDefaultWriteParam());
                        output.flush();
                    } finally {
                        writer.dispose();
                    }
                    byte[] encoded = bytes.toByteArray();
                    if (encoded.length > MAX_BYTES) {
                        throw new ApiException(ResultCode.FILE_TOO_LARGE);
                    }
                    return new EncodedPostImage(encoded, png ? "image/png" : "image/jpeg", width, height);
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException ex) {
            throw new ApiException(ResultCode.POST_IMAGE_INVALID);
        }
    }

    @Override
    public void store(String storageKey, byte[] bytes) {
        try {
            Path target = target(storageKey);
            Files.createDirectories(java.util.Objects.requireNonNull(target.getParent()));
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException | IllegalArgumentException ex) {
            throw new ApiException(ResultCode.POST_IMAGE_STORAGE_UNAVAILABLE);
        }
    }

    @Override
    public byte[] read(String storageKey) {
        try {
            Path target = target(storageKey);
            Path root = java.util.Objects.requireNonNull(target.getParent()).toRealPath();
            if (!target.toRealPath().startsWith(root) || Files.size(target) > MAX_BYTES) {
                throw new ApiException(ResultCode.NOT_FOUND);
            }
            return Files.readAllBytes(target);
        } catch (IOException | IllegalArgumentException ex) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
    }

    private Path target(String key) {
        if (key == null || !key.matches("[0-9a-f-]{36}\\.(png|jpg)")) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        Path root = Path.of(props.getPostImages().getDir()).toAbsolutePath().normalize();
        return root.resolve(key);
    }
}
