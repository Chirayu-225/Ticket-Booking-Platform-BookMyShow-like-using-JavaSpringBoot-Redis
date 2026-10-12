package com.ticketbooking.booking;

import com.ticketbooking.domain.ScreenSeat;
import com.ticketbooking.domain.Show;
import com.ticketbooking.domain.ShowSeat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * Single source of truth for ticket prices. The Razorpay order amount, the
 * booking's stored total and every price shown in the UI all come from here,
 * so they can never disagree.
 */
public final class Pricing {

    private Pricing() {}

    public static BigDecimal multiplier(ScreenSeat.Tier tier) {
        return switch (tier) {
            case RECLINER -> new BigDecimal("1.60");
            case PREMIUM -> new BigDecimal("1.25");
            case STANDARD -> BigDecimal.ONE;
        };
    }

    /** Whole rupees for one seat of {@code tier} at {@code show}. */
    public static BigDecimal price(Show show, ScreenSeat.Tier tier) {
        return show.getBasePrice().multiply(multiplier(tier)).setScale(0, RoundingMode.HALF_UP);
    }

    public static BigDecimal total(Show show, Collection<ShowSeat> seats) {
        return seats.stream()
                .map(seat -> price(show, seat.getScreenSeat().getTier()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Razorpay amounts are integers in paise. */
    public static long toPaise(BigDecimal rupees) {
        return rupees.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
