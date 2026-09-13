package com.apextick.booking.seat.admin;

import com.apextick.booking.outbox.AfterCommit;
import com.apextick.booking.outbox.DomainEventPublisher;
import com.apextick.booking.outbox.EventTypes;
import com.apextick.booking.outbox.payload.SeatBlockPayload;
import com.apextick.booking.realtime.RealtimePublisher;
import com.apextick.booking.realtime.dto.SeatStatusChange;
import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.SeatStatus;
import com.apextick.booking.web.ConflictException;
import com.apextick.booking.web.ErrorCodes;
import com.apextick.booking.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Takes a seat off sale, and puts it back. Layouts are create-only, so a seat found broken, or
 * needed for the press or for a wheelchair space, used to go on selling with nothing to stop it.
 */
@Service
public class SeatBlockService {

    private static final Logger log = LoggerFactory.getLogger(SeatBlockService.class);

    private final SeatRepository seats;
    private final DomainEventPublisher domainEvents;
    private final RealtimePublisher realtime;

    public SeatBlockService(SeatRepository seats, DomainEventPublisher domainEvents, RealtimePublisher realtime) {
        this.seats = seats;
        this.domainEvents = domainEvents;
        this.realtime = realtime;
    }

    /** Only an available seat is blocked: a held or booked one belongs to somebody, who has to be dealt with first. */
    @Transactional
    public Seat block(Long seatId, String adminSub) {
        Long eventId = eventOf(seatId);
        if (seats.blockSeat(seatId) == 0) {
            throw new ConflictException(ErrorCodes.SEAT_UNAVAILABLE, "Only an available seat can be blocked",
                    Map.of("seatStatus", statusOf(seatId)));
        }
        announce(eventId, seatId, SeatStatus.BLOCKED, EventTypes.SEAT_BLOCKED, adminSub);
        log.info("Admin {} blocked seat {} (event {})", adminSub, seatId, eventId);
        return seats.findById(seatId).orElseThrow();
    }

    @Transactional
    public Seat unblock(Long seatId, String adminSub) {
        Long eventId = eventOf(seatId);
        if (seats.unblockSeat(seatId) == 0) {
            throw new ConflictException(ErrorCodes.SEAT_NOT_BLOCKED, "This seat is not blocked",
                    Map.of("seatStatus", statusOf(seatId)));
        }
        announce(eventId, seatId, SeatStatus.AVAILABLE, EventTypes.SEAT_UNBLOCKED, adminSub);
        log.info("Admin {} put seat {} (event {}) back on sale", adminSub, seatId, eventId);
        return seats.findById(seatId).orElseThrow();
    }

    private Long eventOf(Long seatId) {
        return seats.findById(seatId).map(Seat::getEventId).orElseThrow(() -> new NotFoundException("Seat", seatId));
    }

    private String statusOf(Long seatId) {
        return seats.findById(seatId).map(s -> s.getStatus().name()).orElse("UNKNOWN");
    }

    /** The outbox keeps who did it; every open seat map shows it at once. */
    private void announce(Long eventId, Long seatId, SeatStatus status, String type, String adminSub) {
        domainEvents.publish(type, "seat", String.valueOf(seatId), new SeatBlockPayload(seatId, eventId, adminSub));
        List<SeatStatusChange> changes = List.of(new SeatStatusChange(seatId, status.name(), null));
        AfterCommit.run(() -> realtime.seatStatusChanged(eventId, changes));
    }
}
