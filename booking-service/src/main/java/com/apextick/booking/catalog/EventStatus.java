package com.apextick.booking.catalog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Sales status of an event. Stored as the enum name; serialized as the hyphenated code. */
public enum EventStatus {
    ONSALE("onsale"),
    SELLING_FAST("selling-fast"),
    FINAL_RELEASE("final-release"),
    SOLD_OUT("sold-out"),
    DRAFT("draft"),
    CANCELLED("cancelled");

    private final String code;

    EventStatus(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static EventStatus fromCode(String value) {
        for (EventStatus s : values()) {
            if (s.code.equalsIgnoreCase(value) || s.name().equalsIgnoreCase(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown event status: " + value);
    }

    /** Statuses hidden from the public catalog. */
    public boolean isPublic() {
        return this != DRAFT && this != CANCELLED;
    }

    /**
     * Statuses that still accept holds, orders and payments. Deliberately narrower than
     * {@link #isPublic()}: a sold-out or cancelled event stays visible (with its badge and
     * its seat map) but must stop selling, which is exactly what the admin status control
     * is for.
     */
    public boolean isPurchasable() {
        return this == ONSALE || this == SELLING_FAST || this == FINAL_RELEASE;
    }
}
