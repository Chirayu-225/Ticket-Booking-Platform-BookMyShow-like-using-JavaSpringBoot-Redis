package com.ticketbooking.ui.components;

import com.vaadin.flow.component.html.Span;

/** The small ticket-shaped logo mark used in the header and on auth pages. */
public final class Brand {

    private Brand() {}

    public static Span mark() {
        Span mark = new Span("TB");
        mark.addClassName("brand-mark");
        return mark;
    }
}
