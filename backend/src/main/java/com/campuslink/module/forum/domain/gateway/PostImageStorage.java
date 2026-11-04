package com.campuslink.module.forum.domain.gateway;

import com.campuslink.module.forum.domain.model.EncodedPostImage;

public interface PostImageStorage {
    EncodedPostImage prepare(byte[] raw);
    void store(String storageKey, byte[] bytes);
    byte[] read(String storageKey);
}
