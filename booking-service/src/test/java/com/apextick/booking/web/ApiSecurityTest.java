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
        // event 1 is seeded by Liquibase (demo context) with 20 seats
        mvc.perform(get("/api/events/1/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void unknown_path_returns_problem_detail() throws Exception {
        String token = TestTokens.user("sub-x", "x", "x@apextick.local");
        mvc.perform(get("/api/nope").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
