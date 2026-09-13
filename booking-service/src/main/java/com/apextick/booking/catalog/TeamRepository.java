package com.apextick.booking.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findAllByOrderByNameAsc();

    List<Team> findBySportOrderByNameAsc(Sport sport);
}
