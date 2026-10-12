package com.ticketbooking.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    /** Everything BookingHistoryView renders, in one query. */
    @Query("""
            select distinct b from Booking b
            join fetch b.show s
            join fetch s.movie
            join fetch s.screen sc
            join fetch sc.venue
            left join fetch b.seats seat
            left join fetch seat.screenSeat
            where b.user.id = :userId
            order by b.createdAt desc
            """)
    List<Booking> findHistoryForUser(@Param("userId") Long userId);

    /** Admin sales tab. */
    @Query("""
            select distinct b from Booking b
            join fetch b.user
            join fetch b.show s
            join fetch s.movie
            left join fetch b.seats
            order by b.createdAt desc
            """)
    List<Booking> findAllWithDetails();
}
