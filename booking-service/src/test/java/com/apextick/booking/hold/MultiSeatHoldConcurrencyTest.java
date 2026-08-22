package com.apextick.booking.hold;

import com.apextick.booking.catalog.Event;
import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.catalog.PriceTier;
import com.apextick.booking.catalog.PriceTierRepository;
import com.apextick.booking.catalog.Section;
import com.apextick.booking.catalog.SectionRepository;
import com.apextick.booking.catalog.Sport;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.SeatStatus;
import com.apextick.booking.seat.SeatUnavailableException;
import com.apextick.booking.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class MultiSeatHoldConcurrencyTest {

    @Autowired HoldService holdService;
    @Autowired SeatRepository seatRepository;
    @Autowired EventRepository eventRepository;
    @Autowired PriceTierRepository priceTierRepository;
    @Autowired SectionRepository sectionRepository;

    Long eventId;
    List<Long> seatIds;

    @BeforeEach
    void seedThreeSeats() {
        Event event = new Event();
        event.setName("Multi Hold");
        event.setVenue("Test Arena");
        event.setStartsAt(Instant.now().plusSeconds(86_400));
        event.setSlug("multi-" + System.nanoTime());
        event.setSport(Sport.CRICKET);
        event = eventRepository.save(event);
        eventId = event.getId();

        PriceTier tier = new PriceTier();
        tier.setEvent(event);
        tier.setCode("standard");
        tier.setName("Standard");
        tier.setPrice(new BigDecimal("100.00"));
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
        section.setSeatsPerRow(3);
        section.setSortOrder(0);
        section = sectionRepository.save(section);

        seatIds = new java.util.ArrayList<>();
        for (int c = 0; c < 3; c++) {
            Seat seat = new Seat();
            seat.setEventId(eventId);
            seat.setSection(section);
            seat.setSeatNumber("A" + (c + 1));
            seat.setRowIdx(0);
            seat.setColIdx(c);
            seatIds.add(seatRepository.save(seat).getId());
        }
    }

    @Test
    void exactly_one_contender_wins_all_three_seats() throws InterruptedException {
        int contenders = 60;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(contenders);
        AtomicInteger wins = new AtomicInteger();
        AtomicInteger rejects = new AtomicInteger();

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < contenders; i++) {
                CurrentUser user = new CurrentUser("user-" + i, "user-" + i, "u@x", "U", Set.of("user"));
                pool.submit(() -> {
                    try {
                        start.await();
                        holdService.hold(String.valueOf(eventId), seatIds, user);
                        wins.incrementAndGet();
                    } catch (SeatUnavailableException e) {
                        rejects.incrementAndGet();
                    } catch (Exception e) {
                        // optimistic-lock / serialization losers also count as rejections
                        rejects.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(wins.get()).isEqualTo(1);
        assertThat(rejects.get()).isEqualTo(contenders - 1);
        // all three seats HELD by a single user
        List<Seat> after = seatRepository.findByIdsWithLayout(seatIds);
        assertThat(after).allMatch(s -> s.getStatus() == SeatStatus.HELD);
        assertThat(after.stream().map(Seat::getHeldBy).distinct().toList()).hasSize(1);
    }
}
