package com.apextick.booking.catalog;

import com.apextick.booking.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the public catalog endpoints. Two fixtures matter here: a published event from
 * the demo seed, and the legacy event 1, which 004 leaves DRAFT -- the unpublished event
 * every one of these endpoints has to keep to itself.
 */
@IntegrationTest
class CatalogApiTest {

    private static final String PUBLISHED_SLUG = "arsenal-manchester-city";

    @Autowired
    MockMvc mvc;

    @Test
    void series_list_is_public() throws Exception {
        mvc.perform(get("/api/series"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void events_list_is_a_page_and_excludes_draft_events() throws Exception {
        mvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0));

        // the legacy event is DRAFT, so searching for it by name must come back empty
        mvc.perform(get("/api/events?size=100&q=ApexTick Live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    /**
     * The storefront's "Price" sort. It orders on the cheapest tier, which lives in
     * another table, so the ordering rides on the specification rather than the
     * Pageable -- and a page that came back in kickoff order would still look
     * plausible. Assert the prices themselves are non-decreasing.
     */
    @Test
    void events_can_be_sorted_by_their_cheapest_seat() throws Exception {
        assertThat(fromPrices("/api/events?size=100&sort=price")).hasSizeGreaterThan(1).isSorted();
        // the control: kickoff order is not already price order, so the assertion above bites
        List<BigDecimal> byKickoff = fromPrices("/api/events?size=100");
        assertThat(byKickoff).isNotEqualTo(byKickoff.stream().sorted().toList());
    }

    /** Every non-null `fromPrice` on a catalog page, in the order the API returned them. */
    private List<BigDecimal> fromPrices(String url) throws Exception {
        String body = mvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<BigDecimal> prices = new ArrayList<>();
        for (JsonNode event : new ObjectMapper().readTree(body).get("content")) {
            JsonNode from = event.get("fromPrice");
            if (from != null && !from.isNull()) {
                prices.add(from.decimalValue());
            }
        }
        return prices;
    }

    @Test
    void event_detail_by_slug_returns_tiers_and_sections() throws Exception {
        mvc.perform(get("/api/events/" + PUBLISHED_SLUG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(PUBLISHED_SLUG))
                .andExpect(jsonPath("$.tiers").isArray())
                .andExpect(jsonPath("$.sections").isArray());
    }

    @Test
    void event_seats_have_the_layout_shape() throws Exception {
        mvc.perform(get("/api/events/" + PUBLISHED_SLUG + "/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].label").exists())
                .andExpect(jsonPath("$[0].tierCode").exists())
                .andExpect(jsonPath("$[0].price").exists());
    }

    /**
     * A draft is unannounced: its name, prices and seat layout are not public, and the list
     * endpoint already hides it. Detail and seats therefore report it missing rather than
     * confirming it exists, so an id or slug cannot be guessed into a preview of it.
     */
    @Test
    void a_draft_event_is_not_found_on_the_public_detail_endpoint() throws Exception {
        mvc.perform(get("/api/events/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void a_draft_events_seats_are_not_found_on_the_public_endpoint() throws Exception {
        mvc.perform(get("/api/events/1/seats"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void unknown_event_slug_is_not_found() throws Exception {
        mvc.perform(get("/api/events/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
