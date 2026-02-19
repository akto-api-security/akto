package com.akto.action;

import org.bson.conversions.Bson;

import com.akto.dao.WizIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.wiz_integration.WizIntegration;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.utils.WizIntegrationUtils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

import java.util.List;
import java.util.Map;

import com.opensymphony.xwork2.Action;

import lombok.Getter;
import lombok.Setter;

public class WizIntegrationAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(WizIntegrationAction.class, LogDb.DASHBOARD);

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
        if (tenantDataCenter == null || tenantDataCenter.isEmpty()) { 
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
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error verifying Wiz credentials: " + e.getMessage());
            addActionError("Failed to authenticate with Wiz. Please verify your credentials.");
            return Action.ERROR.toUpperCase();
        }

        Bson combineUpdates = Updates.combine(
            Updates.set(WizIntegration.TENANT_DATA_CENTER, tenantDataCenter),
            Updates.set(WizIntegration.CLIENT_ID, clientId),
            Updates.set(WizIntegration.CLIENT_SECRET, clientSecret),
            Updates.setOnInsert(WizIntegration.CREATED_TS, Context.now()),
            Updates.set(WizIntegration.UPDATED_TS, Context.now())
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
        WizIntegrationDao.instance.deleteAll(new BasicDBObject());
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
    private TestingIssuesId testingIssuesId;

    public String createWizFinding() {
        WizIntegration wizIntegration = WizIntegrationDao.instance.findOne(new BasicDBObject());
        if(wizIntegration == null) {
            logger.errorAndAddToDb("Wiz not not integrated for this account: " + Context.accountId.get());
            addActionError("Wiz is not integrated.");
            return Action.ERROR.toUpperCase();
        }

        if (this.testingIssuesId == null) {
            logger.errorAndAddToDb("Testing Issues Id is null");
            addActionError("Testing Issues Id cannot be null.");
            return Action.ERROR.toUpperCase();
        }

        String enrichmentJSON;
        try {
            enrichmentJSON = WizIntegrationUtils.prepareSingleIssueEnrichmentJSON(testingIssuesId);
        } catch (Exception e) {
            String errString = "Error preparing enrichment JSON for Wiz: " + e.getMessage();
            logger.errorAndAddToDb(e, errString);
            addActionError("Error creating wiz finding.");
            return Action.ERROR.toUpperCase();
        }

        try {
            Map<String, String> securityScanUploadResult = WizIntegrationUtils.requestSecurityScanUpload("akto-testing-issue.json");
            String signedS3Url = securityScanUploadResult.get("url");
            WizIntegrationUtils.uploadEnrichmentJSONToS3(enrichmentJSON, signedS3Url);
            WizIntegrationUtils.updateWizFindingUrl(testingIssuesId);
        } catch (Exception e) {
            String errString = "Error uploading enrichment JSON to Wiz: " + e.getMessage();
            logger.errorAndAddToDb(errString);
            addActionError(errString);
            return Action.ERROR.toUpperCase();
        }

        return Action.SUCCESS.toUpperCase();
    }

    @Getter
    @Setter
    private List<TestingIssuesId> testingIssuesIdList;

    public String createWizFindings() {
        WizIntegration wizIntegration = WizIntegrationDao.instance.findOne(new BasicDBObject());
        if(wizIntegration == null) {
            logger.errorAndAddToDb("Wiz not not integrated for this account: " + Context.accountId.get());
            addActionError("Wiz is not integrated.");
            return Action.ERROR.toUpperCase();
        }

        // check if list is null or empty
        if (this.testingIssuesIdList == null || this.testingIssuesIdList.isEmpty()) {
            logger.errorAndAddToDb("Testing Issues Id list is null or empty");
            addActionError("Testing Issues Id list cannot be null or empty.");
            return Action.ERROR.toUpperCase();
        }

        String enrichmentJSON;
        try {
            enrichmentJSON = WizIntegrationUtils.prepareMultipleIssueEnrichmentJSON(testingIssuesIdList);
        } catch (Exception e) {
            String errString = "Error preparing enrichment JSON for Wiz: " + e.getMessage();
            logger.errorAndAddToDb(e, errString);
            addActionError("Error creating wiz findings.");
            return Action.ERROR.toUpperCase();
        }

        try {
            Map<String, String> securityScanUploadResult = WizIntegrationUtils.requestSecurityScanUpload("akto-testing-issue.json");
            String signedS3Url = securityScanUploadResult.get("url");
            WizIntegrationUtils.uploadEnrichmentJSONToS3(enrichmentJSON, signedS3Url);

            // todo: update finding url for each issue
            // for (TestingIssuesId testingIssuesId : testingIssuesIdList) {
            //     WizIntegrationUtils.updateWizFindingUrl(testingIssuesId);
            // }
        } catch (Exception e) {
            String errString = "Error uploading enrichment JSON to Wiz: " + e.getMessage();
            logger.errorAndAddToDb(errString);
            addActionError(errString);
            return Action.ERROR.toUpperCase();
        }

        return Action.SUCCESS.toUpperCase();
    }
}
