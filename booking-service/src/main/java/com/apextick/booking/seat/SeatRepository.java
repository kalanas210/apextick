package com.apextick.booking.seat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SeatRepository extends JpaRepository<Seat,Long> {

    List<Seat> findByEventIdOrderByIdAsc(Long eventId);

    @Modifying
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

    @Modifying
    @Query(value = "UPDATE seats SET status = 'AVAILABLE', held_by = NULL, held_until = NULL " +
            "WHERE id = :seatId AND status = 'HELD'", nativeQuery = true)
    int releaseSeat(@Param("seatId") Long seatId);

}
