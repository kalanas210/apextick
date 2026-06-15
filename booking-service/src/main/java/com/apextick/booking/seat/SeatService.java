package com.apextick.booking.seat;

import com.apextick.booking.messaging.RabbitConfig;
import com.apextick.booking.messaging.SeatHeldEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class SeatService {

    private static final Duration HOLD_DURATION = Duration.ofSeconds(30);

    private final SeatRepository seatRepository;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redisTemplate;

    public SeatService(SeatRepository seatRepository,
                       RabbitTemplate rabbitTemplate ,
                       StringRedisTemplate redisTemplate) {
        this.seatRepository = seatRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public Seat holdSeat(Long seatId, String userId) {
        Instant heldUntil = Instant.now().plus(HOLD_DURATION);

        int updated = seatRepository.holdSeat(seatId, userId, heldUntil);
        if (updated == 0) {
            throw new SeatUnavailableException(seatId);
        }

        Seat seat = seatRepository.findById(seatId).orElseThrow();

        // Redis TTL key — Redis deletes it when the hold expires
        redisTemplate.opsForValue().set("seat-hold:" + seatId, userId, HOLD_DURATION);

        // publish the event — fire and forget
        SeatHeldEvent event = new SeatHeldEvent(
                seat.getId(), seat.getSeatNumber(), seat.getHeldBy(), seat.getHeldUntil());
        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE, RabbitConfig.SEAT_HELD_ROUTING_KEY, event);

        return seat;
    }

    @Transactional
    public int releaseExpiredHold(Long seatId) {
        return seatRepository.releaseSeat(seatId);
    }

    @Transactional(readOnly = true)
    public List<Seat> getSeatsForEvent(Long eventId) {
        return seatRepository.findByEventIdOrderByIdAsc(eventId);
    }
}