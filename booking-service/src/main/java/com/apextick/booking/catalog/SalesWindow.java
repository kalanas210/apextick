package com.apextick.booking.catalog;

import com.apextick.booking.web.ConflictException;
import com.apextick.booking.web.ErrorCodes;

import java.time.Instant;
import java.util.Map;

/**
 * The single place that answers "may this event be sold right now?".
 *
 * <p>Every step that moves inventory or money re-asks it -- holding seats, creating the
 * order, and starting the payment -- so a hold taken seconds before the gates close cannot
 * be turned into a sale afterwards. The answer comes entirely from the event's own row, so
 * the admin status pill and the sales-window fields actually govern selling.
 */
public final class SalesWindow {

    private SalesWindow() {
    }

    /** Throws 409 unless the event is on sale right now. */
    public static void assertOpen(Event event) {
        Instant now = Instant.now();
        if (event.getStatus() == null || !event.getStatus().isPurchasable()) {
            throw new ConflictException(ErrorCodes.SALES_CLOSED, "Sales are closed for this event");
        }
        if (event.getSalesStartAt() != null && now.isBefore(event.getSalesStartAt())) {
            throw new ConflictException(ErrorCodes.SALES_NOT_OPEN,
                    "Sales for this event have not opened yet",
                    Map.of("salesStartAt", event.getSalesStartAt().toString()));
        }
        if (event.getSalesEndAt() != null && now.isAfter(event.getSalesEndAt())) {
            throw new ConflictException(ErrorCodes.SALES_CLOSED, "Sales have ended for this event");
        }
        // No explicit sales end means sales run up to kickoff, never past it.
        if (event.getStartsAt() != null && !now.isBefore(event.getStartsAt())) {
            throw new ConflictException(ErrorCodes.SALES_CLOSED, "This event has already started");
        }
    }
}
