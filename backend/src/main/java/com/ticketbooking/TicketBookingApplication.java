package com.ticketbooking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * One process, one JAR: Vaadin Flow UI + Spring Boot backend running in the same JVM.
 * A seat selection in the browser becomes a direct Java method call on the server,
 * not a network hop to a separate frontend — so there is no CORS configuration and
 * no separate frontend deployment anywhere in this project.
 */
@SpringBootApplication
@EnableScheduling // powers the expired-hold cleanup sweep in hold/HoldCleanupJob.java
public class TicketBookingApplication {
    public static void main(String[] args) {
        SpringApplication.run(TicketBookingApplication.class, args);
    }
}
