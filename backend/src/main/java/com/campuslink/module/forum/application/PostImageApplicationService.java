package com.campuslink.module.forum.application;

import com.campuslink.common.exception.ApiException;
import com.campuslink.common.markdown.PostImageUrls;
import com.campuslink.common.result.ResultCode;
import com.campuslink.config.AppProperties;
import com.campuslink.module.account.application.AccountApplicationService;
import com.campuslink.module.forum.domain.gateway.PostImageRepository;
import com.campuslink.module.forum.domain.gateway.PostImageStorage;
import com.campuslink.module.forum.domain.gateway.PostImageRateLimit;
import com.campuslink.module.forum.domain.model.PostImage;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostImageApplicationService {
    private static final Logger LOG = LoggerFactory.getLogger(PostImageApplicationService.class);
    private final PostImageRepository repository;
    private final PostImageStorage storage;
    private final PostImageRateLimit rateLimit;
    private final AccountApplicationService accounts;
    private final ForumQueryApplicationService forum;
    private final AppProperties props;
    private final Semaphore processing = new Semaphore(2);

    public record UploadedImage(UUID id, String url, int width, int height, int size) { }
    public record ImageContent(byte[] bytes, String mimeType) {
        public ImageContent { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
    }

    /** No DB transaction spans processing or disk IO. Reservation commits before creating the file. */
    public UploadedImage upload(long userId, byte[] raw) {
        if (!props.getPostImages().isUploadEnabled()) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
        accounts.assertActiveForContent(userId);
        if (!rateLimit.allow(userId) || !processing.tryAcquire()) {
            throw new ApiException(ResultCode.POST_IMAGE_RATE_LIMITED);
        }
        UUID id = UUID.randomUUID();
        boolean reserved = false;
        try {
            var encoded = storage.prepare(raw);
            byte[] bytes = encoded.bytes();
            String key = id + ("image/png".equals(encoded.mimeType()) ? ".png" : ".jpg");
            repository.reserve(new PostImage(id, userId, null, false, "UPLOADING", key,
                    encoded.mimeType(), bytes.length, encoded.width(), encoded.height(), Instant.now()));
            reserved = true;
            storage.store(key, bytes);
            repository.state(id, "READY");
            return new UploadedImage(id, PostImageUrls.url(id), encoded.width(), encoded.height(), bytes.length);
        } catch (RuntimeException ex) {
            if (reserved) {
                try {
                    repository.state(id, "FAILED");
                } catch (RuntimeException failedRecord) {
                    LOG.warn("Post image failure record unavailable imageId={}", id);
                }
            }
            if (ex instanceof ApiException api) {
                throw api;
            }
            throw new ApiException(ResultCode.POST_IMAGE_STORAGE_UNAVAILABLE);
        } finally {
            processing.release();
        }
    }

    public ImageContent read(UUID id, Long viewerId) {
        if (!props.getPostImages().isReadEnabled()) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        PostImage image = repository.find(id).filter(item -> "READY".equals(item.state()))
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        if (image.postId() != null) {
            forum.requireReadablePost(image.postId(), viewerId);
        }
        boolean publicReference = image.postId() != null && image.inUse();
        if (!publicReference && !Objects.equals(viewerId, image.ownerId())) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        if (!publicReference) {
            accounts.assertActiveForContent(image.ownerId());
        }
        return new ImageContent(storage.read(image.storageKey()), image.mimeType());
    }

    @Transactional
    public void bind(long postId, long userId, String safeHtml) {
        var ids = PostImageUrls.references(safeHtml);
        if (ids.size() > 10) {
            throw new ApiException(ResultCode.POST_IMAGE_REFERENCE_INVALID);
        }
        if (!ids.isEmpty() && !props.getPostImages().isReadEnabled()) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
        for (UUID id : ids.stream().sorted().toList()) {
            PostImage image = repository.lock(id)
                    .orElseThrow(() -> new ApiException(ResultCode.POST_IMAGE_REFERENCE_INVALID));
            if (!"READY".equals(image.state()) || image.ownerId() != userId
                    || image.postId() != null && image.postId() != postId) {
                throw new ApiException(ResultCode.POST_IMAGE_REFERENCE_INVALID);
            }
            if (image.postId() == null && !props.getPostImages().isUploadEnabled()) {
                throw new ApiException(ResultCode.FORBIDDEN);
            }
        }
        repository.clearReferences(postId);
        for (UUID id : ids) {
            repository.reference(id, postId);
        }
    }
}
