package com.akto.utils.crons;

import com.akto.dao.ImpervaIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dto.imperva_integration.ImpervaIntegration;
import com.akto.imperva.ImpervaApiClient;
import com.akto.imperva.ImpervaApiEndpointsImporter;
import com.akto.imperva.ImpervaApiEndpointsImporter.ImportResult;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.utils.imperva.ImpervaApiEndpointsImportJobUtil;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// Imports the API inventory from Imperva API Security for ImpervaIntegration.ENABLED_ACCOUNT_ID
// whenever an import is requested from the dashboard (Settings > Integrations > Imperva).
public class ImpervaApiImportCron {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ImpervaApiImportCron.class, LogDb.DASHBOARD);

    private static final int CHECK_INTERVAL_MINUTES = 5;

    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public void setUpImpervaApiImportCronScheduler() {
        scheduler.scheduleWithFixedDelay(this::run, CHECK_INTERVAL_MINUTES, CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    private void run() {
        Context.accountId.set(ImpervaIntegration.ENABLED_ACCOUNT_ID);
        ImpervaIntegration integration;
        try {
            // clearing the flag atomically makes sure only one instance runs the requested import
            integration = ImpervaIntegrationDao.instance.getMCollection().findOneAndUpdate(
                Filters.eq(ImpervaIntegration.SYNC_REQUESTED, true),
                Updates.combine(
                    Updates.set(ImpervaIntegration.SYNC_REQUESTED, false),
                    Updates.set(ImpervaIntegration.LAST_SYNC_START_TS, Context.now())
                )
            );
        } catch (Exception e) {
            loggerMaker.error("Error checking for a requested Imperva API import: " + e.getMessage(), e);
            return;
        }
        if (integration == null) {
            return;
        }

        try {
            loggerMaker.infoAndAddToDb("Starting Imperva API import");
            ImpervaApiClient client = new ImpervaApiClient(null, integration.getApiId(), integration.getApiKey());
            ImportResult result = ImpervaApiEndpointsImporter.importEndpoints(client,
                ImpervaApiEndpointsImportJobUtil::impervaApiEndpointsProcessor);
            loggerMaker.infoAndAddToDb(String.format("Imperva API import finished. Endpoints: %d, basic samples: %d",
                result.getEndpointCount(), result.getFallbackCount()));
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in Imperva API import cron: " + e.getMessage());
        }

        try {
            ImpervaIntegrationDao.instance.updateOneNoUpsert(new BasicDBObject(),
                Updates.set(ImpervaIntegration.LAST_SYNC_END_TS, Context.now()));
        } catch (Exception e) {
            loggerMaker.error("Error updating Imperva API import end time: " + e.getMessage(), e);
        }
    }
}
