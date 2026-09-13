package com.apextick.booking.ticket.gate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Undoing an admission is on the record, so it has to say why. */
public record UnadmitRequest(@NotBlank @Size(max = 255) String reason) {
}
