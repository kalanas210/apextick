package com.apextick.notification.messaging;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

/** Parses the {eventId,type,occurredAt,payload} envelope without relying on AMQP __TypeId__ headers. */
@Component
public class EnvelopeParser {

    private final ObjectMapper mapper;

    public EnvelopeParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public <T> EventEnvelope<T> parse(byte[] body, Class<T> payloadType) {
        JsonNode root = mapper.readTree(body);
        return new EventEnvelope<>(
                UUID.fromString(root.get("eventId").asString()),
                root.get("type").asString(),
                Instant.parse(root.get("occurredAt").asString()),
                mapper.treeToValue(root.get("payload"), payloadType));
    }
}
