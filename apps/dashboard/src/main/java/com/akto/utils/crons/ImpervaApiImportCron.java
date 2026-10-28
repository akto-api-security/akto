package com.akto.utils.crons;

import com.akto.dao.context.Context;
import com.akto.imperva.ImpervaApiClient;
import com.akto.imperva.ImpervaApiEndpointsImporter;
import com.akto.imperva.ImpervaApiEndpointsImporter.ImportResult;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.task.Cluster;
import com.akto.utils.imperva.ImpervaApiEndpointsImportJobUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.akto.task.Cluster.callDibs;

// Imports the API inventory from Imperva API Security every 24 hours for the single account configured via env.
// Does nothing unless IMPERVA_API_IMPORT_ACCOUNT_ID, IMPERVA_API_ID and IMPERVA_API_KEY are set.
public class ImpervaApiImportCron {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ImpervaApiImportCron.class, LogDb.DASHBOARD);

    private static final int INTERVAL_HOURS = 24;
    private static final int INITIAL_DELAY_MINUTES = 10;
    // a little under the interval so the next scheduled run on any instance can take over
    private static final int DIBS_EXPIRY_SECONDS = INTERVAL_HOURS * 3600 - 600;

    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public void setUpImpervaApiImportCronScheduler() {
        if (getAccountId() == null || StringUtils.isEmpty(System.getenv("IMPERVA_API_ID")) || StringUtils.isEmpty(System.getenv("IMPERVA_API_KEY"))) {
            return;
        }
        scheduler.scheduleWithFixedDelay(this::run, INITIAL_DELAY_MINUTES * 60, INTERVAL_HOURS * 3600, TimeUnit.SECONDS);
    }

    private static Integer getAccountId() {
        try {
            return Integer.parseInt(System.getenv("IMPERVA_API_IMPORT_ACCOUNT_ID").trim());
        } catch (Exception e) {
            return null;
        }
    }

    private void run() {
        try {
            Context.accountId.set(1_000_000);
            if (!callDibs(Cluster.IMPERVA_API_IMPORT_CRON_INFO, DIBS_EXPIRY_SECONDS, 60)) {
                loggerMaker.debugAndAddToDb("Imperva API import cron dibs not acquired, thus skipping cron");
                return;
            }

            int accountId = getAccountId();
            Context.accountId.set(accountId);
            loggerMaker.infoAndAddToDb("Starting Imperva API import for account " + accountId);

            ImpervaApiClient client = new ImpervaApiClient(System.getenv("IMPERVA_API_BASE_URL"),
                System.getenv("IMPERVA_API_ID"), System.getenv("IMPERVA_API_KEY"));
            ImportResult result = ImpervaApiEndpointsImporter.importEndpoints(client,
                ImpervaApiEndpointsImportJobUtil::impervaApiEndpointsProcessor);

            loggerMaker.infoAndAddToDb(String.format("Imperva API import finished for account %d. Endpoints: %d, basic samples: %d",
                accountId, result.getEndpointCount(), result.getFallbackCount()));
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in Imperva API import cron: " + e.getMessage());
        }
    }
}
