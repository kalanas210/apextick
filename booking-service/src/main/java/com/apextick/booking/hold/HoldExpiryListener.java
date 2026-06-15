package com.apextick.booking.hold;

import com.apextick.booking.seat.SeatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class HoldExpiryListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryListener.class);
    private static final String KEY_PREFIX = "seat-hold:";

    private final SeatService seatService;

    public HoldExpiryListener(SeatService seatService) {
        this.seatService = seatService;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = new String(message.getBody(), StandardCharsets.UTF_8);
        if (!expiredKey.startsWith(KEY_PREFIX)) {
            return; // some other key expired — ignore it
        }
        Long seatId = Long.valueOf(expiredKey.substring(KEY_PREFIX.length()));
        int released = seatService.releaseExpiredHold(seatId);
        if (released > 0) {
            log.info("Hold expired — seat {} released back to AVAILABLE", seatId);
        }
    }
}