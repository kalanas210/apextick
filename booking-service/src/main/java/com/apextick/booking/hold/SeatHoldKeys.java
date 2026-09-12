package com.apextick.booking.hold;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;

/**
 * The Redis keys behind seat holds: {@code seat-hold:<seatId>} holds the sub of the holder,
 * and its TTL is what expires the hold (see {@link HoldExpiryListener}).
 *
 * <p>Written from one place because the delete is conditional. Every release publishes its
 * side effects after the database transaction commits, and by then the seat may already be
 * held by the next buyer -- dropping their key would silently cancel their TTL and leave
 * their hold to the slower sweeper.
 */
@Component
public class SeatHoldKeys {

    public static final String PREFIX = "seat-hold:";

    private final StringRedisTemplate redis;

    public SeatHoldKeys(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** (Re)arms the expiry key for each seat now held by {@code sub}. */
    public void arm(Collection<Long> seatIds, String sub, Duration ttl) {
        seatIds.forEach(id -> redis.opsForValue().set(PREFIX + id, sub, ttl));
    }

    /** Drops each key, but only while it still names {@code sub} as the holder. */
    public void dropOwn(Collection<Long> seatIds, String sub) {
        for (Long id : seatIds) {
            String key = PREFIX + id;
            if (sub == null || sub.equals(redis.opsForValue().get(key))) {
                redis.delete(key);
            }
        }
    }

    /** Drops each key outright: the seat has left the hold lifecycle for good (it was booked). */
    public void drop(Collection<Long> seatIds) {
        seatIds.forEach(id -> redis.delete(PREFIX + id));
    }
}
