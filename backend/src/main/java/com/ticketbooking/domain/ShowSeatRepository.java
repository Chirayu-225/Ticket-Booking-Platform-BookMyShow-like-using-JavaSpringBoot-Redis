package com.ticketbooking.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    // join fetch screenSeat: the seat map renders row/number/tier for every
    // seat, including from push callbacks that run outside any request.
    @Query("select s from ShowSeat s join fetch s.screenSeat where s.show.id = :showId")
    List<ShowSeat> findByShowId(@Param("showId") Long showId);

    // Pessimistic row lock (SELECT ... FOR UPDATE). Ordered by id so two
    // transactions locking overlapping seat sets always lock in the same
    // order and can't deadlock each other.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ShowSeat s where s.id in :ids order by s.id")
    List<ShowSeat> findByIdsForUpdate(@Param("ids") Collection<Long> ids);
}
