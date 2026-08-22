package com.apextick.booking.catalog.admin;

import com.apextick.booking.catalog.Event;
import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.catalog.PriceTier;
import com.apextick.booking.catalog.PriceTierRepository;
import com.apextick.booking.catalog.Section;
import com.apextick.booking.catalog.SectionRepository;
import com.apextick.booking.catalog.dto.LayoutRequest;
import com.apextick.booking.catalog.dto.LayoutResult;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.web.ConflictException;
import com.apextick.booking.web.NotFoundException;
import com.apextick.booking.web.UnprocessableException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/** Applies a seating layout to an event: creates tiers + sections, then generates the seats. */
@Service
public class LayoutService {

    private final EventRepository events;
    private final PriceTierRepository tiers;
    private final SectionRepository sections;
    private final SeatRepository seats;
    private final JdbcClient jdbc;

    public LayoutService(EventRepository events, PriceTierRepository tiers, SectionRepository sections,
                         SeatRepository seats, JdbcClient jdbc) {
        this.events = events;
        this.tiers = tiers;
        this.sections = sections;
        this.seats = seats;
        this.jdbc = jdbc;
    }

    @Transactional
    public LayoutResult applyLayout(Long eventId, LayoutRequest request) {
        Event event = events.findById(eventId).orElseThrow(() -> new NotFoundException("Event", eventId));
        if (!tiers.findByEventIdOrderBySortOrderAscIdAsc(eventId).isEmpty() || seats.countByEventId(eventId) > 0) {
            throw new ConflictException("LAYOUT_EXISTS", "This event already has a seating layout");
        }

        Map<String, Long> tierIdByCode = new HashMap<>();
        int order = 0;
        for (LayoutRequest.TierSpec t : request.tiers()) {
            PriceTier tier = new PriceTier();
            tier.setEvent(event);
            tier.setCode(t.code());
            tier.setName(t.name());
            tier.setPrice(t.price());
            tier.setPerks(t.perks() == null ? java.util.List.of() : t.perks());
            tier.setSortOrder(t.sortOrder() == null ? order++ : t.sortOrder());
            tiers.saveAndFlush(tier);
            tierIdByCode.put(t.code(), tier.getId());
        }

        int seatsCreated = 0;
        int sOrder = 0;
        for (LayoutRequest.SectionSpec s : request.sections()) {
            Long tierId = tierIdByCode.get(s.tierCode());
            if (tierId == null) {
                throw new UnprocessableException("UNKNOWN_TIER", "Section references unknown tier " + s.tierCode());
            }
            if (s.rows() == null || s.rows() < 1 || s.rows() > 26 || s.seatsPerRow() == null || s.seatsPerRow() < 1) {
                throw new UnprocessableException("INVALID_SECTION",
                        "rows must be 1..26 and seatsPerRow >= 1 for section " + s.code());
            }
            Section section = new Section();
            section.setEvent(event);
            section.setCode(s.code());
            section.setName(s.name());
            section.setTier(tiers.getReferenceById(tierId));
            section.setSide(s.side());
            section.setRows(s.rows());
            section.setSeatsPerRow(s.seatsPerRow());
            section.setSortOrder(s.sortOrder() == null ? sOrder++ : s.sortOrder());
            sections.saveAndFlush(section);

            seatsCreated += jdbc.sql("""
                    INSERT INTO seats (event_id, section_id, seat_number, row_idx, col_idx, status, version)
                    SELECT :eventId, :sectionId, chr(65 + r) || (c + 1)::text, r, c, 'AVAILABLE', 0
                    FROM generate_series(0, :rows - 1) AS r
                    CROSS JOIN generate_series(0, :cols - 1) AS c
                    """)
                    .param("eventId", eventId)
                    .param("sectionId", section.getId())
                    .param("rows", s.rows())
                    .param("cols", s.seatsPerRow())
                    .update();
        }
        return new LayoutResult(eventId, request.tiers().size(), request.sections().size(), seatsCreated);
    }
}
