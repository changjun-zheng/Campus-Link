package com.campuslink.module.forum.infrastructure.redis;

import com.campuslink.common.redis.RedisKeys;
import com.campuslink.module.forum.domain.gateway.PostImageRateLimit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisPostImageRateLimit implements PostImageRateLimit {
    private static final DefaultRedisScript<Long> HIT = new DefaultRedisScript<>("""
            local count=redis.call('INCR',KEYS[1])
            if count==1 then redis.call('EXPIRE',KEYS[1],3600) end
            return count
            """, Long.class);
    private final StringRedisTemplate redis;

    @Override
    public boolean allow(long userId) {
        Long count = redis.execute(HIT, List.of(RedisKeys.of("post-image:upload:" + userId)));
        return count != null && count <= 20;
    }
}
