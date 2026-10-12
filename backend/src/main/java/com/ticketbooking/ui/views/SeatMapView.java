package com.ticketbooking.ui.views;

import com.ticketbooking.booking.Pricing;
import com.ticketbooking.domain.*;
import com.ticketbooking.hold.HoldService;
import com.ticketbooking.hold.SeatStatusBroadcaster;
import com.ticketbooking.hold.SeatUnavailableException;
import com.ticketbooking.security.CurrentUser;
import com.ticketbooking.ui.MainLayout;
import com.ticketbooking.ui.components.Formats;
import com.ticketbooking.ui.components.Poster;
import com.ticketbooking.ui.components.SeatMapComponent;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.springframework.dao.ConcurrencyFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Every seat click is a server-side Java call — no fetch(), no REST. While
 * this view is open it is subscribed to SeatStatusBroadcaster, so seats other
 * people hold or buy change colour here within moments via server push.
 */
@Route(value = "seats/:showId", layout = MainLayout.class)
@PageTitle("Select seats | Ticket Booking")
@AnonymousAllowed
public class SeatMapView extends Div implements BeforeEnterObserver {

    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final HoldService holdService;
    private final SeatStatusBroadcaster broadcaster;
    private final CurrentUser currentUser;

    private Show show;
    private SeatMapComponent seatMap;
    private final Span summarySeats = new Span();
    private final Span summaryTotal = new Span();
    private final Button continueButton = new Button();
    private SeatStatusBroadcaster.Registration pushRegistration;

    public SeatMapView(ShowRepository showRepository, ShowSeatRepository showSeatRepository,
                       HoldService holdService, SeatStatusBroadcaster broadcaster, CurrentUser currentUser) {
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
        this.holdService = holdService;
        this.broadcaster = broadcaster;
        this.currentUser = currentUser;
        addClassName("page");

        continueButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        continueButton.addClickListener(e -> holdAndContinue());
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Optional<Show> found = event.getRouteParameters().get("showId")
                .flatMap(SeatMapView::parseId)
                .flatMap(showRepository::findWithDetailsById);
        if (found.isEmpty()) {
            event.rerouteToError(NotFoundException.class);
            return;
        }
        show = found.get();
        render();
    }

    private void render() {
        removeAll();
        Movie movie = show.getMovie();

        RouterLink back = new RouterLink("← All showtimes", ShowBrowseView.class);
        back.addClassName("back-link");

        H1 title = new H1(movie.getTitle());
        Div meta = new Div(
                new Span(show.getScreen().getVenue().getName() + " · " + show.getScreen().getName()),
                new Span(Formats.dayTime(show.getStartsAt())));
        meta.addClassName("show-header-meta");
        Div headerText = new Div(back, title, meta);
        headerText.addClassName("show-header-text");

        Div header = new Div(new Poster(movie, "poster-sm"), headerText);
        header.addClassName("show-header");

        Div content = new Div();
        content.addClassName("container");

        if (!show.getStartsAt().isAfter(Instant.now())) {
            Div notice = new Div(new H3("This show has already started"),
                    new Paragraph("Bookings are closed. Pick another showtime."));
            notice.addClassName("empty-state");
            content.add(header, notice);
            add(content);
            return;
        }

        seatMap = new SeatMapComponent(show, showSeatRepository.findByShowId(show.getId()), holdService.getMaxSeatsPerHold());
        seatMap.addSelectionListener(this::updateSummary);

        Div card = new Div(seatMap);
        card.addClassName("card");
        card.addClassName("seat-card");
        content.add(header, card);
        add(content, bookingBar());
        updateSummary(List.of());
        subscribe();
    }

    private Div bookingBar() {
        summarySeats.addClassName("bar-seats");
        summaryTotal.addClassName("bar-total");
        Div text = new Div(summarySeats, summaryTotal);
        text.addClassName("bar-text");
        Div inner = new Div(text, continueButton);
        inner.addClassName("bar-inner");
        Div bar = new Div(inner);
        bar.addClassName("booking-bar");
        return bar;
    }

    private void updateSummary(List<ShowSeat> selected) {
        if (selected.isEmpty()) {
            summarySeats.setText("Select up to " + holdService.getMaxSeatsPerHold() + " seats");
            summaryTotal.setText("Prices shown per seat on the map");
            continueButton.setText("Continue");
            continueButton.setEnabled(false);
            return;
        }
        String labels = selected.stream().map(s -> s.getScreenSeat().getLabel()).collect(Collectors.joining(", "));
        BigDecimal total = Pricing.total(show, selected);
        summarySeats.setText(selected.size() + (selected.size() == 1 ? " seat · " : " seats · ") + labels);
        summaryTotal.setText(Formats.money(total));
        continueButton.setText("Pay " + Formats.money(total));
        continueButton.setEnabled(true);
    }

    private void holdAndContinue() {
        Optional<Long> userId = currentUser.id();
        if (userId.isEmpty()) {
            Notification.show("Log in to book these seats.", 3000, Notification.Position.MIDDLE);
            UI.getCurrent().navigate(LoginView.class);
            return;
        }
        try {
            Hold hold = holdService.holdSeats(userId.get(), show.getId(), seatMap.getSelectedSeatIds());
            UI.getCurrent().navigate(CheckoutView.class, new RouteParameters("holdId", String.valueOf(hold.getId())));
        } catch (SeatUnavailableException ex) {
            showError(ex.getMessage());
            seatMap.refresh(showSeatRepository.findByShowId(show.getId()));
        } catch (ConcurrencyFailureException ex) {
            showError("Lots of people are booking right now — please try again.");
            seatMap.refresh(showSeatRepository.findByShowId(show.getId()));
        }
    }

    private void showError(String message) {
        Notification n = Notification.show(message, 4000, Notification.Position.MIDDLE);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    private void subscribe() {
        unsubscribe();
        if (show == null || seatMap == null) return;
        getUI().ifPresent(ui -> {
            Long showId = show.getId();
            pushRegistration = broadcaster.register(showId, ui,
                    () -> seatMap.refresh(showSeatRepository.findByShowId(showId)));
        });
    }

    private void unsubscribe() {
        if (pushRegistration != null) {
            pushRegistration.remove();
            pushRegistration = null;
        }
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        subscribe();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        unsubscribe();
        super.onDetach(detachEvent);
    }

    private static Optional<Long> parseId(String raw) {
        try {
            return Optional.of(Long.parseLong(raw));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
