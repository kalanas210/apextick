package com.apextick.booking.admin.dto;

import com.apextick.booking.order.dto.OrderResponse;

import java.util.List;

/**
 * One order as the box office sees it: what was bought, where every ticket stands, every attempt
 * to pay for it, and whether it can be refunded.
 *
 * @param refundBlockedBy {@code null} when the order can be refunded; otherwise why not:
 *                        {@code NOT_PAID}, {@code NO_SETTLED_PAYMENT} or {@code TICKETS_USED}
 */
public record AdminOrderDetailResponse(
        OrderResponse order, String userSub, String refundedBy, String refundReason, String refundBlockedBy,
        List<AdminTicketResponse> tickets, List<AdminPaymentResponse> payments) {
}
