package com.ticketbooking.ui.views;

import com.ticketbooking.booking.Pricing;
import com.ticketbooking.domain.*;
import com.ticketbooking.ui.MainLayout;
import com.ticketbooking.ui.components.Formats;
import com.ticketbooking.ui.components.Poster;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.*;

@Route(value = "", layout = MainLayout.class)
@PageTitle("Movies | Ticket Booking")
@AnonymousAllowed
public class ShowBrowseView extends Div {

    private static final int DAYS_SHOWN = 5;

    private final ShowRepository showRepository;
    private final Div dateStrip = new Div();
    private final Div results = new Div();
    private final H1 heading = new H1();

    private String city;
    private LocalDate selectedDate = LocalDate.now(Formats.ZONE);

    public ShowBrowseView(ShowRepository showRepository, VenueRepository venueRepository) {
        this.showRepository = showRepository;
        addClassName("page");

        List<String> cities = venueRepository.findDistinctCities();
        city = cities.isEmpty() ? "Bengaluru" : cities.get(0);

        // Hero
        Paragraph sub = new Paragraph("Pick a showtime, choose your seats on the live map, and they're yours for 10 minutes while you pay.");
        sub.addClassName("hero-sub");

        Select<String> citySelect = new Select<>();
        citySelect.setLabel("City");
        citySelect.setItems(cities.isEmpty() ? List.of(city) : cities);
        citySelect.setValue(city);
        citySelect.addClassName("city-select");
        citySelect.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                city = e.getValue();
                render();
            }
        });

        Div heroText = new Div(heading, sub);
        heroText.addClassName("hero-text");
        Div heroInner = new Div(heroText, citySelect);
        heroInner.addClassName("hero-inner");
        Div hero = new Div(heroInner);
        hero.addClassName("hero");

        dateStrip.addClassName("date-strip");
        results.addClassName("movie-list");

        Div content = new Div(dateStrip, results);
        content.addClassName("container");
        add(hero, content);

        render();
    }

    private void render() {
        heading.setText("Now showing in " + city);
        renderDateStrip();

        Instant now = Instant.now();
        Instant dayStart = selectedDate.atStartOfDay(Formats.ZONE).toInstant();
        Instant from = dayStart.isBefore(now) ? now : dayStart;
        Instant to = selectedDate.plusDays(1).atStartOfDay(Formats.ZONE).toInstant();

        // One query, then grouped in memory: movie -> venue -> showtimes.
        Map<Long, List<Show>> byMovie = new LinkedHashMap<>();
        for (Show show : showRepository.findInCityBetween(city, from, to)) {
            byMovie.computeIfAbsent(show.getMovie().getId(), id -> new ArrayList<>()).add(show);
        }

        results.removeAll();
        if (byMovie.isEmpty()) {
            Div empty = new Div(new H3("No more shows on this day"), new Paragraph("Try another date above."));
            empty.addClassName("empty-state");
            results.add(empty);
            return;
        }
        byMovie.values().stream()
                .sorted(Comparator.comparing(shows -> shows.get(0).getMovie().getTitle()))
                .forEach(shows -> results.add(movieCard(shows)));
    }

    private void renderDateStrip() {
        dateStrip.removeAll();
        LocalDate today = LocalDate.now(Formats.ZONE);
        for (int i = 0; i < DAYS_SHOWN; i++) {
            LocalDate date = today.plusDays(i);
            Span dow = new Span(i == 0 ? "Today" : i == 1 ? "Tmrw" : date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            dow.addClassName("date-dow");
            Span dom = new Span(String.valueOf(date.getDayOfMonth()));
            dom.addClassName("date-dom");
            Span month = new Span(date.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            month.addClassName("date-month");

            Div chip = new Div(dow, dom, month);
            chip.addClassName("date-chip");
            if (date.equals(selectedDate)) {
                chip.addClassName("is-active");
            }
            chip.addClickListener(e -> {
                selectedDate = date;
                render();
            });
            dateStrip.add(chip);
        }
    }

    private Div movieCard(List<Show> shows) {
        Movie movie = shows.get(0).getMovie();

        H3 title = new H3(movie.getTitle());
        title.addClassName("movie-title");

        Div meta = new Div();
        meta.addClassName("movie-meta");
        meta.add(tag(movie.getLanguage()));
        if (movie.getGenre() != null) meta.add(new Span(movie.getGenre()));
        meta.add(new Span(Formats.duration(movie.getDurationMinutes())));

        Div info = new Div(title, meta);
        info.addClassName("movie-info");

        if (movie.getSynopsis() != null && !movie.getSynopsis().isBlank()) {
            Paragraph synopsis = new Paragraph(movie.getSynopsis());
            synopsis.addClassName("movie-synopsis");
            info.add(synopsis);
        }

        Map<Long, List<Show>> byVenue = new LinkedHashMap<>();
        for (Show show : shows) {
            byVenue.computeIfAbsent(show.getScreen().getVenue().getId(), id -> new ArrayList<>()).add(show);
        }
        for (List<Show> venueShows : byVenue.values()) {
            Venue venue = venueShows.get(0).getScreen().getVenue();
            Div venueName = new Div(new Span(venue.getName()));
            venueName.addClassName("venue-name");
            if (venue.getAddress() != null) {
                Span address = new Span(venue.getAddress());
                address.addClassName("venue-address");
                venueName.add(address);
            }

            Div times = new Div();
            times.addClassName("showtimes");
            for (Show show : venueShows) {
                times.add(showtimeChip(show));
            }
            Div venueRow = new Div(venueName, times);
            venueRow.addClassName("venue-row");
            info.add(venueRow);
        }

        Div card = new Div(new Poster(movie, "poster-md"), info);
        card.addClassName("movie-card");
        return card;
    }

    private RouterLink showtimeChip(Show show) {
        RouterLink chip = new RouterLink("", SeatMapView.class,
                new RouteParameters("showId", String.valueOf(show.getId())));
        chip.addClassName("showtime");
        Span time = new Span(Formats.time(show.getStartsAt()));
        time.addClassName("showtime-time");
        Span price = new Span("from " + Formats.money(Pricing.price(show, ScreenSeat.Tier.STANDARD)));
        price.addClassName("showtime-price");
        chip.add(time, price);
        chip.getElement().setAttribute("title", show.getScreen().getName());
        return chip;
    }

    private Span tag(String text) {
        Span span = new Span(text);
        span.addClassName("tag");
        return span;
    }
}
