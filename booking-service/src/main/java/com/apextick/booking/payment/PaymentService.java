package com.apextick.booking.payment;

import com.apextick.booking.catalog.SalesWindow;
import com.apextick.booking.order.Order;
import com.apextick.booking.order.OrderRepository;
import com.apextick.booking.order.OrderService;
import com.apextick.booking.order.OrderStatus;
import com.apextick.booking.payment.dto.PayRequest;
import com.apextick.booking.payment.dto.PaymentResponse;
import com.apextick.booking.payment.model.CallbackRequest;
import com.apextick.booking.payment.model.Customer;
import com.apextick.booking.payment.model.PaymentContext;
import com.apextick.booking.payment.model.PaymentInitiation;
import com.apextick.booking.payment.model.PaymentResult;
import com.apextick.booking.payment.model.RefundResult;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.web.ConflictException;
import com.apextick.booking.web.ErrorCodes;
import com.apextick.booking.web.NotFoundException;
import com.apextick.booking.web.UnprocessableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    /**
     * How long an INITIATED payment may wait on its provider call before it counts as abandoned.
     * Longer than a call can take -- stripe-java gives up after a 30s connect and an 80s read --
     * so a payment still INITIATED after this lost its request to a crash or a timeout, and must
     * not block the order's next attempt.
     */
    static final Duration ABANDONED_AFTER = Duration.ofMinutes(2);

    /** A charge already settled one way or the other: hearing about it again changes nothing. */
    private static final Set<PaymentStatus> SETTLED =
            EnumSet.of(PaymentStatus.SUCCEEDED, PaymentStatus.REFUNDED, PaymentStatus.REFUND_REQUIRED);

    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final OrderService orderService;
    private final PaymentGatewayRegistry registry;
    private final PaymentWebhookEventRepository webhookEvents;
    private final TransactionTemplate tx;

    public PaymentService(PaymentRepository payments, OrderRepository orders, OrderService orderService,
                          PaymentGatewayRegistry registry, PaymentWebhookEventRepository webhookEvents,
                          PlatformTransactionManager txManager) {
        this.payments = payments;
        this.orders = orders;
        this.orderService = orderService;
        this.registry = registry;
        this.webhookEvents = webhookEvents;
        this.tx = new TransactionTemplate(txManager);
    }

    public PaymentResponse pay(UUID orderId, PayRequest request, String idempotencyKey, CurrentUser user) {
        // Required here rather than only at the controller: the key is what tells a resend from a
        // second charge, and a keyless attempt could never be recognised again.
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new UnprocessableException(ErrorCodes.IDEMPOTENCY_KEY_MISSING, "Idempotency-Key header is required");
        }
        PaymentGateway gateway = registry.defaultGateway();

        // Phase A (tx, order row locked): answer an attempt that already ran, or record an INITIATED payment.
        Attempt attempt = tx.execute(s -> initPayment(orderId, request, idempotencyKey, user, gateway));
        if (attempt.answer() != null) {
            return attempt.answer();
        }
        PaymentContext ctx = attempt.context();

        // Phase B (no tx): call the provider (may block on a network call for real gateways).
        PaymentInitiation init = gateway.initiate(ctx);

        // Phase C (tx): apply the result atomically with booking confirmation, or give the money back.
        try {
            return tx.execute(s -> applyResult(ctx.paymentId(), orderId, init, gateway));
        } catch (SeatsLostException e) {
            return tx.execute(s -> compensate(ctx.paymentId(), orderId, init, gateway));
        }
    }

    public List<PaymentResponse> listForOrder(UUID orderId, CurrentUser user) {
        return tx.execute(s -> {
            Order order = orderService.loadOwned(orderId, user);
            return payments.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                    .map(p -> responseOf(p, order)).toList();
        });
    }

    /**
     * Process a verified provider callback (the Stripe webhook). Idempotent: each external event is
     * recorded once via the {@code payment_webhook_events} unique key, so re-deliveries are ignored.
     * A charge its order can no longer take -- its seats lost, the order closed, or the order already
     * paid by another charge -- is refunded as the synchronous pay path would refund it, and the
     * delivery is acknowledged either way: refusing it only has the provider send it again, for days,
     * while the customer stays charged.
     */
    public void handleWebhook(PaymentProvider provider, CallbackRequest req) {
        PaymentGateway gateway = registry.get(provider);
        Optional<PaymentResult> maybe = gateway.verifyCallback(req); // throws on a bad signature
        if (maybe.isEmpty()) {
            return; // an event type we don't act on -> acknowledged, no state change
        }
        PaymentResult result = maybe.get();
        try {
            try {
                tx.executeWithoutResult(s -> applyWebhook(provider, result, gateway));
            } catch (SeatsLostException e) {
                tx.executeWithoutResult(s -> compensateWebhook(provider, result, gateway));
            }
        } catch (DataIntegrityViolationException e) {
            if (!webhookEvents.existsByProviderAndExternalEventId(provider.name(), result.externalEventId())) {
                throw e; // not a second delivery of this event, so let the provider retry it
            }
            log.debug("Duplicate {} webhook {} ignored", provider, result.externalEventId());
        }
    }

    private void applyWebhook(PaymentProvider provider, PaymentResult result, PaymentGateway gateway) {
        if (webhookEvents.existsByProviderAndExternalEventId(provider.name(), result.externalEventId())) {
            return; // a prior delivery already handled this event
        }
        Instant now = Instant.now();
        PaymentWebhookEvent event = newWebhookEvent(provider, result, now);
        webhookEvents.saveAndFlush(event); // surface the unique-key race as DataIntegrityViolation

        Payment p = payments.findByProviderAndProviderRefForUpdate(provider, result.providerRef()).orElse(null);
        if (p == null) {
            log.warn("{} webhook {} matched no payment (ref {})",
                    provider, result.externalEventId(), result.providerRef());
            event.setOutcome("unmatched");
            event.setProcessedAt(now);
            return;
        }

        switch (result.outcome()) {
            case SUCCEEDED -> {
                if (!SETTLED.contains(p.getStatus())) {
                    applyCard(p, result);
                    p.setStatus(PaymentStatus.SUCCEEDED);
                    p.setConfirmedAt(now);
                    p.setUpdatedAt(now);
                    p.setRawResult(result.rawJson());
                    settle(p, gateway); // may throw SeatsLostException
                }
            }
            case FAILED, DECLINED, CANCELLED -> {
                if (p.getStatus() == PaymentStatus.INITIATED || p.getStatus() == PaymentStatus.REQUIRES_ACTION) {
                    p.setStatus(PaymentStatus.FAILED);
                    p.setFailureCode(result.failureCode());
                    p.setUpdatedAt(now);
                }
            }
            default -> { /* PENDING / other: leave the payment as-is until a terminal event */ }
        }
        event.setOutcome(result.outcome() == PaymentOutcome.SUCCEEDED ? p.getStatus().name() : result.outcome().name());
        event.setProcessedAt(Instant.now());
    }

    private void compensateWebhook(PaymentProvider provider, PaymentResult result, PaymentGateway gateway) {
        // A fresh transaction after the seats-lost rollback. The delivery is recorded before any
        // money moves, so a redelivery racing this one stops at the unique key instead of refunding.
        if (webhookEvents.existsByProviderAndExternalEventId(provider.name(), result.externalEventId())) {
            return;
        }
        Instant now = Instant.now();
        PaymentWebhookEvent event = newWebhookEvent(provider, result, now);
        webhookEvents.saveAndFlush(event);

        Payment p = payments.findByProviderAndProviderRefForUpdate(provider, result.providerRef())
                .orElseThrow(() -> new NotFoundException("Payment", result.providerRef()));
        if (!SETTLED.contains(p.getStatus())) {
            applyCard(p, result);
            p.setRawResult(result.rawJson());
            refund(p, gateway, "seats_lost", "Seats were lost before the payment could be confirmed");
            orderService.compensateSeatsLost(p.getOrder().getId());
        }
        event.setOutcome(p.getStatus().name());
        event.setProcessedAt(Instant.now());
    }

    private static PaymentWebhookEvent newWebhookEvent(PaymentProvider provider, PaymentResult result, Instant now) {
        PaymentWebhookEvent event = new PaymentWebhookEvent();
        event.setProvider(provider.name());
        event.setExternalEventId(result.externalEventId());
        event.setPayload(result.rawJson());
        event.setReceivedAt(now);
        return event;
    }

    private static void applyCard(Payment p, PaymentResult result) {
        if (result.cardBrand() != null) {
            p.setCardBrand(result.cardBrand());
        }
        if (result.cardLast4() != null) {
            p.setCardLast4(result.cardLast4());
        }
    }

    /** What Phase A decided: the answer an attempt already has, or the context to make a new one. */
    private record Attempt(PaymentResponse answer, PaymentContext context) {
        static Attempt answered(PaymentResponse answer) {
            return new Attempt(answer, null);
        }

        static Attempt started(PaymentContext context) {
            return new Attempt(null, context);
        }
    }

    private Attempt initPayment(UUID orderId, PayRequest request, String idempotencyKey,
                                CurrentUser user, PaymentGateway gateway) {
        // Locked before anything is decided, so attempts on one order take turns. Without it two
        // of them could both pass every check below before either recorded a payment, and both
        // would charge.
        Order order = orderService.loadOwnedForUpdate(orderId, user);

        // The same key is the same attempt, so it gets the answer that attempt got: a resend after
        // a dropped connection must never become a second charge.
        Optional<Payment> sameAttempt = payments.findByOrderIdAndIdempotencyKey(orderId, idempotencyKey);
        if (sameAttempt.isPresent()) {
            if (sameAttempt.get().getStatus() == PaymentStatus.INITIATED) {
                throw new ConflictException(ErrorCodes.PAYMENT_IN_PROGRESS, "This payment is still being processed");
            }
            return Attempt.answered(responseOf(sameAttempt.get(), order));
        }
        if (order.getStatus() == OrderStatus.PAID) {
            return Attempt.answered(paidResponse(order));
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ConflictException(ErrorCodes.ORDER_NOT_PAYABLE, "Order is not payable");
        }
        // Last gate before money moves: an order created while the event was on sale must not
        // be payable after kickoff, after the sale ends, or once it is marked sold out.
        SalesWindow.assertOpen(order.getEvent());
        if (!gateway.supports(order.getCurrency())) {
            throw new UnprocessableException("CURRENCY_UNSUPPORTED",
                    gateway.provider() + " does not support " + order.getCurrency());
        }
        // A new key while another attempt still waits on its provider is a second charge in the
        // making: a second tab, or a double click the button did not swallow.
        Instant abandoned = Instant.now().minus(ABANDONED_AFTER);
        if (payments.findByOrderIdAndStatus(orderId, PaymentStatus.INITIATED).stream()
                .anyMatch(inFlight -> inFlight.getCreatedAt().isAfter(abandoned))) {
            throw new ConflictException(ErrorCodes.PAYMENT_IN_PROGRESS,
                    "Another payment for this order is still being processed");
        }
        Payment p = new Payment();
        p.setId(UUID.randomUUID());
        p.setOrder(order);
        p.setProvider(gateway.provider());
        p.setStatus(PaymentStatus.INITIATED);
        p.setAmount(order.getTotal());
        p.setCurrency(order.getCurrency());
        p.setIdempotencyKey(idempotencyKey);
        p.setCreatedAt(Instant.now());
        payments.saveAndFlush(p);

        return Attempt.started(new PaymentContext(p.getId(), order.getId(), order.getOrderNumber(), order.getTotal(),
                order.getCurrency(), new Customer(user.sub(), user.email(), user.name()), idempotencyKey,
                request == null ? null : request.returnUrl(), request == null ? null : request.cancelUrl(),
                request == null ? null : request.card(), request == null ? null : request.paymentMethodId()));
    }

    private PaymentResponse applyResult(UUID paymentId, UUID orderId, PaymentInitiation init, PaymentGateway gateway) {
        // Locked, so a webhook for this same charge landing mid-request either waits for this to
        // finish or makes this wait, and whichever goes second finds the charge already settled.
        Payment p = payments.findByIdForUpdate(paymentId).orElseThrow(() -> new NotFoundException("Payment", paymentId));
        if (p.getStatus() != PaymentStatus.INITIATED) {
            return responseOf(p, order(orderId));
        }
        Instant now = Instant.now();
        p.setCardBrand(init.cardBrand());
        p.setCardLast4(init.cardLast4());
        p.setProviderRef(init.providerRef());
        p.setClientSecret(init.clientSecret());
        p.setRawResult(init.rawJson());
        p.setUpdatedAt(now);
        switch (init.outcome()) {
            case SUCCEEDED -> {
                p.setStatus(PaymentStatus.SUCCEEDED);
                p.setConfirmedAt(now);
                settle(p, gateway); // may throw SeatsLostException -> rolls back this tx
            }
            case REQUIRES_ACTION -> p.setStatus(PaymentStatus.REQUIRES_ACTION);
            case REDIRECT -> p.setStatus(PaymentStatus.REDIRECTED);
            default -> {
                p.setStatus(PaymentStatus.FAILED);
                p.setFailureCode(init.failureCode());
                p.setFailureMessage(init.failureMessage());
            }
        }
        return responseOf(p, order(orderId));
    }

    private PaymentResponse compensate(UUID paymentId, UUID orderId, PaymentInitiation init, PaymentGateway gateway) {
        Payment p = payments.findByIdForUpdate(paymentId).orElseThrow(() -> new NotFoundException("Payment", paymentId));
        // the charge succeeded but seats were lost -> give the money back and cancel the order
        p.setProviderRef(init.providerRef());
        p.setCardBrand(init.cardBrand());
        p.setCardLast4(init.cardLast4());
        refund(p, gateway, "seats_lost", "Seats were lost before the payment could be confirmed");
        orderService.compensateSeatsLost(orderId);
        return responseOf(p, order(orderId));
    }

    /**
     * A charge went through: book the order it pays for, or give the money back when that order can
     * no longer take it. Throws {@link SeatsLostException}, rolling the booking back, when the order
     * is still open but its seats went first.
     */
    private void settle(Payment p, PaymentGateway gateway) {
        UUID orderId = p.getOrder().getId();
        switch (orderService.confirmPaid(orderId, p)) {
            case CONFIRMED -> { /* booked, ticketed and announced */ }
            case ALREADY_PAID -> {
                // another charge paid for these seats first, which makes this one a second payment
                if (payments.existsByOrderIdAndStatusAndIdNot(orderId, PaymentStatus.SUCCEEDED, p.getId())) {
                    refund(p, gateway, "duplicate_charge", "The order had already been paid by another payment");
                }
            }
            case ORDER_CLOSED -> refund(p, gateway, "order_closed",
                    "The order had expired or been cancelled before the payment went through");
        }
    }

    /**
     * Gives a charge back and records what the provider said. The refund's idempotency key comes
     * from the payment, so however many retries and redeliveries end up here, the charge is refunded
     * once.
     */
    private void refund(Payment p, PaymentGateway gateway, String reason, String message) {
        RefundResult refund = gateway.refund(p.getProviderRef(), p.getAmount(), p.getCurrency(), refundKey(p));
        Instant now = Instant.now();
        p.setFailureCode(reason);
        p.setFailureMessage(message);
        p.setUpdatedAt(now);
        if (refund.accepted()) {
            p.setStatus(PaymentStatus.REFUNDED);
            p.setRefundRef(refund.providerRef());
            p.setRefundedAt(now);
        } else {
            // the money still has to go back and nothing retries it yet, so say so where it is seen
            p.setStatus(PaymentStatus.REFUND_REQUIRED);
            log.error("{} refused to refund payment {} ({} {}, {}): {}", p.getProvider(), p.getId(),
                    p.getAmount(), p.getCurrency(), reason, refund.rawJson());
        }
    }

    /** The idempotency key of the one refund a payment can have. */
    static String refundKey(Payment p) {
        return "refund:" + p.getId();
    }

    /** The order was already paid: answer with the payment that paid it. */
    private PaymentResponse paidResponse(Order order) {
        Payment succeeded = payments.findByOrderIdOrderByCreatedAtAsc(order.getId()).stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCEEDED).reduce((a, b) -> b).orElse(null);
        if (succeeded == null) {
            throw new ConflictException(ErrorCodes.ORDER_NOT_PAYABLE, "Order is already closed");
        }
        return responseOf(succeeded, order);
    }

    private Order order(UUID orderId) {
        return orders.findById(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
    }

    private PaymentResponse responseOf(Payment p, Order order) {
        return new PaymentResponse(
                p.getId().toString(), order.getId().toString(), p.getProvider().name(), p.getStatus().name(),
                p.getCardBrand(), p.getCardLast4(), p.getFailureCode(), p.getFailureMessage(),
                p.getClientSecret(), null, orderService.toResponse(order));
    }
}
