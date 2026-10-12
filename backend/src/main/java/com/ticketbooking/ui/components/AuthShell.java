package com.ticketbooking.ui.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;

/** Split-screen frame shared by the login and register pages. */
public final class AuthShell {

    private AuthShell() {}

    public static Div wrap(Component... formContent) {
        Div brandPanel = new Div();
        brandPanel.addClassName("auth-brand");

        Div logo = new Div(Brand.mark(), new Span("Ticket Booking"));
        logo.addClassName("auth-logo");

        H1 headline = new H1("Your seat, locked in before anyone else gets it.");
        headline.addClassName("auth-headline");

        Div points = new Div(
                point("Live seat map — see seats disappear as others book"),
                point("Seats held for 10 minutes while you pay"),
                point("Secure checkout with Razorpay"));
        points.addClassName("auth-points");

        brandPanel.add(logo, headline, points);

        Div card = new Div(formContent);
        card.addClassName("auth-card");
        Div formPanel = new Div(card);
        formPanel.addClassName("auth-panel");

        Div page = new Div(brandPanel, formPanel);
        page.addClassName("auth-page");
        return page;
    }

    private static Paragraph point(String text) {
        Paragraph p = new Paragraph(text);
        p.addClassName("auth-point");
        return p;
    }
}
