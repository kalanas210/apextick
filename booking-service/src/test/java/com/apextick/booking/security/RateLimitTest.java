package com.apextick.booking.security;

import com.apextick.booking.security.ratelimit.RedisRateLimiter;
import com.apextick.booking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class RateLimitTest {

    @Autowired RedisRateLimiter limiter;

    @Test
    void allows_up_to_the_limit_then_blocks_with_retry_after() {
        String bucket = "test-" + System.nanoTime();
        String subject = "sub-1";
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire(bucket, subject, 3, Duration.ofMinutes(1)).allowed())
                    .as("call %d should be allowed", i + 1).isTrue();
        }
        RedisRateLimiter.Decision blocked = limiter.tryAcquire(bucket, subject, 3, Duration.ofMinutes(1));
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterSeconds()).isGreaterThan(0);
    }

    @Test
    void separate_subjects_have_independent_windows() {
        String bucket = "test-" + System.nanoTime();
        assertThat(limiter.tryAcquire(bucket, "a", 1, Duration.ofMinutes(1)).allowed()).isTrue();
        assertThat(limiter.tryAcquire(bucket, "a", 1, Duration.ofMinutes(1)).allowed()).isFalse();
        assertThat(limiter.tryAcquire(bucket, "b", 1, Duration.ofMinutes(1)).allowed()).isTrue();
    }
}
