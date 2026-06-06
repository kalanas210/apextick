package com.apextick.booking.seat;

import com.apextick.booking.seat.dto.HoldSeatRequest;
import com.apextick.booking.seat.dto.HoldSeatResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    @PostMapping("/{seatId}/hold")
    public ResponseEntity<HoldSeatResponse> hold(
            @PathVariable Long seatId,
            @Valid @RequestBody HoldSeatRequest request) {

        Seat held = seatService.holdSeat(seatId, request.userId());
        return ResponseEntity.ok(HoldSeatResponse.from(held));
    }
}