package com.apextick.booking.admin;

import com.apextick.booking.hold.HoldService;
import com.apextick.booking.order.Order;
import com.apextick.booking.order.OrderRepository;
import com.apextick.booking.order.OrderService;
import com.apextick.booking.order.OrderStatus;
import com.apextick.booking.order.dto.CreateOrderRequest;
import com.apextick.booking.order.dto.OrderResponse;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.support.IntegrationTest;
import com.apextick.booking.support.TestTokens;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
    @Autowired SeatRepository seatRepository;
    @Autowired HoldService holdService;
    @Autowired OrderService orderService;
    @Autowired OrderRepository orderRepository;

    private final ObjectMapper json = new ObjectMapper();

    private String adminToken() {
        return "Bearer " + TestTokens.admin("admin-1", "admin1", "admin@apextick.local");
    }

    private long createEvent(String slug) throws Exception {
        return createEvent(slug, "draft");
    }

    private long createEvent(String slug, String status) throws Exception {
        Map<String, Object> event = Map.of(
                "name", "Error Fixture", "slug", slug, "sport", "football",
                "startsAt", "2027-05-01T18:00:00Z", "venue", "Test Arena", "currency", "USD",
                "status", status);
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
     * A cancelled order is still a sales record (it can carry a refunded payment), and it
     * references the event and its seats by plain foreign keys. So even an event whose only
     * order was cancelled refuses deletion and points the admin at cancelling it instead.
     */
    @Test
    void an_event_with_only_a_cancelled_order_is_not_deleted() throws Exception {
        String slug = "error-delete-cancelled-" + System.nanoTime();
        long eventId = createEvent(slug, "onsale");
        applyLayout(eventId);

        CurrentUser buyer = new CurrentUser("delete-guard-buyer", "dgbuyer", "dg@apextick.local",
                "Delete Guard", Set.of("user"));
        List<Long> seatIds = List.of(seatRepository.findByEventIdOrderByIdAsc(eventId).get(0).getId());
        holdService.hold(slug, seatIds, buyer);
        OrderResponse order = orderService.create(new CreateOrderRequest(eventId, seatIds),
                "delete-guard-" + System.nanoTime(), buyer);
        orderService.cancel(UUID.fromString(order.id()), buyer);

        mvc.perform(delete("/api/admin/events/" + eventId).header("Authorization", adminToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_HAS_ORDERS"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("cancelled")));

        assertThat(orderRepository.findById(UUID.fromString(order.id()))).get()
                .extracting(Order::getStatus).isEqualTo(OrderStatus.CANCELLED);
        mvc.perform(get("/api/events/" + slug))
                .andExpect(status().isOk());
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
