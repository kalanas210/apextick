package com.apextick.booking.seat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class SeatService {

    private static final Duration HOLD_DURATION = Duration.ofMinutes(5);

    private final SeatRepository seatRepository;

    @Transactional
    public Seat holdSeat(Long seatId, String userId) {
        Instant heldUntil = Instant.now().plus(HOLD_DURATION);

        int updated = seatRepository.holdSeat(seatId, userId, heldUntil);
        if (updated == 0) {
            throw new SeatUnavailableException(seatId);
        }

        return seatRepository.findById(seatId).orElseThrow();
    }
}