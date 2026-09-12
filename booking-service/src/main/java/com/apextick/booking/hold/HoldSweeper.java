package com.apextick.booking.hold;

import com.apextick.booking.config.AppProperties;
import com.apextick.booking.outbox.AfterCommit;
import com.apextick.booking.outbox.DomainEventPublisher;
import com.apextick.booking.outbox.EventTypes;
import com.apextick.booking.outbox.payload.SeatReleasedPayload;
import com.apextick.booking.realtime.RealtimePublisher;
import com.apextick.booking.realtime.dto.SeatStatusChange;
import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.SeatStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Fallback reconciliation: releases HELD seats whose TTL elapsed but whose Redis expiry was missed. */
@Component
public class HoldSweeper {

    private static final Logger log = LoggerFactory.getLogger(HoldSweeper.class);

    private final SeatRepository seats;
    private final DomainEventPublisher events;
    private final RealtimePublisher realtime;
    private final AppProperties props;

    public HoldSweeper(SeatRepository seats, DomainEventPublisher events,
                       RealtimePublisher realtime, AppProperties props) {
        this.seats = seats;
        this.events = events;
        this.realtime = realtime;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${app.hold.sweeper-interval}")
    @Transactional
    public int sweep() {
        Instant cutoff = Instant.now().minus(props.hold().expiryTolerance());
        List<Seat> expired = seats.findExpiredHolds(cutoff, PageRequest.of(0, props.hold().sweeperBatch()));
        if (expired.isEmpty()) {
            return 0;
        }
        List<Long> ids = expired.stream().map(Seat::getId).toList();
        // The UPDATE reports which rows it actually moved. A seat selected a moment ago may
        // have been booked or re-held since, and announcing that one AVAILABLE would show every
        // viewer a seat that is not free -- and tell the notification service it was released.
        Set<Long> released = Set.copyOf(seats.releaseSeats(ids));
        List<Seat> freed = expired.stream().filter(s -> released.contains(s.getId())).toList();
        for (Seat s : freed) {
            events.publish(EventTypes.SEAT_RELEASED, "seat", String.valueOf(s.getId()),
                    new SeatReleasedPayload(s.getId(), s.getEventId(), "EXPIRED"));
        }
        Map<Long, List<SeatStatusChange>> byEvent = freed.stream().collect(Collectors.groupingBy(
                Seat::getEventId,
                Collectors.mapping(s -> new SeatStatusChange(s.getId(), SeatStatus.AVAILABLE.name(), null),
                        Collectors.toList())));
        AfterCommit.run(() -> byEvent.forEach(realtime::seatStatusChanged));
        log.info("Hold sweeper released {} of {} expired seats", freed.size(), ids.size());
        return freed.size();
    }
}
