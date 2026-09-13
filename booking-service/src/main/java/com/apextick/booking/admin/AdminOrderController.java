package com.apextick.booking.admin;

import com.apextick.booking.admin.dto.AdminOrderDetailResponse;
import com.apextick.booking.admin.dto.RefundRequest;
import com.apextick.booking.order.OrderStatus;
import com.apextick.booking.order.dto.OrderResponse;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: Orders")
public class AdminOrderController {

    private final AdminOrderService adminOrders;

    public AdminOrderController(AdminOrderService adminOrders) {
        this.adminOrders = adminOrders;
    }

    @GetMapping
    @Operation(summary = "Orders, newest first: by status, by order number or customer, or those still owed a refund")
    public PageResponse<OrderResponse> list(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(defaultValue = "false") boolean refundRequired,
                                            @PageableDefault(size = 20) Pageable pageable) {
        OrderStatus wanted = status == null || status.isBlank() ? null : parseStatus(status);
        return adminOrders.list(wanted, q, refundRequired, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One order in full: its tickets, its payments, and whether it can be refunded")
    public AdminOrderDetailResponse detail(@PathVariable UUID id) {
        return adminOrders.detail(id);
    }

    @PostMapping("/{id}/refund")
    @Operation(summary = "Refund a paid order in full: its tickets are voided and its seats go back on sale")
    public AdminOrderDetailResponse refund(@PathVariable UUID id, @Valid @RequestBody RefundRequest request,
                                           CurrentUser admin) {
        return adminOrders.refund(id, request.reason(), admin);
    }

    @PostMapping("/{id}/refund/retry")
    @Operation(summary = "Ask the payment provider again for a refund it has not yet made")
    public AdminOrderDetailResponse retryRefund(@PathVariable UUID id) {
        return adminOrders.retryRefund(id);
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
