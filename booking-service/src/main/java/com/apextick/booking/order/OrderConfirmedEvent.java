package com.apextick.booking.order;

import java.util.UUID;

/**
 * In-process signal that an order reached PAID and its tickets exist. Published inside the
 * confirming transaction; listeners run after it commits, so slow work (PDF rendering,
 * object-store uploads) stays off the payment path.
 */
public record OrderConfirmedEvent(UUID orderId) {
}
