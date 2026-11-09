package com.savewise.service;

import com.savewise.repository.RecurringTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
public class RecurringScheduler {
    private static final Logger log = LoggerFactory.getLogger(RecurringScheduler.class);

    private final RecurringTransactionRepository repo;
    private final RecurringExecutionService executor;
    private final Clock clock;

    public RecurringScheduler(RecurringTransactionRepository repo, RecurringExecutionService executor, Clock clock) {
        this.repo = repo;
        this.executor = executor;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.recurring.cron}", zone = "${app.timezone}")
    public void scheduledRun() {
        runDue();
    }

    /** Also runs once at startup, so nothing is missed if the app was down at 00:05. */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        runDue();
    }

    /** @return total records created across all schedules */
    public int runDue() {
        int total = 0;
        for (Long id : repo.findDueIds(LocalDate.now(clock))) {
            try {
                total += executor.processOne(id);
            } catch (Exception e) {
                log.error("Recurring schedule {} failed; it will be retried on the next run", id, e);
            }
        }
        if (total > 0) log.info("Recurring job posted {} record(s)", total);
        return total;
    }
}
