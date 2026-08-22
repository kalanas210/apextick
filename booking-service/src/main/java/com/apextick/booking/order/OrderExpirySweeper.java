package com.apextick.booking.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Expires unpaid orders past their payment window and releases their seats. */
@Component
public class OrderExpirySweeper {

    private static final Logger log = LoggerFactory.getLogger(OrderExpirySweeper.class);

    private final OrderRepository orders;
    private final OrderService orderService;

    public OrderExpirySweeper(OrderRepository orders, OrderService orderService) {
        this.orders = orders;
        this.orderService = orderService;
    }

    @Scheduled(fixedDelayString = "${app.order.sweeper-interval}")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NEVER)
    public int sweep() {
        List<UUID> expired = orders.findExpiredPendingIds(Instant.now(), PageRequest.of(0, 200));
        for (UUID id : expired) {
            orderService.expire(id);
        }
        if (!expired.isEmpty()) {
            log.info("Order sweeper expired {} unpaid orders", expired.size());
        }
        return expired.size();
    }
}
