package com.ticketbooking.ui.views;

import com.ticketbooking.domain.Booking;
import com.ticketbooking.domain.BookingRepository;
import com.ticketbooking.domain.Show;
import com.ticketbooking.security.CurrentUser;
import com.ticketbooking.ui.MainLayout;
import com.ticketbooking.ui.components.Formats;
import com.ticketbooking.ui.components.Poster;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import jakarta.annotation.security.PermitAll;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Route(value = "bookings", layout = MainLayout.class)
@PageTitle("My bookings | Ticket Booking")
@PermitAll
public class BookingHistoryView extends Div {

    public BookingHistoryView(BookingRepository bookingRepository, CurrentUser currentUser) {
        addClassName("page");
        Div content = new Div();
        content.addClassNames("container", "narrow");

        H1 title = new H1("My bookings");
        title.addClassName("page-title");
        content.add(title);

        List<Booking> bookings = currentUser.id()
                .map(bookingRepository::findHistoryForUser)
                .orElse(List.of());

        if (bookings.isEmpty()) {
            RouterLink browse = new RouterLink("Browse movies", ShowBrowseView.class);
            browse.addClassName("button-link");
            Div empty = new Div(new H3("No bookings yet"),
                    new Paragraph("When you book seats, your tickets show up here."), browse);
            empty.addClassName("empty-state");
            content.add(empty);
        } else {
            Instant now = Instant.now();
            bookings.forEach(b -> content.add(ticket(b, now)));
        }
        add(content);
    }

    private Div ticket(Booking booking, Instant now) {
        Show show = booking.getShow();
        boolean upcoming = show.getStartsAt().isAfter(now);

        H3 movie = new H3(show.getMovie().getTitle());
        Paragraph where = new Paragraph(show.getScreen().getVenue().getName() + " · " + show.getScreen().getName());
        where.addClassName("ticket-where");
        Paragraph when = new Paragraph(Formats.dayTime(show.getStartsAt()));
        when.addClassName("ticket-when");

        Div seats = new Div();
        seats.addClassName("seat-chips");
        booking.getSeats().stream()
                .map(s -> s.getScreenSeat().getLabel())
                .sorted(Comparator.comparing((String l) -> l.substring(0, 1))
                        .thenComparingInt(l -> Integer.parseInt(l.substring(1))))
                .forEach(label -> {
                    Span chip = new Span(label);
                    chip.addClassName("seat-chip");
                    seats.add(chip);
                });

        Div main = new Div(new Poster(show.getMovie(), "poster-sm"), new Div(movie, where, when, seats));
        main.addClassName("ticket-main");

        Span status = new Span(upcoming ? "Upcoming" : "Watched");
        status.addClassNames("status-pill", upcoming ? "is-upcoming" : "is-past");
        Span codeLabel = new Span("Booking ID");
        codeLabel.addClassName("detail-label");
        Span code = new Span(Formats.bookingCode(booking.getId()));
        code.addClassName("ticket-code");
        Span amount = new Span(Formats.money(booking.getTotalAmount()));
        amount.addClassName("ticket-amount");

        Div stub = new Div(status, codeLabel, code, amount);
        stub.addClassName("ticket-stub");

        Div ticket = new Div(main, stub);
        ticket.addClassName("ticket");
        return ticket;
    }
}
