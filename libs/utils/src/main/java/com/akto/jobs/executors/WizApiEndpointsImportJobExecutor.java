package com.akto.jobs.executors;

import com.akto.dao.WizIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dto.jobs.Job;
import com.akto.dto.jobs.WizApiEndpointsImportJobParams;
import com.akto.dto.wiz_integration.WizIntegration;
import com.akto.jobs.JobExecutor;
import com.akto.log.LoggerMaker;
import com.akto.open_api.parser.ParserResult;
import com.akto.wiz.WizApiEndpointsImporter;
import com.akto.wiz.WizImportJobPageContext;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Updates;

import java.util.function.BiConsumer;

public class WizApiEndpointsImportJobExecutor extends JobExecutor<WizApiEndpointsImportJobParams> {

    private static final LoggerMaker logger = new LoggerMaker(WizApiEndpointsImportJobExecutor.class, LoggerMaker.LogDb.DASHBOARD);
    public static final WizApiEndpointsImportJobExecutor INSTANCE = new WizApiEndpointsImportJobExecutor();

    private BiConsumer<ParserResult, WizImportJobPageContext> wizApiEndpointsProcessor;

    public void setWizApiEndpointsProcessor(BiConsumer<ParserResult, WizImportJobPageContext> wizApiEndpointsProcessor) {
        this.wizApiEndpointsProcessor = wizApiEndpointsProcessor;
    }

    public WizApiEndpointsImportJobExecutor() {
        super(WizApiEndpointsImportJobParams.class);
    }

    @Override
    protected void runJob(Job job) throws Exception {
        logger.info("Starting Wiz API endpoints import job for job: {}", job.getId());
        try {
            WizIntegration wizIntegration = WizIntegrationDao.instance.findOne(new BasicDBObject());
            if (wizIntegration == null) {
                logger.info("No Wiz integration found. Skipping Wiz API endpoints import.");
                return;
            }

            int nextJobDeltaTs = Context.now();
            int endpointCount = WizApiEndpointsImporter.importWizApiEndpoints(wizIntegration, wizApiEndpointsProcessor, () -> updateJobHeartbeat(job));

            WizIntegrationDao.instance.updateOne(
                new BasicDBObject(),
                Updates.combine(
                    Updates.set(WizIntegration.WIZ_IMPORT_API_ENDPOINTS_JOB_DELTA_ENDPOINT_COUNT, endpointCount),
                    Updates.set(WizIntegration.WIZ_IMPORT_API_ENDPOINTS_JOB_DELTA_TS, nextJobDeltaTs)
                )
            );

            logger.info("Wiz API endpoints import job completed successfully.");
        } catch (Exception e) {
            logger.error("Error in Wiz API endpoints import job: {}", e.getMessage(), e);
            throw e;
        }
    }
}
