package com.ticketbooking.security;

import com.vaadin.flow.spring.security.AuthenticationContext;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The logged-in user, if any. Views ask this instead of casting
 * SecurityContextHolder's principal: for an anonymous visitor that principal
 * is the String "anonymousUser", and the old casts threw ClassCastException.
 */
@Component
public class CurrentUser {

    private final AuthenticationContext authenticationContext;

    public CurrentUser(AuthenticationContext authenticationContext) {
        this.authenticationContext = authenticationContext;
    }

    public Optional<AppUserDetails> get() {
        return authenticationContext.getAuthenticatedUser(AppUserDetails.class);
    }

    public Optional<Long> id() {
        return get().map(details -> details.getUser().getId());
    }

    public boolean isAdmin() {
        return get().map(details -> details.getUser().getRole() == com.ticketbooking.domain.User.Role.ADMIN)
                .orElse(false);
    }

    public void logout() {
        authenticationContext.logout();
    }
}
