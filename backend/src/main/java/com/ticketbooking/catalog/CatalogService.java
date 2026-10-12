package com.ticketbooking.catalog;

import com.ticketbooking.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Admin-side writes that touch many rows at once (a screen and all its
 * seats, a show and one show_seat per physical seat). Each is one
 * transaction, so a failure halfway never leaves a screen with half a seat
 * layout or a show with half its seats.
 */
@Service
public class CatalogService {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final MovieRepository movieRepository;
    private final VenueRepository venueRepository;
    private final ScreenRepository screenRepository;
    private final ScreenSeatRepository screenSeatRepository;
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;

    public CatalogService(MovieRepository movieRepository, VenueRepository venueRepository,
                          ScreenRepository screenRepository, ScreenSeatRepository screenSeatRepository,
                          ShowRepository showRepository, ShowSeatRepository showSeatRepository) {
        this.movieRepository = movieRepository;
        this.venueRepository = venueRepository;
        this.screenRepository = screenRepository;
        this.screenSeatRepository = screenSeatRepository;
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
    }

    @Transactional
    public Movie addMovie(String title, String language, int durationMinutes, String genre, String certificate) {
        if (title.isBlank() || language.isBlank() || durationMinutes <= 0) {
            throw new IllegalArgumentException("Title, language and a positive duration are required.");
        }
        return movieRepository.save(new Movie(title.trim(), language.trim(), durationMinutes, null, "",
                blankToNull(genre), blankToNull(certificate)));
    }

    @Transactional
    public Venue addVenue(String name, String city, String address) {
        if (name.isBlank() || city.isBlank()) {
            throw new IllegalArgumentException("Venue name and city are required.");
        }
        return venueRepository.save(new Venue(name.trim(), city.trim(), blankToNull(address)));
    }

    /** Creates the screen and its full seat grid. Back rows are the premium tiers. */
    @Transactional
    public Screen addScreen(Long venueId, String name, int rows, int seatsPerRow) {
        if (rows < 1 || rows > 26 || seatsPerRow < 1 || seatsPerRow > 40) {
            throw new IllegalArgumentException("Rows must be 1-26 (A-Z) and seats per row 1-40.");
        }
        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown venue."));
        Screen screen = screenRepository.save(new Screen(venue, name.trim(), rows, seatsPerRow));
        List<ScreenSeat> seats = new ArrayList<>();
        int reclinerRows = Math.max(1, rows / 4);
        int premiumRows = Math.max(1, rows / 3);
        for (int r = 1; r <= rows; r++) {
            ScreenSeat.Tier tier = r <= reclinerRows ? ScreenSeat.Tier.RECLINER
                    : r <= reclinerRows + premiumRows ? ScreenSeat.Tier.PREMIUM
                    : ScreenSeat.Tier.STANDARD;
            String rowLabel = String.valueOf((char) ('A' + r - 1));
            for (int c = 1; c <= seatsPerRow; c++) {
                seats.add(new ScreenSeat(screen, rowLabel, c, tier));
            }
        }
        screenSeatRepository.saveAll(seats);
        return screen;
    }

    /** Creates the show plus one AVAILABLE show_seat per physical seat. */
    @Transactional
    public Show scheduleShow(Long movieId, Long screenId, Instant startsAt, BigDecimal basePrice) {
        if (startsAt.isBefore(Instant.now())) {
            throw new IllegalArgumentException("Shows must be scheduled in the future.");
        }
        if (basePrice == null || basePrice.signum() <= 0) {
            throw new IllegalArgumentException("Base price must be greater than zero.");
        }
        Movie movie = movieRepository.findById(movieId).orElseThrow(() -> new IllegalArgumentException("Unknown movie."));
        Screen screen = screenRepository.findById(screenId).orElseThrow(() -> new IllegalArgumentException("Unknown screen."));
        return createShow(movie, screen, startsAt, basePrice);
    }

    /**
     * Demo upkeep: makes sure every screen has showtimes for the next
     * {@code days} days. Idempotent — existing slots are skipped.
     */
    @Transactional
    public int ensureUpcomingShows(int days) {
        List<Movie> movies = movieRepository.findAllByOrderByTitleAsc();
        List<Screen> screens = screenRepository.findAllWithVenue();
        if (movies.isEmpty() || screens.isEmpty()) {
            return 0;
        }
        LocalTime[] slots = {LocalTime.of(10, 15), LocalTime.of(13, 45), LocalTime.of(17, 30), LocalTime.of(21, 0)};
        BigDecimal[] prices = {new BigDecimal("180"), new BigDecimal("220"), new BigDecimal("260"), new BigDecimal("280")};
        Instant cutoff = Instant.now().plus(Duration.ofMinutes(30));
        LocalDate today = LocalDate.now(ZONE);
        int created = 0;
        for (int d = 0; d < days; d++) {
            LocalDate date = today.plusDays(d);
            for (int s = 0; s < screens.size(); s++) {
                Screen screen = screens.get(s);
                for (int t = 0; t < slots.length; t++) {
                    Instant startsAt = ZonedDateTime.of(date, slots[t], ZONE).toInstant();
                    if (startsAt.isBefore(cutoff) || showRepository.existsByScreenIdAndStartsAt(screen.getId(), startsAt)) {
                        continue;
                    }
                    Movie movie = movies.get((date.getDayOfYear() + s * slots.length + t) % movies.size());
                    createShow(movie, screen, startsAt, prices[t]);
                    created++;
                }
            }
        }
        return created;
    }

    private Show createShow(Movie movie, Screen screen, Instant startsAt, BigDecimal basePrice) {
        Show show = showRepository.save(new Show(movie, screen, startsAt, basePrice));
        List<ShowSeat> showSeats = screenSeatRepository.findByScreenIdOrderByRowLabelAscSeatNumberAsc(screen.getId())
                .stream().map(seat -> new ShowSeat(show, seat)).toList();
        showSeatRepository.saveAll(showSeats);
        return show;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
