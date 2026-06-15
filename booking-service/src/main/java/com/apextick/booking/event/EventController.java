package com.apextick.booking.event;

import com.apextick.booking.seat.SeatService;
import com.apextick.booking.seat.dto.SeatResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final SeatService seatService;

    public EventController(SeatService seatService) {
        this.seatService = seatService;
    }

    @GetMapping("/{eventId}/seats")
    public List<SeatResponse> getSeats(@PathVariable Long eventId) {
        return seatService.getSeatsForEvent(eventId)
                .stream()
                .map(SeatResponse::from)
                .toList();
    }
}