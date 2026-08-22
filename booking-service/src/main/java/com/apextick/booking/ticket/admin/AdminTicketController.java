package com.apextick.booking.ticket.admin;

import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.ticket.dto.VerifyRequest;
import com.apextick.booking.ticket.dto.VerifyResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/tickets")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: Tickets")
public class AdminTicketController {

    private final TicketVerificationService verification;

    public AdminTicketController(TicketVerificationService verification) {
        this.verification = verification;
    }

    @PostMapping("/verify")
    public VerifyResponse verify(@Valid @RequestBody VerifyRequest request, CurrentUser admin) {
        return verification.verify(request.qrToken(), admin);
    }
}
