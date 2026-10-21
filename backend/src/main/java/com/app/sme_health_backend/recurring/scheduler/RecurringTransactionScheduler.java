package com.app.sme_health_backend.recurring.scheduler;

import com.app.sme_health_backend.recurring.service.RecurringRuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecurringTransactionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecurringTransactionScheduler.class);

    private final RecurringRuleService recurringRuleService;
    private final boolean enabled;

    @Autowired
    public RecurringTransactionScheduler(
            RecurringRuleService recurringRuleService,
            @Value("${app.recurring.scheduler.enabled:true}") boolean enabled
    ) {
        this.recurringRuleService = recurringRuleService;
        this.enabled = enabled;
    }

    @Scheduled(
            cron = "${app.recurring.scheduler.cron:0 0 1 * * *}",
            zone = "${app.recurring.scheduler.timezone:Asia/Karachi}"
    )
    public void runScheduledRecurringGeneration() {
        if (!enabled) {
            log.info("Recurring transactions background scheduler is disabled by configuration");
            return;
        }

        log.info("Starting scheduled recurring draft generation batch");
        try {
            int count = recurringRuleService.generateDueOccurrencesAllBusinesses();
            log.info("Completed scheduled recurring draft generation batch: {} drafts created", count);
        } catch (Exception e) {
            log.error("Error occurred during scheduled recurring draft generation", e);
        }
    }
}
