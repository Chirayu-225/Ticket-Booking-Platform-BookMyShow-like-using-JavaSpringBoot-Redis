package com.ticketbooking.security;

import com.ticketbooking.ui.views.LoginView;
import com.vaadin.flow.spring.security.VaadinWebSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Because the whole UI is Vaadin running server-side in this same process,
 * there's no separate SPA that needs a bearer token on every fetch — Spring
 * Security's normal session cookie, scoped by VaadinWebSecurity to Vaadin's
 * routing, is simpler AND correct here. The only endpoints outside Vaadin's
 * own routing are the Razorpay webhook (protected instead by HMAC signature
 * verification, not a login) and the actuator health check (public, for
 * Render's health probe) — both explicitly permitted below.
 */
@Configuration
public class SecurityConfig extends VaadinWebSecurity {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        // These two matchers must be registered before super.configure()'s own
        // rules so they're evaluated first — otherwise the Vaadin-added
        // "anyRequest().authenticated()" fallback would shadow them.
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/webhooks/**").permitAll()
                .requestMatchers("/actuator/health").permitAll());
        // Razorpay POSTs here with no CSRF token (it's not a browser form submission).
        http.csrf(csrf -> csrf.ignoringRequestMatchers("/api/webhooks/**"));
        super.configure(http);
        // Per-view access is then enforced by Vaadin's own @AnonymousAllowed /
        // @RolesAllowed annotations (checked by VaadinWebSecurity's
        // ViewAccessChecker), not by anything in this HTTP filter chain.
        setLoginView(http, LoginView.class);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
