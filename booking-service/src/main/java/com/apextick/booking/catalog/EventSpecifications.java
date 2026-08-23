package com.apextick.booking.catalog;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public final class EventSpecifications {

    private EventSpecifications() {
    }

    /** Public catalog search: always hides DRAFT/CANCELLED events. */
    public static Specification<Event> forFilter(EventFilter f) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.not(root.get("status").in(EventStatus.DRAFT, EventStatus.CANCELLED)));
            if (f.status() != null) {
                ps.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.sport() != null) {
                ps.add(cb.equal(root.get("sport"), f.sport()));
            }
            if (f.seriesId() != null) {
                ps.add(cb.equal(root.get("series").get("id"), f.seriesId()));
            }
            if (f.seriesSlug() != null && !f.seriesSlug().isBlank()) {
                ps.add(cb.equal(root.get("series").get("slug"), f.seriesSlug()));
            }
            if (f.city() != null && !f.city().isBlank()) {
                ps.add(cb.equal(cb.lower(root.get("city")), f.city().toLowerCase()));
            }
            if (f.from() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("startsAt"),
                        f.from().atStartOfDay(ZoneOffset.UTC).toInstant()));
            }
            if (f.to() != null) {
                ps.add(cb.lessThan(root.get("startsAt"),
                        f.to().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
            }
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().toLowerCase() + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("city")), like),
                        cb.like(cb.lower(root.get("stage")), like)));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }
}
