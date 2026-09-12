package com.apextick.booking.platform;

import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.hold.HoldService;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.SeatStatus;
import com.apextick.booking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The primary hold-expiry path: Redis expires {@code seat-hold:{id}}, the keyspace
 * notification reaches {@code HoldExpiryListener}, and the seat goes back on sale with a
 * {@code seat.released}/{@code EXPIRED} row in the outbox.
 *
 * <p>Only the 30-second fallback sweeper was covered before, so a change to the key
 * prefix, the channel name or Redis's {@code notify-keyspace-events} config would have
 * left the README's headline mechanism dead with the suite green.
 *
 * <p>The hold's {@code held_until} stays five minutes out and the sweeper only releases
 * seats whose deadline has passed, so the keyspace notification is the only thing that
 * can release this seat -- shortening the Redis TTL to one second is the whole trigger.
 */
@IntegrationTest
class RedisHoldExpiryTest {

    // a slug no other test class touches, so nothing else competes for these seats
    private static final String SLUG = "liverpool-manchester-united";
    private static final String KEY = "seat-hold:";

    @Autowired HoldService holdService;
    @Autowired EventRepository eventRepository;
    @Autowired SeatRepository seatRepository;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcClient jdbc;

    /** A seat nobody has touched. The offset keeps the two tests off each other's seat. */
    private Long availableSeatId(int offset) {
        Long eventId = eventRepository.findBySlug(SLUG).orElseThrow().getId();
        return seatRepository.findAllForEventWithLayout(eventId).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE)
                .map(Seat::getId).skip(offset).findFirst().orElseThrow();
    }

    private long expiredReleaseRows(Long seatId) {
        return jdbc.sql("""
                        SELECT count(*) FROM outbox_events
                         WHERE type = 'seat.released'
                           AND aggregate_type = 'seat'
                           AND aggregate_id = :seatId
                           AND payload ->> 'reason' = 'EXPIRED'
                        """)
                .param("seatId", String.valueOf(seatId))
                .query(Long.class)
                .single();
    }

    @Test
    void an_expiring_redis_key_releases_the_seat_and_records_an_expired_release() {
        Long seatId = availableSeatId(0);
        CurrentUser user = new CurrentUser("hold-expiry-tester", "expiry",
                "expiry@apextick.local", "Expiry Tester", Set.of("user"));

        holdService.hold(SLUG, List.of(seatId), user);

        Seat held = seatRepository.findById(seatId).orElseThrow();
        assertThat(held.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(held.getHeldBy()).isEqualTo(user.sub());
        assertThat(redis.getExpire(KEY + seatId)).isPositive();
        assertThat(expiredReleaseRows(seatId)).isZero();

        // shorten the TTL to one second; everything after this is the listener's doing
        redis.opsForValue().set(KEY + seatId, user.sub(), Duration.ofSeconds(1));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            Seat after = seatRepository.findById(seatId).orElseThrow();
            assertThat(after.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
            assertThat(after.getHeldBy()).isNull();
            assertThat(after.getHeldUntil()).isNull();
        });

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(expiredReleaseRows(seatId)).isEqualTo(1));
    }

    @Test
    void an_expiry_for_a_seat_that_is_no_longer_held_releases_nothing() {
        Long seatId = availableSeatId(1);

        // a late or duplicate notification for a seat nobody holds must not emit a release
        assertThat(holdService.releaseExpired(seatId)).isFalse();

        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus())
                .isEqualTo(SeatStatus.AVAILABLE);
        assertThat(expiredReleaseRows(seatId)).isZero();
    }

    /**
     * The key is dropped when the order is paid, but only after that transaction commits --
     * a crash in between leaves an armed key on a sold seat. The release must then be a
     * no-op, or a paid ticket goes back on sale and is resold underneath its owner.
     */
    @Test
    void a_late_expiry_never_puts_a_booked_seat_back_on_sale() {
        Long seatId = availableSeatId(2);
        CurrentUser buyer = new CurrentUser("hold-expiry-buyer", "buyer",
                "buyer@apextick.local", "Expiry Buyer", Set.of("user"));

        holdService.hold(SLUG, List.of(seatId), buyer);

        // the hold became a sale (the purchase path itself is covered by the order tests)
        Seat sold = seatRepository.findById(seatId).orElseThrow();
        sold.setStatus(SeatStatus.BOOKED);
        sold.setHeldUntil(null);
        seatRepository.save(sold);

        assertThat(holdService.releaseExpired(seatId)).isFalse();

        Seat after = seatRepository.findById(seatId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(SeatStatus.BOOKED);
        assertThat(after.getHeldBy()).isEqualTo(buyer.sub());
        assertThat(expiredReleaseRows(seatId)).isZero();
    }
}
