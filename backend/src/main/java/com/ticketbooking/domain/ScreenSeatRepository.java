package com.ticketbooking.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ScreenSeatRepository extends JpaRepository<ScreenSeat, Long> {
    List<ScreenSeat> findByScreenIdOrderByRowLabelAscSeatNumberAsc(Long screenId);
}
