package com.apextick.booking.ticket.gate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** A scan at a gate: the code read off the ticket, and the event this gate is admitting to. */
public record ScanRequest(@NotBlank String qrToken, @NotNull Long eventId) {
}
