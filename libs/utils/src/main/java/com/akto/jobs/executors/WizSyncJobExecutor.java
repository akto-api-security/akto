package com.akto.jobs.executors;

import com.akto.dao.WizIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dto.jobs.Job;
import com.akto.dto.jobs.WizSyncJobParams;
import com.akto.dto.wiz_integration.WizIntegration;
import com.akto.jobs.JobExecutor;
import com.akto.log.LoggerMaker;
import com.akto.wiz.WizIntegrationUtils;
import com.mongodb.BasicDBObject;

public class WizSyncJobExecutor extends JobExecutor<WizSyncJobParams> {

    private static final LoggerMaker logger = new LoggerMaker(WizSyncJobExecutor.class, LoggerMaker.LogDb.DASHBOARD);
    public static final WizSyncJobExecutor INSTANCE = new WizSyncJobExecutor();

    public WizSyncJobExecutor() {
        super(WizSyncJobParams.class);
    }

    @Override
    protected void runJob(Job job) throws Exception {
        logger.info("Starting Wiz sync job for job: {}", job.getId());
        WizSyncJobParams params = paramClass.cast(job.getJobParams());

        try {
            WizIntegration wizIntegration = WizIntegrationDao.instance.findOne(new BasicDBObject());
            WizIntegrationUtils.uploadWizDataSource(wizIntegration);

            params.setLastSyncedAt(Context.now());
            updateJobParams(job, params);

            logger.info("Wiz sync job completed successfully.");
        } catch (Exception e) {
            logger.error("Error in wiz sync job: {}", e.getMessage(), e);
            throw e;
        }
    }
}
