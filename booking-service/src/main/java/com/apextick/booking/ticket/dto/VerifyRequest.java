package com.apextick.booking.ticket.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyRequest(@NotBlank String qrToken) {
}
