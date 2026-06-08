package com.apextick.booking.seat;

import com.apextick.booking.event.Event;
import com.apextick.booking.event.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class SeatConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired SeatService seatService;
    @Autowired SeatRepository seatRepository;
    @Autowired EventRepository eventRepository;

    Long seatId;

    @BeforeEach
    void seedOneAvailableSeat() {
        seatRepository.deleteAll();
        eventRepository.deleteAll();

        Event event = new Event();
        event.setName("Cup Final");
        event.setVenue("R. Premadasa Stadium");
        event.setStartsAt(Instant.now().plusSeconds(86_400));
        event = eventRepository.save(event);

        Seat seat = new Seat();
        seat.setEventId(event.getId());
        seat.setSeatNumber("A12");
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
                        startGate.await();                  // line up...
                        seatService.holdSeat(seatId, user); // ...then all fire at once
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

            startGate.countDown();                          // release the stampede
            assertThat(doneGate.await(20, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(wins.get()).isEqualTo(1);                    // exactly one winner
        assertThat(rejections.get()).isEqualTo(contenders - 1); // everyone else rejected

        Seat finalSeat = seatRepository.findById(seatId).orElseThrow();
        assertThat(finalSeat.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(finalSeat.getHeldBy()).isNotNull();
    }
}