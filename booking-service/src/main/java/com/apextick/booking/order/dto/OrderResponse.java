package com.apextick.booking.order.dto;

import com.apextick.booking.order.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String id, String orderNumber, String status, Long eventId, String eventSlug, String eventName,
        Instant startsAt, String currency, BigDecimal subtotal, BigDecimal fee, BigDecimal total,
        Instant expiresAt, Instant createdAt, Instant paidAt, String cancelReason,
        List<OrderItemResponse> items) {

    public static OrderResponse from(Order o) {
        List<OrderItemResponse> items = o.getItems().stream()
                .map(i -> OrderItemResponse.from(i, null)).toList();
        return new OrderResponse(
                o.getId().toString(), o.getOrderNumber(), o.getStatus().name(),
                o.getEvent().getId(), o.getEvent().getSlug(), o.getEvent().getName(),
                o.getEvent().getStartsAt(), o.getCurrency(), o.getSubtotal(), o.getFee(), o.getTotal(),
                o.getExpiresAt(), o.getCreatedAt(), o.getPaidAt(), o.getCancelReason(), items);
    }
}
