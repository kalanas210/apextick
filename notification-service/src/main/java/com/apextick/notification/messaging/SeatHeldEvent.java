package com.apextick.notification.messaging;

import java.time.Instant;

public record SeatHeldEvent(
        Long seatId,
        String seatNumber,
        String heldBy,
        Instant heldUntil
) {}