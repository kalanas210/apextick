package com.apextick.booking.hold.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record HoldRequest(@NotEmpty List<@NotNull Long> seatIds) {
}
