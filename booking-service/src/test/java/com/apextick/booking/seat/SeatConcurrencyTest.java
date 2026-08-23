package com.apextick.booking.seat;

import com.apextick.booking.catalog.Event;
import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.catalog.PriceTier;
import com.apextick.booking.catalog.PriceTierRepository;
import com.apextick.booking.catalog.Section;
import com.apextick.booking.catalog.SectionRepository;
import com.apextick.booking.catalog.Sport;
import com.apextick.booking.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class SeatConcurrencyTest {

    @Autowired SeatService seatService;
    @Autowired SeatRepository seatRepository;
    @Autowired EventRepository eventRepository;
    @Autowired PriceTierRepository priceTierRepository;
    @Autowired SectionRepository sectionRepository;

    Long seatId;

    @BeforeEach
    void seedOneAvailableSeat() {
        Event event = new Event();
        event.setName("Cup Final");
        event.setVenue("R. Premadasa Stadium");
        event.setStartsAt(Instant.now().plusSeconds(86_400));
        event.setSlug("concurrency-" + System.nanoTime());
        event.setSport(Sport.CRICKET);
        event = eventRepository.save(event);

        PriceTier tier = new PriceTier();
        tier.setEvent(event);
        tier.setCode("standard");
        tier.setName("Standard");
        tier.setPrice(new BigDecimal("1000.00"));
        tier.setPerks(List.of());
        tier.setSortOrder(0);
        tier = priceTierRepository.save(tier);

        Section section = new Section();
        section.setEvent(event);
        section.setCode("main");
        section.setName("Main Stand");
        section.setTier(tier);
        section.setSide("n");
        section.setRows(1);
        section.setSeatsPerRow(1);
        section.setSortOrder(0);
        section = sectionRepository.save(section);

        Seat seat = new Seat();
        seat.setEventId(event.getId());
        seat.setSection(section);
        seat.setSeatNumber("A12");
        seat.setRowIdx(0);
        seat.setColIdx(0);
        seat = seatRepository.save(seat);

        seatId = seat.getId();
    }

    @Test
    void only_one_of_many_concurrent_holds_wins() throws InterruptedException {
        int contenders = 200;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(contenders);
        AtomicInteger wins = new AtomicInteger();
        AtomicInteger rejections = new AtomicInteger();

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < contenders; i++) {
                String user = "user-" + i;
                pool.submit(() -> {
                    try {
                        startGate.await();
                        seatService.holdSeat(seatId, user);
                        wins.incrementAndGet();
                    } catch (SeatUnavailableException e) {
                        rejections.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneGate.countDown();
                    }
                });
            }

            startGate.countDown();
            assertThat(doneGate.await(20, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(wins.get()).isEqualTo(1);
        assertThat(rejections.get()).isEqualTo(contenders - 1);

        Seat finalSeat = seatRepository.findById(seatId).orElseThrow();
        assertThat(finalSeat.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(finalSeat.getHeldBy()).isNotNull();
    }
}
