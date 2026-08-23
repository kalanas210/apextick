package com.apextick.booking.security.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/** Atomic fixed-window rate limiter backed by a single Redis INCR/PEXPIRE Lua script. Fail-open. */
@Component
public class RedisRateLimiter {

    // returns the remaining window TTL (ms) when blocked, or -1 when the call is allowed
    private static final RedisScript<Long> SCRIPT = RedisScript.of("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            if current > tonumber(ARGV[2]) then
              return redis.call('PTTL', KEYS[1])
            end
            return -1
            """, Long.class);

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Decision tryAcquire(String bucket, String subject, int limit, Duration window) {
        try {
            Long ttlMillis = redis.execute(SCRIPT, List.of("rl:" + bucket + ":" + subject),
                    String.valueOf(window.toMillis()), String.valueOf(limit));
            if (ttlMillis != null && ttlMillis >= 0) {
                return new Decision(false, Math.max(1, (int) Math.ceil(ttlMillis / 1000.0)));
            }
            return new Decision(true, 0);
        } catch (Exception e) {
            // never let the limiter take the API down
            return new Decision(true, 0);
        }
    }

    public record Decision(boolean allowed, int retryAfterSeconds) {
    }
}
