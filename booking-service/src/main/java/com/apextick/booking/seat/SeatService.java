package com.apextick.booking.seat;

import com.apextick.booking.config.AppProperties;
import com.apextick.booking.outbox.AfterCommit;
import com.apextick.booking.outbox.DomainEventPublisher;
import com.apextick.booking.outbox.EventTypes;
import com.apextick.booking.outbox.payload.SeatHeldPayload;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class SeatService {

    private static final String HOLD_KEY_PREFIX = "seat-hold:";

    private final SeatRepository seatRepository;
    private final StringRedisTemplate redisTemplate;
    private final DomainEventPublisher events;
    private final Duration holdDuration;

    public SeatService(SeatRepository seatRepository,
                       StringRedisTemplate redisTemplate,
                       DomainEventPublisher events,
                       AppProperties props) {
        this.seatRepository = seatRepository;
        this.redisTemplate = redisTemplate;
        this.events = events;
        this.holdDuration = props.hold().duration();
    }

    @Transactional
    public Seat holdSeat(Long seatId, String userId) {
        Instant heldUntil = Instant.now().plus(holdDuration);

        int updated = seatRepository.holdSeat(seatId, userId, heldUntil);
        if (updated == 0) {
            throw new SeatUnavailableException(seatId);
        }

        Seat seat = seatRepository.findById(seatId).orElseThrow();

        // Record the event in the transactional outbox — published to RabbitMQ after commit
        // by OutboxPublisher, so a rollback never emits a phantom event.
        events.publish(EventTypes.SEAT_HELD, "seat", String.valueOf(seatId),
                new SeatHeldPayload(seat.getId(), seat.getSeatNumber(), seat.getHeldBy(), seat.getHeldUntil()));

        // Redis TTL key drives hold expiry; write it only after the DB commit succeeds.
        AfterCommit.run(() ->
                redisTemplate.opsForValue().set(HOLD_KEY_PREFIX + seatId, userId, holdDuration));

        return seat;
    }

    @Transactional
    public int releaseExpiredHold(Long seatId) {
        return seatRepository.releaseSeat(seatId);
    }

    @Transactional(readOnly = true)
    public List<Seat> getSeatsForEvent(Long eventId) {
        return seatRepository.findByEventIdOrderByIdAsc(eventId);
    }
}
