package com.apextick.booking.realtime;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/** Relays a Redis-fan-out message to this instance's local STOMP broker. */
@Component
public class RedisRealtimeSubscriber implements MessageListener {

    private final SimpMessagingTemplate simp;
    private final ObjectMapper mapper;

    public RedisRealtimeSubscriber(SimpMessagingTemplate simp, ObjectMapper mapper) {
        this.simp = simp;
        this.mapper = mapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        JsonNode node = mapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
        String destination = node.get("destination").asString();
        Object payload = mapper.treeToValue(node.get("payload"), Object.class);
        simp.convertAndSend(destination, payload);
    }
}
