package com.apextick.booking.catalog.admin;

import com.apextick.booking.catalog.Event;
import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.catalog.EventStatus;
import com.apextick.booking.hold.SeatHoldKeys;
import com.apextick.booking.order.Order;
import com.apextick.booking.order.OrderRepository;
import com.apextick.booking.order.OrderService;
import com.apextick.booking.order.OrderStatus;
import com.apextick.booking.outbox.AfterCommit;
import com.apextick.booking.outbox.DomainEventPublisher;
import com.apextick.booking.outbox.EventTypes;
import com.apextick.booking.outbox.payload.EventCancelledPayload;
import com.apextick.booking.outbox.payload.SeatReleasedPayload;
import com.apextick.booking.payment.RefundService;
import com.apextick.booking.realtime.RealtimePublisher;
import com.apextick.booking.realtime.dto.SeatStatusChange;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.SeatStatus;
import com.apextick.booking.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Calls an event off. Cancelling used to flip a badge and nothing else: its tickets went on
 * opening gates, its unpaid orders stayed payable, nobody was told and nobody was refunded. It now
 * unwinds every sale in one transaction -- paid orders refunded, unpaid ones cancelled, holds let
 * go, every buyer told about their own order -- and only then asks the provider for the money.
 */
@Service
public class EventCancellationService {

    private static final Logger log = LoggerFactory.getLogger(EventCancellationService.class);

    /**
     * How many refunds the cancelling request asks for before it answers. The rest are owed and
     * due already, so the reconciler asks for them on its next pass: calling off a sold-out
     * stadium must not hold one request open across thousands of provider calls.
     */
    static final int REFUNDS_ASKED_AT_ONCE = 50;

    private final EventRepository events;
    private final OrderRepository orders;
    private final OrderService orderService;
    private final RefundService refunds;
    private final SeatRepository seats;
    private final SeatHoldKeys holdKeys;
    private final RealtimePublisher realtime;
    private final DomainEventPublisher domainEvents;
    private final TransactionTemplate tx;

    public EventCancellationService(EventRepository events, OrderRepository orders, OrderService orderService,
                                    RefundService refunds, SeatRepository seats, SeatHoldKeys holdKeys,
                                    RealtimePublisher realtime, DomainEventPublisher domainEvents,
                                    PlatformTransactionManager txManager) {
        this.events = events;
        this.orders = orders;
        this.orderService = orderService;
        this.refunds = refunds;
        this.seats = seats;
        this.holdKeys = holdKeys;
        this.realtime = realtime;
        this.domainEvents = domainEvents;
        this.tx = new TransactionTemplate(txManager);
    }

    /** What a cancellation undid. */
    public record Outcome(int paidOrdersRefunded, int unpaidOrdersCancelled, int holdsReleased, int refundsStillOwed) {
    }

    private record Unwound(List<UUID> owedPayments, int paidOrders, int unpaidOrders, int holds) {
    }

    public Outcome cancel(Long eventId, String reason, CurrentUser admin) {
        Unwound unwound = tx.execute(s -> unwind(eventId, reason, admin));
        List<UUID> owed = unwound.owedPayments();
        int stillOwed = Math.max(0, owed.size() - REFUNDS_ASKED_AT_ONCE);
        for (UUID paymentId : owed.subList(0, Math.min(owed.size(), REFUNDS_ASKED_AT_ONCE))) {
            try {
                if (!refunds.retry(paymentId)) {
                    stillOwed++;
                }
            } catch (RuntimeException e) {
                // owed and due either way, so the reconciler picks it up
                log.error("Asking for the refund of payment {} after cancelling event {} failed", paymentId, eventId, e);
                stillOwed++;
            }
        }
        if (unwound.paidOrders() + unwound.unpaidOrders() + unwound.holds() > 0) {
            log.info("Event {} cancelled by {}: {} paid orders refunded ({} refunds still owed), "
                            + "{} unpaid orders cancelled, {} holds released", eventId, admin.sub(),
                    unwound.paidOrders(), stillOwed, unwound.unpaidOrders(), unwound.holds());
        }
        return new Outcome(unwound.paidOrders(), unwound.unpaidOrders(), unwound.holds(), stillOwed);
    }

    private Unwound unwind(Long eventId, String reason, CurrentUser admin) {
        // Locked first, so two cancellations of one event take turns and the second finds it done.
        Event event = events.findByIdForUpdate(eventId).orElseThrow(() -> new NotFoundException("Event", eventId));
        if (event.getStatus() == EventStatus.CANCELLED) {
            return new Unwound(List.of(), 0, 0, 0);
        }
        Instant now = Instant.now();
        // The status goes first: a hold, an order or a payment arriving once this commits finds the sale closed.
        event.setStatus(EventStatus.CANCELLED);
        event.setCancelledAt(now);
        event.setCancelReason(reason);
        event.setUpdatedAt(now);

        List<UUID> owed = new ArrayList<>();
        int paid = 0;
        for (UUID orderId : orders.findIdsByEventIdAndStatus(eventId, OrderStatus.PAID)) {
            RefundService.CancelledSale voided = refunds.voidForCancelledEvent(orderId, admin.sub());
            if (voided.voided()) {
                paid++;
                if (voided.owedPaymentId() != null) {
                    owed.add(voided.owedPaymentId());
                }
                tell(event, orderId, reason, true);
            }
        }
        int unpaid = 0;
        for (UUID orderId : orders.findIdsByEventIdAndStatus(eventId, OrderStatus.PENDING_PAYMENT)) {
            if (orderService.cancelForCancelledEvent(orderId)) {
                unpaid++;
                tell(event, orderId, reason, false);
            }
        }

        // what is still held has no order behind it: somebody mid-selection
        List<Long> released = seats.releaseAllHeld(eventId);
        for (Long seatId : released) {
            domainEvents.publish(EventTypes.SEAT_RELEASED, "seat", String.valueOf(seatId),
                    new SeatReleasedPayload(seatId, eventId, "EVENT_CANCELLED"));
        }
        if (!released.isEmpty()) {
            List<SeatStatusChange> changes = released.stream()
                    .map(id -> new SeatStatusChange(id, SeatStatus.AVAILABLE.name(), null)).toList();
            AfterCommit.run(() -> {
                holdKeys.drop(released);
                realtime.seatStatusChanged(eventId, changes);
            });
        }
        return new Unwound(owed, paid, unpaid, released.size());
    }

    /** Tells one buyer about their own order: what happened to the event, and whether money is coming back. */
    private void tell(Event event, UUID orderId, String reason, boolean refundDue) {
        Order order = orders.findById(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
        domainEvents.publish(EventTypes.EVENT_CANCELLED, "order", orderId.toString(),
                new EventCancelledPayload(event.getId(), event.getName(), event.getStartsAt(), event.getTimeZone(),
                        event.getVenue(), reason, orderId.toString(), order.getOrderNumber(), order.getUserEmail(),
                        order.getUserName(), order.getTotal(), order.getCurrency(), refundDue));
    }
}
