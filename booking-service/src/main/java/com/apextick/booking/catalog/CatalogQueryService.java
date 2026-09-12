package com.apextick.booking.catalog;

import com.apextick.booking.catalog.dto.EventDetailResponse;
import com.apextick.booking.catalog.dto.EventSummaryResponse;
import com.apextick.booking.catalog.dto.PriceTierResponse;
import com.apextick.booking.catalog.dto.SectionResponse;
import com.apextick.booking.catalog.dto.SeriesResponse;
import com.apextick.booking.catalog.dto.TeamResponse;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.SeatStatus;
import com.apextick.booking.seat.dto.SeatResponse;
import com.apextick.booking.web.NotFoundException;
import com.apextick.booking.web.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CatalogQueryService {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final EventRepository eventRepository;
    private final SeriesRepository seriesRepository;
    private final PriceTierRepository priceTierRepository;
    private final SectionRepository sectionRepository;
    private final SeatRepository seatRepository;
    private final EventLookup eventLookup;

    public CatalogQueryService(EventRepository eventRepository, SeriesRepository seriesRepository,
                               PriceTierRepository priceTierRepository, SectionRepository sectionRepository,
                               SeatRepository seatRepository, EventLookup eventLookup) {
        this.eventRepository = eventRepository;
        this.seriesRepository = seriesRepository;
        this.priceTierRepository = priceTierRepository;
        this.sectionRepository = sectionRepository;
        this.seatRepository = seatRepository;
        this.eventLookup = eventLookup;
    }

    @Transactional(readOnly = true)
    public List<SeriesResponse> series() {
        return seriesRepository.findAllOrdered().stream().map(SeriesResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SeriesResponse seriesBySlug(String slug) {
        return SeriesResponse.from(seriesRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Series", slug)));
    }

    @Transactional(readOnly = true)
    public PageResponse<EventSummaryResponse> search(EventFilter filter, int page, int size, String sort) {
        return search(EventSpecifications.forFilter(filter), page, size, sort);
    }

    /** Admin variant of {@link #search}: identical shape, but drafts and cancellations are visible. */
    @Transactional(readOnly = true)
    public PageResponse<EventSummaryResponse> searchAll(EventFilter filter, int page, int size, String sort) {
        return search(EventSpecifications.forAdminFilter(filter), page, size, sort);
    }

    private PageResponse<EventSummaryResponse> search(Specification<Event> spec, int page, int size, String sort) {
        Sort order = "latest".equalsIgnoreCase(sort)
                ? Sort.by("startsAt").descending()
                : Sort.by("startsAt").ascending();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), order);
        Page<Event> events = eventRepository.findAll(spec, pageable);
        List<Long> ids = events.getContent().stream().map(Event::getId).toList();

        Map<Long, SeatRepository.SeatCountView> counts = ids.isEmpty() ? Map.of()
                : seatRepository.countByEventIds(ids, SeatStatus.AVAILABLE).stream()
                .collect(Collectors.toMap(SeatRepository.SeatCountView::getEventId, v -> v));
        Map<Long, BigDecimal> minPrice = ids.isEmpty() ? Map.of()
                : priceTierRepository.minPriceByEventIds(ids).stream()
                .collect(Collectors.toMap(PriceTierRepository.MinPriceView::getEventId,
                        PriceTierRepository.MinPriceView::getFromPrice));

        return PageResponse.of(events, e -> summary(e, counts.get(e.getId()), minPrice.get(e.getId())));
    }

    @Transactional(readOnly = true)
    public EventDetailResponse detail(String idOrSlug) {
        return detailOf(eventLookup.resolvePublic(idOrSlug));
    }

    /**
     * Admin variant of {@link #detail}: identical payload, but a draft or cancelled event
     * resolves instead of 404ing. The panel has to be able to open the draft it just created.
     */
    @Transactional(readOnly = true)
    public EventDetailResponse adminDetail(Long id) {
        return detailOf(eventLookup.resolve(String.valueOf(id)));
    }

    private EventDetailResponse detailOf(Event e) {
        Long id = e.getId();

        SeatRepository.SeatCountView count = seatRepository.countByEventIds(List.of(id), SeatStatus.AVAILABLE)
                .stream().findFirst().orElse(null);
        BigDecimal fromPrice = priceTierRepository.minPriceByEventIds(List.of(id))
                .stream().findFirst().map(PriceTierRepository.MinPriceView::getFromPrice).orElse(null);
        EventSummaryResponse summary = summary(e, count, fromPrice);

        Map<Long, SeatRepository.TierCountView> tierCounts = seatRepository.countByTier(id)
                .stream().collect(Collectors.toMap(SeatRepository.TierCountView::getTierId, v -> v));
        Map<Long, Long> sectionAvail = seatRepository.countBySection(id, SeatStatus.AVAILABLE)
                .stream().collect(Collectors.toMap(SeatRepository.SectionCountView::getSectionId,
                        SeatRepository.SectionCountView::getAvailable));

        List<PriceTierResponse> tiers = priceTierRepository.findByEventIdOrderBySortOrderAscIdAsc(id).stream()
                .map(t -> {
                    SeatRepository.TierCountView tc = tierCounts.get(t.getId());
                    long total = tc == null ? 0 : tc.getTotal();
                    long avail = tc == null ? 0 : tc.getAvailable();
                    return new PriceTierResponse(t.getId(), t.getCode(), t.getName(), t.getPrice(),
                            t.getPerks(), avail, total);
                })
                .toList();

        List<SectionResponse> sections = sectionRepository.findByEventIdOrderBySortOrderAscIdAsc(id).stream()
                .map(sec -> new SectionResponse(sec.getId(), sec.getCode(), sec.getName(),
                        sec.getTier().getId(), sec.getTier().getCode(), sec.getSide(),
                        sec.getRows(), sec.getSeatsPerRow(), sectionAvail.getOrDefault(sec.getId(), 0L)))
                .toList();

        return EventDetailResponse.of(summary, SeriesResponse.from(e.getSeries()), tiers, sections);
    }

    @Transactional(readOnly = true)
    public List<SeatResponse> seats(String idOrSlug, String sub) {
        Event e = eventLookup.resolvePublic(idOrSlug);
        return seatRepository.findAllForEventWithLayout(e.getId()).stream()
                .map(s -> SeatResponse.from(s, sub)).toList();
    }

    private EventSummaryResponse summary(Event e, SeatRepository.SeatCountView count, BigDecimal fromPrice) {
        ZoneId zone = ZoneId.of(e.getTimeZone() == null ? "UTC" : e.getTimeZone());
        ZonedDateTime zdt = e.getStartsAt().atZone(zone);
        long total = count == null ? 0 : count.getTotal();
        long avail = count == null ? 0 : count.getAvailable();
        Series ser = e.getSeries();
        String currency = e.getCurrency();
        String symbol = ser != null && ser.getCurrencySymbol() != null
                ? ser.getCurrencySymbol() : symbolFor(currency);
        return new EventSummaryResponse(
                e.getId(), e.getSlug(), e.getName(),
                ser == null ? null : ser.getId(), ser == null ? null : ser.getSlug(),
                e.getSport() == null ? null : e.getSport().code(),
                TeamResponse.from(e.getHomeTeam()), TeamResponse.from(e.getAwayTeam()),
                e.getStartsAt(), zdt.toLocalDate().toString(), zdt.toLocalTime().format(HHMM), e.getTimeZone(),
                e.getVenue(), e.getCity(), e.getCountry(), e.getStage(),
                e.getStatus() == null ? null : e.getStatus().code(), e.getImage(), e.getBlurb(),
                currency, symbol, fromPrice, avail, total, e.getSalesStartAt(), e.getSalesEndAt());
    }

    private static String symbolFor(String currency) {
        if (currency == null) {
            return "";
        }
        return switch (currency) {
            case "INR" -> "\u20B9";
            case "GBP" -> "\u00A3";
            case "USD" -> "$";
            case "EUR" -> "\u20AC";
            default -> currency;
        };
    }
}
