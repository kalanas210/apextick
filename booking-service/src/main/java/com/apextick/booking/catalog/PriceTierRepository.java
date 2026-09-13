package com.apextick.booking.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface PriceTierRepository extends JpaRepository<PriceTier, Long> {

    List<PriceTier> findByEventIdOrderBySortOrderAscIdAsc(Long eventId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PriceTier t where t.event.id = :eventId")
    int deleteByEventId(@Param("eventId") Long eventId);

    @Query("select t.event.id as eventId, min(t.price) as fromPrice "
            + "from PriceTier t where t.event.id in :ids group by t.event.id")
    List<MinPriceView> minPriceByEventIds(@Param("ids") Collection<Long> ids);

    interface MinPriceView {
        Long getEventId();

        BigDecimal getFromPrice();
    }
}
