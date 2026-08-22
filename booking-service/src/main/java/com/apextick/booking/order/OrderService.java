package com.apextick.booking.order;

import com.apextick.booking.catalog.Event;
import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.config.AppProperties;
import com.apextick.booking.order.dto.CreateOrderRequest;
import com.apextick.booking.order.dto.OrderResponse;
import com.apextick.booking.outbox.AfterCommit;
import com.apextick.booking.outbox.DomainEventPublisher;
import com.apextick.booking.outbox.EventTypes;
import com.apextick.booking.outbox.payload.SeatReleasedPayload;
import com.apextick.booking.realtime.RealtimePublisher;
import com.apextick.booking.realtime.dto.SeatStatusChange;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.SeatStatus;
import com.apextick.booking.web.ConflictException;
import com.apextick.booking.web.ErrorCodes;
import com.apextick.booking.web.NotFoundException;
import com.apextick.booking.web.PageResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final EventRepository events;
    private final SeatRepository seats;
    private final DomainEventPublisher domainEvents;
    private final RealtimePublisher realtime;
    private final BigDecimal feePercent;
    private final java.time.Duration paymentWindow;

    public OrderService(OrderRepository orders, EventRepository events, SeatRepository seats,
                        DomainEventPublisher domainEvents, RealtimePublisher realtime, AppProperties props) {
        this.orders = orders;
        this.events = events;
        this.seats = seats;
        this.domainEvents = domainEvents;
        this.realtime = realtime;
        this.feePercent = props.order().feePercent();
        this.paymentWindow = props.order().paymentWindow();
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request, String idempotencyKey, CurrentUser user) {
        // idempotent replay
        var existing = orders.findByUserSubAndIdempotencyKey(user.sub(), idempotencyKey);
        if (existing.isPresent()) {
            return OrderResponse.from(existing.get());
        }

        Event event = events.findById(request.eventId())
                .orElseThrow(() -> new NotFoundException("Event", request.eventId()));
        List<Long> seatIds = request.seatIds().stream().distinct().sorted().toList();

        List<Seat> held = seats.findByIdsWithLayout(seatIds);
        if (held.size() != seatIds.size()
                || held.stream().anyMatch(s -> !s.getEventId().equals(event.getId())
                || s.getStatus() != SeatStatus.HELD
                || !user.sub().equals(s.getHeldBy()))) {
            throw new ConflictException(ErrorCodes.HOLD_EXPIRED,
                    "Your hold on one or more seats is no longer valid",
                    java.util.Map.of("seatIds", seatIds));
        }
        if (orders.existsPendingForSeats(seatIds)) {
            throw new ConflictException(ErrorCodes.ORDER_ALREADY_PENDING,
                    "There is already a pending order for one of these seats");
        }

        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setOrderNumber(OrderNumber.next());
        order.setUserSub(user.sub());
        order.setUserEmail(user.email());
        order.setUserName(user.name());
        order.setEvent(event);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setCurrency(event.getCurrency());
        order.setIdempotencyKey(idempotencyKey);
        order.setCreatedAt(Instant.now());

        BigDecimal subtotal = BigDecimal.ZERO;
        for (Seat s : held) {
            OrderItem item = new OrderItem();
            item.setSeatId(s.getId());
            item.setSeatLabel(s.getSeatNumber());
            item.setSectionName(s.getSection().getName());
            item.setTierCode(s.getSection().getTier().getCode());
            item.setTierName(s.getSection().getTier().getName());
            item.setUnitPrice(s.getSection().getTier().getPrice());
            order.addItem(item);
            subtotal = subtotal.add(s.getSection().getTier().getPrice());
        }
        BigDecimal fee = subtotal.multiply(feePercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        order.setSubtotal(subtotal);
        order.setFee(fee);
        order.setTotal(subtotal.add(fee));

        Instant soonestHold = held.stream().map(Seat::getHeldUntil).min(Instant::compareTo).orElse(null);
        Instant window = Instant.now().plus(paymentWindow);
        order.setExpiresAt(soonestHold == null || window.isBefore(soonestHold) ? window : soonestHold);

        try {
            orders.saveAndFlush(order);
        } catch (DataIntegrityViolationException e) {
            // concurrent create with the same idempotency key won the unique constraint
            return OrderResponse.from(orders.findByUserSubAndIdempotencyKey(user.sub(), idempotencyKey)
                    .orElseThrow(() -> e));
        }
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id, CurrentUser user) {
        Order order = user.isAdmin()
                ? orders.findById(id).orElseThrow(() -> new NotFoundException("Order", id))
                : orders.findByIdAndUserSub(id, user.sub()).orElseThrow(() -> new NotFoundException("Order", id));
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> mine(CurrentUser user, Pageable pageable) {
        return PageResponse.of(orders.findByUserSubOrderByCreatedAtDesc(user.sub(), pageable), OrderResponse::from);
    }

    @Transactional
    public OrderResponse cancel(UUID id, CurrentUser user) {
        Order order = orders.findByIdAndUserSub(id, user.sub())
                .orElseThrow(() -> new NotFoundException("Order", id));
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ConflictException(ErrorCodes.ORDER_NOT_PAYABLE, "Order can no longer be cancelled");
        }
        releaseSeatsAndClose(order, OrderStatus.CANCELLED, "USER_CANCELLED");
        return OrderResponse.from(order);
    }

    /** Called by the expiry sweeper. */
    @Transactional
    public void expire(UUID id) {
        Order order = orders.findById(id).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return;
        }
        releaseSeatsAndClose(order, OrderStatus.EXPIRED, "EXPIRED");
    }

    private void releaseSeatsAndClose(Order order, OrderStatus status, String reason) {
        List<Long> seatIds = order.getItems().stream().map(OrderItem::getSeatId).toList();
        // release only the seats still HELD by this buyer
        seats.releaseSeatsHeldBy(seatIds, order.getUserSub());
        order.setStatus(status);
        order.setCancelReason(reason);
        order.setCancelledAt(Instant.now());
        order.setUpdatedAt(Instant.now());

        Long eventId = order.getEvent().getId();
        for (Long seatId : seatIds) {
            domainEvents.publish(EventTypes.SEAT_RELEASED, "seat", String.valueOf(seatId),
                    new SeatReleasedPayload(seatId, eventId, "ORDER_" + reason));
        }
        domainEvents.publish(EventTypes.ORDER_CANCELLED, "order", order.getId().toString(),
                new java.util.LinkedHashMap<>(java.util.Map.of(
                        "orderId", order.getId().toString(), "orderNumber", order.getOrderNumber(),
                        "userSub", order.getUserSub(), "userEmail", String.valueOf(order.getUserEmail()),
                        "eventId", eventId, "reason", reason)));
        List<SeatStatusChange> changes = seatIds.stream()
                .map(sid -> new SeatStatusChange(sid, SeatStatus.AVAILABLE.name(), null)).toList();
        AfterCommit.run(() -> realtime.seatStatusChanged(eventId, changes));
    }
}
