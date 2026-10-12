package com.ticketbooking.ui.views;

import com.ticketbooking.security.CurrentUser;
import com.ticketbooking.ui.components.AuthShell;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("login")
@PageTitle("Log in | Ticket Booking")
@AnonymousAllowed
public class LoginView extends Div implements BeforeEnterObserver {

    private final LoginForm loginForm = new LoginForm();
    private final CurrentUser currentUser;

    public LoginView(CurrentUser currentUser) {
        this.currentUser = currentUser;

        H2 title = new H2("Welcome back");
        title.addClassName("auth-title");
        Paragraph subtitle = new Paragraph("Log in to book seats and see your tickets.");
        subtitle.addClassName("auth-subtitle");

        LoginI18n i18n = LoginI18n.createDefault();
        i18n.getForm().setTitle("");
        i18n.getForm().setUsername("Email");
        i18n.getForm().setSubmit("Log in");
        i18n.getErrorMessage().setTitle("Wrong email or password");
        i18n.getErrorMessage().setMessage("Check both and try again.");
        loginForm.setI18n(i18n);
        loginForm.setForgotPasswordButtonVisible(false);
        loginForm.setAction("login"); // Spring Security's form-login endpoint, wired by VaadinWebSecurity
        loginForm.addClassName("login-form");

        Div demo = new Div(new Span("Demo account"), new Span("demo@ticketbooking.dev · password123"));
        demo.addClassName("demo-hint");

        Paragraph switchLink = new Paragraph("New here? ");
        switchLink.addClassName("auth-switch");
        switchLink.add(new RouterLink("Create an account", RegisterView.class));

        add(AuthShell.wrap(title, subtitle, loginForm, demo, switchLink));
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (currentUser.get().isPresent()) {
            event.forwardTo(ShowBrowseView.class);
            return;
        }
        if (event.getLocation().getQueryParameters().getParameters().containsKey("error")) {
            loginForm.setError(true);
        }
    }
}
