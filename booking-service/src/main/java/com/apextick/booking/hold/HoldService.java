package com.apextick.booking.hold;

import com.apextick.booking.catalog.Event;
import com.apextick.booking.catalog.EventLookup;
import com.apextick.booking.config.AppProperties;
import com.apextick.booking.hold.dto.HoldResponse;
import com.apextick.booking.outbox.AfterCommit;
import com.apextick.booking.outbox.DomainEventPublisher;
import com.apextick.booking.outbox.EventTypes;
import com.apextick.booking.outbox.payload.SeatHeldPayload;
import com.apextick.booking.outbox.payload.SeatReleasedPayload;
import com.apextick.booking.realtime.RealtimePublisher;
import com.apextick.booking.realtime.dto.SeatStatusChange;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.SeatStatus;
import com.apextick.booking.seat.SeatUnavailableException;
import com.apextick.booking.seat.dto.SeatResponse;
import com.apextick.booking.web.ConflictException;
import com.apextick.booking.web.ErrorCodes;
import com.apextick.booking.web.UnprocessableException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Multi-seat, all-or-nothing holds with a configurable TTL; releases publish seat.released. */
@Service
public class HoldService {

    private static final String KEY = "seat-hold:";

    private final SeatRepository seats;
    private final EventLookup eventLookup;
    private final StringRedisTemplate redis;
    private final DomainEventPublisher events;
    private final RealtimePublisher realtime;
    private final Duration holdDuration;
    private final int maxSeats;

    public HoldService(SeatRepository seats, EventLookup eventLookup, StringRedisTemplate redis,
                       DomainEventPublisher events, RealtimePublisher realtime, AppProperties props) {
        this.seats = seats;
        this.eventLookup = eventLookup;
        this.redis = redis;
        this.events = events;
        this.realtime = realtime;
        this.holdDuration = props.hold().duration();
        this.maxSeats = props.hold().maxSeats();
    }

    @Transactional
    public HoldResponse hold(String idOrSlug, List<Long> requested, CurrentUser user) {
        if (requested == null || requested.isEmpty()) {
            throw new UnprocessableException(ErrorCodes.VALIDATION_FAILED, "Select at least one seat");
        }
        List<Long> ids = requested.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (ids.size() > maxSeats) {
            throw new UnprocessableException("TOO_MANY_SEATS",
                    "At most " + maxSeats + " seats per hold", Map.of("maxSeats", maxSeats));
        }
        Event event = eventLookup.resolve(idOrSlug);
        if (!event.getStatus().isPublic()) {
            throw new ConflictException(ErrorCodes.SALES_CLOSED, "Sales are closed for this event");
        }
        if (event.getSalesEndAt() != null && event.getSalesEndAt().isBefore(Instant.now())) {
            throw new ConflictException(ErrorCodes.SALES_CLOSED, "Sales have ended for this event");
        }

        Instant heldUntil = Instant.now().plus(holdDuration);
        int updated = seats.holdSeatsAtomically(event.getId(), ids, user.sub(), heldUntil);
        if (updated != ids.size()) {
            List<Long> held = seats.findHeldSeatIds(event.getId(), ids, user.sub());
            List<Long> conflicts = ids.stream().filter(id -> !held.contains(id)).toList();
            throw new SeatUnavailableException(conflicts.isEmpty() ? ids : conflicts);
        }

        List<Seat> heldSeats = seats.findByIdsWithLayout(ids);
        String sub = user.sub();
        for (Seat s : heldSeats) {
            events.publish(EventTypes.SEAT_HELD, "seat", String.valueOf(s.getId()),
                    new SeatHeldPayload(s.getId(), s.getSeatNumber(), sub, heldUntil));
        }
        Long eventId = event.getId();
        List<SeatStatusChange> changes = ids.stream()
                .map(id -> new SeatStatusChange(id, SeatStatus.HELD.name(), heldUntil)).toList();
        AfterCommit.run(() -> {
            ids.forEach(id -> redis.opsForValue().set(KEY + id, sub, holdDuration));
            realtime.seatStatusChanged(eventId, changes);
        });

        List<SeatResponse> dtos = heldSeats.stream().map(s -> SeatResponse.from(s, sub)).toList();
        return new HoldResponse(eventId, ids, heldUntil, holdDuration.toSeconds(), dtos);
    }

    @Transactional(readOnly = true)
    public HoldResponse myHolds(String idOrSlug, CurrentUser user) {
        Event event = eventLookup.resolve(idOrSlug);
        List<Seat> mine = seats.findMyHolds(event.getId(), user.sub());
        if (mine.isEmpty()) {
            return new HoldResponse(event.getId(), List.of(), null, holdDuration.toSeconds(), List.of());
        }
        Instant earliest = mine.stream().map(Seat::getHeldUntil).filter(Objects::nonNull)
                .min(Instant::compareTo).orElse(null);
        List<Long> ids = mine.stream().map(Seat::getId).toList();
        List<SeatResponse> dtos = mine.stream().map(s -> SeatResponse.from(s, user.sub())).toList();
        return new HoldResponse(event.getId(), ids, earliest, holdDuration.toSeconds(), dtos);
    }

    @Transactional
    public int releaseMine(String idOrSlug, CurrentUser user) {
        Event event = eventLookup.resolve(idOrSlug);
        List<Long> ids = seats.findMyHeldSeatIds(event.getId(), user.sub());
        if (ids.isEmpty()) {
            return 0;
        }
        seats.releaseMyHolds(event.getId(), user.sub());
        for (Long id : ids) {
            events.publish(EventTypes.SEAT_RELEASED, "seat", String.valueOf(id),
                    new SeatReleasedPayload(id, event.getId(), "USER_RELEASED"));
        }
        Long eventId = event.getId();
        List<SeatStatusChange> changes = ids.stream()
                .map(id -> new SeatStatusChange(id, SeatStatus.AVAILABLE.name(), null)).toList();
        AfterCommit.run(() -> {
            ids.forEach(id -> redis.delete(KEY + id));
            realtime.seatStatusChanged(eventId, changes);
        });
        return ids.size();
    }

    /** Called by the Redis keyspace expiry listener for a single seat. */
    @Transactional
    public boolean releaseExpired(Long seatId) {
        Seat seat = seats.findById(seatId).orElse(null);
        if (seat == null) {
            return false;
        }
        Long eventId = seat.getEventId();
        int released = seats.releaseSeat(seatId);
        if (released > 0) {
            events.publish(EventTypes.SEAT_RELEASED, "seat", String.valueOf(seatId),
                    new SeatReleasedPayload(seatId, eventId, "EXPIRED"));
            AfterCommit.run(() -> realtime.seatStatusChanged(eventId,
                    List.of(new SeatStatusChange(seatId, SeatStatus.AVAILABLE.name(), null))));
            return true;
        }
        return false;
    }
}
