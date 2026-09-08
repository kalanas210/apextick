package com.apextick.booking.admin;

import com.apextick.booking.support.IntegrationTest;
import com.apextick.booking.support.TestTokens;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The admin list exists precisely because the public one hides drafts — an event the
 * panel had just created was invisible to the panel. These tests pin that difference.
 */
@IntegrationTest
class AdminEventListTest {

    @Autowired MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    private String adminToken() {
        return "Bearer " + TestTokens.admin("admin-1", "admin1", "admin@apextick.local");
    }

    private String createDraft(String slug) throws Exception {
        Map<String, Object> event = Map.of(
                "name", "Draft Fixture", "slug", slug, "sport", "football",
                "startsAt", "2027-03-01T18:00:00Z", "venue", "Test Arena",
                "currency", "USD", "status", "draft");
        return mvc.perform(post("/api/admin/events").header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(event)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("draft"))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void admin_list_shows_a_draft_the_public_catalog_hides() throws Exception {
        String slug = "admin-draft-" + System.nanoTime();
        createDraft(slug);

        mvc.perform(get("/api/admin/events?size=100&q=Draft Fixture").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.slug == '" + slug + "')]").exists());

        mvc.perform(get("/api/events?size=100&q=Draft Fixture"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.slug == '" + slug + "')]").doesNotExist());
    }

    @Test
    void admin_list_filters_by_status_and_pages() throws Exception {
        createDraft("admin-draft-" + System.nanoTime());

        mvc.perform(get("/api/admin/events?status=draft&size=1").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value("draft"));
    }

    @Test
    void admin_can_look_up_teams_for_the_event_form() throws Exception {
        mvc.perform(get("/api/admin/teams?sport=cricket").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$[0].short").exists());
    }

    @Test
    void admin_list_is_forbidden_without_the_role() throws Exception {
        mvc.perform(get("/api/admin/events")
                        .header("Authorization", "Bearer " + TestTokens.user("u-1", "user1", "user1@apextick.local")))
                .andExpect(status().isForbidden());
    }
}
