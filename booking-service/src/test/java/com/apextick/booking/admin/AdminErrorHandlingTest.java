package com.apextick.booking.admin;

import com.apextick.booking.support.IntegrationTest;
import com.apextick.booking.support.TestTokens;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bad admin input used to come back as 500 INTERNAL_ERROR, which is indistinguishable
 * from a real fault and gives a UI nothing to say. These pin the 4xx contract.
 */
@IntegrationTest
class AdminErrorHandlingTest {

    @Autowired MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    private String adminToken() {
        return "Bearer " + TestTokens.admin("admin-1", "admin1", "admin@apextick.local");
    }

    private long createEvent(String slug) throws Exception {
        Map<String, Object> event = Map.of(
                "name", "Error Fixture", "slug", slug, "sport", "football",
                "startsAt", "2027-05-01T18:00:00Z", "venue", "Test Arena", "currency", "USD");
        String created = mvc.perform(post("/api/admin/events").header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(event)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(created).get("id").asLong();
    }

    private void applyLayout(long eventId) throws Exception {
        Map<String, Object> layout = Map.of(
                "tiers", List.of(Map.of("code", "std", "name", "Standard", "price", 100)),
                "sections", List.of(Map.of("code", "main", "name", "Main", "tierCode", "std",
                        "side", "n", "rows", 2, "seatsPerRow", 2)));
        mvc.perform(post("/api/admin/events/" + eventId + "/layout").header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(layout)))
                .andExpect(status().isCreated());
    }

    @Test
    void unknown_order_status_filter_is_a_bad_request() throws Exception {
        mvc.perform(get("/api/admin/orders?status=NOPE").header("Authorization", adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void order_status_filter_accepts_the_lowercase_form() throws Exception {
        mvc.perform(get("/api/admin/orders?status=paid").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void status_patch_without_a_status_is_a_bad_request() throws Exception {
        long eventId = createEvent("error-status-" + System.nanoTime());

        mvc.perform(patch("/api/admin/events/" + eventId + "/status").header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("status"));

        mvc.perform(patch("/api/admin/events/" + eventId + "/status").header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"nonsense\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mvc.perform(patch("/api/admin/events/" + eventId + "/status").header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"onsale\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("onsale"));
    }

    @Test
    void releasing_an_unknown_seat_is_a_not_found() throws Exception {
        mvc.perform(post("/api/admin/seats/999999/release").header("Authorization", adminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void deleting_an_event_takes_its_layout_with_it() throws Exception {
        String slug = "error-delete-" + System.nanoTime();
        long eventId = createEvent(slug);
        applyLayout(eventId);

        mvc.perform(delete("/api/admin/events/" + eventId).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/admin/seats?eventId=" + eventId).header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/events/" + slug))
                .andExpect(status().isNotFound());
    }

    /**
     * The time zone is free text, and it is parsed only when the event is later summarised --
     * so an unparseable one used to be accepted here and then throw on every subsequent read,
     * including the public catalog. ZoneId signals it with DateTimeException, which is not an
     * IllegalArgumentException and so did not reach the 400 handler.
     */
    @Test
    void an_unknown_time_zone_is_a_bad_request() throws Exception {
        Map<String, Object> bad = Map.of(
                "name", "Zone Fixture", "slug", "error-zone-" + System.nanoTime(), "sport", "football",
                "startsAt", "2027-05-01T18:00:00Z", "venue", "Test Arena", "currency", "USD",
                "timeZone", "Sri Lanka");

        mvc.perform(post("/api/admin/events").header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        String slug = "error-zone-ok-" + System.nanoTime();
        Map<String, Object> good = Map.of(
                "name", "Zone Fixture", "slug", slug, "sport", "football",
                "startsAt", "2027-05-01T18:00:00Z", "venue", "Test Arena", "currency", "USD",
                "timeZone", "Asia/Colombo");

        mvc.perform(post("/api/admin/events").header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(good)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.timeZone").value("Asia/Colombo"));
    }
}
