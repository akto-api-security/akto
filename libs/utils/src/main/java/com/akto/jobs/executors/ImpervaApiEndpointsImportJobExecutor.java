package com.akto.jobs.executors;

import com.akto.dao.ImpervaIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dto.imperva_integration.ImpervaIntegration;
import com.akto.dto.jobs.ImpervaApiEndpointsImportJobParams;
import com.akto.dto.jobs.Job;
import com.akto.jobs.JobExecutor;
import com.akto.log.LoggerMaker;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Updates;

import java.util.function.BiConsumer;

public class ImpervaApiEndpointsImportJobExecutor extends JobExecutor<ImpervaApiEndpointsImportJobParams> {

    private static final LoggerMaker logger = new LoggerMaker(ImpervaApiEndpointsImportJobExecutor.class, LoggerMaker.LogDb.DASHBOARD);
    public static final ImpervaApiEndpointsImportJobExecutor INSTANCE = new ImpervaApiEndpointsImportJobExecutor();

    // set by the jobs service; takes the integration and a heartbeat callback
    private BiConsumer<ImpervaIntegration, Runnable> impervaApiEndpointsImporter;

    public void setImpervaApiEndpointsImporter(BiConsumer<ImpervaIntegration, Runnable> impervaApiEndpointsImporter) {
        this.impervaApiEndpointsImporter = impervaApiEndpointsImporter;
    }

    public ImpervaApiEndpointsImportJobExecutor() {
        super(ImpervaApiEndpointsImportJobParams.class);
    }

    @Override
    protected void runJob(Job job) throws Exception {
        ImpervaIntegration integration = ImpervaIntegrationDao.instance.findOne(new BasicDBObject());
        if (integration == null) {
            logger.infoAndAddToDb("No Imperva integration found. Skipping Imperva API endpoints import.");
            return;
        }

        try {
            if (impervaApiEndpointsImporter == null) {
                logger.errorAndAddToDb("impervaApiEndpointsImporter is not configured. Skipping Imperva API endpoints import.");
                return;
            }
            ImpervaIntegrationDao.instance.updateOneNoUpsert(new BasicDBObject(),
                Updates.set(ImpervaIntegration.LAST_SYNC_START_TS, Context.now()));
            impervaApiEndpointsImporter.accept(integration, () -> updateJobHeartbeat(job));
        } finally {
            ImpervaIntegrationDao.instance.updateOneNoUpsert(new BasicDBObject(), Updates.combine(
                Updates.set(ImpervaIntegration.SYNC_REQUESTED, false),
                Updates.set(ImpervaIntegration.LAST_SYNC_END_TS, Context.now())
            ));
        }
    }
}
