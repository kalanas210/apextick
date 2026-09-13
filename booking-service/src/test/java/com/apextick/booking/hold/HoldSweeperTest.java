package com.apextick.booking.hold;

import com.apextick.booking.catalog.Event;
import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.catalog.PriceTier;
import com.apextick.booking.catalog.PriceTierRepository;
import com.apextick.booking.catalog.Section;
import com.apextick.booking.catalog.SectionRepository;
import com.apextick.booking.catalog.Sport;
import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.SeatStatus;
import com.apextick.booking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class HoldSweeperTest {

    @Autowired HoldSweeper sweeper;
    @Autowired SeatRepository seatRepository;
    @Autowired EventRepository eventRepository;
    @Autowired PriceTierRepository priceTierRepository;
    @Autowired SectionRepository sectionRepository;

    /** A one-seat event of its own, so the sweeper's batch never crosses another test. */
    private Long seedHeldSeat(Instant heldUntil) {
        Event event = new Event();
        event.setName("Sweeper");
        event.setVenue("Test Arena");
        event.setStartsAt(Instant.now().plusSeconds(86_400));
        event.setSlug("sweeper-" + System.nanoTime());
        event.setSport(Sport.CRICKET);
        event = eventRepository.save(event);

        PriceTier tier = new PriceTier();
        tier.setEvent(event);
        tier.setCode("standard");
        tier.setName("Standard");
        tier.setPrice(new BigDecimal("10.00"));
        tier.setPerks(List.of());
        tier.setSortOrder(0);
        tier = priceTierRepository.save(tier);

        Section section = new Section();
        section.setEvent(event);
        section.setCode("main");
        section.setName("Main");
        section.setTier(tier);
        section.setSide("n");
        section.setRows(1);
        section.setSeatsPerRow(1);
        section.setSortOrder(0);
        section = sectionRepository.save(section);

        Seat seat = new Seat();
        seat.setEventId(event.getId());
        seat.setSection(section);
        seat.setSeatNumber("A1");
        seat.setRowIdx(0);
        seat.setColIdx(0);
        seat.setStatus(SeatStatus.HELD);
        seat.setHeldBy("ghost");
        seat.setHeldUntil(heldUntil);
        return seatRepository.save(seat).getId();
    }

    @Test
    void sweep_releases_seats_whose_hold_has_elapsed() {
        Long seatId = seedHeldSeat(Instant.now().minus(1, ChronoUnit.HOURS)); // already elapsed

        int released = sweeper.sweep();
        assertThat(released).isGreaterThanOrEqualTo(1);

        Seat after = seatRepository.findById(seatId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(after.getHeldBy()).isNull();
    }

    /**
     * The sweep is a SELECT and then an UPDATE, and the holder can re-post their selection
     * in between -- the idempotent re-hold pushes the deadline forward on a row the sweeper
     * has already decided to free. The UPDATE re-checks the deadline against the same cutoff
     * for exactly that reason; without it a live hold is released and announced AVAILABLE to
     * every open seat map moments after its owner was told it had been extended.
     */
    @Test
    void a_hold_refreshed_after_the_sweeper_picked_it_up_survives_the_sweep() {
        Instant cutoff = Instant.now().minusSeconds(5);
        Long seatId = seedHeldSeat(Instant.now().minus(1, ChronoUnit.HOURS));

        // the sweeper has selected this seat as expired...
        assertThat(seatRepository.findExpiredHolds(cutoff, PageRequest.of(0, 500))
                .stream().map(Seat::getId)).contains(seatId);

        // ...but the holder re-posts their selection before the UPDATE runs
        Seat refreshed = seatRepository.findById(seatId).orElseThrow();
        refreshed.setHeldUntil(Instant.now().plus(5, ChronoUnit.MINUTES));
        seatRepository.saveAndFlush(refreshed);

        assertThat(seatRepository.releaseSeats(List.of(seatId), cutoff)).isEmpty();

        Seat after = seatRepository.findById(seatId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(after.getHeldBy()).isEqualTo("ghost");
    }
}
