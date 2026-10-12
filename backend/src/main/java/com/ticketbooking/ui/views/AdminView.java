package com.ticketbooking.ui.views;

import com.ticketbooking.catalog.CatalogService;
import com.ticketbooking.domain.*;
import com.ticketbooking.ui.MainLayout;
import com.ticketbooking.ui.components.Formats;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Catalog management + sales. Every multi-row write goes through
 * CatalogService (one transaction each); every grid is fed by a
 * fetch-joined query, so rendering never lazy-loads.
 */
@Route(value = "admin", layout = MainLayout.class)
@PageTitle("Admin | Ticket Booking")
@RolesAllowed("ADMIN")
public class AdminView extends Div {

    private final CatalogService catalogService;
    private final MovieRepository movieRepository;
    private final VenueRepository venueRepository;
    private final ScreenRepository screenRepository;
    private final ShowRepository showRepository;
    private final BookingRepository bookingRepository;

    private final Grid<Movie> movieGrid = new Grid<>();
    private final Grid<Venue> venueGrid = new Grid<>();
    private final Grid<Screen> screenGrid = new Grid<>();
    private final Grid<Show> showGrid = new Grid<>();
    private final ComboBox<Venue> screenVenue = new ComboBox<>("Venue");
    private final ComboBox<Movie> showMovie = new ComboBox<>("Movie");
    private final ComboBox<Screen> showScreen = new ComboBox<>("Screen");

    public AdminView(CatalogService catalogService, MovieRepository movieRepository, VenueRepository venueRepository,
                     ScreenRepository screenRepository, ShowRepository showRepository,
                     BookingRepository bookingRepository) {
        this.catalogService = catalogService;
        this.movieRepository = movieRepository;
        this.venueRepository = venueRepository;
        this.screenRepository = screenRepository;
        this.showRepository = showRepository;
        this.bookingRepository = bookingRepository;
        addClassName("page");

        Div content = new Div();
        content.addClassName("container");
        H1 title = new H1("Admin");
        title.addClassName("page-title");

        TabSheet tabs = new TabSheet();
        tabs.addClassName("admin-tabs");
        tabs.add("Sales", salesTab());
        tabs.add("Shows", showsTab());
        tabs.add("Movies", moviesTab());
        tabs.add("Venues & screens", venuesTab());
        tabs.setWidthFull();

        content.add(title, tabs);
        add(content);
        refreshAll();
    }

    // ---------- Sales ----------

    private Component salesTab() {
        List<Booking> bookings = bookingRepository.findAllWithDetails();
        BigDecimal revenue = bookings.stream().map(Booking::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        int seatsSold = bookings.stream().mapToInt(b -> b.getSeats().size()).sum();
        long upcomingShows = showRepository.findAllWithDetails().stream()
                .filter(s -> s.getStartsAt().isAfter(Instant.now())).count();

        Div stats = new Div(
                stat("Revenue", Formats.money(revenue)),
                stat("Bookings", String.valueOf(bookings.size())),
                stat("Seats sold", String.valueOf(seatsSold)),
                stat("Upcoming shows", String.valueOf(upcomingShows)));
        stats.addClassName("stat-grid");

        Grid<Booking> grid = new Grid<>();
        grid.addColumn(b -> Formats.bookingCode(b.getId())).setHeader("Booking").setAutoWidth(true);
        grid.addColumn(b -> b.getShow().getMovie().getTitle()).setHeader("Movie").setFlexGrow(1);
        grid.addColumn(b -> b.getUser().getEmail()).setHeader("Customer").setFlexGrow(1);
        grid.addColumn(b -> b.getSeats().size()).setHeader("Seats").setAutoWidth(true);
        grid.addColumn(b -> Formats.money(b.getTotalAmount())).setHeader("Amount").setAutoWidth(true);
        grid.addColumn(b -> Formats.dayTime(b.getCreatedAt())).setHeader("Booked at").setAutoWidth(true);
        grid.setItems(bookings);
        styleGrid(grid);

        return section(stats, card("Recent bookings", grid));
    }

    // ---------- Shows ----------

    private Component showsTab() {
        showGrid.addColumn(s -> s.getMovie().getTitle()).setHeader("Movie").setFlexGrow(1);
        showGrid.addColumn(s -> s.getScreen().getVenue().getName() + " · " + s.getScreen().getName()).setHeader("Screen").setFlexGrow(1);
        showGrid.addColumn(s -> Formats.dayTime(s.getStartsAt())).setHeader("Starts").setAutoWidth(true);
        showGrid.addColumn(s -> Formats.money(s.getBasePrice())).setHeader("Base price").setAutoWidth(true);
        styleGrid(showGrid);

        showMovie.setItemLabelGenerator(Movie::getTitle);
        showScreen.setItemLabelGenerator(sc -> sc.getVenue().getName() + " · " + sc.getName());
        DateTimePicker startsAt = new DateTimePicker("Starts at (IST)");
        startsAt.setMin(LocalDateTime.now(Formats.ZONE));
        BigDecimalField price = new BigDecimalField("Base price (₹)");

        Button add = primary("Schedule show", () -> {
            if (showMovie.isEmpty() || showScreen.isEmpty() || startsAt.isEmpty() || price.isEmpty()) {
                throw new IllegalArgumentException("Fill in every field.");
            }
            Instant start = startsAt.getValue().atZone(Formats.ZONE).toInstant();
            catalogService.scheduleShow(showMovie.getValue().getId(), showScreen.getValue().getId(), start, price.getValue());
            startsAt.clear();
            price.clear();
            return "Show scheduled.";
        });

        return section(card("Schedule a show", formRow(showMovie, showScreen, startsAt, price, add)),
                card("All shows", showGrid));
    }

    // ---------- Movies ----------

    private Component moviesTab() {
        movieGrid.addColumn(Movie::getTitle).setHeader("Title").setFlexGrow(1);
        movieGrid.addColumn(Movie::getLanguage).setHeader("Language").setAutoWidth(true);
        movieGrid.addColumn(m -> m.getGenre() == null ? "—" : m.getGenre()).setHeader("Genre").setAutoWidth(true);
        movieGrid.addColumn(m -> m.getCertificate() == null ? "—" : m.getCertificate()).setHeader("Cert").setAutoWidth(true);
        movieGrid.addColumn(m -> Formats.duration(m.getDurationMinutes())).setHeader("Runtime").setAutoWidth(true);
        styleGrid(movieGrid);

        TextField title = new TextField("Title");
        TextField language = new TextField("Language");
        TextField genre = new TextField("Genre");
        ComboBox<String> certificate = new ComboBox<>("Certificate");
        certificate.setItems("U", "UA", "A");
        IntegerField duration = new IntegerField("Minutes");
        duration.setMin(1);

        Button add = primary("Add movie", () -> {
            if (title.isEmpty() || language.isEmpty() || duration.isEmpty()) {
                throw new IllegalArgumentException("Title, language and minutes are required.");
            }
            catalogService.addMovie(title.getValue(), language.getValue(), duration.getValue(),
                    genre.getValue(), certificate.getValue());
            title.clear(); language.clear(); genre.clear(); certificate.clear(); duration.clear();
            return "Movie added.";
        });

        return section(card("Add a movie", formRow(title, language, genre, certificate, duration, add)),
                card("Movies", movieGrid));
    }

    // ---------- Venues & screens ----------

    private Component venuesTab() {
        venueGrid.addColumn(Venue::getName).setHeader("Venue").setFlexGrow(1);
        venueGrid.addColumn(Venue::getCity).setHeader("City").setAutoWidth(true);
        venueGrid.addColumn(v -> v.getAddress() == null ? "—" : v.getAddress()).setHeader("Address").setFlexGrow(1);
        styleGrid(venueGrid);

        screenGrid.addColumn(sc -> sc.getVenue().getName()).setHeader("Venue").setFlexGrow(1);
        screenGrid.addColumn(Screen::getName).setHeader("Screen").setAutoWidth(true);
        screenGrid.addColumn(sc -> sc.getTotalRows() * sc.getSeatsPerRow() + " seats").setHeader("Capacity").setAutoWidth(true);
        styleGrid(screenGrid);

        TextField name = new TextField("Venue name");
        TextField city = new TextField("City");
        TextField address = new TextField("Address");
        Button addVenue = primary("Add venue", () -> {
            catalogService.addVenue(name.getValue(), city.getValue(), address.getValue());
            name.clear(); city.clear(); address.clear();
            return "Venue added.";
        });

        screenVenue.setItemLabelGenerator(Venue::getName);
        TextField screenName = new TextField("Screen name");
        IntegerField rows = new IntegerField("Rows (1-26)");
        IntegerField perRow = new IntegerField("Seats per row");
        Button addScreen = primary("Add screen", () -> {
            if (screenVenue.isEmpty() || screenName.isEmpty() || rows.isEmpty() || perRow.isEmpty()) {
                throw new IllegalArgumentException("Fill in every field.");
            }
            Screen screen = catalogService.addScreen(screenVenue.getValue().getId(), screenName.getValue(),
                    rows.getValue(), perRow.getValue());
            screenName.clear(); rows.clear(); perRow.clear();
            return "Screen created with " + (screen.getTotalRows() * screen.getSeatsPerRow()) + " seats.";
        });

        return section(
                card("Add a venue", formRow(name, city, address, addVenue)),
                card("Add a screen", formRow(screenVenue, screenName, rows, perRow, addScreen)),
                card("Venues", venueGrid),
                card("Screens", screenGrid));
    }

    // ---------- helpers ----------

    private void refreshAll() {
        List<Movie> movies = movieRepository.findAllByOrderByTitleAsc();
        List<Venue> venues = venueRepository.findAllByOrderByNameAsc();
        List<Screen> screens = screenRepository.findAllWithVenue();
        movieGrid.setItems(movies);
        venueGrid.setItems(venues);
        screenGrid.setItems(screens);
        showGrid.setItems(showRepository.findAllWithDetails());
        showMovie.setItems(movies);
        screenVenue.setItems(venues);
        showScreen.setItems(screens);
    }

    @FunctionalInterface
    private interface Action {
        String run();
    }

    /** A primary button whose action reports success/validation errors as notifications and refreshes all lists. */
    private Button primary(String text, Action action) {
        Button button = new Button(text, e -> {
            try {
                String message = action.run();
                refreshAll();
                Notification n = Notification.show(message, 3000, Notification.Position.BOTTOM_START);
                n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (IllegalArgumentException ex) {
                Notification n = Notification.show(ex.getMessage(), 4000, Notification.Position.BOTTOM_START);
                n.addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        return button;
    }

    private void styleGrid(Grid<?> grid) {
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        grid.setHeight("380px");
    }

    private Div stat(String label, String value) {
        Span l = new Span(label);
        l.addClassName("stat-label");
        Span v = new Span(value);
        v.addClassName("stat-value");
        Div d = new Div(l, v);
        d.addClassName("stat");
        return d;
    }

    private Div card(String heading, Component body) {
        Div card = new Div(new H3(heading), body);
        card.addClassNames("card", "admin-card");
        return card;
    }

    private Div formRow(Component... fields) {
        Div row = new Div(fields);
        row.addClassName("form-row");
        return row;
    }

    private Div section(Component... parts) {
        Div d = new Div(parts);
        d.addClassName("admin-section");
        return d;
    }
}
