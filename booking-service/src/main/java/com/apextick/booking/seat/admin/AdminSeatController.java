package com.apextick.booking.seat.admin;

import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.dto.AdminSeatResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/seats")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: Seats")
public class AdminSeatController {

    private final SeatRepository seats;

    public AdminSeatController(SeatRepository seats) {
        this.seats = seats;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AdminSeatResponse> byEvent(@RequestParam Long eventId) {
        return seats.findByEventIdOrderByIdAsc(eventId).stream().map(AdminSeatResponse::from).toList();
    }

    @PostMapping("/{id}/release")
    @Transactional
    public AdminSeatResponse release(@PathVariable Long id) {
        seats.releaseSeat(id);
        Seat seat = seats.findById(id).orElseThrow();
        return AdminSeatResponse.from(seat);
    }
}
