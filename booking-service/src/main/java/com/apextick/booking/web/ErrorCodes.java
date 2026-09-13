package com.apextick.booking.web;

/** Stable machine-readable error codes carried in the ProblemDetail {@code code} member. */
public final class ErrorCodes {
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String SEAT_UNAVAILABLE = "SEAT_UNAVAILABLE";
    public static final String SALES_CLOSED = "SALES_CLOSED";
    public static final String SALES_NOT_OPEN = "SALES_NOT_OPEN";
    public static final String TOO_MANY_SEATS = "TOO_MANY_SEATS";
    public static final String HOLD_EXPIRED = "HOLD_EXPIRED";
    public static final String ORDER_ALREADY_PENDING = "ORDER_ALREADY_PENDING";
    public static final String ORDER_PENDING = "ORDER_PENDING";
    public static final String ORDER_NOT_PAYABLE = "ORDER_NOT_PAYABLE";
    public static final String ORDER_NOT_REFUNDABLE = "ORDER_NOT_REFUNDABLE";
    public static final String ORDER_TICKETS_USED = "ORDER_TICKETS_USED";
    public static final String REFUND_NOT_OWED = "REFUND_NOT_OWED";
    public static final String SEAT_NOT_HELD = "SEAT_NOT_HELD";
    public static final String SEAT_NOT_BLOCKED = "SEAT_NOT_BLOCKED";
    public static final String EVENT_STATUS_TRANSITION = "EVENT_STATUS_TRANSITION";
    public static final String IDEMPOTENCY_KEY_REUSED = "IDEMPOTENCY_KEY_REUSED";
    public static final String IDEMPOTENCY_KEY_MISSING = "IDEMPOTENCY_KEY_MISSING";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    public static final String PAYMENT_IN_PROGRESS = "PAYMENT_IN_PROGRESS";
    public static final String TICKET_ALREADY_USED = "TICKET_ALREADY_USED";
    public static final String TICKET_CANCELLED = "TICKET_CANCELLED";
    public static final String TICKET_WRONG_EVENT = "TICKET_WRONG_EVENT";
    public static final String TICKET_NOT_ADMITTED = "TICKET_NOT_ADMITTED";
    public static final String EVENT_NOT_ADMITTING = "EVENT_NOT_ADMITTING";
    public static final String CONFLICT = "CONFLICT";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    private ErrorCodes() {}
}
