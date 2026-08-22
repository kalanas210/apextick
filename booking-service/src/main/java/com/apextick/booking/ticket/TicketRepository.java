package com.apextick.booking.ticket;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    List<Ticket> findByOrderId(UUID orderId);

    Optional<Ticket> findByQrToken(String qrToken);
}
