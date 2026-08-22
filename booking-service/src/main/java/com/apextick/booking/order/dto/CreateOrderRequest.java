package com.apextick.booking.order.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateOrderRequest(@NotNull Long eventId, @NotEmpty List<@NotNull Long> seatIds) {
}
