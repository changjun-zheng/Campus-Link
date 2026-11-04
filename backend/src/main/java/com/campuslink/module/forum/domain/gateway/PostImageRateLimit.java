package com.campuslink.module.forum.domain.gateway;

public interface PostImageRateLimit {
    boolean allow(long userId);
}
