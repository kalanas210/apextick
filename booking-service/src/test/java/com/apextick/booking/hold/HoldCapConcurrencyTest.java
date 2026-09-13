package com.apextick.booking.hold;

import com.apextick.booking.catalog.Event;
import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.catalog.PriceTier;
import com.apextick.booking.catalog.PriceTierRepository;
import com.apextick.booking.catalog.Section;
import com.apextick.booking.catalog.SectionRepository;
import com.apextick.booking.catalog.Sport;
import com.apextick.booking.config.AppProperties;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.SeatStatus;
import com.apextick.booking.support.IntegrationTest;
import com.apextick.booking.web.ErrorCodes;
import com.apextick.booking.web.UnprocessableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The per-user seat cap is the anti-hoarding control, so it has to hold under the one case
 * that actually beats a ceiling: the same account asking twice at once.
 *
 * <p>Counting the caller's seats and then holding more is a check-then-act, and under READ
 * COMMITTED neither request sees the other's uncommitted rows -- two tabs, a double-submit or
 * a load-test pool reusing a sub would each read zero held seats, each pass the test and each
 * commit a full allowance, walking off with twice the cap. Locking rows cannot close it either,
 * because an account that holds nothing yet has no rows to lock. {@code hold()} takes a
 * transaction-scoped advisory lock on (event, user) before counting instead; this pins that the
 * ceiling survives concurrent requests and that the friendly TOO_MANY_SEATS payload is still
 * what the loser gets.
 */
@IntegrationTest
class HoldCapConcurrencyTest {

    @Autowired HoldService holdService;
    @Autowired SeatRepository seatRepository;
    @Autowired EventRepository eventRepository;
    @Autowired PriceTierRepository priceTierRepository;
    @Autowired SectionRepository sectionRepository;
    @Autowired AppProperties props;

    private Long eventId;
    private List<Long> seatIds;

    /** A private event with three full allowances' worth of seats, so no seat is contended. */
    @BeforeEach
    void seedOneEventWithThreeAllowances() {
        int cap = props.hold().maxSeats();

        Event event = new Event();
        event.setName("Hold Cap");
        event.setVenue("Test Arena");
        event.setStartsAt(Instant.now().plusSeconds(86_400));
        event.setSlug("hold-cap-" + System.nanoTime());
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
        section.setSeatsPerRow(cap * 3);
        section.setSortOrder(0);
        section = sectionRepository.save(section);

        seatIds = new ArrayList<>();
        for (int c = 0; c < cap * 3; c++) {
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
    void one_account_asking_three_times_at_once_still_only_gets_the_cap() throws InterruptedException {
        int cap = props.hold().maxSeats();
        String sub = "cap-racer";
        CurrentUser racer = new CurrentUser(sub, "capracer", "cap@apextick.local", "Cap Racer", Set.of("user"));

        // three disjoint full-allowance selections: nothing here contends for a seat, only for
        // the caller's own allowance
        List<List<Long>> selections = List.of(
                seatIds.subList(0, cap),
                seatIds.subList(cap, cap * 2),
                seatIds.subList(cap * 2, cap * 3));

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(selections.size());
        AtomicInteger granted = new AtomicInteger();
        AtomicInteger capped = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (List<Long> selection : selections) {
                pool.submit(() -> {
                    try {
                        start.await();
                        holdService.hold(String.valueOf(eventId), selection, racer);
                        granted.incrementAndGet();
                    } catch (UnprocessableException e) {
                        if (ErrorCodes.TOO_MANY_SEATS.equals(e.getCode())) {
                            capped.incrementAndGet();
                            assertThat(e.getProperties()).containsEntry("maxSeats", cap);
                        } else {
                            other.incrementAndGet();
                        }
                    } catch (Exception e) {
                        other.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(granted.get()).isEqualTo(1);
        assertThat(capped.get()).isEqualTo(selections.size() - 1);
        assertThat(other.get()).isZero();

        assertThat(seatRepository.findMyHeldSeatIds(eventId, sub)).hasSize(cap);
        assertThat(seatRepository.findByIdsWithLayout(seatIds).stream()
                .filter(s -> s.getStatus() == SeatStatus.HELD)).hasSize(cap);
    }
}
