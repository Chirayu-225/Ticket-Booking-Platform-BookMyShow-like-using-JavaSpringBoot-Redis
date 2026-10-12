package com.ticketbooking.hold;

import com.ticketbooking.common.AfterCommit;
import com.ticketbooking.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * The heart of "how do you stop 1,000 users booking the same seat."
 *
 * Layer 1 — Redis SET key value NX EX ttl, one key per seat. Atomic per key,
 * so of N concurrent requests for seat A5 exactly one sees the key absent.
 * The 49 losers are rejected here without ever touching Postgres.
 *
 * Layer 2 — the winner then takes a row lock (SELECT ... FOR UPDATE) on the
 * seats in Postgres and re-checks their status there. Postgres is the source
 * of truth: a BOOKED seat is never re-held even if every Redis key vanished.
 *
 * Each hold's Redis keys hold a random per-hold token. Locks are only ever
 * deleted with a compare-and-delete on that token, so cleaning up an old hold
 * can never delete a lock some newer hold owns.
 */
@Service
public class HoldService {

    private static final String SEAT_LOCK_PREFIX = "seat-lock:";

    /** DEL the key only if it still holds our token — atomic thanks to Lua. */
    private static final RedisScript<Long> COMPARE_AND_DELETE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;
    private final ShowSeatRepository showSeatRepository;
    private final HoldRepository holdRepository;
    private final ShowRepository showRepository;
    private final UserRepository userRepository;
    private final SeatStatusBroadcaster broadcaster;
    private final Duration holdTtl;
    private final int maxSeatsPerHold;

    public HoldService(StringRedisTemplate redis,
                       ShowSeatRepository showSeatRepository,
                       HoldRepository holdRepository,
                       ShowRepository showRepository,
                       UserRepository userRepository,
                       SeatStatusBroadcaster broadcaster,
                       @Value("${app.hold.ttl-minutes}") long holdTtlMinutes,
                       @Value("${app.hold.max-seats:10}") int maxSeatsPerHold) {
        this.redis = redis;
        this.showSeatRepository = showSeatRepository;
        this.holdRepository = holdRepository;
        this.showRepository = showRepository;
        this.userRepository = userRepository;
        this.broadcaster = broadcaster;
        this.holdTtl = Duration.ofMinutes(holdTtlMinutes);
        this.maxSeatsPerHold = maxSeatsPerHold;
    }

    public Duration getHoldTtl() { return holdTtl; }
    public int getMaxSeatsPerHold() { return maxSeatsPerHold; }

    /**
     * Holds every requested seat for this user, or none of them.
     *
     * @throws SeatUnavailableException with a user-facing message when any seat can't be held
     */
    @Transactional
    public Hold holdSeats(Long userId, Long showId, Collection<Long> requestedSeatIds) {
        List<Long> seatIds = new ArrayList<>(new TreeSet<>(requestedSeatIds));
        if (seatIds.isEmpty()) {
            throw new SeatUnavailableException("Pick at least one seat.");
        }
        if (seatIds.size() > maxSeatsPerHold) {
            throw new SeatUnavailableException("You can book up to " + maxSeatsPerHold + " seats at a time.");
        }

        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new SeatUnavailableException("This show no longer exists."));
        if (!show.getStartsAt().isAfter(Instant.now())) {
            throw new SeatUnavailableException("This show has already started.");
        }

        String token = UUID.randomUUID().toString();
        List<String> acquiredKeys = new ArrayList<>();
        // If anything below fails — including the commit itself — give back
        // exactly the keys this call took, and only those.
        AfterCommit.onRollback(() -> releaseKeys(acquiredKeys, token));

        // Layer 1: Redis.
        for (Long seatId : seatIds) {
            String key = SEAT_LOCK_PREFIX + seatId;
            Boolean acquired = redis.opsForValue().setIfAbsent(key, token, holdTtl);
            if (!Boolean.TRUE.equals(acquired)) {
                throw new SeatUnavailableException(
                        "Someone just grabbed one of those seats. The map has been refreshed — pick again.");
            }
            acquiredKeys.add(key);
        }

        // Layer 2: Postgres row locks + status re-check.
        List<ShowSeat> seats = showSeatRepository.findByIdsForUpdate(seatIds);
        if (seats.size() != seatIds.size()) {
            throw new SeatUnavailableException("Some of those seats don't exist any more.");
        }
        Instant now = Instant.now();
        for (ShowSeat seat : seats) {
            if (!seat.getShow().getId().equals(showId)) {
                throw new SeatUnavailableException("Those seats belong to a different show.");
            }
            if (seat.getStatus() == ShowSeat.Status.BOOKED) {
                throw new SeatUnavailableException("Seat " + seat.getScreenSeat().getLabel() + " is already booked.");
            }
            if (seat.getStatus() == ShowSeat.Status.HELD) {
                // We got the Redis key, so the previous holder's lock has
                // expired. If its Postgres hold is past expiry too, it is
                // stale (the cleanup sweep just hasn't reached it yet):
                // retire it so it can never be confirmed against this seat.
                for (Hold previous : holdRepository.findByStatusContainingSeat(seat.getId(), Hold.Status.ACTIVE)) {
                    if (previous.getExpiresAt().isAfter(now)) {
                        throw new SeatUnavailableException(
                                "Seat " + seat.getScreenSeat().getLabel() + " is being held by someone else.");
                    }
                    previous.setStatus(Hold.Status.EXPIRED);
                }
            }
        }

        User user = userRepository.getReferenceById(userId);
        Hold hold = new Hold(user, show, now.plus(holdTtl), token);
        for (ShowSeat seat : seats) {
            seat.setStatus(ShowSeat.Status.HELD);
            hold.getSeats().add(seat);
        }
        Hold saved = holdRepository.save(hold);
        AfterCommit.run(() -> broadcaster.seatsChanged(showId));
        return saved;
    }

    /** The user backed out on the checkout page: free the seats right away. */
    @Transactional
    public void cancel(Long holdId, Long userId) {
        Hold hold = holdRepository.findById(holdId).orElse(null);
        if (hold == null || !hold.getUser().getId().equals(userId) || hold.getStatus() != Hold.Status.ACTIVE) {
            return;
        }
        endHold(hold, Hold.Status.CANCELLED);
    }

    /**
     * Expires one hold if it is still ACTIVE and past its expiry. Runs in its
     * own transaction per hold, so one failure can't roll back the others.
     */
    @Transactional
    public boolean expireIfOverdue(Long holdId) {
        Hold hold = holdRepository.findById(holdId).orElse(null);
        if (hold == null || hold.getStatus() != Hold.Status.ACTIVE || hold.getExpiresAt().isAfter(Instant.now())) {
            return false;
        }
        endHold(hold, Hold.Status.EXPIRED);
        return true;
    }

    private void endHold(Hold hold, Hold.Status finalStatus) {
        List<Long> seatIds = hold.getSeats().stream().map(ShowSeat::getId).toList();
        for (ShowSeat seat : showSeatRepository.findByIdsForUpdate(seatIds)) {
            boolean ownedElsewhere = holdRepository.countOtherHoldsContainingSeat(
                    seat.getId(), Hold.Status.ACTIVE, hold.getId()) > 0;
            if (seat.getStatus() == ShowSeat.Status.HELD && !ownedElsewhere) {
                seat.setStatus(ShowSeat.Status.AVAILABLE);
            }
        }
        hold.setStatus(finalStatus);
        Long showId = hold.getShow().getId();
        AfterCommit.run(() -> {
            releaseLocks(hold);
            broadcaster.seatsChanged(showId);
        });
    }

    /** Deletes this hold's Redis keys — only the ones still carrying its token. */
    public void releaseLocks(Hold hold) {
        List<String> keys = hold.getSeats().stream().map(s -> SEAT_LOCK_PREFIX + s.getId()).toList();
        releaseKeys(keys, hold.getLockToken());
    }

    private void releaseKeys(List<String> keys, String token) {
        for (String key : keys) {
            if (token == null) {
                // Holds created before lock tokens existed (V3 migration).
                redis.delete(key);
            } else {
                redis.execute(COMPARE_AND_DELETE, List.of(key), token);
            }
        }
    }
}
