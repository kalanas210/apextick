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

    @Test
    void sweep_releases_seats_whose_hold_has_elapsed() {
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
        seat.setHeldUntil(Instant.now().minus(1, ChronoUnit.HOURS)); // already elapsed
        Long seatId = seatRepository.save(seat).getId();

        int released = sweeper.sweep();
        assertThat(released).isGreaterThanOrEqualTo(1);

        Seat after = seatRepository.findById(seatId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(after.getHeldBy()).isNull();
    }
}
