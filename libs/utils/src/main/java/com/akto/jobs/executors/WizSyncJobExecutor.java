package com.akto.jobs.executors;

import com.akto.dao.WizIntegrationDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.jobs.Job;
import com.akto.dto.jobs.WizSyncJobParams;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.wiz_integration.WizIntegration;
import com.akto.jobs.JobExecutor;
import com.akto.log.LoggerMaker;
import com.akto.wiz.WizIntegrationUtils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

public class WizSyncJobExecutor extends JobExecutor<WizSyncJobParams> {

    private static final LoggerMaker logger = new LoggerMaker(WizSyncJobExecutor.class, LoggerMaker.LogDb.DASHBOARD);
    public static final WizSyncJobExecutor INSTANCE = new WizSyncJobExecutor();

    public WizSyncJobExecutor() {
        super(WizSyncJobParams.class);
    }

    @Override
    protected void runJob(Job job) throws Exception {
        logger.info("Starting Wiz sync job for job: {}", job.getId());
        try {
            WizIntegration wizIntegration = WizIntegrationDao.instance.findOne(new BasicDBObject());

            long secondsSinceLastUpload = (System.currentTimeMillis() / 1000L) - wizIntegration.getLastUploadedScanTs();
            boolean pastTwentyFourHours = secondsSinceLastUpload > 24 * 60 * 60;

            boolean hasNewFindings = TestingRunIssuesDao.instance.findOne(
                Filters.eq(TestingRunIssues.WIZ_FINDING_CREATION_STATUS, WizIntegration.FindingCreationStatus.CREATION_REQUESTED.toString())
            ) != null;

            if (!pastTwentyFourHours && !hasNewFindings) {
                logger.info("Skipping Wiz sync: last upload was {} seconds ago and no new findings pending.", secondsSinceLastUpload);
                return;
            }

            WizIntegrationUtils.uploadWizDataSource(wizIntegration);

            logger.info("Wiz sync job completed successfully.");
        } catch (Exception e) {
            logger.error("Error in wiz sync job: {}", e.getMessage(), e);
            throw e;
        }
    }
}
