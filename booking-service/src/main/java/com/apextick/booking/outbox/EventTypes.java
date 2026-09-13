package com.apextick.booking.outbox;

/** Routing keys / event type identifiers published to the {@code apextick.events} exchange. */
public final class EventTypes {
    public static final String SEAT_HELD = "seat.held";
    public static final String SEAT_RELEASED = "seat.released";
    public static final String BOOKING_CONFIRMED = "booking.confirmed";
    public static final String ORDER_CANCELLED = "order.cancelled";
    public static final String TICKET_USED = "ticket.used";
    public static final String TICKET_UNADMITTED = "ticket.unadmitted";
    public static final String PAYMENT_REFUNDED = "payment.refunded";
    public static final String EVENT_CANCELLED = "event.cancelled";
    public static final String SEAT_BLOCKED = "seat.blocked";
    public static final String SEAT_UNBLOCKED = "seat.unblocked";

    private EventTypes() {
    }
}
