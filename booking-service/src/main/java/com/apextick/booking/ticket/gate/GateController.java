package com.apextick.booking.ticket.gate;

import com.apextick.booking.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The gate API. A steward's turnstile device holds the {@code scanner} role and can reach this and
 * nothing else; an admin can do everything a steward can.
 */
@RestController
@RequestMapping("/api/gate")
@PreAuthorize("hasAnyRole('ADMIN', 'SCANNER')")
@Tag(name = "Gate")
public class GateController {

    private final GateService gate;

    public GateController(GateService gate) {
        this.gate = gate;
    }

    @PostMapping("/scans")
    @Operation(summary = "Admit the ticket behind a scanned QR code to the event this gate is admitting")
    public ScanResponse scan(@Valid @RequestBody ScanRequest request, CurrentUser steward) {
        return gate.scan(request.qrToken(), request.eventId(), steward);
    }
}
