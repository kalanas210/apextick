package com.apextick.booking.ticket;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    List<Ticket> findByOrderId(UUID orderId);

    Optional<Ticket> findByQrToken(String qrToken);

    long countByEventIdAndStatus(Long eventId, TicketStatus status);

    /**
     * Admits the ticket if, and only if, it is still ISSUED. The same atomic conditional update the
     * seat hold uses: of two scans racing on one ticket, exactly one moves the row and gets 1, and
     * the other finds it already USED and gets 0.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Ticket t
               set t.status = com.apextick.booking.ticket.TicketStatus.USED, t.usedAt = :usedAt, t.usedBy = :usedBy,
                   t.usedGate = :usedGate
             where t.id = :id and t.status = com.apextick.booking.ticket.TicketStatus.ISSUED
            """)
    int admit(@Param("id") UUID id, @Param("usedAt") Instant usedAt, @Param("usedBy") String usedBy,
              @Param("usedGate") String usedGate);
}
