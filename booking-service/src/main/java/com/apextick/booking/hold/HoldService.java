package com.apextick.booking.hold;

import com.apextick.booking.catalog.Event;
import com.apextick.booking.catalog.EventLookup;
import com.apextick.booking.catalog.SalesWindow;
import com.apextick.booking.config.AppProperties;
import com.apextick.booking.hold.dto.HoldResponse;
import com.apextick.booking.order.OrderRepository;
import com.apextick.booking.order.OrderService;
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
import com.apextick.booking.web.NotFoundException;
import com.apextick.booking.web.UnprocessableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Multi-seat, all-or-nothing holds with a configurable TTL; releases publish seat.released. */
@Service
public class HoldService {

    private static final Logger log = LoggerFactory.getLogger(HoldService.class);

    private final SeatRepository seats;
    private final EventLookup eventLookup;
    private final SeatHoldKeys holdKeys;
    private final DomainEventPublisher events;
    private final RealtimePublisher realtime;
    private final OrderRepository orders;
    private final OrderService orderService;
    private final Duration holdDuration;
    private final int maxSeats;

    public HoldService(SeatRepository seats, EventLookup eventLookup, SeatHoldKeys holdKeys,
                       DomainEventPublisher events, RealtimePublisher realtime,
                       OrderRepository orders, OrderService orderService, AppProperties props) {
        this.seats = seats;
        this.eventLookup = eventLookup;
        this.holdKeys = holdKeys;
        this.events = events;
        this.realtime = realtime;
        this.orders = orders;
        this.orderService = orderService;
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
            throw tooManySeats(0, ids.size());
        }
        Event event = eventLookup.resolve(idOrSlug);
        SalesWindow.assertOpen(event);

        // Counting the caller's seats and then holding more is a check-then-act, so queue this
        // account's own concurrent requests for this event behind one another before counting.
        // Without it two tabs asking for eight disjoint seats each would both read zero and both
        // commit, and the cap below would be advisory. Rivals are not affected: the lock is per
        // (event, user) and Postgres drops it when this transaction ends.
        seats.lockHoldsOf(holdCapLockKey(event.getId(), user.sub()));

        // The cap is per user, not per request: counting only this call would let one account
        // hoard an event eight seats at a time. Seats the caller already holds and is simply
        // re-posting are not counted twice.
        List<Long> alreadyMine = seats.findMyHeldSeatIds(event.getId(), user.sub());
        long extra = ids.stream().filter(id -> !alreadyMine.contains(id)).count();
        if (alreadyMine.size() + extra > maxSeats) {
            throw tooManySeats(alreadyMine.size(), (int) extra);
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
            holdKeys.arm(ids, sub, holdDuration);
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
        // Freeing a seat an unpaid order still covers puts the two aggregates out of step: the
        // seat map shows it available while existsPendingForSeats keeps every buyer out, and
        // paying the order charges the card only to refund it. Cancelling the order is the
        // release path for those seats -- which only works for the caller's own orders, so the
        // guard asks about those alone.
        List<Long> pending = orders.findPendingSeatIds(ids, user.sub());
        if (!pending.isEmpty()) {
            throw new ConflictException(ErrorCodes.ORDER_PENDING,
                    "Cancel your pending order to release these seats",
                    Map.of("seatIds", pending));
        }
        List<Long> released = seats.releaseSeatsHeldBy(ids, user.sub());
        publishReleased(event.getId(), released, user.sub(), "USER_RELEASED");
        return released.size();
    }

    /**
     * Called by the Redis keyspace expiry listener for a single seat. The release only fires if
     * the deadline really has passed: the notification may have been overtaken by the holder
     * re-posting their selection, which pushes {@code heldUntil} forward and re-arms the key.
     */
    @Transactional
    public boolean releaseExpired(Long seatId) {
        Seat seat = seats.findById(seatId).orElse(null);
        if (seat == null) {
            return false;
        }
        Long eventId = seat.getEventId();
        int released = seats.releaseExpiredSeat(seatId, Instant.now());
        if (released > 0) {
            events.publish(EventTypes.SEAT_RELEASED, "seat", String.valueOf(seatId),
                    new SeatReleasedPayload(seatId, eventId, "EXPIRED"));
            AfterCommit.run(() -> realtime.seatStatusChanged(eventId,
                    List.of(new SeatStatusChange(seatId, SeatStatus.AVAILABLE.name(), null))));
            return true;
        }
        return false;
    }

    /**
     * Force-releases a stuck hold on an operator's behalf. Goes through the same release path
     * as every other one -- outbox event, realtime broadcast, Redis key -- because a seat that
     * quietly changes state in the database only is a seat every open seat map still shows as
     * held. Any unpaid order covering it is cancelled in the same transaction: it could never
     * be paid now, and while it stands it blocks the next buyer.
     */
    @Transactional
    public Seat adminRelease(Long seatId, String adminSub) {
        Seat seat = seats.findById(seatId).orElseThrow(() -> new NotFoundException("Seat", seatId));
        Long eventId = seat.getEventId();
        String holder = seat.getHeldBy();
        if (seats.releaseSeat(seatId) > 0) {
            for (UUID orderId : orders.findPendingOrderIdsForSeat(seatId)) {
                orderService.cancelForAdminRelease(orderId);
            }
            publishReleased(eventId, List.of(seatId), holder, "ADMIN");
            log.info("Admin {} force-released seat {} (event {}) held by {}",
                    adminSub, seatId, eventId, holder);
        }
        return seats.findById(seatId).orElseThrow(() -> new NotFoundException("Seat", seatId));
    }

    /** Records the outbox event now and defers the broadcast and key drop until the commit. */
    private void publishReleased(Long eventId, List<Long> seatIds, String holder, String reason) {
        if (seatIds.isEmpty()) {
            return;
        }
        for (Long id : seatIds) {
            events.publish(EventTypes.SEAT_RELEASED, "seat", String.valueOf(id),
                    new SeatReleasedPayload(id, eventId, reason));
        }
        List<SeatStatusChange> changes = seatIds.stream()
                .map(id -> new SeatStatusChange(id, SeatStatus.AVAILABLE.name(), null)).toList();
        AfterCommit.run(() -> {
            holdKeys.dropOwn(seatIds, holder);
            realtime.seatStatusChanged(eventId, changes);
        });
    }

    /**
     * Advisory-lock key for one account's holds on one event. A collision between two different
     * (event, user) pairs only costs them a moment of needless queuing, never correctness.
     */
    private static long holdCapLockKey(Long eventId, String sub) {
        return (Long.hashCode(eventId) & 0xffff_ffffL) << 32 | (sub.hashCode() & 0xffff_ffffL);
    }

    private UnprocessableException tooManySeats(int alreadyHeld, int requested) {
        return new UnprocessableException(ErrorCodes.TOO_MANY_SEATS,
                "At most " + maxSeats + " seats per person for this event",
                Map.of("maxSeats", maxSeats, "heldSeats", alreadyHeld, "requestedSeats", requested));
    }
}
