package com.apextick.booking.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

public record PriceTierResponse(
        Long id, String code, String name, BigDecimal price,
        List<String> perks, long remaining, long total) {
}
