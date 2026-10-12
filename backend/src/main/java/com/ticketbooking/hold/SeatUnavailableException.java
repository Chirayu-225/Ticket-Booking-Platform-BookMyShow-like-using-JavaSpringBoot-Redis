package com.ticketbooking.hold;

/** Thrown when one or more requested seats could not be held — already HELD, BOOKED, or lost the Redis race. */
public class SeatUnavailableException extends RuntimeException {
    public SeatUnavailableException(String message) {
        super(message);
    }
}
