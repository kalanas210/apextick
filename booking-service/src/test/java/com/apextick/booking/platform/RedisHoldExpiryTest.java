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
import java.time.Instant;
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
 * <p>Redis's TTL and the row's {@code held_until} are two views of one deadline -- the key is
 * armed for exactly the hold duration -- so the test moves both together rather than only the
 * TTL. A notification whose deadline has not passed is now deliberately a no-op (see
 * {@link #an_expiry_that_arrives_after_the_hold_was_extended_leaves_the_seat_held}), so a test
 * holding {@code held_until} five minutes out while expiring the key after one second would be
 * asserting something production can never produce. The fallback sweeper still cannot claim
 * these seats: it only looks at deadlines older than {@code app.hold.expiry-tolerance}, which
 * the test profile sets well beyond the second this test needs.
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

    /** Moves the row's half of the deadline, e.g. {@code "1 second"} or {@code "-1 second"}. */
    private void setHeldUntil(Long seatId, String interval) {
        jdbc.sql("UPDATE seats SET held_until = now() + CAST(:offset AS interval) WHERE id = :seatId")
                .param("offset", interval)
                .param("seatId", seatId)
                .update();
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

        // bring the whole deadline forward to one second -- the row's and Redis's, the way a
        // one-second hold duration would have set them; everything after this is the listener's
        setHeldUntil(seatId, "1 second");
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
     * Keyspace notifications are fire-and-forget and the listener thread can lag, so an expiry
     * can land after the holder has already re-posted their selection -- a reload, Back from
     * checkout, "keep my seats" -- and the idempotent re-hold has pushed the deadline forward
     * and re-armed the key. Releasing on {@code status = 'HELD'} alone would wipe a hold the
     * caller was just told (201) they still had: their countdown keeps running, the seat map
     * hands the seat to the next buyer and their order fails with HOLD_EXPIRED.
     */
    @Test
    void an_expiry_that_arrives_after_the_hold_was_extended_leaves_the_seat_held() {
        Long seatId = availableSeatId(3);
        CurrentUser user = new CurrentUser("hold-extender", "extender",
                "extender@apextick.local", "Hold Extender", Set.of("user"));

        holdService.hold(SLUG, List.of(seatId), user);

        // the first deadline lapses and Redis publishes its expiry...
        setHeldUntil(seatId, "-1 second");
        // ...but the holder re-posts the selection before the listener gets there, which
        // extends the hold and re-arms the key
        holdService.hold(SLUG, List.of(seatId), user);
        assertThat(seatRepository.findById(seatId).orElseThrow().getHeldUntil())
                .isAfter(Instant.now());

        // the late notification must now be a no-op
        assertThat(holdService.releaseExpired(seatId)).isFalse();

        Seat after = seatRepository.findById(seatId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(after.getHeldBy()).isEqualTo(user.sub());
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
