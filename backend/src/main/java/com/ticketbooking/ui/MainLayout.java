package com.ticketbooking.ui;

import com.ticketbooking.security.CurrentUser;
import com.ticketbooking.ui.components.Brand;
import com.ticketbooking.ui.views.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.HighlightConditions;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.theme.lumo.Lumo;

/**
 * App chrome: a single top bar (brand, primary nav, theme toggle, account).
 * No side drawer — a consumer booking site has three destinations, which
 * fit in the header on every screen size.
 */
public class MainLayout extends AppLayout {

    private final CurrentUser currentUser;

    public MainLayout(CurrentUser currentUser) {
        this.currentUser = currentUser;
        addClassName("app-shell");
        setPrimarySection(Section.NAVBAR);
        setDrawerOpened(false);

        RouterLink brand = new RouterLink("", ShowBrowseView.class);
        brand.addClassName("brand");
        Span brandName = new Span("Ticket Booking");
        brandName.addClassName("brand-name");
        brand.add(Brand.mark(), brandName);

        Div nav = new Div();
        nav.addClassName("top-nav");
        RouterLink movies = new RouterLink("Movies", ShowBrowseView.class);
        movies.setHighlightCondition(HighlightConditions.sameLocation());
        nav.add(movies);
        if (currentUser.get().isPresent()) {
            nav.add(new RouterLink("My bookings", BookingHistoryView.class));
        }
        if (currentUser.isAdmin()) {
            nav.add(new RouterLink("Admin", AdminView.class));
        }

        Span spacer = new Span();
        spacer.addClassName("spacer");

        Button themeToggle = new Button(VaadinIcon.ADJUST.create(), e -> toggleDarkMode());
        themeToggle.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        themeToggle.addClassName("icon-button");
        themeToggle.setAriaLabel("Toggle dark mode");

        Div header = new Div(brand, nav, spacer, themeToggle, accountArea());
        header.addClassName("app-header");
        addToNavbar(header);
    }

    private Component accountArea() {
        Div area = new Div();
        area.addClassName("account");
        currentUser.get().ifPresentOrElse(user -> {
            String name = user.getUser().getFullName();
            Span avatar = new Span(name.isBlank() ? "?" : name.substring(0, 1).toUpperCase());
            avatar.addClassName("avatar");
            Span firstName = new Span(name.split(" ")[0]);
            firstName.addClassName("account-name");
            Button logout = new Button("Log out", e -> currentUser.logout());
            logout.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            area.add(avatar, firstName, logout);
        }, () -> {
            RouterLink login = new RouterLink("Log in", LoginView.class);
            login.addClassName("login-link");
            RouterLink signUp = new RouterLink("Sign up", RegisterView.class);
            signUp.addClassName("signup-link");
            area.add(login, signUp);
        });
        return area;
    }

    private void toggleDarkMode() {
        var themeList = UI.getCurrent().getElement().getThemeList();
        if (themeList.contains(Lumo.DARK)) {
            themeList.remove(Lumo.DARK);
        } else {
            themeList.add(Lumo.DARK);
        }
    }
}
