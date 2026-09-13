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

    long countByOrderIdAndStatus(UUID orderId, TicketStatus status);

    @Query("select t.id from Ticket t where t.order.id = :orderId and t.status = :status")
    List<UUID> findIdsByOrderIdAndStatus(@Param("orderId") UUID orderId, @Param("status") TicketStatus status);

    /**
     * Voids an order's tickets that have not been used, and names the seats they were for. A
     * ticket a gate admitted a moment ago is no longer ISSUED and is left as it is, so a caller
     * can tell from the count that came back whether it raced one.
     */
    @Query(value = """
            UPDATE tickets SET status = 'CANCELLED'
             WHERE order_id = :orderId AND status = 'ISSUED'
            RETURNING seat_id
            """, nativeQuery = true)
    List<Long> cancelIssued(@Param("orderId") UUID orderId);

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

    /**
     * Reverses an admission, again only while the ticket is USED, so two admins undoing the same
     * mistake -- or an undo racing a fresh scan -- cannot leave the record saying something the
     * ticket row does not.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Ticket t
               set t.status = com.apextick.booking.ticket.TicketStatus.ISSUED, t.usedAt = null, t.usedBy = null,
                   t.usedGate = null
             where t.id = :id and t.status = com.apextick.booking.ticket.TicketStatus.USED
            """)
    int unadmit(@Param("id") UUID id);
}
