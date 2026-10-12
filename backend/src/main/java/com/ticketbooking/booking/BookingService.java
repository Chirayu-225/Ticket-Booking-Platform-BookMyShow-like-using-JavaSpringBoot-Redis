package com.ticketbooking.booking;

import com.ticketbooking.common.AfterCommit;
import com.ticketbooking.domain.*;
import com.ticketbooking.hold.HoldService;
import com.ticketbooking.hold.SeatStatusBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Where a paid hold becomes a durably BOOKED set of seats. Called from two
 * places that can both fire for the same payment — the browser's Razorpay
 * success callback and Razorpay's server-to-server webhook — so it must be
 * idempotent. Three things make that true:
 *
 * 1. The idempotency key is the Razorpay payment id: one payment can only
 *    ever produce one booking. bookings.idempotency_key is UNIQUE, so even
 *    two concurrent calls can't both insert.
 * 2. Seats are row-locked (SELECT ... FOR UPDATE) and re-checked before they
 *    flip to BOOKED, inside one transaction.
 * 3. booking_seats.show_seat_id is UNIQUE (V3): the database itself refuses
 *    to put one seat into two bookings.
 *
 * Callers should go through PaymentConfirmationService, which also handles
 * the "lost the insert race to a concurrent duplicate" case.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final HoldRepository holdRepository;
    private final ShowSeatRepository showSeatRepository;
    private final BookingRepository bookingRepository;
    private final HoldService holdService;
    private final SeatStatusBroadcaster broadcaster;

    public BookingService(HoldRepository holdRepository, ShowSeatRepository showSeatRepository,
                          BookingRepository bookingRepository, HoldService holdService,
                          SeatStatusBroadcaster broadcaster) {
        this.holdRepository = holdRepository;
        this.showSeatRepository = showSeatRepository;
        this.bookingRepository = bookingRepository;
        this.holdService = holdService;
        this.broadcaster = broadcaster;
    }

    /**
     * @throws HoldNoLongerValidException if the hold can't be honoured (a refund case);
     *         the hold's status change is still committed (noRollbackFor).
     */
    @Transactional(noRollbackFor = HoldNoLongerValidException.class)
    public Booking confirmPayment(Long holdId, String razorpayPaymentId, String idempotencyKey) {
        Optional<Booking> existing = bookingRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Payment {} already confirmed as booking {} — no-op.", idempotencyKey, existing.get().getId());
            return existing.get();
        }

        Hold hold = holdRepository.findById(holdId)
                .orElseThrow(() -> new HoldNoLongerValidException("Hold " + holdId + " not found"));
        switch (hold.getStatus()) {
            case CONFIRMED -> throw new HoldNoLongerValidException(
                    "Hold " + holdId + " was already paid by a different payment; " + razorpayPaymentId + " needs a refund");
            case EXPIRED, CANCELLED -> throw new HoldNoLongerValidException(
                    "Hold " + holdId + " is " + hold.getStatus() + "; payment " + razorpayPaymentId + " needs a refund");
            case ACTIVE -> { /* continue */ }
        }

        List<Long> seatIds = hold.getSeats().stream().map(ShowSeat::getId).toList();
        List<ShowSeat> lockedSeats = showSeatRepository.findByIdsForUpdate(seatIds);

        // A late payment is still honoured as long as nobody else has taken
        // the seats in the meantime.
        for (ShowSeat seat : lockedSeats) {
            boolean takenByOtherHold = holdRepository.countOtherHoldsContainingSeat(
                    seat.getId(), Hold.Status.ACTIVE, hold.getId()) > 0;
            if (seat.getStatus() == ShowSeat.Status.BOOKED || takenByOtherHold) {
                hold.setStatus(Hold.Status.EXPIRED);
                throw new HoldNoLongerValidException("Seat " + seat.getScreenSeat().getLabel()
                        + " was re-sold after hold " + holdId + " lapsed; payment " + razorpayPaymentId + " needs a refund");
            }
        }

        for (ShowSeat seat : lockedSeats) {
            seat.setStatus(ShowSeat.Status.BOOKED);
        }
        hold.setStatus(Hold.Status.CONFIRMED);

        Show show = hold.getShow();
        Booking booking = new Booking(hold.getUser(), show, hold, Pricing.total(show, lockedSeats),
                razorpayPaymentId, idempotencyKey);
        booking.getSeats().addAll(lockedSeats);
        Booking saved = bookingRepository.save(booking);

        Long showId = show.getId();
        AfterCommit.run(() -> {
            holdService.releaseLocks(hold);
            broadcaster.seatsChanged(showId);
        });
        return saved;
    }
}
