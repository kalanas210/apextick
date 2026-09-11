package com.apextick.notification;

import com.apextick.notification.idempotency.ProcessedEventRepository;
import com.apextick.notification.log.NotificationLogRepository;
import com.apextick.notification.log.NotificationStatus;
import com.apextick.notification.messaging.RabbitTopologyConfig;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
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
        publish("booking.confirmed", envelope(eventId, "booking.confirmed", bookingPayload("winner@apextick.local")));

        assertThat(greenMail.waitForIncomingEmail(15_000, 1)).isTrue();
        assertThat(greenMail.getReceivedMessages()).hasSize(1);
        assertThat(greenMail.getReceivedMessages()[0].getSubject()).contains("World Cup Final");
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
}
