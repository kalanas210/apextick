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
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.web.ConflictException;
import com.apextick.booking.web.ErrorCodes;
import com.apextick.booking.web.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

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
    private final EventCancellationService cancellations;
    private final TransactionTemplate tx;

    public AdminEventService(EventRepository events, SeriesRepository series, TeamRepository teams,
                             OrderRepository orders, CatalogQueryService catalog,
                             SeatRepository seats, SectionRepository sections, PriceTierRepository tiers,
                             EventCancellationService cancellations, PlatformTransactionManager txManager) {
        this.events = events;
        this.series = series;
        this.teams = teams;
        this.orders = orders;
        this.catalog = catalog;
        this.seats = seats;
        this.sections = sections;
        this.tiers = tiers;
        this.cancellations = cancellations;
        this.tx = new TransactionTemplate(txManager);
    }

    @Transactional
    public EventDetailResponse create(EventUpsertRequest r) {
        EventStatus status = r.status() == null ? EventStatus.DRAFT : EventStatus.fromCode(r.status());
        if (status == EventStatus.CANCELLED) {
            throw new IllegalArgumentException("A new event cannot start out cancelled");
        }
        Event e = new Event();
        apply(e, r);
        e.setStatus(status);
        e.setCreatedAt(Instant.now());
        save(e);
        return catalog.adminDetail(e.getId());
    }

    /**
     * Replaces an event's details, but not its status. A save that left the status out used to
     * un-publish a live event, and one that named another status would have cancelled it without
     * refunding anybody: the status moves through {@link #setStatus} alone.
     */
    @Transactional
    public EventDetailResponse update(Long id, EventUpsertRequest r) {
        Event e = events.findById(id).orElseThrow(() -> new NotFoundException("Event", id));
        if (r.status() != null) {
            EventStatus requested = EventStatus.fromCode(r.status());
            if (requested != e.getStatus()) {
                throw transition(e.getStatus(), requested,
                        "Change an event's status with PATCH /api/admin/events/{id}/status, not by saving it");
            }
        }
        apply(e, r);
        e.setUpdatedAt(Instant.now());
        save(e);
        return catalog.adminDetail(id);
    }

    /**
     * Moves an event to another status. Cancelling unwinds every sale made for it (see
     * {@link EventCancellationService}); every other move is checked against where the event
     * has been, and asking for the status it already has changes nothing.
     */
    public EventDetailResponse setStatus(Long id, String status, String reason, CurrentUser admin) {
        EventStatus next = EventStatus.fromCode(status);
        if (next == EventStatus.CANCELLED) {
            cancellations.cancel(id, reason == null || reason.isBlank() ? null : reason.strip(), admin);
            return catalog.adminDetail(id);
        }
        return tx.execute(s -> {
            Event e = events.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Event", id));
            if (e.getStatus() != next) {
                assertMayBecome(e, next);
                e.setStatus(next);
                e.setUpdatedAt(Instant.now());
            }
            return catalog.adminDetail(id);
        });
    }

    @Transactional
    public void delete(Long id) {
        Event e = events.findById(id).orElseThrow(() -> new NotFoundException("Event", id));
        // Deliberately counts orders in every status, expired and cancelled included: orders
        // reference the event, order_items reference its seats, and payments and tickets hang
        // off those orders, all with plain foreign keys. A cancelled order can still carry a
        // captured-then-refunded payment, so clearing the way would mean deleting sales history.
        if (orders.existsByEventId(id)) {
            throw new ConflictException("EVENT_HAS_ORDERS",
                    "Event has orders (expired and cancelled ones included), which are kept as the sales "
                            + "record; set its status to cancelled instead of deleting it");
        }
        // Seating layouts are create-only, so deleting the event is the only way back
        // from a mis-built one. The schema declares plain foreign keys with no cascade,
        // so the layout has to come away by hand, innermost first.
        seats.deleteByEventId(id);
        sections.deleteByEventId(id);
        tiers.deleteByEventId(id);
        events.delete(e);
    }

    /**
     * The moves an operator may make. A cancelled event stays cancelled: its tickets are void and
     * its money has gone back, and reopening it would sell seats against orders that no longer
     * exist. And an event that has taken orders cannot slip back to draft, out of the catalog its
     * buyers found it in; it is cancelled if it will not go ahead.
     */
    private void assertMayBecome(Event e, EventStatus next) {
        if (e.getStatus() == EventStatus.CANCELLED) {
            throw transition(e.getStatus(), next, "A cancelled event cannot be reopened");
        }
        if (next == EventStatus.DRAFT && orders.existsByEventId(e.getId())) {
            throw transition(e.getStatus(), next,
                    "This event has orders, so it cannot go back to draft; cancel it if it will not go ahead");
        }
    }

    private static ConflictException transition(EventStatus from, EventStatus to, String message) {
        return new ConflictException(ErrorCodes.EVENT_STATUS_TRANSITION, message,
                Map.of("from", from.code(), "to", to.code()));
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
