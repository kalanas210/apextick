package com.apextick.booking.seat.dto;

import jakarta.validation.constraints.NotBlank;

public record HoldSeatRequest(@NotBlank String userId) {}