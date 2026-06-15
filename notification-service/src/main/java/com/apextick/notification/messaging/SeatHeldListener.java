package com.apextick.notification.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class SeatHeldListener {

    private static final Logger log = LoggerFactory.getLogger(SeatHeldListener.class);

    @RabbitListener(queues = RabbitConfig.SEAT_HELD_QUEUE)
    public void onSeatHeld(SeatHeldEvent event) {
        log.info("Notification sent to {} — seat {} ({}) is held until {}",
                event.heldBy(), event.seatNumber(), event.seatId(), event.heldUntil());
    }
}