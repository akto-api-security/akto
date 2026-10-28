package com.akto.action;

import org.apache.commons.lang3.StringUtils;

import com.akto.dao.ImpervaIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dto.imperva_integration.ImpervaIntegration;
import com.akto.imperva.ImpervaApiClient;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;

import lombok.Getter;
import lombok.Setter;

public class ImpervaIntegrationAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(ImpervaIntegrationAction.class, LogDb.DASHBOARD);

    @Setter
    private String apiId;

    @Setter
    private String apiKey;

    @Getter
    private ImpervaIntegration impervaIntegration;

    public String addImpervaIntegration() {
        if (Context.accountId.get() != ImpervaIntegration.ENABLED_ACCOUNT_ID) {
            addActionError("Imperva integration is not enabled for this account.");
            return Action.ERROR.toUpperCase();
        }

        if (StringUtils.isBlank(apiId) || StringUtils.isBlank(apiKey)) {
            addActionError("Please enter a valid API ID and API key.");
            return Action.ERROR.toUpperCase();
        }

        try {
            new ImpervaApiClient(null, apiId.trim(), apiKey.trim()).fetchAllEndpoints();
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error verifying Imperva credentials: " + e.getMessage());
            addActionError("Failed to authenticate with Imperva. Please verify your API ID and API key.");
            return Action.ERROR.toUpperCase();
        }

        ImpervaIntegrationDao.instance.getMCollection().updateOne(
            new BasicDBObject(),
            Updates.combine(
                Updates.set(ImpervaIntegration.API_ID, apiId.trim()),
                Updates.set(ImpervaIntegration.API_KEY, apiKey.trim()),
                Updates.setOnInsert(ImpervaIntegration.CREATED_TS, Context.now()),
                Updates.set(ImpervaIntegration.UPDATED_TS, Context.now())
            ),
            new UpdateOptions().upsert(true)
        );

        logger.infoAndAddToDb("Added Imperva integration successfully");
        return Action.SUCCESS.toUpperCase();
    }

    // the import itself is run by the jobs service, which picks up this flag
    public String triggerImpervaImport() {
        if (Context.accountId.get() != ImpervaIntegration.ENABLED_ACCOUNT_ID) {
            addActionError("Imperva integration is not enabled for this account.");
            return Action.ERROR.toUpperCase();
        }

        ImpervaIntegration integration = ImpervaIntegrationDao.instance.updateOneNoUpsert(
            new BasicDBObject(),
            Updates.set(ImpervaIntegration.SYNC_REQUESTED, true)
        );
        if (integration == null) {
            addActionError("Please add the Imperva API ID and API key first.");
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String removeImpervaIntegration() {
        ImpervaIntegrationDao.instance.deleteAll(new BasicDBObject());
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchImpervaIntegration() {
        impervaIntegration = ImpervaIntegrationDao.instance.findOne(
            new BasicDBObject(),
            Projections.exclude(ImpervaIntegration.API_KEY)
        );
        return Action.SUCCESS.toUpperCase();
    }
}
