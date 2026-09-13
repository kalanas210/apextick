package com.apextick.booking.ticket.gate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketScanRepository extends JpaRepository<TicketScan, Long> {
    List<TicketScan> findByTicketIdOrderByScannedAtAscIdAsc(UUID ticketId);
}
