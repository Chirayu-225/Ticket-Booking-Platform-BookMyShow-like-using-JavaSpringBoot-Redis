package com.ticketbooking.booking;

/**
 * A payment arrived for a hold that can no longer become a booking (expired
 * and its seats re-sold, cancelled, or already confirmed by a different
 * payment). In production this is where a refund would be issued; it is a
 * business outcome, not a server error, so the webhook answers 200 for it.
 */
public class HoldNoLongerValidException extends RuntimeException {
    public HoldNoLongerValidException(String message) {
        super(message);
    }
}
