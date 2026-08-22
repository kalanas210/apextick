package com.apextick.booking.admin.dto;

import java.math.BigDecimal;
import java.util.List;

public record EventStatsResponse(
        Long eventId, long available, long held, long booked, long total,
        BigDecimal revenue, String currency, List<TierStat> byTier) {

    public record TierStat(Long tierId, String tierCode, long total, long available, long booked) {
    }
}
