package com.apextick.booking.platform;

import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.hold.HoldService;
import com.apextick.booking.order.OrderService;
import com.apextick.booking.order.dto.CreateOrderRequest;
import com.apextick.booking.order.dto.OrderResponse;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.SeatStatus;
import com.apextick.booking.support.IntegrationTest;
import com.apextick.booking.support.TestTokens;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The IDOR / admin authorization matrix: who may touch someone else's order, tickets and
 * payments, and who may reach the admin API.
 *
 * <p>Ownership is enforced in one place ({@code OrderService.loadOwned}, plus the
 * equivalent check in {@code TicketService}/{@code TicketPdfService}), so a refactor of
 * it -- or a new endpoint that forgets to call it -- would hand other customers' orders
 * and QR admission tokens out. Until now the only cross-user test in the suite was the
 * ticket PDF one.
 *
 * <p>Every owned endpoint answers a stranger with 404, not 403: existence itself is
 * private.
 */
@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthorizationMatrixTest {

    // a slug no other test class touches, so the seat this buys is never contended
    private static final String SLUG = "arsenal-manchester-city";
    private static final String VISA_OK = """
            {"card":{"number":"4242424242424242","expMonth":12,"expYear":2030,"cvc":"123","holder":"Authz Owner"}}""";

    @Autowired MockMvc mvc;
    @Autowired HoldService holdService;
    @Autowired OrderService orderService;
    @Autowired EventRepository eventRepository;
    @Autowired SeatRepository seatRepository;

    private final ObjectMapper json = new ObjectMapper();

    private String ownerToken;
    private String intruderToken;
    private String adminToken;
    private String orderId;
    private String ticketId;

    /** Buys one seat as user A; every test below comes at it as somebody else. */
    @BeforeAll
    void user_a_buys_a_ticket() throws Exception {
        Long eventId = eventRepository.findBySlug(SLUG).orElseThrow().getId();
        List<Long> seatIds = seatRepository.findAllForEventWithLayout(eventId).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE)
                .map(Seat::getId).limit(1).toList();

        CurrentUser owner = new CurrentUser("authz-owner", "authzowner",
                "authzowner@apextick.local", "Authz Owner", Set.of("user"));
        holdService.hold(SLUG, seatIds, owner);
        OrderResponse order = orderService.create(new CreateOrderRequest(eventId, seatIds),
                "authz-order-" + System.nanoTime(), owner);
        orderId = order.id().toString();

        ownerToken = TestTokens.user(owner.sub(), owner.username(), owner.email());
        intruderToken = TestTokens.user("authz-intruder", "intruder", "intruder@apextick.local");
        adminToken = TestTokens.admin("authz-admin", "authzadmin", "authzadmin@apextick.local");

        mvc.perform(post("/api/orders/" + orderId + "/pay")
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(VISA_OK))
                .andExpect(status().isOk());

        String tickets = mvc.perform(get("/api/orders/" + orderId + "/tickets")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ticketId = json.readTree(tickets).get(0).get("id").asText();
    }

    // ---- user A's resources ------------------------------------------------------

    static Stream<Arguments> ownedEndpoints() {
        return Stream.concat(ownedReads(), Stream.of(
                Arguments.of("POST", "/api/orders/{orderId}/cancel"),
                Arguments.of("POST", "/api/orders/{orderId}/pay")));
    }

    static Stream<Arguments> ownedReads() {
        return Stream.of(
                Arguments.of("GET", "/api/orders/{orderId}"),
                Arguments.of("GET", "/api/orders/{orderId}/payments"),
                Arguments.of("GET", "/api/orders/{orderId}/tickets"),
                Arguments.of("GET", "/api/tickets/{ticketId}"),
                Arguments.of("GET", "/api/tickets/{ticketId}/pdf"));
    }

    private MockHttpServletRequestBuilder call(String method, String template) {
        String path = template.replace("{orderId}", orderId).replace("{ticketId}", ticketId);
        MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(method), URI.create(path));
        if (!"GET".equals(method)) {
            builder.contentType(MediaType.APPLICATION_JSON).content("{}")
                    .header("Idempotency-Key", UUID.randomUUID().toString());
        }
        return builder;
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("ownedEndpoints")
    void another_user_gets_404_on_someone_elses_order(String method, String template) throws Exception {
        mvc.perform(call(method, template).header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("ownedEndpoints")
    void owned_endpoints_reject_anonymous_callers(String method, String template) throws Exception {
        mvc.perform(call(method, template)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("ownedReads")
    void an_admin_can_read_any_customers_order(String method, String template) throws Exception {
        mvc.perform(call(method, template).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // ---- the admin API -----------------------------------------------------------

    /** Every mutating route across the admin controllers. */
    static Stream<Arguments> mutatingAdminEndpoints() {
        return Stream.of(
                Arguments.of("POST", "/api/admin/events"),
                Arguments.of("PUT", "/api/admin/events/999999999"),
                Arguments.of("PATCH", "/api/admin/events/999999999/status"),
                Arguments.of("DELETE", "/api/admin/events/999999999"),
                Arguments.of("POST", "/api/admin/events/999999999/layout"),
                Arguments.of("POST", "/api/admin/seats/999999999/release"),
                Arguments.of("POST", "/api/admin/tickets/verify"));
    }

    private MockHttpServletRequestBuilder adminCall(String method, String path) {
        return request(HttpMethod.valueOf(method), URI.create(path))
                .contentType(MediaType.APPLICATION_JSON).content("{}");
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("mutatingAdminEndpoints")
    void mutating_admin_routes_reject_anonymous_callers(String method, String path) throws Exception {
        mvc.perform(adminCall(method, path)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("mutatingAdminEndpoints")
    void mutating_admin_routes_reject_role_user(String method, String path) throws Exception {
        // the filter chain denies before the handler, so this is Spring Security's own
        // 403 (no problem+json body), the same shape ApiSecurityTest pins for reads
        mvc.perform(adminCall(method, path).header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isForbidden());
    }
}
