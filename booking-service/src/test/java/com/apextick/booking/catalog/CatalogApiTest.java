package com.apextick.booking.catalog;

import com.apextick.booking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the public catalog endpoints. Runs against the migrated schema; the
 * only event present without the demo catalog seed is the legacy (DRAFT) event 1.
 */
@IntegrationTest
class CatalogApiTest {

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
        // the legacy event is DRAFT, so it must not appear in the public listing
        mvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void event_detail_by_id_returns_tiers_and_sections() throws Exception {
        mvc.perform(get("/api/events/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").exists())
                .andExpect(jsonPath("$.tiers").isArray())
                .andExpect(jsonPath("$.sections").isArray());
    }

    @Test
    void event_seats_have_the_layout_shape() throws Exception {
        mvc.perform(get("/api/events/1/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].label").exists())
                .andExpect(jsonPath("$[0].tierCode").exists())
                .andExpect(jsonPath("$[0].price").exists());
    }

    @Test
    void unknown_event_slug_is_not_found() throws Exception {
        mvc.perform(get("/api/events/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
