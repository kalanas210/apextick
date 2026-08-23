package com.apextick.booking.catalog;

import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the demo catalog seed (005). Assertions target the known seed slugs so
 * they are unaffected by ad-hoc events other tests create in the shared database.
 */
@IntegrationTest
class CatalogSeedTest {

    private static final List<String> SEED_SLUGS = List.of(
            "india-pakistan-group-stage", "australia-england-super-8", "world-cup-final",
            "mumbai-indians-chennai-super-kings", "qualifier-one",
            "arsenal-manchester-city", "tottenham-chelsea-derby",
            "usa-paraguay-group-stage", "england-croatia-group-stage", "world-cup-final-2026");

    @Autowired SeriesRepository seriesRepository;
    @Autowired EventRepository eventRepository;
    @Autowired PriceTierRepository priceTierRepository;
    @Autowired SectionRepository sectionRepository;
    @Autowired SeatRepository seatRepository;

    @Test
    void seed_creates_four_series() {
        assertThat(seriesRepository.count()).isEqualTo(4);
    }

    @Test
    void all_known_seed_events_exist_with_four_tiers_and_four_sections() {
        for (String slug : SEED_SLUGS) {
            Event e = eventRepository.findBySlug(slug)
                    .orElseThrow(() -> new AssertionError("missing seeded event: " + slug));
            assertThat(priceTierRepository.findByEventIdOrderBySortOrderAscIdAsc(e.getId())).hasSize(4);
            assertThat(sectionRepository.findByEventIdOrderBySortOrderAscIdAsc(e.getId())).hasSize(4);
        }
    }

    @Test
    void each_seeded_event_has_a_full_300_seat_bowl() {
        // north 4x18 + east 5x12 + south 6x18 + west 5x12 = 300
        Event fifaFinal = eventRepository.findBySlug("world-cup-final-2026").orElseThrow();
        assertThat(seatRepository.countByEventId(fifaFinal.getId())).isEqualTo(300);

        Event indPak = eventRepository.findBySlug("india-pakistan-group-stage").orElseThrow();
        assertThat(seatRepository.countByEventId(indPak.getId())).isEqualTo(300);
    }
}
