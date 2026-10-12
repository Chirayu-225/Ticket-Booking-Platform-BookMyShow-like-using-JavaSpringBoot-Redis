package com.ticketbooking.ui.views;

import com.razorpay.RazorpayException;
import com.ticketbooking.booking.HoldNoLongerValidException;
import com.ticketbooking.booking.Pricing;
import com.ticketbooking.domain.*;
import com.ticketbooking.hold.HoldService;
import com.ticketbooking.payment.CheckoutService;
import com.ticketbooking.payment.RazorpayService;
import com.ticketbooking.security.CurrentUser;
import com.ticketbooking.ui.MainLayout;
import com.ticketbooking.ui.components.Formats;
import com.ticketbooking.ui.components.Poster;
import com.ticketbooking.ui.components.SeatMapComponent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Order summary + payment. Razorpay's checkout.js is the one piece of
 * third-party JavaScript in the project: it renders the card/UPI popup,
 * which only Razorpay can host. When it reports success, the JS calls back
 * into paymentSucceeded() below (@ClientCallable), the server verifies
 * Razorpay's signature and books the seats — so the flow works locally
 * without a public webhook URL. The webhook remains as a backstop for users
 * who close the tab mid-payment; both paths are idempotent on payment id.
 */
@Route(value = "checkout/:holdId", layout = MainLayout.class)
@PageTitle("Checkout | Ticket Booking")
@PermitAll
@JavaScript("https://checkout.razorpay.com/v1/checkout.js")
public class CheckoutView extends Div implements BeforeEnterObserver {

    private final HoldRepository holdRepository;
    private final CheckoutService checkoutService;
    private final HoldService holdService;
    private final RazorpayService razorpayService;
    private final CurrentUser currentUser;

    private Long holdId;
    private Long userId;
    private Long showId;
    private Instant heldUntil;
    private final Div statusArea = new Div();
    private final Button payButton = new Button();

    public CheckoutView(HoldRepository holdRepository, CheckoutService checkoutService, HoldService holdService,
                        RazorpayService razorpayService, CurrentUser currentUser) {
        this.holdRepository = holdRepository;
        this.checkoutService = checkoutService;
        this.holdService = holdService;
        this.razorpayService = razorpayService;
        this.currentUser = currentUser;
        addClassName("page");
        statusArea.addClassName("checkout-status");
        statusArea.setVisible(false);
        payButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        payButton.addClassName("pay-button");
        payButton.addClickListener(e -> openPayment());
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        userId = currentUser.id().orElse(null);
        Optional<Hold> found = event.getRouteParameters().get("holdId")
                .flatMap(CheckoutView::parseId)
                .flatMap(holdRepository::findWithDetailsById)
                .filter(h -> h.getUser().getId().equals(userId));
        if (found.isEmpty()) {
            event.rerouteToError(NotFoundException.class);
            return;
        }
        render(found.get());
    }

    private void render(Hold hold) {
        removeAll();
        holdId = hold.getId();
        Show show = hold.getShow();
        showId = show.getId();
        heldUntil = hold.getExpiresAt();

        Div content = new Div();
        content.addClassName("container");
        content.addClassName("checkout");

        H1 title = new H1("Review & pay");
        title.addClassName("page-title");
        content.add(title);

        if (hold.getStatus() == Hold.Status.CONFIRMED) {
            content.add(message("You're all set", "These seats are already booked.",
                    new RouterLink("View my bookings", BookingHistoryView.class)));
            add(content);
            return;
        }
        if (hold.getStatus() != Hold.Status.ACTIVE || hold.getExpiresAt().isBefore(Instant.now())) {
            content.add(message("Your hold has expired",
                    "Seats are held for a few minutes while you pay. Pick them again to continue.",
                    seatMapLink("Choose seats again")));
            add(content);
            return;
        }

        List<ShowSeat> seats = hold.getSeats().stream()
                .sorted(Comparator.comparing((ShowSeat s) -> s.getScreenSeat().getRowLabel())
                        .thenComparingInt(s -> s.getScreenSeat().getSeatNumber()))
                .toList();
        BigDecimal total = Pricing.total(show, seats);

        Div grid = new Div(orderCard(show, seats), paymentCard(show, seats, total));
        grid.addClassName("checkout-grid");
        content.add(grid);
        add(content);
    }

    private Div orderCard(Show show, List<ShowSeat> seats) {
        Movie movie = show.getMovie();
        H3 movieTitle = new H3(movie.getTitle());
        Div meta = new Div(new Span(movie.getLanguage()), new Span(Formats.duration(movie.getDurationMinutes())));
        meta.addClassName("movie-meta");
        Div top = new Div(new Poster(movie, "poster-sm"), new Div(movieTitle, meta));
        top.addClassName("order-top");

        Div details = new Div(
                detail("Cinema", show.getScreen().getVenue().getName()),
                detail("Screen", show.getScreen().getName()),
                detail("When", Formats.dayTime(show.getStartsAt())));
        details.addClassName("order-details");

        Div seatChips = new Div();
        seatChips.addClassName("seat-chips");
        seats.forEach(s -> {
            Span chip = new Span(s.getScreenSeat().getLabel());
            chip.addClassName("seat-chip");
            seatChips.add(chip);
        });
        Div seatsBlock = new Div(label("Seats"), seatChips);

        Div card = new Div(top, details, seatsBlock);
        card.addClassNames("card", "order-card");
        return card;
    }

    private Div paymentCard(Show show, List<ShowSeat> seats, BigDecimal total) {
        Div lines = new Div();
        lines.addClassName("price-lines");
        Map<ScreenSeat.Tier, Long> countByTier = new EnumMap<>(ScreenSeat.Tier.class);
        seats.forEach(s -> countByTier.merge(s.getScreenSeat().getTier(), 1L, Long::sum));
        countByTier.forEach((tier, count) -> {
            BigDecimal each = Pricing.price(show, tier);
            lines.add(priceLine(count + " × " + SeatMapComponent.tierName(tier) + " (" + Formats.money(each) + ")",
                    Formats.money(each.multiply(BigDecimal.valueOf(count)))));
        });
        Div totalLine = priceLine("Total", Formats.money(total));
        totalLine.addClassName("is-total");

        Paragraph timer = new Paragraph("Seats held until " + Formats.time(heldUntil));
        timer.addClassName("hold-timer");

        payButton.setText("Pay " + Formats.money(total));
        payButton.setWidthFull();

        Button cancel = new Button("Release seats", e -> {
            holdService.cancel(holdId, userId);
            UI.getCurrent().navigate(SeatMapView.class, new RouteParameters("showId", String.valueOf(showId)));
        });
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        cancel.setWidthFull();

        Paragraph note = new Paragraph("Razorpay test mode — use a Razorpay test card or test UPI id. No real money moves.");
        note.addClassName("fine-print");

        Div card = new Div(new H3("Payment"), lines, totalLine, timer, statusArea, payButton, cancel, note);
        card.addClassNames("card", "payment-card");

        if (!razorpayService.isConfigured()) {
            payButton.setEnabled(false);
            setStatus("warning", "Payments aren't configured: set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET in backend/.env and restart.");
        }
        return card;
    }

    private void openPayment() {
        CheckoutService.CheckoutSession session;
        try {
            session = checkoutService.startCheckout(holdId, userId);
        } catch (HoldNoLongerValidException e) {
            setStatus("error", e.getMessage());
            payButton.setEnabled(false);
            return;
        } catch (RazorpayException e) {
            setStatus("error", "Couldn't start the payment with Razorpay: " + e.getMessage());
            return;
        }
        String email = currentUser.get().map(u -> u.getUser().getEmail()).orElse("");
        payButton.setEnabled(false);
        setStatus("info", "Complete the payment in the Razorpay window…");
        getElement().executeJs("""
                const view = this;
                if (typeof Razorpay === 'undefined') {
                  view.$server.paymentFailed('The Razorpay checkout script could not be loaded. Check your connection.');
                  return;
                }
                const rzp = new Razorpay({
                  key: $0,
                  order_id: $1,
                  name: 'Ticket Booking',
                  description: $2,
                  prefill: { email: $3 },
                  theme: { color: '#e11d48' },
                  handler: function (r) {
                    view.$server.paymentSucceeded(r.razorpay_order_id, r.razorpay_payment_id, r.razorpay_signature);
                  },
                  modal: { ondismiss: function () { view.$server.paymentDismissed(); } }
                });
                rzp.on('payment.failed', function (r) {
                  view.$server.paymentFailed((r && r.error && r.error.description) || 'Payment failed');
                });
                rzp.open();
                """, session.keyId(), session.orderId(), "Hold #" + holdId, email);
    }

    @ClientCallable
    public void paymentSucceeded(String orderId, String paymentId, String signature) {
        try {
            Booking booking = checkoutService.completePayment(holdId, userId, orderId, paymentId, signature);
            Notification n = Notification.show("Booked! Your booking id is " + Formats.bookingCode(booking.getId()),
                    5000, Notification.Position.TOP_CENTER);
            n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            UI.getCurrent().navigate(BookingHistoryView.class);
        } catch (HoldNoLongerValidException e) {
            setStatus("error", "Payment received, but your seats had already been released. "
                    + "In live mode this payment would be refunded automatically.");
        } catch (IllegalArgumentException e) {
            setStatus("error", "We couldn't verify that payment. You have not been charged twice — please contact support.");
        }
    }

    @ClientCallable
    public void paymentDismissed() {
        payButton.setEnabled(true);
        setStatus("info", "Payment cancelled. Your seats are still held until " + Formats.time(heldUntil) + ".");
    }

    @ClientCallable
    public void paymentFailed(String reason) {
        payButton.setEnabled(true);
        setStatus("error", reason);
    }

    private void setStatus(String kind, String text) {
        statusArea.removeAll();
        statusArea.getElement().setAttribute("data-kind", kind);
        statusArea.add(new Span(text));
        statusArea.setVisible(true);
    }

    private Div message(String heading, String body, RouterLink action) {
        action.addClassName("button-link");
        Div box = new Div(new H3(heading), new Paragraph(body), action);
        box.addClassName("empty-state");
        return box;
    }

    private RouterLink seatMapLink(String text) {
        return new RouterLink(text, SeatMapView.class, new RouteParameters("showId", String.valueOf(showId)));
    }

    private Div detail(String label, String value) {
        Span v = new Span(value);
        v.addClassName("detail-value");
        Div d = new Div(label(label), v);
        d.addClassName("detail");
        return d;
    }

    private Span label(String text) {
        Span s = new Span(text);
        s.addClassName("detail-label");
        return s;
    }

    private Div priceLine(String left, String right) {
        Div line = new Div(new Span(left), new Span(right));
        line.addClassName("price-line");
        return line;
    }

    private static Optional<Long> parseId(String raw) {
        try {
            return Optional.of(Long.parseLong(raw));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
