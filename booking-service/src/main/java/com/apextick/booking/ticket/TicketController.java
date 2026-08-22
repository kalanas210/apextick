package com.apextick.booking.ticket;

import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.ticket.dto.TicketResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Tickets")
public class TicketController {

    private final TicketService tickets;

    public TicketController(TicketService tickets) {
        this.tickets = tickets;
    }

    @GetMapping("/api/orders/{orderId}/tickets")
    @Operation(summary = "Tickets issued for an order")
    public List<TicketResponse> forOrder(@PathVariable UUID orderId, CurrentUser user) {
        return tickets.forOrder(orderId, user);
    }

    @GetMapping("/api/tickets/{id}")
    @Operation(summary = "A single ticket")
    public TicketResponse get(@PathVariable UUID id, CurrentUser user) {
        return tickets.get(id, user);
    }
}
