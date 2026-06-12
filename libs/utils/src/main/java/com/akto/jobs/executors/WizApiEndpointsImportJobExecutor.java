package com.akto.jobs.executors;

import com.akto.dao.WizIntegrationDao;
import com.akto.dto.jobs.Job;
import com.akto.dto.jobs.WizApiEndpointsImportJobParams;
import com.akto.dto.wiz_integration.WizIntegration;
import com.akto.jobs.JobExecutor;
import com.akto.log.LoggerMaker;
import com.akto.open_api.parser.ParserResult;
import com.akto.wiz.WizApiEndpointsImporter;
import com.mongodb.BasicDBObject;

import java.util.function.BiConsumer;

public class WizApiEndpointsImportJobExecutor extends JobExecutor<WizApiEndpointsImportJobParams> {

    private static final LoggerMaker logger = new LoggerMaker(WizApiEndpointsImportJobExecutor.class, LoggerMaker.LogDb.DASHBOARD);
    public static final WizApiEndpointsImportJobExecutor INSTANCE = new WizApiEndpointsImportJobExecutor();

    private BiConsumer<Integer, ParserResult> wizApiEndpointsProcessor;

    public void setWizApiEndpointsProcessor(BiConsumer<Integer, ParserResult> wizApiEndpointsProcessor) {
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

            WizApiEndpointsImporter.importWizApiEndpoints(wizIntegration, wizApiEndpointsProcessor);

            logger.info("Wiz API endpoints import job completed successfully.");
        } catch (Exception e) {
            logger.error("Error in Wiz API endpoints import job: {}", e.getMessage(), e);
            throw e;
        }
    }
}
