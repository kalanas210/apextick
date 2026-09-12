package com.apextick.booking.order;

import com.apextick.booking.catalog.Event;
import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.catalog.SalesWindow;
import com.apextick.booking.config.AppProperties;
import com.apextick.booking.hold.SeatHoldKeys;
import com.apextick.booking.order.dto.CreateOrderRequest;
import com.apextick.booking.order.dto.OrderResponse;
import com.apextick.booking.outbox.AfterCommit;
import com.apextick.booking.outbox.DomainEventPublisher;
import com.apextick.booking.outbox.EventTypes;
import com.apextick.booking.outbox.payload.SeatReleasedPayload;
import com.apextick.booking.payment.Payment;
import com.apextick.booking.payment.SeatsLostException;
import com.apextick.booking.realtime.RealtimePublisher;
import com.apextick.booking.realtime.dto.SeatStatusChange;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.SeatStatus;
import com.apextick.booking.ticket.Ticket;
import com.apextick.booking.ticket.TicketRepository;
import com.apextick.booking.ticket.TicketStatus;
import com.apextick.booking.web.ConflictException;
import com.apextick.booking.web.ErrorCodes;
import com.apextick.booking.web.NotFoundException;
import com.apextick.booking.web.PageResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrderRepository orders;
    private final EventRepository events;
    private final SeatRepository seats;
    private final TicketRepository tickets;
    private final DomainEventPublisher domainEvents;
    private final RealtimePublisher realtime;
    private final ApplicationEventPublisher appEvents;
    private final SeatHoldKeys holdKeys;
    private final BigDecimal feePercent;
    private final Duration paymentWindow;

    public OrderService(OrderRepository orders, EventRepository events, SeatRepository seats,
                        TicketRepository tickets, DomainEventPublisher domainEvents, RealtimePublisher realtime,
                        ApplicationEventPublisher appEvents, SeatHoldKeys holdKeys, AppProperties props) {
        this.orders = orders;
        this.events = events;
        this.seats = seats;
        this.tickets = tickets;
        this.domainEvents = domainEvents;
        this.realtime = realtime;
        this.appEvents = appEvents;
        this.holdKeys = holdKeys;
        this.feePercent = props.order().feePercent();
        this.paymentWindow = props.order().paymentWindow();
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request, String idempotencyKey, CurrentUser user) {
        var existing = orders.findByUserSubAndIdempotencyKey(user.sub(), idempotencyKey);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        Event event = events.findById(request.eventId())
                .orElseThrow(() -> new NotFoundException("Event", request.eventId()));
        // Re-checked here, not only at hold time: a hold taken while the event was on sale
        // outlives the moment the gates close, the sale ends, or an operator marks it sold out.
        SalesWindow.assertOpen(event);
        List<Long> seatIds = request.seatIds().stream().distinct().sorted().toList();

        List<Seat> held = seats.findByIdsWithLayout(seatIds);
        if (held.size() != seatIds.size()
                || held.stream().anyMatch(s -> !s.getEventId().equals(event.getId())
                || s.getStatus() != SeatStatus.HELD
                || !user.sub().equals(s.getHeldBy()))) {
            throw new ConflictException(ErrorCodes.HOLD_EXPIRED,
                    "Your hold on one or more seats is no longer valid", Map.of("seatIds", seatIds));
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
            return toResponse(orders.findByUserSubAndIdempotencyKey(user.sub(), idempotencyKey).orElseThrow(() -> e));
        }
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id, CurrentUser user) {
        return toResponse(loadOwned(id, user));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> mine(CurrentUser user, Pageable pageable) {
        return PageResponse.of(orders.findByUserSubOrderByCreatedAtDesc(user.sub(), pageable), this::toResponse);
    }

    @Transactional
    public OrderResponse cancel(UUID id, CurrentUser user) {
        Order order = orders.findByIdAndUserSub(id, user.sub())
                .orElseThrow(() -> new NotFoundException("Order", id));
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ConflictException(ErrorCodes.ORDER_NOT_PAYABLE, "Order can no longer be cancelled");
        }
        releaseSeatsAndClose(order, OrderStatus.CANCELLED, "USER_CANCELLED");
        return toResponse(order);
    }

    /** Called by the expiry sweeper. */
    @Transactional
    public void expire(UUID id) {
        close(id, OrderStatus.EXPIRED, "EXPIRED");
    }

    /**
     * An admin force-released one of the order's seats, so it can never be paid. Closing it
     * frees the rest of its seats and stops it blocking the next buyer on those.
     */
    @Transactional
    public void cancelForAdminRelease(UUID id) {
        close(id, OrderStatus.CANCELLED, "ADMIN_RELEASED");
    }

    private void close(UUID id, OrderStatus status, String reason) {
        Order order = orders.findById(id).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return;
        }
        releaseSeatsAndClose(order, status, reason);
    }

    /**
     * Confirms a paid order within the payment transaction: books the seats (all-or-nothing),
     * issues tickets and emits booking.confirmed. Throws {@link SeatsLostException} if a held
     * seat was lost before payment (triggers compensation in the caller).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void confirmPaid(UUID orderId, Payment payment) {
        Order order = orders.findById(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
        if (order.getStatus() == OrderStatus.PAID) {
            return;
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ConflictException(ErrorCodes.ORDER_NOT_PAYABLE, "Order is not payable");
        }
        List<Long> seatIds = order.getItems().stream().map(OrderItem::getSeatId).toList();
        int booked = seats.bookSeatsHeldBy(seatIds, order.getUserSub());
        if (booked != seatIds.size()) {
            throw new SeatsLostException(seatIds);
        }

        Instant now = Instant.now();
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(now);
        order.setUpdatedAt(now);

        List<Map<String, Object>> itemPayloads = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            Ticket ticket = new Ticket();
            ticket.setId(UUID.randomUUID());
            ticket.setOrder(order);
            ticket.setOrderItem(item);
            ticket.setEventId(order.getEvent().getId());
            ticket.setSeatId(item.getSeatId());
            ticket.setQrToken(newQrToken());
            ticket.setStatus(TicketStatus.ISSUED);
            ticket.setIssuedAt(now);
            tickets.save(ticket);

            Map<String, Object> ip = new LinkedHashMap<>();
            ip.put("orderItemId", item.getId());
            ip.put("ticketId", ticket.getId().toString());
            ip.put("seatId", item.getSeatId());
            ip.put("label", item.getSeatLabel());
            ip.put("sectionName", item.getSectionName());
            ip.put("tierName", item.getTierName());
            ip.put("price", item.getUnitPrice());
            ip.put("qrToken", ticket.getQrToken());
            ip.put("pdfS3Key", ticket.getS3Key());
            itemPayloads.add(ip);
        }

        Long eventId = order.getEvent().getId();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId().toString());
        payload.put("orderNumber", order.getOrderNumber());
        payload.put("userSub", order.getUserSub());
        payload.put("userEmail", order.getUserEmail());
        payload.put("userName", order.getUserName());
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("id", eventId);
        ev.put("slug", order.getEvent().getSlug());
        ev.put("name", order.getEvent().getName());
        ev.put("startsAt", order.getEvent().getStartsAt());
        ev.put("timeZone", order.getEvent().getTimeZone());
        ev.put("venue", order.getEvent().getVenue());
        ev.put("city", order.getEvent().getCity());
        ev.put("country", order.getEvent().getCountry());
        payload.put("event", ev);
        payload.put("items", itemPayloads);
        payload.put("subtotal", order.getSubtotal());
        payload.put("fee", order.getFee());
        payload.put("total", order.getTotal());
        payload.put("currency", order.getCurrency());
        Map<String, Object> pay = new LinkedHashMap<>();
        pay.put("paymentId", payment.getId().toString());
        pay.put("provider", payment.getProvider().name());
        pay.put("providerRef", payment.getProviderRef());
        pay.put("cardBrand", payment.getCardBrand());
        pay.put("cardLast4", payment.getCardLast4());
        payload.put("payment", pay);
        payload.put("paidAt", now);
        domainEvents.publish(EventTypes.BOOKING_CONFIRMED, "order", order.getId().toString(), payload);
        // renders ticket PDFs after this transaction commits, off the payment path
        appEvents.publishEvent(new OrderConfirmedEvent(order.getId()));

        List<SeatStatusChange> changes = seatIds.stream()
                .map(sid -> new SeatStatusChange(sid, SeatStatus.BOOKED.name(), null)).toList();
        AfterCommit.run(() -> {
            holdKeys.drop(seatIds);
            realtime.seatStatusChanged(eventId, changes);
        });
    }

    /** Cancels an order whose seats were lost mid-payment (compensation path). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void compensateSeatsLost(UUID orderId) {
        Order order = orders.findById(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            releaseSeatsAndClose(order, OrderStatus.CANCELLED, "SEATS_LOST");
        }
    }

    public OrderResponse toResponse(Order order) {
        Map<Long, UUID> ticketByItem = tickets.findByOrderId(order.getId()).stream()
                .collect(Collectors.toMap(t -> t.getOrderItem().getId(), Ticket::getId));
        return OrderResponse.of(order, ticketByItem);
    }

    public Order loadOwned(UUID id, CurrentUser user) {
        return user.isAdmin()
                ? orders.findById(id).orElseThrow(() -> new NotFoundException("Order", id))
                : orders.findByIdAndUserSub(id, user.sub()).orElseThrow(() -> new NotFoundException("Order", id));
    }

    private void releaseSeatsAndClose(Order order, OrderStatus status, String reason) {
        List<Long> seatIds = order.getItems().stream().map(OrderItem::getSeatId).toList();
        // Only the rows this order actually still held. An order expires at its earliest hold
        // deadline but the sweeper runs every 30s, so by now the next buyer may already hold
        // one of these seats; announcing it AVAILABLE, publishing seat.released for it or
        // dropping its Redis key would be acting on someone else's hold.
        List<Long> released = seats.releaseSeatsHeldBy(seatIds, order.getUserSub());
        order.setStatus(status);
        order.setCancelReason(reason);
        order.setCancelledAt(Instant.now());
        order.setUpdatedAt(Instant.now());

        Long eventId = order.getEvent().getId();
        for (Long seatId : released) {
            domainEvents.publish(EventTypes.SEAT_RELEASED, "seat", String.valueOf(seatId),
                    new SeatReleasedPayload(seatId, eventId, "ORDER_" + reason));
        }
        Map<String, Object> cancelPayload = new LinkedHashMap<>();
        cancelPayload.put("orderId", order.getId().toString());
        cancelPayload.put("orderNumber", order.getOrderNumber());
        cancelPayload.put("userSub", order.getUserSub());
        cancelPayload.put("userEmail", order.getUserEmail());
        cancelPayload.put("eventId", eventId);
        cancelPayload.put("seatIds", seatIds);
        cancelPayload.put("reason", reason);
        domainEvents.publish(EventTypes.ORDER_CANCELLED, "order", order.getId().toString(), cancelPayload);

        List<SeatStatusChange> changes = released.stream()
                .map(sid -> new SeatStatusChange(sid, SeatStatus.AVAILABLE.name(), null)).toList();
        String userSub = order.getUserSub();
        AfterCommit.run(() -> {
            holdKeys.dropOwn(released, userSub);
            realtime.seatStatusChanged(eventId, changes);
        });
    }

    private static String newQrToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
