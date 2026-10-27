package com.akto.utils.crons;

import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.service.guardrails.DefaultEndpointGuardrailsService;
import com.akto.util.AccountTask;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


// Every 30 min: seeds the default endpoint guardrail policies into accounts that have an endpoint collection.
public class EndpointDefaultGuardrailsCron {

    private static final LoggerMaker loggerMaker = new LoggerMaker(EndpointDefaultGuardrailsCron.class, LogDb.DASHBOARD);

    private static final int PERIOD_MINUTES = 30;

    private final DefaultEndpointGuardrailsService service = new DefaultEndpointGuardrailsService();

    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public void setUpEndpointDefaultGuardrailsCronScheduler() {
        scheduler.scheduleWithFixedDelay(this::run, 0, PERIOD_MINUTES, TimeUnit.MINUTES);
    }

    private void run() {
        try {
            AccountTask.instance.executeTask(this::processAccount, "endpoint-default-guardrails-cron");
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in endpoint default guardrails cron: " + e.getMessage());
        }
    }

    private void processAccount(Account account) {
        try {
            Context.accountId.set(account.getId());
            service.seedCurrentAccount();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error seeding default endpoint guardrails for account " + account.getId()
                    + ": " + e.getMessage());
        }
    }
}
