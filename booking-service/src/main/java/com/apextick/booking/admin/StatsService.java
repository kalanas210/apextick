package com.apextick.booking.admin;

import com.apextick.booking.admin.dto.EventStatsResponse;
import com.apextick.booking.catalog.Event;
import com.apextick.booking.catalog.EventRepository;
import com.apextick.booking.catalog.PriceTier;
import com.apextick.booking.catalog.PriceTierRepository;
import com.apextick.booking.order.OrderRepository;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.SeatStatus;
import com.apextick.booking.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StatsService {

    private final EventRepository events;
    private final SeatRepository seats;
    private final PriceTierRepository tiers;
    private final OrderRepository orders;

    public StatsService(EventRepository events, SeatRepository seats,
                        PriceTierRepository tiers, OrderRepository orders) {
        this.events = events;
        this.seats = seats;
        this.tiers = tiers;
        this.orders = orders;
    }

    @Transactional(readOnly = true)
    public EventStatsResponse eventStats(Long eventId) {
        Event event = events.findById(eventId).orElseThrow(() -> new NotFoundException("Event", eventId));
        long available = seats.countByEventIdAndStatus(eventId, SeatStatus.AVAILABLE);
        long held = seats.countByEventIdAndStatus(eventId, SeatStatus.HELD);
        long booked = seats.countByEventIdAndStatus(eventId, SeatStatus.BOOKED);
        long total = available + held + booked;
        BigDecimal revenue = orders.paidRevenueForEvent(eventId);

        // held and booked are counted separately: a hold is transient, so folding it into
        // booked would inflate a tier during an on-sale rush and disagree with the event totals
        Map<Long, SeatRepository.TierCountView> tierCounts = seats.countByTier(eventId)
                .stream().collect(Collectors.toMap(SeatRepository.TierCountView::getTierId, v -> v));
        List<EventStatsResponse.TierStat> byTier = tiers.findByEventIdOrderBySortOrderAscIdAsc(eventId).stream()
                .map(t -> {
                    SeatRepository.TierCountView tc = tierCounts.get(t.getId());
                    return tc == null
                            ? new EventStatsResponse.TierStat(t.getId(), t.getCode(), 0, 0, 0, 0)
                            : new EventStatsResponse.TierStat(t.getId(), t.getCode(),
                                    tc.getTotal(), tc.getAvailable(), tc.getHeld(), tc.getBooked());
                }).toList();

        return new EventStatsResponse(eventId, available, held, booked, total,
                revenue == null ? BigDecimal.ZERO : revenue, event.getCurrency(), byTier);
    }
}
