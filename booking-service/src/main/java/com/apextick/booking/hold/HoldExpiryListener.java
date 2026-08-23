package com.apextick.booking.hold;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** Releases a seat when its Redis TTL key expires (primary hold-expiry trigger). */
@Component
public class HoldExpiryListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryListener.class);
    private static final String KEY_PREFIX = "seat-hold:";

    private final HoldService holdService;

    public HoldExpiryListener(HoldService holdService) {
        this.holdService = holdService;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = new String(message.getBody(), StandardCharsets.UTF_8);
        if (!expiredKey.startsWith(KEY_PREFIX)) {
            return;
        }
        Long seatId = Long.valueOf(expiredKey.substring(KEY_PREFIX.length()));
        if (holdService.releaseExpired(seatId)) {
            log.info("Hold expired - seat {} released back to AVAILABLE", seatId);
        }
    }
}
