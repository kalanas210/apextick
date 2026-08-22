package com.apextick.booking.hold;

import com.apextick.booking.config.AppProperties;
import com.apextick.booking.outbox.DomainEventPublisher;
import com.apextick.booking.outbox.EventTypes;
import com.apextick.booking.outbox.payload.SeatReleasedPayload;
import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Fallback reconciliation: releases HELD seats whose TTL elapsed but whose Redis expiry was missed. */
@Component
public class HoldSweeper {

    private static final Logger log = LoggerFactory.getLogger(HoldSweeper.class);

    private final SeatRepository seats;
    private final DomainEventPublisher events;
    private final AppProperties props;

    public HoldSweeper(SeatRepository seats, DomainEventPublisher events, AppProperties props) {
        this.seats = seats;
        this.events = events;
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
        int released = seats.releaseSeats(ids);
        for (Seat s : expired) {
            events.publish(EventTypes.SEAT_RELEASED, "seat", String.valueOf(s.getId()),
                    new SeatReleasedPayload(s.getId(), s.getEventId(), "EXPIRED"));
        }
        log.info("Hold sweeper released {} expired seats", released);
        return released;
    }
}
