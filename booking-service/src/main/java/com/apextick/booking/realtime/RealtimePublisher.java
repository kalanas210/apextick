package com.apextick.booking.realtime;

import com.apextick.booking.realtime.dto.SeatStatusChange;
import com.apextick.booking.realtime.dto.SeatStatusMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/** Publishes realtime messages to a Redis channel; every instance relays them to its local broker. */
@Component
public class RealtimePublisher {

    public static final String CHANNEL = "apextick:ws";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public RealtimePublisher(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    public void seatStatusChanged(Long eventId, List<SeatStatusChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return;
        }
        RealtimeMessage envelope = new RealtimeMessage(
                "/topic/events/" + eventId + "/seats", null, SeatStatusMessage.of(eventId, changes));
        redis.convertAndSend(CHANNEL, mapper.writeValueAsString(envelope));
    }
}
