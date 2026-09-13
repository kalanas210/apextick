package com.apextick.notification;

import com.apextick.notification.idempotency.ProcessedEventRepository;
import com.apextick.notification.log.NotificationLogRepository;
import com.apextick.notification.log.NotificationStatus;
import com.apextick.notification.messaging.RabbitTopologyConfig;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NotificationEmailTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    @DynamicPropertySource
    static void mailProps(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", ServerSetupTest.SMTP::getPort);
        // trailing slash on purpose: the order link must not come out as "//orders"
        registry.add("app.public-base-url", () -> "https://tickets.apextick.test/");
    }

    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitAdmin rabbitAdmin;
    @Autowired ProcessedEventRepository processed;
    @Autowired NotificationLogRepository logs;
    @Autowired tools.jackson.databind.ObjectMapper mapper;
    @Autowired JavaMailSenderImpl mailSender;

    private Map<String, Object> bookingPayload(String email) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", 1);
        event.put("slug", "final");
        event.put("name", "World Cup Final");
        event.put("startsAt", "2026-09-01T18:00:00Z");
        event.put("timeZone", "UTC");
        event.put("venue", "MetLife Stadium");
        event.put("city", "New York");
        event.put("country", "United States");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("orderItemId", 1);
        item.put("ticketId", UUID.randomUUID().toString());
        item.put("seatId", 10);
        item.put("label", "A1");
        item.put("sectionName", "North Stand");
        item.put("tierName", "Gold");
        item.put("price", 100);
        item.put("qrToken", "tok-123");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", UUID.randomUUID().toString());
        payload.put("orderNumber", "APX-TEST01");
        payload.put("userSub", "buyer-sub");
        payload.put("userEmail", email); // may be null (poison)
        payload.put("userName", "Buyer");
        payload.put("event", event);
        payload.put("items", List.of(item));
        payload.put("subtotal", 100);
        payload.put("fee", 5);
        payload.put("total", 105);
        payload.put("currency", "USD");
        return payload;
    }

    private String envelope(UUID eventId, String type, Map<String, Object> payload) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("eventId", eventId.toString());
        env.put("type", type);
        env.put("version", 1);
        env.put("occurredAt", Instant.now().toString());
        env.put("aggregateType", "order");
        env.put("aggregateId", "1");
        env.put("payload", payload);
        return mapper.writeValueAsString(env);
    }


    private void publish(String routingKey, String bodyJson) {
        Message message = MessageBuilder.withBody(bodyJson.getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json").build();
        rabbit.send(RabbitTopologyConfig.EXCHANGE, routingKey, message);
    }

    @Test
    void booking_confirmed_sends_an_email_and_records_it() throws Exception {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> payload = bookingPayload("winner@apextick.local");
        publish("booking.confirmed", envelope(eventId, "booking.confirmed", payload));

        assertThat(greenMail.waitForIncomingEmail(15_000, 1)).isTrue();
        assertThat(greenMail.getReceivedMessages()).hasSize(1);
        MimeMessage mail = greenMail.getReceivedMessages()[0];
        assertThat(mail.getSubject()).contains("World Cup Final");
        // the link must land on the frontend's order page (app/orders/[id]), not a 404
        assertThat((String) mail.getContent())
                .contains("href=\"https://tickets.apextick.test/orders/" + payload.get("orderId") + "\"")
                .doesNotContain("/account/tickets");
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(processed.findById(eventId)).isPresent());
    }

    @Test
    void smtp_calls_are_bounded_by_timeouts() {
        // JavaMail's own defaults are infinite; a hung server must fail the send instead.
        assertThat(mailSender.getJavaMailProperties())
                .containsEntry("mail.smtp.connectiontimeout", "10000")
                .containsEntry("mail.smtp.timeout", "10000")
                .containsEntry("mail.smtp.writetimeout", "10000")
                .containsEntry("mail.smtp.ssl.enable", "false");
    }

    @Test
    void duplicate_event_is_processed_once() throws Exception {
        UUID eventId = UUID.randomUUID();
        String msg = envelope(eventId, "booking.confirmed", bookingPayload("dupe@apextick.local"));
        publish("booking.confirmed", msg);
        assertThat(greenMail.waitForIncomingEmail(15_000, 1)).isTrue();

        publish("booking.confirmed", msg);
        Thread.sleep(1500); // give the duplicate a chance to (not) send

        assertThat(greenMail.getReceivedMessages()).hasSize(1);
    }

    @Test
    void poison_message_lands_in_the_dlq_after_retries() {
        UUID eventId = UUID.randomUUID();
        // null recipient -> EmailService throws -> retried -> dead-lettered
        publish("booking.confirmed", envelope(eventId, "booking.confirmed", bookingPayload(null)));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            QueueInformation info = rabbitAdmin.getQueueInfo(RabbitTopologyConfig.Q_BOOKING + ".dlq");
            assertThat(info).isNotNull();
            assertThat(info.getMessageCount()).isGreaterThanOrEqualTo(1);
        });
        assertThat(logs.countByStatus(NotificationStatus.FAILED)).isGreaterThanOrEqualTo(1);
    }

    private Map<String, Object> refundPayload(String email) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("paymentId", UUID.randomUUID().toString());
        payload.put("orderId", UUID.randomUUID().toString());
        payload.put("orderNumber", "APX-REFUND1");
        payload.put("userSub", "buyer-sub");
        payload.put("userEmail", email);
        payload.put("userName", "Buyer");
        payload.put("eventId", 1);
        payload.put("eventName", "World Cup Final");
        payload.put("amount", new BigDecimal("105.00"));
        payload.put("currency", "USD");
        payload.put("reason", "box_office_refund");
        payload.put("refundRef", "re_test_123");
        payload.put("refundedAt", Instant.now().toString());
        return payload;
    }

    @Test
    void an_accepted_refund_is_emailed_with_how_much_and_why() throws Exception {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> payload = refundPayload("refunded@apextick.local");
        publish("payment.refunded", envelope(eventId, "payment.refunded", payload));

        assertThat(greenMail.waitForIncomingEmail(15_000, 1)).isTrue();
        MimeMessage mail = greenMail.getReceivedMessages()[0];
        assertThat(mail.getSubject()).contains("APX-REFUND1");
        assertThat((String) mail.getContent())
                .contains("USD 105.00")
                .contains("The box office refunded this order")
                .contains("href=\"https://tickets.apextick.test/orders/" + payload.get("orderId") + "\"");
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(processed.findById(eventId)).isPresent());
    }

    /** It used to tell every customer a refund had been requested, whether or not they were ever charged. */
    @Test
    void a_cancellation_says_what_happened_instead_of_promising_a_refund() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", UUID.randomUUID().toString());
        payload.put("orderNumber", "APX-EXPIRED");
        payload.put("userSub", "buyer-sub");
        payload.put("userEmail", "expired@apextick.local");
        payload.put("eventId", 1);
        payload.put("seatIds", List.of(10, 11));
        payload.put("reason", "EXPIRED");
        publish("order.cancelled", envelope(UUID.randomUUID(), "order.cancelled", payload));

        assertThat(greenMail.waitForIncomingEmail(15_000, 1)).isTrue();
        assertThat((String) greenMail.getReceivedMessages()[0].getContent())
                .contains("The payment window closed")
                .doesNotContain("a refund has been requested")
                .doesNotContain("(EXPIRED)");
    }

    private Map<String, Object> eventCancelledPayload(String email, boolean refundDue) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", 1);
        payload.put("eventName", "World Cup Final");
        payload.put("startsAt", "2026-07-19T19:00:00Z");
        payload.put("timeZone", "America/New_York");
        payload.put("venue", "MetLife Stadium");
        payload.put("reason", "Floodlight failure");
        payload.put("orderId", UUID.randomUUID().toString());
        payload.put("orderNumber", "APX-CALLED1");
        payload.put("userEmail", email);
        payload.put("userName", "Fan");
        payload.put("total", new BigDecimal("210.00"));
        payload.put("currency", "USD");
        payload.put("refundDue", refundDue);
        return payload;
    }

    @Test
    void a_cancelled_event_tells_each_buyer_what_happens_to_their_own_order() throws Exception {
        publish("event.cancelled", envelope(UUID.randomUUID(), "event.cancelled",
                eventCancelledPayload("paid-fan@apextick.local", true)));

        assertThat(greenMail.waitForIncomingEmail(15_000, 1)).isTrue();
        MimeMessage mail = greenMail.getReceivedMessages()[0];
        assertThat(mail.getSubject()).isEqualTo("World Cup Final has been cancelled");
        assertThat((String) mail.getContent())
                // on the event's own clock: 19:00 UTC is 15:00 in New York in July
                .contains("Sun 19 Jul 2026, 15:00")
                .contains("Floodlight failure")
                .contains("refunded in full")
                .contains("USD 210.00");
    }

    /** A buyer whose unpaid order went down with its event hears it once, from event.cancelled. */
    @Test
    void an_order_cancelled_with_its_event_sends_no_second_email() throws Exception {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", UUID.randomUUID().toString());
        payload.put("orderNumber", "APX-CALLED2");
        payload.put("userSub", "buyer-sub");
        payload.put("userEmail", "unpaid-fan@apextick.local");
        payload.put("eventId", 1);
        payload.put("seatIds", List.of(12));
        payload.put("reason", "EVENT_CANCELLED");
        publish("order.cancelled", envelope(eventId, "order.cancelled", payload));

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(processed.findById(eventId)).isPresent());
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    /** A 3-D Secure challenge left unanswered used to fail in silence while the order ran out its window. */
    @Test
    void a_payment_that_failed_after_the_buyer_left_sends_them_back_to_finish_paying() throws Exception {
        String orderId = UUID.randomUUID().toString();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("paymentId", UUID.randomUUID().toString());
        payload.put("orderId", orderId);
        payload.put("orderNumber", "APX-FAILED1");
        payload.put("userEmail", "unpaid@apextick.local");
        payload.put("userName", "Fan");
        payload.put("eventName", "World Cup Final");
        payload.put("total", new BigDecimal("105.00"));
        payload.put("currency", "USD");
        payload.put("failureCode", "insufficient_funds");
        payload.put("expiresAt", "2026-07-19T14:32:00Z");
        publish("payment.failed", envelope(UUID.randomUUID(), "payment.failed", payload));

        assertThat(greenMail.waitForIncomingEmail(15_000, 1)).isTrue();
        MimeMessage mail = greenMail.getReceivedMessages()[0];
        assertThat(mail.getSubject()).contains("APX-FAILED1").contains("did not go through");
        assertThat((String) mail.getContent())
                .contains("insufficient funds")
                .contains("14:32 UTC on 19 Jul")
                .contains("href=\"https://tickets.apextick.test/checkout/" + orderId + "\"");
    }
}
