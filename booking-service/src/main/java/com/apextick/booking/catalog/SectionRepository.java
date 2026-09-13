package com.apextick.booking.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SectionRepository extends JpaRepository<Section, Long> {
    List<Section> findByEventIdOrderBySortOrderAscIdAsc(Long eventId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Section s where s.event.id = :eventId")
    int deleteByEventId(@Param("eventId") Long eventId);
}
