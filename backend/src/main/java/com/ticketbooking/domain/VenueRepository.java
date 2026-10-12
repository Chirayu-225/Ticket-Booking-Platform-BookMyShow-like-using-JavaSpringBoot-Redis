package com.ticketbooking.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface VenueRepository extends JpaRepository<Venue, Long> {
    List<Venue> findAllByOrderByNameAsc();

    @Query("select distinct v.city from Venue v order by v.city")
    List<String> findDistinctCities();
}
