package com.apextick.booking.catalog;

import com.apextick.booking.web.NotFoundException;
import org.springframework.stereotype.Component;

@Component
public class EventLookup {

    private final EventRepository eventRepository;

    public EventLookup(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    /** Resolve an event by numeric id or by slug. */
    public Event resolve(String idOrSlug) {
        if (idOrSlug != null && !idOrSlug.isEmpty() && idOrSlug.chars().allMatch(Character::isDigit)) {
            return eventRepository.findById(Long.valueOf(idOrSlug))
                    .orElseThrow(() -> new NotFoundException("Event", idOrSlug));
        }
        return eventRepository.findBySlug(idOrSlug)
                .orElseThrow(() -> new NotFoundException("Event", idOrSlug));
    }
}
