package com.apextick.booking.seat;

import com.apextick.booking.seat.dto.HoldSeatResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seats")
public class SeatController {

    private final SeatService seatService;

    public SeatController(SeatService seatService) {
        this.seatService = seatService;
    }

    @PostMapping("/{seatId}/hold")
    public HoldSeatResponse hold(@PathVariable Long seatId,
                                 @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getClaimAsString("preferred_username");
        Seat seat = seatService.holdSeat(seatId, userId);
        return HoldSeatResponse.from(seat);
    }
}