package com.akto.action;

import org.bson.conversions.Bson;

import com.akto.dao.WizIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dto.jobs.Job;
import com.akto.dto.jobs.JobExecutorType;
import com.akto.dto.jobs.WizSyncJobParams;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.wiz_integration.WizIntegration;
import com.akto.jobs.JobScheduler;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.wiz.WizIntegrationUtils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

import java.util.List;

import com.opensymphony.xwork2.Action;

import lombok.Getter;
import lombok.Setter;

public class WizIntegrationAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(WizIntegrationAction.class, LogDb.DASHBOARD);
    private static final int WIZ_SYNC_INTERVAL_SECONDS = 60 * 60; // 1 hour

    @Getter
    @Setter
    private String tenantDataCenter;

    @Getter
    @Setter
    private String clientId;
    
    @Getter
    @Setter
    private String clientSecret;

    public String addWizIntegration() {
        if (tenantDataCenter == null || !tenantDataCenter.matches("^[a-z0-9]+$")) {
            addActionError("Please enter a valid tenant data center.");
            return Action.ERROR.toUpperCase();
        }

        if (clientId == null || clientId.isEmpty()) {
            addActionError("Please enter a valid client ID.");
            return Action.ERROR.toUpperCase();
        }

        if (clientSecret == null || clientSecret.isEmpty()) {
            addActionError("Please enter a valid client secret.");
            return Action.ERROR.toUpperCase();
        }

        // Verify credentials by getting OAuth token
        try {
            WizIntegrationUtils.generateAccessToken(clientId, clientSecret);
            logger.info("Successfully authenticated with Wiz");
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error verifying Wiz credentials: " + e.getMessage());
            addActionError("Failed to authenticate with Wiz. Please verify your credentials.");
            return Action.ERROR.toUpperCase();
        }

        /*
         * Create a recurrring job to sync with Wiz every hour.
         * 1. Reupload findings to Wiz periodically to ensure that Wiz does not mark them as stale/closed. (docs - finding closed in 7 days if not updated)
         * 2. todo: Pull finding related data from Wiz
        */
        Job wizSyncJob = createWizSyncJob(false);
        if (wizSyncJob == null) {
            addActionError("Failed to integrate with Wiz. Please try again.");
            return Action.ERROR.toUpperCase();
        }

        Bson combineUpdates = Updates.combine(
            Updates.set(WizIntegration.TENANT_DATA_CENTER, tenantDataCenter),
            Updates.set(WizIntegration.CLIENT_ID, clientId),
            Updates.set(WizIntegration.CLIENT_SECRET, clientSecret),
            Updates.setOnInsert(WizIntegration.CREATED_TS, Context.now()),
            Updates.set(WizIntegration.UPDATED_TS, Context.now()),
            Updates.set(WizIntegration.WIZ_SYNC_JOB_ID, wizSyncJob.getId())
        );

        WizIntegrationDao.instance.getMCollection().updateOne(
            new BasicDBObject(),
            combineUpdates,
            new UpdateOptions().upsert(true)
        );

        logger.infoAndAddToDb("Added Wiz integration successfully");

        return Action.SUCCESS.toUpperCase();
    }

    public String removeWizIntegration() {
        try {
            WizIntegration existingIntegration = WizIntegrationDao.instance.findOne(new BasicDBObject());
            if (existingIntegration != null && existingIntegration.getWizSyncJobId() != null) {
                JobScheduler.deleteJob(existingIntegration.getWizSyncJobId());
            }
            WizIntegrationDao.instance.deleteAll(new BasicDBObject());
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error while removing Wiz integration for accountId: " + Context.accountId.get());
            addActionError("Failed to remove Wiz integration. Please try again.");
            return Action.ERROR.toUpperCase();
        }
        
        return Action.SUCCESS.toUpperCase();
    }

    @Getter
    @Setter
    private WizIntegration wizIntegration;

    public String fetchWizIntegration() {
        wizIntegration = WizIntegrationDao.instance.findOne(
            new BasicDBObject(),
            Projections.exclude(WizIntegration.CLIENT_SECRET)
        );
        return Action.SUCCESS.toUpperCase();
    }

    @Getter
    @Setter
    private List<TestingIssuesId> testingIssuesIdList;

    public String createWizFindings() {
        WizIntegration wizIntegration = WizIntegrationDao.instance.findOne(new BasicDBObject());
        if(wizIntegration == null) {
            logger.errorAndAddToDb("Wiz not integrated for this account: " + Context.accountId.get());
            addActionError("Wiz is not integrated.");
            return Action.ERROR.toUpperCase();
        }

        // check if list is null or empty
        if (this.testingIssuesIdList == null || this.testingIssuesIdList.isEmpty()) {
            logger.errorAndAddToDb("Testing Issues Id list is null or empty");
            addActionError("Testing Issues Id list cannot be null or empty.");
            return Action.ERROR.toUpperCase();
        }

        try {
            WizIntegrationUtils.markIssuesAsWizFinding(testingIssuesIdList);
            //WizIntegrationUtils.uploadWizDataSource(wizIntegration);
        } catch (Exception e) {
            String errString = "Error initiating wiz finding(s) creation: " + e.getMessage();
            logger.errorAndAddToDb(errString);
            addActionError(errString);
            return Action.ERROR.toUpperCase();
        }

        createWizSyncJob(true);

        return Action.SUCCESS.toUpperCase();
    }

    private Job createWizSyncJob(boolean runOnce) {
        if (runOnce) {
            JobScheduler.scheduleRunOnceJob(Context.accountId.get(), new WizSyncJobParams(), JobExecutorType.DASHBOARD);
            return null;
        } else {
            return JobScheduler.scheduleRecurringJob(Context.accountId.get(),new WizSyncJobParams(),JobExecutorType.DASHBOARD, WIZ_SYNC_INTERVAL_SECONDS);
        }
    }
}
