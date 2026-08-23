package com.apextick.booking.order.dto;

import com.apextick.booking.order.OrderItem;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        Long id, Long seatId, String label, String sectionName,
        String tierCode, String tierName, BigDecimal unitPrice, UUID ticketId) {

    public static OrderItemResponse from(OrderItem item, UUID ticketId) {
        return new OrderItemResponse(item.getId(), item.getSeatId(), item.getSeatLabel(),
                item.getSectionName(), item.getTierCode(), item.getTierName(), item.getUnitPrice(), ticketId);
    }
}
