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

        //Verify credentials by getting OAuth token
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

    public String sendToWiz() {
        return Action.SUCCESS.toUpperCase();
    }
}
