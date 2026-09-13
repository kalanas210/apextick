package com.apextick.booking.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A refund is on the order's record, so it has to say why. */
public record RefundRequest(@NotBlank @Size(max = 255) String reason) {
}
