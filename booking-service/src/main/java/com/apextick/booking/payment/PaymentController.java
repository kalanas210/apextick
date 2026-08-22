package com.apextick.booking.payment;

import com.apextick.booking.payment.dto.PayRequest;
import com.apextick.booking.payment.dto.PaymentResponse;
import com.apextick.booking.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Payments")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/{id}/pay")
    @Operation(summary = "Pay for an order via the configured gateway")
    public ResponseEntity<PaymentResponse> pay(@PathVariable UUID id,
                                               @RequestBody(required = false) PayRequest request,
                                               @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                               CurrentUser user) {
        PaymentResponse response = payments.pay(id, request, idempotencyKey, user);
        HttpStatus status = switch (response.status()) {
            case "SUCCEEDED", "REQUIRES_ACTION", "REDIRECTED" -> HttpStatus.OK;
            case "REFUND_REQUIRED" -> HttpStatus.CONFLICT;
            default -> HttpStatus.PAYMENT_REQUIRED;
        };
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{id}/payments")
    @Operation(summary = "Payment attempts for an order")
    public List<PaymentResponse> list(@PathVariable UUID id, CurrentUser user) {
        return payments.listForOrder(id, user);
    }
}
