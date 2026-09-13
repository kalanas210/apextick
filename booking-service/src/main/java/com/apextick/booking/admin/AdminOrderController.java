package com.apextick.booking.admin;

import com.apextick.booking.order.Order;
import com.apextick.booking.order.OrderRepository;
import com.apextick.booking.order.OrderStatus;
import com.apextick.booking.order.dto.OrderResponse;
import com.apextick.booking.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Locale;

@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: Orders")
public class AdminOrderController {

    private final OrderRepository orders;

    public AdminOrderController(OrderRepository orders) {
        this.orders = orders;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> list(@RequestParam(required = false) String status,
                                            @PageableDefault(size = 20) Pageable pageable) {
        Page<Order> page = status == null || status.isBlank()
                ? orders.findAllByOrderByCreatedAtDesc(pageable)
                : orders.findByStatusOrderByCreatedAtDesc(parseStatus(status), pageable);
        return PageResponse.of(page, OrderResponse::from);
    }

    /**
     * Accepts the wire form the client sends back to us ("PAID") as well as a lowercase
     * one. An unknown value raises IllegalArgumentException, which the global handler
     * turns into a 400 -- a typo in a query string is not a server error.
     */
    private OrderStatus parseStatus(String status) {
        try {
            return OrderStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            // valueOf's own message names the Java enum class, which has no business
            // in an API response. Say what is actually allowed instead.
            throw new IllegalArgumentException("Unknown order status: " + status
                    + ". Expected one of " + Arrays.toString(OrderStatus.values()));
        }
    }
}
