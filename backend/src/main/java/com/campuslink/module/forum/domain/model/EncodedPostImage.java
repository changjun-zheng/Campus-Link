package com.campuslink.module.forum.domain.model;

public record EncodedPostImage(byte[] bytes, String mimeType, int width, int height) {
    public EncodedPostImage {
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
