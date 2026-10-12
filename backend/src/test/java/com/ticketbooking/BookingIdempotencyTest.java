package com.ticketbooking;

import com.ticketbooking.booking.HoldNoLongerValidException;
import com.ticketbooking.booking.PaymentConfirmationService;
import com.ticketbooking.domain.*;
import com.ticketbooking.hold.HoldService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The browser callback and the Razorpay webhook can both report the same
 * payment. Whichever arrives second must be a no-op, not a second booking;
 * and a second, different payment for an already-paid hold must be refused.
 */
@SpringBootTest
class BookingIdempotencyTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestContainersConfig.register(registry);
    }

    @Autowired private PaymentConfirmationService paymentConfirmationService;
    @Autowired private HoldService holdService;
    @Autowired private UserRepository userRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private ScreenRepository screenRepository;
    @Autowired private ScreenSeatRepository screenSeatRepository;
    @Autowired private ShowRepository showRepository;
    @Autowired private ShowSeatRepository showSeatRepository;
    @Autowired private BookingRepository bookingRepository;

    @Test
    void samePaymentReportedTwiceCreatesOneBooking() {
        Long holdId = newHold("payer@test.dev");

        Booking first = paymentConfirmationService.confirm(holdId, "pay_abc123");
        Booking second = paymentConfirmationService.confirm(holdId, "pay_abc123");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(bookingRepository.findByIdempotencyKey("pay_abc123")).isPresent();
        assertThat(bookingRepository.findAll().stream()
                .filter(b -> b.getRazorpayPaymentId().equals("pay_abc123")).count()).isEqualTo(1);
    }

    @Test
    void secondDifferentPaymentForPaidHoldIsRefused() {
        Long holdId = newHold("double@test.dev");
        paymentConfirmationService.confirm(holdId, "pay_first");

        assertThatThrownBy(() -> paymentConfirmationService.confirm(holdId, "pay_second"))
                .isInstanceOf(HoldNoLongerValidException.class);
    }

    private Long newHold(String email) {
        Venue venue = venueRepository.save(new Venue("Idem Venue " + email, "Bengaluru", null));
        Screen screen = screenRepository.save(new Screen(venue, "Screen 1", 1, 1));
        ScreenSeat screenSeat = screenSeatRepository.save(new ScreenSeat(screen, "A", 1, ScreenSeat.Tier.STANDARD));
        Movie movie = movieRepository.save(new Movie("Idem Movie", "English", 120, null, ""));
        Show show = showRepository.save(new Show(movie, screen, Instant.now().plusSeconds(3600), BigDecimal.TEN));
        ShowSeat showSeat = showSeatRepository.save(new ShowSeat(show, screenSeat));
        User user = userRepository.save(new User(email, new BCryptPasswordEncoder(4).encode("x"), "Payer", User.Role.USER));
        return holdService.holdSeats(user.getId(), show.getId(), List.of(showSeat.getId())).getId();
    }
}
