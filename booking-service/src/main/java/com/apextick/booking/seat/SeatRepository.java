package com.apextick.booking.seat;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByEventIdOrderByIdAsc(Long eventId);

    @Query("select s from Seat s join fetch s.section sec join fetch sec.tier "
            + "where s.eventId = :eventId order by s.id")
    List<Seat> findAllForEventWithLayout(@Param("eventId") Long eventId);

    @Query("select s from Seat s join fetch s.section sec join fetch sec.tier where s.id in :ids order by s.id")
    List<Seat> findByIdsWithLayout(@Param("ids") Collection<Long> ids);

    // ---- single-seat native ops (legacy endpoint, concurrency test, expiry listener) ----

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE seats
               SET status     = 'HELD',
                   held_by    = :userId,
                   held_until = :heldUntil,
                   version    = version + 1
             WHERE id = :seatId
               AND status = 'AVAILABLE'
            """, nativeQuery = true)
    int holdSeat(@Param("seatId") Long seatId,
                 @Param("userId") String userId,
                 @Param("heldUntil") Instant heldUntil);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE seats SET status = 'AVAILABLE', held_by = NULL, held_until = NULL "
            + "WHERE id = :seatId AND status = 'HELD'", nativeQuery = true)
    int releaseSeat(@Param("seatId") Long seatId);

    // ---- multi-seat atomic hold (single UPDATE statement = deadlock-safe, all-or-nothing via tx) ----

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Seat s
               set s.status = com.apextick.booking.seat.SeatStatus.HELD,
                   s.heldBy = :sub, s.heldUntil = :until, s.version = s.version + 1
             where s.eventId = :eventId and s.id in :ids
               and s.status = com.apextick.booking.seat.SeatStatus.AVAILABLE
            """)
    int holdSeatsAtomically(@Param("eventId") Long eventId, @Param("ids") Collection<Long> ids,
                            @Param("sub") String sub, @Param("until") Instant until);

    @Query("select s.id from Seat s where s.eventId = :eventId and s.id in :ids "
            + "and s.status = com.apextick.booking.seat.SeatStatus.HELD and s.heldBy = :sub")
    List<Long> findHeldSeatIds(@Param("eventId") Long eventId, @Param("ids") Collection<Long> ids,
                               @Param("sub") String sub);

    @Query("select s from Seat s join fetch s.section sec join fetch sec.tier "
            + "where s.eventId = :eventId and s.status = com.apextick.booking.seat.SeatStatus.HELD "
            + "and s.heldBy = :sub order by s.id")
    List<Seat> findMyHolds(@Param("eventId") Long eventId, @Param("sub") String sub);

    @Query("select s.id from Seat s where s.eventId = :eventId "
            + "and s.status = com.apextick.booking.seat.SeatStatus.HELD and s.heldBy = :sub")
    List<Long> findMyHeldSeatIds(@Param("eventId") Long eventId, @Param("sub") String sub);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Seat s
               set s.status = com.apextick.booking.seat.SeatStatus.AVAILABLE,
                   s.heldBy = null, s.heldUntil = null, s.version = s.version + 1
             where s.eventId = :eventId and s.status = com.apextick.booking.seat.SeatStatus.HELD
               and s.heldBy = :sub
            """)
    int releaseMyHolds(@Param("eventId") Long eventId, @Param("sub") String sub);

    // ---- sweeper (fallback for lost Redis expiries) ----

    @Query("select s from Seat s where s.status = com.apextick.booking.seat.SeatStatus.HELD "
            + "and s.heldUntil < :cutoff order by s.id")
    List<Seat> findExpiredHolds(@Param("cutoff") Instant cutoff, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Seat s
               set s.status = com.apextick.booking.seat.SeatStatus.AVAILABLE,
                   s.heldBy = null, s.heldUntil = null, s.version = s.version + 1
             where s.id in :ids and s.status = com.apextick.booking.seat.SeatStatus.HELD
            """)
    int releaseSeats(@Param("ids") Collection<Long> ids);

    // flush-only (no context clear) so callers keep their managed order/event entities attached
    @Modifying(flushAutomatically = true)
    @Query("""
            update Seat s
               set s.status = com.apextick.booking.seat.SeatStatus.AVAILABLE,
                   s.heldBy = null, s.heldUntil = null, s.version = s.version + 1
             where s.id in :ids and s.status = com.apextick.booking.seat.SeatStatus.HELD and s.heldBy = :sub
            """)
    int releaseSeatsHeldBy(@Param("ids") Collection<Long> ids, @Param("sub") String sub);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Seat s
               set s.status = com.apextick.booking.seat.SeatStatus.BOOKED,
                   s.heldUntil = null, s.version = s.version + 1
             where s.id in :ids and s.status = com.apextick.booking.seat.SeatStatus.HELD and s.heldBy = :sub
            """)
    int bookSeatsHeldBy(@Param("ids") Collection<Long> ids, @Param("sub") String sub);

    // ---- counts (catalog) ----

    long countByEventId(Long eventId);

    long countByEventIdAndStatus(Long eventId, SeatStatus status);

    @Query("select s.eventId as eventId, count(s) as total, "
            + "sum(case when s.status = :available then 1 else 0 end) as available "
            + "from Seat s where s.eventId in :eventIds group by s.eventId")
    List<SeatCountView> countByEventIds(@Param("eventIds") Collection<Long> eventIds,
                                        @Param("available") SeatStatus available);

    @Query("select sec.tier.id as tierId, count(s) as total, "
            + "sum(case when s.status = :available then 1 else 0 end) as available "
            + "from Seat s join s.section sec where s.eventId = :eventId group by sec.tier.id")
    List<TierCountView> countByTier(@Param("eventId") Long eventId,
                                    @Param("available") SeatStatus available);

    @Query("select s.section.id as sectionId, "
            + "sum(case when s.status = :available then 1 else 0 end) as available "
            + "from Seat s where s.eventId = :eventId group by s.section.id")
    List<SectionCountView> countBySection(@Param("eventId") Long eventId,
                                          @Param("available") SeatStatus available);

    interface SeatCountView {
        Long getEventId();

        long getTotal();

        long getAvailable();
    }

    interface TierCountView {
        Long getTierId();

        long getTotal();

        long getAvailable();
    }

    interface SectionCountView {
        Long getSectionId();

        long getAvailable();
    }
}
