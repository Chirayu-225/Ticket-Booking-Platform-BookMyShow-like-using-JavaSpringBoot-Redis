package com.ticketbooking;

import com.ticketbooking.domain.*;
import com.ticketbooking.hold.HoldService;
import com.ticketbooking.hold.SeatUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "How do you stop 1,000 users booking the same seat", as a test: 50
 * threads race to hold the SAME seat at the same instant. Exactly one must
 * win; the other 49 must get SeatUnavailableException — never an
 * unexpected error and never a second hold.
 */
@SpringBootTest
class ConcurrentSeatHoldTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestContainersConfig.register(registry);
    }

    @Autowired private HoldService holdService;
    @Autowired private UserRepository userRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private ScreenRepository screenRepository;
    @Autowired private ScreenSeatRepository screenSeatRepository;
    @Autowired private ShowRepository showRepository;
    @Autowired private ShowSeatRepository showSeatRepository;
    @Autowired private HoldRepository holdRepository;

    @Test
    void exactlyOneOfManyConcurrentRequestsWinsTheSameSeat() throws InterruptedException {
        Venue venue = venueRepository.save(new Venue("Race Venue", "Bengaluru", null));
        Screen screen = screenRepository.save(new Screen(venue, "Screen 1", 1, 1));
        ScreenSeat screenSeat = screenSeatRepository.save(new ScreenSeat(screen, "A", 1, ScreenSeat.Tier.STANDARD));
        Movie movie = movieRepository.save(new Movie("Race Movie", "English", 120, null, ""));
        Show show = showRepository.save(new Show(movie, screen, Instant.now().plusSeconds(3600), BigDecimal.TEN));
        ShowSeat showSeat = showSeatRepository.save(new ShowSeat(show, screenSeat));

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
        int threadCount = 50;
        List<Long> userIds = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            userIds.add(userRepository.save(new User("racer" + i + "@test.dev", encoder.encode("x"),
                    "Racer " + i, User.Role.USER)).getId());
        }

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejections = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLine = new CountDownLatch(1);
        CountDownLatch finishLine = new CountDownLatch(threadCount);

        for (Long userId : userIds) {
            pool.submit(() -> {
                try {
                    startLine.await();
                    holdService.holdSeats(userId, show.getId(), List.of(showSeat.getId()));
                    successes.incrementAndGet();
                } catch (SeatUnavailableException expected) {
                    rejections.incrementAndGet();
                } catch (Exception e) {
                    unexpected.incrementAndGet();
                } finally {
                    finishLine.countDown();
                }
            });
        }

        startLine.countDown();
        assertThat(finishLine.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(rejections.get()).isEqualTo(threadCount - 1);
        assertThat(unexpected.get()).isZero();
        assertThat(showSeatRepository.findById(showSeat.getId()).orElseThrow().getStatus())
                .isEqualTo(ShowSeat.Status.HELD);
        assertThat(holdRepository.findByStatusContainingSeat(showSeat.getId(), Hold.Status.ACTIVE)).hasSize(1);
    }
}
