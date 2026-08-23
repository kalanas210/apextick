package com.apextick.booking.ticket;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class TicketPdfDownloadTest {

    private static final String SLUG = "tottenham-chelsea-derby";
    private static final String VISA_OK = """
            {"card":{"number":"4242424242424242","expMonth":12,"expYear":2030,"cvc":"123","holder":"PDF Buyer"}}""";

    @Autowired MockMvc mvc;
    @Autowired HoldService holdService;
    @Autowired OrderService orderService;
    @Autowired EventRepository eventRepository;
    @Autowired SeatRepository seatRepository;

    private final ObjectMapper json = new ObjectMapper();

    private record Booked(String ticketId, String token) {
    }

    /** Hold a seat, buy it, and return the issued ticket id plus the buyer's token. */
    private Booked bookOneSeat(String sub, String username) throws Exception {
        Long eventId = eventRepository.findBySlug(SLUG).orElseThrow().getId();
        List<Long> seatIds = seatRepository.findAllForEventWithLayout(eventId).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE).map(Seat::getId).limit(1).toList();

        String email = username + "@apextick.local";
        CurrentUser user = new CurrentUser(sub, username, email, "PDF Buyer", Set.of("user"));
        holdService.hold(SLUG, seatIds, user);
        OrderResponse order = orderService.create(new CreateOrderRequest(eventId, seatIds),
                "pdf-order-" + System.nanoTime(), user);

        String token = TestTokens.user(sub, username, email);
        mvc.perform(post("/api/orders/" + order.id() + "/pay")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(VISA_OK))
                .andExpect(status().isOk());

        String body = mvc.perform(get("/api/orders/" + order.id() + "/tickets")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pdfUrl").exists())
                .andReturn().getResponse().getContentAsString();

        return new Booked(json.readTree(body).get(0).get("id").asText(), token);
    }

    @Test
    void the_owner_can_download_a_ticket_as_a_pdf() throws Exception {
        Booked booked = bookOneSeat("pdf-buyer", "pdfbuyer");

        byte[] pdf = mvc.perform(get("/api/tickets/" + booked.ticketId() + "/pdf")
                        .header("Authorization", "Bearer " + booked.token()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("apextick-ticket-")))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    @Test
    void a_second_download_is_served_from_storage_and_matches_the_first() throws Exception {
        Booked booked = bookOneSeat("pdf-cache", "pdfcache");

        byte[] first = mvc.perform(get("/api/tickets/" + booked.ticketId() + "/pdf")
                        .header("Authorization", "Bearer " + booked.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        byte[] second = mvc.perform(get("/api/tickets/" + booked.ticketId() + "/pdf")
                        .header("Authorization", "Bearer " + booked.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void another_user_cannot_download_someone_elses_ticket() throws Exception {
        Booked booked = bookOneSeat("pdf-owner", "pdfowner");
        String intruder = TestTokens.user("pdf-intruder", "intruder", "intruder@apextick.local");

        mvc.perform(get("/api/tickets/" + booked.ticketId() + "/pdf")
                        .header("Authorization", "Bearer " + intruder))
                .andExpect(status().isNotFound());
    }

    @Test
    void the_pdf_endpoint_requires_authentication() throws Exception {
        mvc.perform(get("/api/tickets/" + UUID.randomUUID() + "/pdf"))
                .andExpect(status().isUnauthorized());
    }
}
