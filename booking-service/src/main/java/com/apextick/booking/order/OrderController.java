package com.apextick.booking.order;

import com.apextick.booking.order.dto.CreateOrderRequest;
import com.apextick.booking.order.dto.OrderResponse;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders")
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping
    @Operation(summary = "Create an order from the caller's held seats (idempotent)")
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            CurrentUser user) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new com.apextick.booking.web.UnprocessableException(
                    com.apextick.booking.web.ErrorCodes.IDEMPOTENCY_KEY_MISSING,
                    "Idempotency-Key header is required");
        }
        OrderResponse order = orders.create(request, idempotencyKey, user);
        return ResponseEntity.created(URI.create("/api/orders/" + order.id())).body(order);
    }

    @GetMapping("/me")
    @Operation(summary = "The caller's orders")
    public PageResponse<OrderResponse> mine(CurrentUser user,
                                            @PageableDefault(size = 20) Pageable pageable) {
        return orders.mine(user, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Order detail")
    public OrderResponse get(@PathVariable UUID id, CurrentUser user) {
        return orders.get(id, user);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a pending order and release its seats")
    public OrderResponse cancel(@PathVariable UUID id, CurrentUser user) {
        return orders.cancel(id, user);
    }
}
