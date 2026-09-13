package com.apextick.booking.admin;

import com.apextick.booking.admin.dto.AdminOrderDetailResponse;
import com.apextick.booking.admin.dto.AdminPaymentResponse;
import com.apextick.booking.admin.dto.AdminTicketResponse;
import com.apextick.booking.order.Order;
import com.apextick.booking.order.OrderRepository;
import com.apextick.booking.order.OrderStatus;
import com.apextick.booking.order.dto.OrderResponse;
import com.apextick.booking.payment.Payment;
import com.apextick.booking.payment.PaymentRepository;
import com.apextick.booking.payment.PaymentStatus;
import com.apextick.booking.payment.RefundService;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.ticket.Ticket;
import com.apextick.booking.ticket.TicketRepository;
import com.apextick.booking.ticket.TicketStatus;
import com.apextick.booking.web.ConflictException;
import com.apextick.booking.web.ErrorCodes;
import com.apextick.booking.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** The box office's view of an order, and the refund it can give. */
@Service
public class AdminOrderService {

    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final TicketRepository tickets;
    private final RefundService refunds;
    private final TransactionTemplate readOnly;

    public AdminOrderService(OrderRepository orders, PaymentRepository payments, TicketRepository tickets,
                             RefundService refunds, PlatformTransactionManager txManager) {
        this.orders = orders;
        this.payments = payments;
        this.tickets = tickets;
        this.refunds = refunds;
        this.readOnly = new TransactionTemplate(txManager);
        this.readOnly.setReadOnly(true);
    }

    public AdminOrderDetailResponse detail(UUID orderId) {
        return readOnly.execute(s -> {
            Order order = orders.findById(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
            List<Ticket> orderTickets = tickets.findByOrderId(orderId);
            List<Payment> orderPayments = payments.findByOrderIdOrderByCreatedAtAsc(orderId);
            Map<Long, UUID> ticketByItem = orderTickets.stream()
                    .collect(Collectors.toMap(t -> t.getOrderItem().getId(), Ticket::getId));
            return new AdminOrderDetailResponse(OrderResponse.of(order, ticketByItem), order.getUserSub(),
                    order.getRefundedBy(), order.getRefundReason(),
                    refundBlockedBy(order, orderTickets, orderPayments),
                    orderTickets.stream().map(AdminTicketResponse::from).toList(),
                    orderPayments.stream().map(AdminPaymentResponse::from).toList());
        });
    }

    /** Refunds a paid order in full and answers with the order as it now stands. */
    public AdminOrderDetailResponse refund(UUID orderId, String reason, CurrentUser admin) {
        refunds.refundOrder(orderId, reason.strip(), admin);
        return detail(orderId);
    }

    /** Asks the provider again, now, for every refund still owed on the order. */
    public AdminOrderDetailResponse retryRefund(UUID orderId) {
        if (!orders.existsById(orderId)) {
            throw new NotFoundException("Order", orderId);
        }
        List<UUID> owed = payments.findByOrderIdAndStatus(orderId, PaymentStatus.REFUND_REQUIRED).stream()
                .map(Payment::getId).toList();
        if (owed.isEmpty()) {
            throw new ConflictException(ErrorCodes.REFUND_NOT_OWED, "No refund is owed on this order");
        }
        owed.forEach(refunds::retry);
        return detail(orderId);
    }

    /** Why this order cannot be refunded from the console, or {@code null} when it can. */
    private static String refundBlockedBy(Order order, List<Ticket> orderTickets, List<Payment> orderPayments) {
        if (order.getStatus() != OrderStatus.PAID) {
            return "NOT_PAID";
        }
        if (orderPayments.stream().noneMatch(p -> p.getStatus() == PaymentStatus.SUCCEEDED)) {
            return "NO_SETTLED_PAYMENT";
        }
        if (orderTickets.stream().anyMatch(t -> t.getStatus() == TicketStatus.USED)) {
            return "TICKETS_USED";
        }
        return null;
    }
}
