package com.apextick.booking.ticket;

import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.hold.HoldService;
import com.apextick.booking.order.OrderService;
import com.apextick.booking.order.dto.CreateOrderRequest;
import com.apextick.booking.payment.PaymentService;
import com.apextick.booking.payment.dto.PayRequest;
import com.apextick.booking.payment.model.PaymentCard;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.SeatStatus;
import com.apextick.booking.support.IntegrationTest;
import com.apextick.booking.ticket.gate.GateService;
import com.apextick.booking.web.ConflictException;
import com.apextick.booking.web.ErrorCodes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One QR code at several turnstiles at once: a screenshot shared with a friend, a ticket held up to
 * two gates. Every scan reads the ticket as unused in the same instant, so only the database can
 * decide which of them admits anyone.
 */
@IntegrationTest
class TicketAdmissionConcurrencyTest {

    private static final String SLUG = "south-africa-new-zealand-super-8";

    @Autowired GateService gate;
    @Autowired TicketRepository ticketRepository;
    @Autowired HoldService holdService;
    @Autowired OrderService orderService;
    @Autowired PaymentService paymentService;
    @Autowired EventRepository eventRepository;
    @Autowired SeatRepository seatRepository;
    @Autowired JdbcClient jdbc;

    @Test
    void scans_racing_on_one_ticket_admit_it_once() throws InterruptedException {
        Long eventId = eventRepository.findBySlug(SLUG).orElseThrow().getId();
        List<Long> seatIds = seatRepository.findAllForEventWithLayout(eventId).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE).map(Seat::getId).limit(1).toList();
        CurrentUser buyer = new CurrentUser("gate-race-buyer", "gateracebuyer", "gaterace@apextick.local",
                "Gate Race Buyer", Set.of("user"));
        holdService.hold(SLUG, seatIds, buyer);
        UUID orderId = UUID.fromString(orderService.create(new CreateOrderRequest(eventId, seatIds),
                "gate-race-order-" + System.nanoTime(), buyer).id());
        paymentService.pay(orderId,
                new PayRequest(new PaymentCard("4242424242424242", 12, 2030, "123", "G"), null, null, null),
                "gate-race-pay-" + System.nanoTime(), buyer);
        Ticket ticket = ticketRepository.findByOrderId(orderId).getFirst();

        CurrentUser steward = new CurrentUser("gate-race-steward", "steward", "steward@apextick.local",
                "Gate Steward", Set.of("user", "scanner"));
        int turnstiles = 12;
        AtomicInteger admitted = new AtomicInteger();
        AtomicInteger turnedAway = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(turnstiles);
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < turnstiles; i++) {
                String turnstile = "Turnstile " + i;
                pool.submit(() -> {
                    try {
                        start.await();
                        gate.scan(ticket.getQrToken(), eventId, turnstile, steward);
                        admitted.incrementAndGet();
                    } catch (ConflictException e) {
                        if (ErrorCodes.TICKET_ALREADY_USED.equals(e.getCode())) {
                            turnedAway.incrementAndGet();
                        }
                    } catch (Exception ignored) {
                        // anything else is neither an admission nor a correct refusal, so the counts catch it
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(admitted).hasValue(1);
        assertThat(turnedAway).hasValue(turnstiles - 1);
        // and the record agrees: one admission, every other turnstile on file as turned away
        Map<String, Long> recorded = jdbc.sql("SELECT outcome, count(*) AS n FROM ticket_scans WHERE ticket_id = :id GROUP BY outcome")
                .param("id", ticket.getId())
                .query((rs, row) -> Map.entry(rs.getString("outcome"), rs.getLong("n")))
                .list().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        assertThat(recorded).containsOnly(Map.entry("ADMITTED", 1L), Map.entry("ALREADY_USED", (long) turnstiles - 1));
    }
}
