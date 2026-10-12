package com.ticketbooking.ui;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.theme.Theme;

/**
 * @Push is the one annotation that turns on Vaadin's server push for the
 * whole app — everything SeatStatusBroadcaster does (UI.access(...) calls
 * from the hold/booking services) only reaches the browser because this is
 * present.
 *
 * No @PWA here on purpose: a PWA registers a service worker that caches the
 * frontend bundle in the browser, which makes "I changed the CSS but nothing
 * changed" much more likely during development and adds nothing for a
 * booking site.
 */
@Push
@Theme("ticketbooking")
public class AppShellConfig implements AppShellConfigurator {

    @Override
    public void configurePage(AppShellSettings settings) {
        settings.addLink("preconnect", "https://fonts.googleapis.com");
        settings.addLink("stylesheet",
                "https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap");
    }
}
