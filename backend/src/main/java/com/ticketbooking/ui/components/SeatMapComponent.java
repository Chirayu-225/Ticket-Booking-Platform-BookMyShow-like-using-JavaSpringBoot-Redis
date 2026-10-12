package com.ticketbooking.ui.components;

import com.ticketbooking.booking.Pricing;
import com.ticketbooking.domain.ScreenSeat;
import com.ticketbooking.domain.Show;
import com.ticketbooking.domain.ShowSeat;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.shared.Registration;

import java.util.*;
import java.util.function.Consumer;

/**
 * The cinema seat map: price tiers stacked back-to-front, row letters on both
 * sides, an aisle in wide rows, and the screen at the bottom. Pure Java —
 * every seat is a Div with a server-side click listener.
 *
 * Seats keep their click listener for their whole life and check the
 * *current* status on click, so a seat that frees up after the map was drawn
 * (a hold expired) becomes selectable on the next push refresh.
 */
public class SeatMapComponent extends Div {

    private static final List<ScreenSeat.Tier> TIER_ORDER =
            List.of(ScreenSeat.Tier.RECLINER, ScreenSeat.Tier.PREMIUM, ScreenSeat.Tier.STANDARD);

    private final Show show;
    private final int maxSelectable;
    private final Map<Long, ShowSeat> seatsById = new HashMap<>();
    private final Map<Long, ShowSeat.Status> statusById = new HashMap<>();
    private final Map<Long, Div> seatElements = new HashMap<>();
    private final Set<Long> selected = new LinkedHashSet<>();
    private final List<Consumer<List<ShowSeat>>> selectionListeners = new ArrayList<>();

    public SeatMapComponent(Show show, List<ShowSeat> seats, int maxSelectable) {
        this.show = show;
        this.maxSelectable = maxSelectable;
        addClassName("seat-map");

        Map<ScreenSeat.Tier, TreeMap<String, List<ShowSeat>>> byTierAndRow = new EnumMap<>(ScreenSeat.Tier.class);
        for (ShowSeat seat : seats) {
            seatsById.put(seat.getId(), seat);
            statusById.put(seat.getId(), seat.getStatus());
            byTierAndRow.computeIfAbsent(seat.getScreenSeat().getTier(), t -> new TreeMap<>())
                    .computeIfAbsent(seat.getScreenSeat().getRowLabel(), r -> new ArrayList<>())
                    .add(seat);
        }

        Div scroller = new Div();
        scroller.addClassName("seat-map-scroll");
        Div inner = new Div();
        inner.addClassName("seat-map-inner");

        for (ScreenSeat.Tier tier : TIER_ORDER) {
            TreeMap<String, List<ShowSeat>> rows = byTierAndRow.get(tier);
            if (rows == null) continue;

            Div section = new Div();
            section.addClassName("seat-section");
            Div label = new Div(new Span(tierName(tier)), new Span(Formats.money(Pricing.price(show, tier))));
            label.addClassName("seat-section-label");
            section.add(label);

            for (Map.Entry<String, List<ShowSeat>> row : rows.entrySet()) {
                List<ShowSeat> rowSeats = row.getValue();
                rowSeats.sort(Comparator.comparingInt(s -> s.getScreenSeat().getSeatNumber()));
                int widest = rowSeats.get(rowSeats.size() - 1).getScreenSeat().getSeatNumber();
                int aisleAfter = widest >= 10 ? widest / 2 : -1;

                Div rowDiv = new Div();
                rowDiv.addClassName("seat-row");
                rowDiv.add(rowLabel(row.getKey()));
                for (ShowSeat seat : rowSeats) {
                    rowDiv.add(buildSeat(seat));
                    if (seat.getScreenSeat().getSeatNumber() == aisleAfter) {
                        Span aisle = new Span();
                        aisle.addClassName("seat-aisle");
                        rowDiv.add(aisle);
                    }
                }
                rowDiv.add(rowLabel(row.getKey()));
                section.add(rowDiv);
            }
            inner.add(section);
        }

        Div screen = new Div(new Div(), new Span("All eyes this way"));
        screen.addClassName("cinema-screen");
        inner.add(screen);

        scroller.add(inner);
        add(scroller, buildLegend());
    }

    public static String tierName(ScreenSeat.Tier tier) {
        return switch (tier) {
            case RECLINER -> "Recliner";
            case PREMIUM -> "Premium";
            case STANDARD -> "Standard";
        };
    }

    private Span rowLabel(String text) {
        Span label = new Span(text);
        label.addClassName("seat-row-label");
        return label;
    }

    private Div buildSeat(ShowSeat seat) {
        Div div = new Div(new Span(String.valueOf(seat.getScreenSeat().getSeatNumber())));
        div.addClassName("seat");
        div.getElement().setAttribute("title", seat.getScreenSeat().getLabel() + " · "
                + tierName(seat.getScreenSeat().getTier()) + " · "
                + Formats.money(Pricing.price(show, seat.getScreenSeat().getTier())));
        div.addClickListener(e -> toggle(seat.getId()));
        seatElements.put(seat.getId(), div);
        applyState(seat.getId());
        return div;
    }

    private void toggle(Long seatId) {
        if (statusById.get(seatId) != ShowSeat.Status.AVAILABLE) {
            return;
        }
        if (selected.contains(seatId)) {
            selected.remove(seatId);
        } else if (selected.size() >= maxSelectable) {
            Notification.show("You can select up to " + maxSelectable + " seats.", 2500, Notification.Position.BOTTOM_CENTER);
            return;
        } else {
            selected.add(seatId);
        }
        applyState(seatId);
        fireSelectionChanged();
    }

    private void applyState(Long seatId) {
        Div div = seatElements.get(seatId);
        div.removeClassNames("is-available", "is-held", "is-booked", "is-selected");
        if (selected.contains(seatId)) {
            div.addClassName("is-selected");
            return;
        }
        switch (statusById.get(seatId)) {
            case AVAILABLE -> div.addClassName("is-available");
            case HELD -> div.addClassName("is-held");
            case BOOKED -> div.addClassName("is-booked");
        }
    }

    /** Push refresh: new statuses from the database; drops selections someone else just took. */
    public void refresh(List<ShowSeat> latest) {
        boolean selectionChanged = false;
        for (ShowSeat seat : latest) {
            Long id = seat.getId();
            if (!seatElements.containsKey(id)) continue;
            seatsById.put(id, seat);
            statusById.put(id, seat.getStatus());
            if (seat.getStatus() != ShowSeat.Status.AVAILABLE && selected.remove(id)) {
                selectionChanged = true;
            }
            applyState(id);
        }
        if (selectionChanged) {
            Notification.show("A seat you picked was just taken by someone else.", 3000, Notification.Position.BOTTOM_CENTER);
            fireSelectionChanged();
        }
    }

    public Set<Long> getSelectedSeatIds() {
        return Collections.unmodifiableSet(selected);
    }

    public List<ShowSeat> getSelectedSeats() {
        return selected.stream().map(seatsById::get)
                .sorted(Comparator.comparing((ShowSeat s) -> s.getScreenSeat().getRowLabel())
                        .thenComparingInt(s -> s.getScreenSeat().getSeatNumber()))
                .toList();
    }

    public Registration addSelectionListener(Consumer<List<ShowSeat>> listener) {
        selectionListeners.add(listener);
        return () -> selectionListeners.remove(listener);
    }

    private void fireSelectionChanged() {
        List<ShowSeat> current = getSelectedSeats();
        selectionListeners.forEach(l -> l.accept(current));
    }

    private Div buildLegend() {
        Div legend = new Div(
                legendItem("is-available", "Available"),
                legendItem("is-selected", "Selected"),
                legendItem("is-held", "On hold"),
                legendItem("is-booked", "Sold"));
        legend.addClassName("seat-legend");
        return legend;
    }

    private Div legendItem(String stateClass, String text) {
        Span swatch = new Span();
        swatch.addClassNames("seat", "seat-swatch", stateClass);
        Div item = new Div(swatch, new Span(text));
        item.addClassName("seat-legend-item");
        return item;
    }
}
