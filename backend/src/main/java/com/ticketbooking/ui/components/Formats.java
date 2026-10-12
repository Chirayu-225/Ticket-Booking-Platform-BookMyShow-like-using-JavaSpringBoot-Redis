package com.ticketbooking.ui.components;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** One place for how dates, times and money look across every screen. */
public final class Formats {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final Locale INDIA = Locale.forLanguageTag("en-IN");

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH).withZone(ZONE);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH).withZone(ZONE);
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a", Locale.ENGLISH).withZone(ZONE);

    private Formats() {}

    public static String time(Instant instant) { return TIME.format(instant); }
    public static String day(Instant instant) { return DAY.format(instant); }
    public static String dayTime(Instant instant) { return DAY_TIME.format(instant); }

    public static String money(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(INDIA);
        format.setMaximumFractionDigits(amount.stripTrailingZeros().scale() > 0 ? 2 : 0);
        format.setMinimumFractionDigits(amount.stripTrailingZeros().scale() > 0 ? 2 : 0);
        return "₹" + format.format(amount);
    }

    public static String duration(int minutes) {
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    public static String bookingCode(Long id) {
        return String.format("TB-%06d", id);
    }
}
