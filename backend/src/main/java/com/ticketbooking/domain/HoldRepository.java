package com.ticketbooking.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface HoldRepository extends JpaRepository<Hold, Long> {

    Optional<Hold> findByRazorpayOrderId(String razorpayOrderId);

    @Query("select h.user.id from Hold h where h.id = :id")
    Optional<Long> findOwnerIdById(@Param("id") Long id);

    /** Everything CheckoutView renders, in one query. */
    @Query("""
            select distinct h from Hold h
            join fetch h.user
            join fetch h.show sh
            join fetch sh.movie
            join fetch sh.screen sc
            join fetch sc.venue
            left join fetch h.seats seat
            left join fetch seat.screenSeat
            where h.id = :id
            """)
    Optional<Hold> findWithDetailsById(@Param("id") Long id);

    @Query("select h.id from Hold h where h.status = :status and h.expiresAt < :cutoff")
    List<Long> findIdsByStatusAndExpiresAtBefore(@Param("status") Hold.Status status,
                                                @Param("cutoff") Instant cutoff);

    @Query("select h from Hold h join h.seats s where s.id = :seatId and h.status = :status")
    List<Hold> findByStatusContainingSeat(@Param("seatId") Long seatId,
                                          @Param("status") Hold.Status status);

    @Query("""
            select count(h) from Hold h join h.seats s
            where s.id = :seatId and h.status = :status and h.id <> :excludeHoldId
            """)
    long countOtherHoldsContainingSeat(@Param("seatId") Long seatId,
                                       @Param("status") Hold.Status status,
                                       @Param("excludeHoldId") Long excludeHoldId);
}
