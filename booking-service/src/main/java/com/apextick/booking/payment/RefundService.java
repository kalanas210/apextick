package com.apextick.booking.payment;

import com.apextick.booking.order.Order;
import com.apextick.booking.order.OrderRepository;
import com.apextick.booking.order.OrderService;
import com.apextick.booking.order.OrderStatus;
import com.apextick.booking.outbox.DomainEventPublisher;
import com.apextick.booking.outbox.EventTypes;
import com.apextick.booking.outbox.payload.PaymentRefundedPayload;
import com.apextick.booking.payment.model.RefundResult;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.ticket.TicketRepository;
import com.apextick.booking.ticket.TicketStatus;
import com.apextick.booking.web.ConflictException;
import com.apextick.booking.web.ErrorCodes;
import com.apextick.booking.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Money going back out. Every refund is asked for here, under the one key a charge's refund can
 * have, and recorded with the provider's answer: accepted, it is announced to the customer;
 * refused, the charge stays owed and is asked for again until the provider takes it.
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    /** As much of a provider's refusal as the payments table keeps. */
    private static final int ERROR_LIMIT = 512;

    private static final String DASHBOARD_REFUND = "Refunded in the payment provider's dashboard";

    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final OrderService orderService;
    private final TicketRepository tickets;
    private final PaymentGatewayRegistry registry;
    private final DomainEventPublisher domainEvents;
    private final TransactionTemplate tx;

    public RefundService(PaymentRepository payments, OrderRepository orders, OrderService orderService,
                         TicketRepository tickets, PaymentGatewayRegistry registry,
                         DomainEventPublisher domainEvents, PlatformTransactionManager txManager) {
        this.payments = payments;
        this.orders = orders;
        this.orderService = orderService;
        this.tickets = tickets;
        this.registry = registry;
        this.domainEvents = domainEvents;
        this.tx = new TransactionTemplate(txManager);
    }

    /** The idempotency key of the one refund a charge can have. */
    static String refundKey(Payment p) {
        return "refund:" + p.getId();
    }

    /**
     * Refunds a paid order in full, for the box office. The decision commits before any money
     * moves: the order is marked refunded, its tickets stop opening gates, its seats go back on
     * sale and its charge is recorded as owed. Only then is the provider asked, so a refusal leaves
     * a charge still owed -- never an order half refunded -- and it is asked for again.
     *
     * @return the payment whose charge is being refunded
     */
    public UUID refundOrder(UUID orderId, String reason, CurrentUser admin) {
        UUID paymentId = tx.execute(s -> decide(orderId, reason, admin));
        attempt(paymentId);
        return paymentId;
    }

    /**
     * Asks the provider again for a refund still owed. Safe to call at any time from anywhere: the
     * key makes a repeat the same refund, and a charge settled in the meantime is left as it is.
     *
     * @return whether the charge is now refunded
     */
    public boolean retry(UUID paymentId) {
        return attempt(paymentId);
    }

    /**
     * Refunds a charge at once, inside the caller's transaction, and records the answer: for the
     * paths that find out mid-settlement that a charge must go back. Callers hold its lock.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void refundNow(Payment p, PaymentGateway gateway, BigDecimal amount, String currency,
                          String reason, String message) {
        owe(p, amount, currency, reason, message);
        record(p, gateway.refund(p.getProviderRef(), amount, currency, refundKey(p)));
    }

    /**
     * The provider reports a charge refunded in full. Usually that is the refund asked for here,
     * reported back, and it changes nothing; a refund still owed has gone through after all. A
     * refund nobody here asked for was made in the provider's own dashboard, so the order it paid
     * for is refunded the way the box office would have refunded it. Callers hold the payment's lock.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordProviderRefund(Payment p) {
        switch (p.getStatus()) {
            case REFUND_REQUIRED -> refunded(p, p.getRefundRef(), Instant.now());
            case SUCCEEDED -> {
                UUID orderId = p.getOrder().getId();
                Order order = orders.findByIdForUpdate(orderId)
                        .orElseThrow(() -> new NotFoundException("Order", orderId));
                if (order.getStatus() == OrderStatus.PAID) {
                    orderService.markRefunded(order, null, DASHBOARD_REFUND);
                }
                owe(p, p.getAmount(), p.getCurrency(), "provider_refund", DASHBOARD_REFUND);
                refunded(p, null, Instant.now());
            }
            default -> log.info("{} reported payment {} refunded while it was {}; nothing to change",
                    p.getProvider(), p.getId(), p.getStatus());
        }
    }

    private UUID decide(UUID orderId, String reason, CurrentUser admin) {
        // The charge's row before the order's, the order every path that settles a charge takes them in.
        Payment paying = payments.findByOrderIdAndStatus(orderId, PaymentStatus.SUCCEEDED).stream()
                .findFirst()
                .flatMap(p -> payments.findByIdForUpdate(p.getId()))
                .orElse(null);
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
        if (order.getStatus() != OrderStatus.PAID) {
            throw new ConflictException(ErrorCodes.ORDER_NOT_REFUNDABLE, "Only a paid order can be refunded",
                    Map.of("orderStatus", order.getStatus().name()));
        }
        if (paying == null || paying.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new ConflictException(ErrorCodes.ORDER_NOT_REFUNDABLE, "This order has no settled payment to refund",
                    Map.of("orderStatus", order.getStatus().name()));
        }
        // Somebody has walked in on one of these tickets, so the money is not simply owed back. If
        // letting them in was the mistake, the admission is undone at the gate first.
        List<UUID> used = tickets.findIdsByOrderIdAndStatus(orderId, TicketStatus.USED);
        if (!used.isEmpty()) {
            throw ticketsUsed(used);
        }
        long unused = tickets.countByOrderIdAndStatus(orderId, TicketStatus.ISSUED);
        if (orderService.markRefunded(order, admin.sub(), reason) != unused) {
            // a gate admitted one of them between the check above and the void: undo all of it
            throw ticketsUsed(tickets.findIdsByOrderIdAndStatus(orderId, TicketStatus.USED));
        }
        owe(paying, paying.getAmount(), paying.getCurrency(), "box_office_refund", "Refunded by the box office");
        return paying.getId();
    }

    private boolean attempt(UUID paymentId) {
        Payment owed = payments.findById(paymentId).orElseThrow(() -> new NotFoundException("Payment", paymentId));
        if (owed.getStatus() != PaymentStatus.REFUND_REQUIRED) {
            return owed.getStatus() == PaymentStatus.REFUNDED;
        }
        RefundResult answer;
        try {
            answer = registry.get(owed.getProvider())
                    .refund(owed.getProviderRef(), owedAmount(owed), owedCurrency(owed), refundKey(owed));
        } catch (RuntimeException e) {
            // a provider that could not even be asked -- no key configured, the network down -- has refused
            log.error("The refund of payment {} could not be requested", paymentId, e);
            answer = new RefundResult(false, null, e.getMessage());
        }
        RefundResult result = answer;
        return Boolean.TRUE.equals(tx.execute(s -> {
            Payment p = payments.findByIdForUpdate(paymentId).orElseThrow();
            // a webhook, another instance or another click may have settled it while the provider was asked
            if (p.getStatus() == PaymentStatus.REFUND_REQUIRED) {
                record(p, result);
            }
            return p.getStatus() == PaymentStatus.REFUNDED;
        }));
    }

    /** Records that a charge is owed back, before the provider is asked for it. */
    private static void owe(Payment p, BigDecimal amount, String currency, String reason, String message) {
        Instant now = Instant.now();
        p.setStatus(PaymentStatus.REFUND_REQUIRED);
        p.setFailureCode(reason);
        p.setFailureMessage(message);
        p.setRefundAmount(amount);
        p.setRefundCurrency(currency);
        // counted as asked, so the reconciler leaves it to the request that is about to ask
        p.setRefundLastAttemptAt(now);
        p.setUpdatedAt(now);
    }

    private void record(Payment p, RefundResult result) {
        Instant now = Instant.now();
        p.setRefundAttempts(p.getRefundAttempts() + 1);
        p.setRefundLastAttemptAt(now);
        p.setUpdatedAt(now);
        if (!result.accepted()) {
            p.setStatus(PaymentStatus.REFUND_REQUIRED);
            p.setRefundError(clip(result.rawJson()));
            log.error("{} refused to refund payment {} ({} {}, {}, attempt {}): {}", p.getProvider(), p.getId(),
                    owedAmount(p), owedCurrency(p), p.getFailureCode(), p.getRefundAttempts(), result.rawJson());
            return;
        }
        refunded(p, result.providerRef(), now);
    }

    /** The refund went through: record it, and tell the customer their money is on its way. */
    private void refunded(Payment p, String refundRef, Instant now) {
        p.setStatus(PaymentStatus.REFUNDED);
        p.setRefundRef(refundRef);
        p.setRefundedAt(now);
        p.setRefundError(null);
        p.setUpdatedAt(now);
        Order order = p.getOrder();
        domainEvents.publish(EventTypes.PAYMENT_REFUNDED, "payment", p.getId().toString(),
                new PaymentRefundedPayload(p.getId().toString(), order.getId().toString(), order.getOrderNumber(),
                        order.getUserSub(), order.getUserEmail(), order.getUserName(), order.getEvent().getId(),
                        order.getEvent().getName(), owedAmount(p), owedCurrency(p), p.getFailureCode(),
                        refundRef, now));
    }

    /** What is owed back; a charge refunded before this was recorded owes all of itself. */
    private static BigDecimal owedAmount(Payment p) {
        return p.getRefundAmount() != null ? p.getRefundAmount() : p.getAmount();
    }

    private static String owedCurrency(Payment p) {
        return p.getRefundCurrency() != null ? p.getRefundCurrency() : p.getCurrency();
    }

    private static String clip(String error) {
        return error == null || error.length() <= ERROR_LIMIT ? error : error.substring(0, ERROR_LIMIT);
    }

    private static ConflictException ticketsUsed(List<UUID> ticketIds) {
        return new ConflictException(ErrorCodes.ORDER_TICKETS_USED,
                "A ticket on this order has already been used at the gate",
                Map.of("ticketIds", ticketIds.stream().map(UUID::toString).toList()));
    }
}
