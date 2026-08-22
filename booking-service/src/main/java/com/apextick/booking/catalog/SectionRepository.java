package com.apextick.booking.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SectionRepository extends JpaRepository<Section, Long> {
    List<Section> findByEventIdOrderBySortOrderAscIdAsc(Long eventId);
}
