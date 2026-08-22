package com.apextick.booking.catalog;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SeriesRepository extends JpaRepository<Series, Long> {
    Optional<Series> findBySlug(String slug);

    default List<Series> findAllOrdered() {
        return findAll(Sort.by("id"));
    }
}
