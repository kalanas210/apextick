package com.apextick.booking.catalog;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public final class EventSpecifications {

    private EventSpecifications() {
    }

    /** Public catalog search: always hides DRAFT/CANCELLED events. */
    public static Specification<Event> forFilter(EventFilter f) {
        return (root, query, cb) -> {
            List<Predicate> ps = filterPredicates(root, cb, f);
            ps.add(cb.not(root.get("status").in(EventStatus.DRAFT, EventStatus.CANCELLED)));
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    /**
     * Admin search: the same filters, but every status is visible. The panel has to
     * be able to find the draft it just created, which the public catalog hides.
     */
    public static Specification<Event> forAdminFilter(EventFilter f) {
        return (root, query, cb) -> cb.and(filterPredicates(root, cb, f).toArray(new Predicate[0]));
    }

    /**
     * Orders a search by its events' cheapest seat, ascending; an event with no
     * tiers priced yet sorts last, and ties break on kickoff.
     *
     * <p>The value is an aggregate over another table, so it cannot be expressed as
     * a {@code Sort} over an {@link Event} property — it goes on the query itself.
     * Spring Data only replaces the order when the {@code Pageable} carries a sort
     * (so the caller must pass an unsorted one), and strips orders from the count
     * query outright, which is why the subquery costs nothing there.
     */
    public static Specification<Event> orderByFromPrice() {
        return (root, query, cb) -> {
            if (query != null) {
                Subquery<BigDecimal> cheapest = query.subquery(BigDecimal.class);
                Root<PriceTier> tier = cheapest.from(PriceTier.class);
                cheapest.select(cb.min(tier.get("price"))).where(cb.equal(tier.get("event"), root));
                query.orderBy(cb.asc(cheapest), cb.asc(root.get("startsAt")));
            }
            return null; // ordering only — restricts nothing
        };
    }

    /** The filters both views share, so the two cannot drift apart. */
    private static List<Predicate> filterPredicates(Root<Event> root, CriteriaBuilder cb, EventFilter f) {
        List<Predicate> ps = new ArrayList<>();
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
        return ps;
    }
}
