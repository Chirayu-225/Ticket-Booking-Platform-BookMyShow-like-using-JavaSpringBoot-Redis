package com.ticketbooking.ui.views;

import com.ticketbooking.domain.User;
import com.ticketbooking.domain.UserRepository;
import com.ticketbooking.ui.components.AuthShell;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Locale;

@Route("register")
@PageTitle("Create account | Ticket Booking")
@AnonymousAllowed
public class RegisterView extends Div {

    private static final int MIN_PASSWORD_LENGTH = 8;

    public RegisterView(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        H2 title = new H2("Create your account");
        title.addClassName("auth-title");
        Paragraph subtitle = new Paragraph("It takes ten seconds. No card needed until you book.");
        subtitle.addClassName("auth-subtitle");

        TextField fullName = new TextField("Full name");
        EmailField email = new EmailField("Email");
        PasswordField password = new PasswordField("Password");
        password.setHelperText("At least " + MIN_PASSWORD_LENGTH + " characters");
        for (var field : new com.vaadin.flow.component.HasSize[]{fullName, email, password}) {
            field.setWidthFull();
        }

        Button submit = new Button("Create account");
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submit.addClickShortcut(Key.ENTER);
        submit.setWidthFull();
        submit.addClickListener(e -> {
            fullName.setInvalid(false);
            email.setInvalid(false);
            password.setInvalid(false);

            String name = fullName.getValue().trim();
            String mail = email.getValue().trim().toLowerCase(Locale.ROOT);
            if (name.isEmpty()) {
                fullName.setErrorMessage("Tell us your name");
                fullName.setInvalid(true);
                return;
            }
            if (mail.isEmpty() || email.isInvalid()) {
                email.setErrorMessage("Enter a valid email");
                email.setInvalid(true);
                return;
            }
            if (password.getValue().length() < MIN_PASSWORD_LENGTH) {
                password.setErrorMessage("Use at least " + MIN_PASSWORD_LENGTH + " characters");
                password.setInvalid(true);
                return;
            }
            if (userRepository.existsByEmail(mail)) {
                email.setErrorMessage("An account with this email already exists");
                email.setInvalid(true);
                return;
            }
            try {
                userRepository.save(new User(mail, passwordEncoder.encode(password.getValue()), name, User.Role.USER));
            } catch (DataIntegrityViolationException race) {
                email.setErrorMessage("An account with this email already exists");
                email.setInvalid(true);
                return;
            }
            Notification n = Notification.show("Account created — log in to continue.", 4000, Notification.Position.TOP_CENTER);
            n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            UI.getCurrent().navigate(LoginView.class);
        });

        Div form = new Div(fullName, email, password, submit);
        form.addClassName("auth-form");

        Paragraph switchLink = new Paragraph("Already have an account? ");
        switchLink.addClassName("auth-switch");
        switchLink.add(new RouterLink("Log in", LoginView.class));

        add(AuthShell.wrap(title, subtitle, form, switchLink));
    }
}
