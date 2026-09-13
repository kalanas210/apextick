package com.apextick.booking.ticket.gate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A scan at a gate: the code read off the ticket, the event this gate is admitting to, and which gate. */
public record ScanRequest(@NotBlank String qrToken, @NotNull Long eventId, @Size(max = 64) String gate) {
}
