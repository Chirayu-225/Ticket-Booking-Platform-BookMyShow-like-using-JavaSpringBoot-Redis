package com.ticketbooking.booking;

import com.ticketbooking.domain.Booking;
import com.ticketbooking.domain.BookingRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Entry point for "this payment succeeded", used by both the browser callback
 * and the webhook. Deliberately NOT @Transactional: if two deliveries of the
 * same payment race, the loser's insert hits the UNIQUE idempotency_key and
 * its transaction rolls back. Only after that rollback — in a fresh
 * transaction — can we read the winner's booking. (Catching the exception
 * inside the failed transaction doesn't work on Postgres: the transaction is
 * already aborted and every further query fails.)
 */
@Service
public class PaymentConfirmationService {

    private final BookingService bookingService;
    private final BookingRepository bookingRepository;

    public PaymentConfirmationService(BookingService bookingService, BookingRepository bookingRepository) {
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
    }

    public Booking confirm(Long holdId, String razorpayPaymentId) {
        String idempotencyKey = razorpayPaymentId;
        try {
            return bookingService.confirmPayment(holdId, razorpayPaymentId, idempotencyKey);
        } catch (DataIntegrityViolationException lostRace) {
            return bookingRepository.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> lostRace);
        }
    }
}
