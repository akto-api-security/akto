package com.akto.action.metrics;

import org.bson.conversions.Bson;

import com.akto.action.UserAction;
import com.akto.dao.OpenTelemetryIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dto.OpenTelemetryIntegration;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;

import lombok.Getter;
import lombok.Setter;

public class OpenTelemetryIntegrationAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(OpenTelemetryIntegrationAction.class, LogDb.DASHBOARD);

    @Getter @Setter
    private String endpoint;

    @Getter @Setter
    private String apiKey;

    public String addOpenTelemetryIntegration() {
        if (endpoint == null || endpoint.isEmpty()) {
            addActionError("Please enter a valid endpoint.");
            return Action.ERROR.toUpperCase();
        }
        if (apiKey == null || apiKey.isEmpty()) {
            addActionError("Please enter a valid API key.");
            return Action.ERROR.toUpperCase();
        }

        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }

        Bson combineUpdates = Updates.combine(
            Updates.set(OpenTelemetryIntegration.ENDPOINT, endpoint),
            Updates.set(OpenTelemetryIntegration.API_KEY, apiKey),
            Updates.setOnInsert(OpenTelemetryIntegration.CREATED_TS, Context.now()),
            Updates.set(OpenTelemetryIntegration.UPDATED_TS, Context.now())
        );

        OpenTelemetryIntegrationDao.instance.getMCollection().updateOne(
            new BasicDBObject(),
            combineUpdates,
            new UpdateOptions().upsert(true)
        );

        logger.infoAndAddToDb("Added OpenTelemetry integration successfully");

        return Action.SUCCESS.toUpperCase();
    }

    public String removeOpenTelemetryIntegration() {
        OpenTelemetryIntegrationDao.instance.deleteAll(new BasicDBObject());

        logger.infoAndAddToDb("Removed OpenTelemetry integration successfully");

        return Action.SUCCESS.toUpperCase();
    }

    @Getter @Setter
    private OpenTelemetryIntegration openTelemetryIntegration;

    public String fetchOpenTelemetryIntegration() {
        openTelemetryIntegration = OpenTelemetryIntegrationDao.instance.findOne(
            new BasicDBObject(),
            Projections.exclude(OpenTelemetryIntegration.API_KEY)
        );
        return Action.SUCCESS.toUpperCase();
    }
}
