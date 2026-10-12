package com.ticketbooking.hold;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.UIDetachedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fan-out for "seat status on show X changed." Every open SeatMapView
 * registers here while attached; this class calls UI.access(...) so each
 * browser's own Vaadin UI gets the update over server push — no polling.
 *
 * Callers only invoke seatsChanged() after their transaction has committed
 * (see AfterCommit), so a pushed refresh always reads committed state.
 *
 * This is the one piece of shared, in-memory state in the project: it is
 * what you'd replace with Redis pub/sub to run more than one instance.
 */
@Component
public class SeatStatusBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(SeatStatusBroadcaster.class);

    private final Set<Subscriber> subscribers = ConcurrentHashMap.newKeySet();

    public Registration register(Long showId, UI ui, Runnable onShowSeatsChanged) {
        Subscriber subscriber = new Subscriber(showId, ui, onShowSeatsChanged);
        subscribers.add(subscriber);
        return () -> subscribers.remove(subscriber);
    }

    public void seatsChanged(Long showId) {
        for (Subscriber subscriber : subscribers) {
            if (!subscriber.showId().equals(showId)) {
                continue;
            }
            try {
                subscriber.ui().access(subscriber.callback()::run);
            } catch (UIDetachedException closedTab) {
                // Browser tab went away without a clean detach; drop it.
                subscribers.remove(subscriber);
            } catch (RuntimeException e) {
                // One broken subscriber must never break the caller (a booking
                // that has already committed) or the other subscribers.
                log.warn("Seat-map push to one subscriber failed", e);
            }
        }
    }

    private record Subscriber(Long showId, UI ui, Runnable callback) {}

    /** Lets SeatMapView detach cleanly with {@code registration.remove()}. */
    @FunctionalInterface
    public interface Registration {
        void remove();
    }
}
