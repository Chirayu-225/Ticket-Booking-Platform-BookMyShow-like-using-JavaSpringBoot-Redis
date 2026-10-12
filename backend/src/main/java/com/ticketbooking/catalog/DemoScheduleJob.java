package com.ticketbooking.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Keeps the demo catalog's showtimes in the future. Disable with app.demo.auto-schedule=false. */
@Component
@ConditionalOnProperty(name = "app.demo.auto-schedule", havingValue = "true", matchIfMissing = true)
public class DemoScheduleJob implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoScheduleJob.class);

    private final CatalogService catalogService;
    private final int daysAhead;

    public DemoScheduleJob(CatalogService catalogService, @Value("${app.demo.days-ahead:4}") int daysAhead) {
        this.catalogService = catalogService;
        this.daysAhead = daysAhead;
    }

    @Override
    public void run(ApplicationArguments args) {
        topUp();
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Kolkata")
    public void topUp() {
        int created = catalogService.ensureUpcomingShows(daysAhead);
        if (created > 0) {
            log.info("Demo schedule: created {} upcoming shows", created);
        }
    }
}
