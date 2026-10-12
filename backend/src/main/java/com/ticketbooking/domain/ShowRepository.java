package com.ticketbooking.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * open-in-view is off, so every query a view renders from fetches the
 * associations that view touches (movie, screen, venue) in the same SQL.
 */
public interface ShowRepository extends JpaRepository<Show, Long> {

    @Query("""
            select s from Show s
            join fetch s.movie
            join fetch s.screen sc
            join fetch sc.venue v
            where lower(v.city) = lower(:city)
              and s.startsAt >= :from and s.startsAt < :to
            order by s.startsAt asc
            """)
    List<Show> findInCityBetween(@Param("city") String city,
                                 @Param("from") Instant from,
                                 @Param("to") Instant to);

    @Query("""
            select s from Show s
            join fetch s.movie
            join fetch s.screen sc
            join fetch sc.venue
            where s.id = :id
            """)
    Optional<Show> findWithDetailsById(@Param("id") Long id);

    @Query("""
            select s from Show s
            join fetch s.movie
            join fetch s.screen sc
            join fetch sc.venue
            order by s.startsAt desc
            """)
    List<Show> findAllWithDetails();

    boolean existsByScreenIdAndStartsAt(Long screenId, Instant startsAt);
}
