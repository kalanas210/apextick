package com.apextick.booking.ticket;

import com.apextick.booking.order.OrderService;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.ticket.dto.TicketResponse;
import com.apextick.booking.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TicketService {

    private final TicketRepository tickets;
    private final OrderService orderService;

    public TicketService(TicketRepository tickets, OrderService orderService) {
        this.tickets = tickets;
        this.orderService = orderService;
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> forOrder(UUID orderId, CurrentUser user) {
        orderService.loadOwned(orderId, user); // enforces ownership / admin
        return tickets.findByOrderId(orderId).stream().map(TicketResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TicketResponse get(UUID ticketId, CurrentUser user) {
        Ticket ticket = tickets.findById(ticketId).orElseThrow(() -> new NotFoundException("Ticket", ticketId));
        if (!user.isAdmin() && !user.sub().equals(ticket.getOrder().getUserSub())) {
            throw new NotFoundException("Ticket", ticketId);
        }
        return TicketResponse.from(ticket);
    }
}
