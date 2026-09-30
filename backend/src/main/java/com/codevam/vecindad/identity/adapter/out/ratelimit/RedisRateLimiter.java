package com.codevam.vecindad.identity.adapter.out.ratelimit;

import com.codevam.vecindad.identity.application.port.out.RateLimiterPort;
import com.codevam.vecindad.shared.security.Tokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Ventana fija con INCR + EXPIRE. Si Redis no responde falla abierto (el bloqueo por cuenta en BD sigue activo). */
@Component
@Profile("!test")
public class RedisRateLimiter implements RateLimiterPort {
    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);
    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean allow(String key, int maxInWindow, Duration window) {
        try {
            String redisKey = "rl:" + Tokens.sha256Hex(key);
            Long count = redis.opsForValue().increment(redisKey);
            if (count != null && count == 1L) {
                redis.expire(redisKey, window);
            }
            return count == null || count <= maxInWindow;
        } catch (RuntimeException e) {
            log.warn("Rate limiter no disponible, se permite la operación: {}", e.getMessage());
            return true;
        }
    }
}
