package com.apextick.booking.order.dto;

import com.apextick.booking.order.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record OrderResponse(
        String id, String orderNumber, String status, Long eventId, String eventSlug, String eventName,
        Instant startsAt, String currency, BigDecimal subtotal, BigDecimal fee, BigDecimal total,
        Instant expiresAt, Instant createdAt, Instant paidAt, String cancelReason,
        List<OrderItemResponse> items, List<String> ticketIds) {

    /** Builds the response, attaching each item's issued ticket id (if any). */
    public static OrderResponse of(Order o, Map<Long, UUID> ticketByItemId) {
        List<OrderItemResponse> items = o.getItems().stream()
                .map(i -> OrderItemResponse.from(i, ticketByItemId.get(i.getId()))).toList();
        List<String> ticketIds = items.stream()
                .map(OrderItemResponse::ticketId).filter(Objects::nonNull).map(UUID::toString).toList();
        return new OrderResponse(
                o.getId().toString(), o.getOrderNumber(), o.getStatus().name(),
                o.getEvent().getId(), o.getEvent().getSlug(), o.getEvent().getName(),
                o.getEvent().getStartsAt(), o.getCurrency(), o.getSubtotal(), o.getFee(), o.getTotal(),
                o.getExpiresAt(), o.getCreatedAt(), o.getPaidAt(), o.getCancelReason(), items, ticketIds);
    }

    public static OrderResponse from(Order o) {
        return of(o, Map.of());
    }
}
