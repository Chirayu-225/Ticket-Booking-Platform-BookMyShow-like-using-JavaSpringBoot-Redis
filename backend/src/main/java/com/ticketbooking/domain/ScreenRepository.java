package com.ticketbooking.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ScreenRepository extends JpaRepository<Screen, Long> {

    @Query("select sc from Screen sc join fetch sc.venue v order by v.name, sc.name")
    List<Screen> findAllWithVenue();
}
