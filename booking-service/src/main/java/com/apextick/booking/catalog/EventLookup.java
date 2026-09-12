package com.apextick.booking.catalog;

import com.apextick.booking.web.NotFoundException;
import org.springframework.stereotype.Component;

@Component
public class EventLookup {

    private final EventRepository eventRepository;

    public EventLookup(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    /** Resolve an event by numeric id or by slug, whatever its status. Admin and hold paths only. */
    public Event resolve(String idOrSlug) {
        if (idOrSlug != null && !idOrSlug.isEmpty() && idOrSlug.chars().allMatch(Character::isDigit)) {
            return eventRepository.findById(Long.valueOf(idOrSlug))
                    .orElseThrow(() -> new NotFoundException("Event", idOrSlug));
        }
        return eventRepository.findBySlug(idOrSlug)
                .orElseThrow(() -> new NotFoundException("Event", idOrSlug));
    }

    /**
     * Resolve an event a public, unauthenticated caller may see. A draft or cancelled event
     * is reported as missing rather than forbidden: the public catalog already hides it
     * ({@link EventSpecifications#forFilter}), so admitting it exists here would leak an
     * unannounced fixture's name, prices and layout to anyone who guesses its slug.
     */
    public Event resolvePublic(String idOrSlug) {
        Event event = resolve(idOrSlug);
        if (!event.getStatus().isPublic()) {
            throw new NotFoundException("Event", idOrSlug);
        }
        return event;
    }
}
