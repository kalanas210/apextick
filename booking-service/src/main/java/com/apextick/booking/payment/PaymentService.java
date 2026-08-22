package com.apextick.booking.payment;

import com.apextick.booking.order.Order;
import com.apextick.booking.order.OrderRepository;
import com.apextick.booking.order.OrderService;
import com.apextick.booking.order.OrderStatus;
import com.apextick.booking.payment.dto.PayRequest;
import com.apextick.booking.payment.dto.PaymentResponse;
import com.apextick.booking.payment.model.Customer;
import com.apextick.booking.payment.model.PaymentContext;
import com.apextick.booking.payment.model.PaymentInitiation;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.web.ConflictException;
import com.apextick.booking.web.ErrorCodes;
import com.apextick.booking.web.NotFoundException;
import com.apextick.booking.web.UnprocessableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final OrderService orderService;
    private final PaymentGatewayRegistry registry;
    private final TransactionTemplate tx;

    public PaymentService(PaymentRepository payments, OrderRepository orders, OrderService orderService,
                          PaymentGatewayRegistry registry, PlatformTransactionManager txManager) {
        this.payments = payments;
        this.orders = orders;
        this.orderService = orderService;
        this.registry = registry;
        this.tx = new TransactionTemplate(txManager);
    }

    public PaymentResponse pay(UUID orderId, PayRequest request, String idempotencyKey, CurrentUser user) {
        PaymentGateway gateway = registry.defaultGateway();

        // Phase A (tx): validate the order + create an INITIATED payment row.
        PaymentContext ctx = tx.execute(s -> initPayment(orderId, request, idempotencyKey, user, gateway));
        if (ctx == null) {
            // order already paid -> return the succeeded payment
            return tx.execute(s -> existingPaidResponse(orderId, user));
        }

        // Phase B (no tx): call the provider (may block on a network call for real gateways).
        PaymentInitiation init = gateway.initiate(ctx);

        // Phase C (tx): apply the result atomically with booking confirmation.
        try {
            return tx.execute(s -> applyResult(ctx.paymentId(), orderId, init));
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

    private PaymentContext initPayment(UUID orderId, PayRequest request, String idempotencyKey,
                                       CurrentUser user, PaymentGateway gateway) {
        Order order = orderService.loadOwned(orderId, user);
        if (order.getStatus() == OrderStatus.PAID) {
            return null;
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ConflictException(ErrorCodes.ORDER_NOT_PAYABLE, "Order is not payable");
        }
        if (!gateway.supports(order.getCurrency())) {
            throw new UnprocessableException("CURRENCY_UNSUPPORTED",
                    gateway.provider() + " does not support " + order.getCurrency());
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

        return new PaymentContext(p.getId(), order.getId(), order.getOrderNumber(), order.getTotal(),
                order.getCurrency(), new Customer(user.sub(), user.email(), user.name()), idempotencyKey,
                request == null ? null : request.returnUrl(), request == null ? null : request.cancelUrl(),
                request == null ? null : request.card());
    }

    private PaymentResponse applyResult(UUID paymentId, UUID orderId, PaymentInitiation init) {
        Payment p = payments.findById(paymentId).orElseThrow(() -> new NotFoundException("Payment", paymentId));
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
                orderService.confirmPaid(orderId, p); // may throw SeatsLostException -> rolls back this tx
            }
            case REQUIRES_ACTION -> p.setStatus(PaymentStatus.REQUIRES_ACTION);
            case REDIRECT -> p.setStatus(PaymentStatus.REDIRECTED);
            default -> {
                p.setStatus(PaymentStatus.FAILED);
                p.setFailureCode(init.failureCode());
                p.setFailureMessage(init.failureMessage());
            }
        }
        return responseOf(p, orders.findById(orderId).orElseThrow(() -> new NotFoundException("Order", orderId)));
    }

    private PaymentResponse compensate(UUID paymentId, UUID orderId, PaymentInitiation init, PaymentGateway gateway) {
        Payment p = payments.findById(paymentId).orElseThrow(() -> new NotFoundException("Payment", paymentId));
        // the charge succeeded but seats were lost -> request a refund and cancel the order
        gateway.refund(init.providerRef(), p.getAmount(), p.getCurrency(), p.getIdempotencyKey());
        p.setProviderRef(init.providerRef());
        p.setCardBrand(init.cardBrand());
        p.setCardLast4(init.cardLast4());
        p.setStatus(PaymentStatus.REFUND_REQUIRED);
        p.setFailureCode("seats_lost");
        p.setFailureMessage("Seats were lost before the payment could be confirmed");
        p.setUpdatedAt(Instant.now());
        orderService.compensateSeatsLost(orderId);
        return responseOf(p, orders.findById(orderId).orElseThrow(() -> new NotFoundException("Order", orderId)));
    }

    private PaymentResponse existingPaidResponse(UUID orderId, CurrentUser user) {
        Order order = orderService.loadOwned(orderId, user);
        Payment succeeded = payments.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCEEDED).reduce((a, b) -> b).orElse(null);
        if (succeeded == null) {
            throw new ConflictException(ErrorCodes.ORDER_NOT_PAYABLE, "Order is already closed");
        }
        return responseOf(succeeded, order);
    }

    private PaymentResponse responseOf(Payment p, Order order) {
        return new PaymentResponse(
                p.getId().toString(), order.getId().toString(), p.getProvider().name(), p.getStatus().name(),
                p.getCardBrand(), p.getCardLast4(), p.getFailureCode(), p.getFailureMessage(),
                p.getClientSecret(), null, null, orderService.toResponse(order));
    }
}
