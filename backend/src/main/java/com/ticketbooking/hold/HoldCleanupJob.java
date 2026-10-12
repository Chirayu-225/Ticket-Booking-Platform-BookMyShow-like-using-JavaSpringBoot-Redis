package com.ticketbooking.hold;

import com.ticketbooking.domain.Hold;
import com.ticketbooking.domain.HoldRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Defensive backstop, not the primary expiry mechanism. Redis's own TTL is
 * what frees a seat's lock the instant a hold lapses; this job flips the
 * matching Postgres rows (hold -> EXPIRED, seats -> AVAILABLE) so the seat
 * map shows them as free again. Each hold is expired in its own transaction
 * (HoldService.expireIfOverdue), so one bad row can't block the rest.
 */
@Component
public class HoldCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(HoldCleanupJob.class);

    private final HoldRepository holdRepository;
    private final HoldService holdService;

    public HoldCleanupJob(HoldRepository holdRepository, HoldService holdService) {
        this.holdRepository = holdRepository;
        this.holdService = holdService;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 15_000)
    public void sweepExpiredHolds() {
        for (Long holdId : holdRepository.findIdsByStatusAndExpiresAtBefore(Hold.Status.ACTIVE, Instant.now())) {
            try {
                if (holdService.expireIfOverdue(holdId)) {
                    log.info("Expired hold {}", holdId);
                }
            } catch (RuntimeException e) {
                log.warn("Could not expire hold {} — will retry next sweep", holdId, e);
            }
        }
    }
}
