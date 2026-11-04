package com.campuslink.module.forum.domain.model;

import java.time.Instant;
import java.util.UUID;

public record PostImage(UUID id, long ownerId, Long postId, boolean inUse, String state,
        String storageKey, String mimeType, int size, int width, int height, Instant createdAt) { }
