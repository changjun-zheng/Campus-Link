package com.campuslink.module.forum.domain.gateway;

import com.campuslink.module.forum.domain.model.PostImage;
import java.util.Optional;
import java.util.UUID;

public interface PostImageRepository {
    void reserve(PostImage image);
    void state(UUID id, String state);
    Optional<PostImage> find(UUID id);
    Optional<PostImage> lock(UUID id);
    void reference(UUID id, long postId);
    void clearReferences(long postId);
}
