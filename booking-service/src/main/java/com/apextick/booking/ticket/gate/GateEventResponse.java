package com.apextick.booking.ticket.gate;

import com.apextick.booking.catalog.Event;
import com.apextick.booking.catalog.EventStatus;

import java.time.Instant;

/** The event a gate is admitting to, named the way the steward's screen shows it. */
public record GateEventResponse(Long id, String slug, String name, Instant startsAt, String timeZone,
                                String venue, EventStatus status) {

    public static GateEventResponse from(Event e) {
        return new GateEventResponse(e.getId(), e.getSlug(), e.getName(), e.getStartsAt(), e.getTimeZone(),
                e.getVenue(), e.getStatus());
    }
}
