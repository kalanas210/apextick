package com.apextick.booking.catalog.admin;

import com.apextick.booking.catalog.CatalogQueryService;
import com.apextick.booking.catalog.Event;
import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.catalog.EventStatus;
import com.apextick.booking.catalog.PriceTierRepository;
import com.apextick.booking.catalog.SectionRepository;
import com.apextick.booking.catalog.SeriesRepository;
import com.apextick.booking.catalog.Sport;
import com.apextick.booking.catalog.TeamRepository;
import com.apextick.booking.catalog.dto.EventDetailResponse;
import com.apextick.booking.catalog.dto.EventUpsertRequest;
import com.apextick.booking.order.OrderRepository;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.web.ConflictException;
import com.apextick.booking.web.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;

@Service
public class AdminEventService {

    private final EventRepository events;
    private final SeriesRepository series;
    private final TeamRepository teams;
    private final OrderRepository orders;
    private final CatalogQueryService catalog;
    private final SeatRepository seats;
    private final SectionRepository sections;
    private final PriceTierRepository tiers;

    public AdminEventService(EventRepository events, SeriesRepository series, TeamRepository teams,
                             OrderRepository orders, CatalogQueryService catalog,
                             SeatRepository seats, SectionRepository sections, PriceTierRepository tiers) {
        this.events = events;
        this.series = series;
        this.teams = teams;
        this.orders = orders;
        this.catalog = catalog;
        this.seats = seats;
        this.sections = sections;
        this.tiers = tiers;
    }

    @Transactional
    public EventDetailResponse create(EventUpsertRequest r) {
        Event e = new Event();
        apply(e, r);
        e.setCreatedAt(Instant.now());
        save(e);
        return catalog.detail(e.getId().toString());
    }

    @Transactional
    public EventDetailResponse update(Long id, EventUpsertRequest r) {
        Event e = events.findById(id).orElseThrow(() -> new NotFoundException("Event", id));
        apply(e, r);
        e.setUpdatedAt(Instant.now());
        save(e);
        return catalog.detail(id.toString());
    }

    @Transactional
    public EventDetailResponse setStatus(Long id, String status) {
        Event e = events.findById(id).orElseThrow(() -> new NotFoundException("Event", id));
        e.setStatus(EventStatus.fromCode(status));
        e.setUpdatedAt(Instant.now());
        return catalog.detail(id.toString());
    }

    @Transactional
    public void delete(Long id) {
        Event e = events.findById(id).orElseThrow(() -> new NotFoundException("Event", id));
        if (orders.existsByEventId(id)) {
            throw new ConflictException("EVENT_HAS_ORDERS",
                    "Event has orders; set status to cancelled instead of deleting");
        }
        // Seating layouts are create-only, so deleting the event is the only way back
        // from a mis-built one. The schema declares plain foreign keys with no cascade,
        // so the layout has to come away by hand, innermost first.
        seats.deleteByEventId(id);
        sections.deleteByEventId(id);
        tiers.deleteByEventId(id);
        events.delete(e);
    }

    private void apply(Event e, EventUpsertRequest r) {
        e.setName(r.name());
        e.setSlug(r.slug());
        e.setSport(Sport.fromCode(r.sport()));
        e.setStartsAt(r.startsAt());
        e.setTimeZone(zoneOrThrow(r.timeZone()));
        e.setVenue(r.venue());
        e.setCity(r.city());
        e.setCountry(r.country());
        e.setStage(r.stage());
        e.setImage(r.image());
        e.setBlurb(r.blurb());
        e.setCurrency(r.currency());
        e.setStatus(r.status() == null ? EventStatus.DRAFT : EventStatus.fromCode(r.status()));
        e.setSalesStartAt(r.salesStartAt());
        e.setSalesEndAt(r.salesEndAt());
        e.setSeries(r.seriesId() == null ? null
                : series.findById(r.seriesId()).orElseThrow(() -> new NotFoundException("Series", r.seriesId())));
        e.setHomeTeam(r.homeTeamId() == null ? null
                : teams.findById(r.homeTeamId()).orElseThrow(() -> new NotFoundException("Team", r.homeTeamId())));
        e.setAwayTeam(r.awayTeamId() == null ? null
                : teams.findById(r.awayTeamId()).orElseThrow(() -> new NotFoundException("Team", r.awayTeamId())));
    }

    /**
     * The time zone is a free-text field, but it is read back as a {@link ZoneId} when an
     * event is summarised. An unparseable one is not merely this request's problem: stored,
     * it would throw on every later read of the event, including the public catalog. So it
     * is rejected here, where the caller can still be told which field was wrong.
     *
     * <p>{@code ZoneId.of} signals a bad zone with {@link DateTimeException}, which is not an
     * {@code IllegalArgumentException} and so would otherwise land on the 500 handler.
     */
    private static String zoneOrThrow(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return "UTC";
        }
        String tz = timeZone.trim();
        try {
            ZoneId.of(tz);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException(
                    "Unknown time zone '" + tz + "'; expected an IANA zone id such as Asia/Colombo or UTC");
        }
        return tz;
    }

    private void save(Event e) {
        try {
            events.saveAndFlush(e);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("SLUG_TAKEN", "An event with slug '" + e.getSlug() + "' already exists");
        }
    }
}
