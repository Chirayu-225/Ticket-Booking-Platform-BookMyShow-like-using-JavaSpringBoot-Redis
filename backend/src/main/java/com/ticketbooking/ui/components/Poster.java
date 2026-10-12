package com.ticketbooking.ui.components;

import com.ticketbooking.domain.Movie;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

/**
 * Typographic poster for movies without artwork: a gradient whose hue is
 * derived from the title, so every movie gets a stable, distinct colour.
 */
public class Poster extends Div {

    public Poster(Movie movie, String sizeClass) {
        addClassNames("poster", sizeClass);
        int hue = Math.floorMod(movie.getTitle().hashCode(), 360);
        getStyle().set("--poster-hue", String.valueOf(hue));

        Span initials = new Span(initials(movie.getTitle()));
        initials.addClassName("poster-initials");
        Span title = new Span(movie.getTitle());
        title.addClassName("poster-title");
        add(initials, title);

        if (movie.getCertificate() != null) {
            Span cert = new Span(movie.getCertificate());
            cert.addClassName("poster-cert");
            add(cert);
        }
    }

    private static String initials(String title) {
        StringBuilder sb = new StringBuilder();
        for (String word : title.split("[\\s&]+")) {
            if (!word.isEmpty() && Character.isLetterOrDigit(word.charAt(0)) && sb.length() < 2) {
                sb.append(Character.toUpperCase(word.charAt(0)));
            }
        }
        return sb.isEmpty() ? "?" : sb.toString();
    }
}
