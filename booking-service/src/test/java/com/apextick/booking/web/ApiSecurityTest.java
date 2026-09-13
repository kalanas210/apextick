package com.apextick.booking.web;

import com.apextick.booking.support.IntegrationTest;
import com.apextick.booking.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class ApiSecurityTest {

    @Autowired
    MockMvc mvc;

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void me_is_unauthorized_without_a_token() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_returns_profile_and_roles_for_an_authenticated_user() throws Exception {
        String token = TestTokens.user("sub-alice", "alice", "alice@apextick.local");
        mvc.perform(get("/api/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sub").value("sub-alice"))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.roles").isArray());
    }

    @Test
    void admin_area_is_forbidden_for_a_non_admin() throws Exception {
        String token = TestTokens.user("sub-bob", "bob", "bob@apextick.local");
        mvc.perform(get("/api/admin/anything").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void public_seats_endpoint_is_reachable_anonymously() throws Exception {
        // a published event from the demo catalog seed
        mvc.perform(get("/api/events/arsenal-manchester-city/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    /**
     * GET /api/events/** is permitAll, so visibility is the service's job, not the filter
     * chain's: the legacy event 1 is DRAFT and must not be readable by anyone who guesses it.
     */
    @Test
    void a_draft_event_is_not_readable_anonymously() throws Exception {
        mvc.perform(get("/api/events/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/api/events/1/seats"))
                .andExpect(status().isNotFound());
    }

    /**
     * holds/me sits under the public GET /api/events/** tree but reads the caller's own
     * holds. It used to be reachable anonymously and then fail on the missing user with a 500.
     */
    @Test
    void my_holds_are_unauthorized_without_a_token() throws Exception {
        mvc.perform(get("/api/events/1/holds/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void my_holds_are_readable_with_a_token() throws Exception {
        String token = TestTokens.user("sub-holds", "holds", "holds@apextick.local");
        mvc.perform(get("/api/events/1/holds/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(1))
                .andExpect(jsonPath("$.seatIds").isArray());
    }

    @Test
    void unknown_path_returns_problem_detail() throws Exception {
        String token = TestTokens.user("sub-x", "x", "x@apextick.local");
        mvc.perform(get("/api/nope").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
