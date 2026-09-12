package com.apextick.booking.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdAndUserSub(UUID id, String userSub);

    Optional<Order> findByUserSubAndIdempotencyKey(String userSub, String idempotencyKey);

    Page<Order> findByUserSubOrderByCreatedAtDesc(String userSub, Pageable pageable);

    @Query("select o.id from Order o where o.status = com.apextick.booking.order.OrderStatus.PENDING_PAYMENT "
            + "and o.expiresAt < :now order by o.expiresAt")
    List<UUID> findExpiredPendingIds(@Param("now") Instant now, Pageable pageable);

    @Query("select count(oi) > 0 from OrderItem oi where oi.seatId in :seatIds "
            + "and oi.order.status = com.apextick.booking.order.OrderStatus.PENDING_PAYMENT")
    boolean existsPendingForSeats(@Param("seatIds") Collection<Long> seatIds);

    /**
     * Which of these seats <em>this buyer's</em> own unpaid order still covers, so a release can
     * refuse them by name. Scoped to the caller on purpose: a stranger's abandoned order can
     * still name a seat this buyer now holds -- the expiry listener frees the seat the moment
     * the hold lapses while the order waits up to a sweeper tick to be cancelled -- and telling
     * this buyer to cancel an order they do not own and cannot see would strand their seats.
     */
    @Query("select distinct oi.seatId from OrderItem oi where oi.seatId in :seatIds "
            + "and oi.order.userSub = :userSub "
            + "and oi.order.status = com.apextick.booking.order.OrderStatus.PENDING_PAYMENT")
    List<Long> findPendingSeatIds(@Param("seatIds") Collection<Long> seatIds,
                                  @Param("userSub") String userSub);

    @Query("select distinct oi.order.id from OrderItem oi where oi.seatId = :seatId "
            + "and oi.order.status = com.apextick.booking.order.OrderStatus.PENDING_PAYMENT")
    List<UUID> findPendingOrderIdsForSeat(@Param("seatId") Long seatId);

    boolean existsByEventId(Long eventId);

    org.springframework.data.domain.Page<Order> findAllByOrderByCreatedAtDesc(org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status, org.springframework.data.domain.Pageable pageable);

    @Query("select coalesce(sum(o.total), 0) from Order o where o.event.id = :eventId and o.status = com.apextick.booking.order.OrderStatus.PAID")
    java.math.BigDecimal paidRevenueForEvent(@Param("eventId") Long eventId);
}
