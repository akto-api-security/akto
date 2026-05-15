package com.akto.action.metrics;

import org.bson.conversions.Bson;

import com.akto.action.UserAction;
import com.akto.dao.NewRelicIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dto.new_relic_integration.NewRelicIntegration;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;

import lombok.Getter;
import lombok.Setter;

public class NewRelicIntegrationAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(NewRelicIntegrationAction.class, LogDb.DASHBOARD);

    @Getter @Setter
    private String apiKey;
    
    public String addNewRelicIntegration() {
        if (apiKey == null || apiKey.isEmpty()) {
            addActionError("Please enter a valid api key.");
            return Action.ERROR.toUpperCase();
        }

        Bson combineUpdates = Updates.combine(
            Updates.set(NewRelicIntegration.API_KEY, apiKey),
            Updates.setOnInsert(NewRelicIntegration.CREATED_TS, Context.now()),
            Updates.set(NewRelicIntegration.UPDATED_TS, Context.now())
        );

        NewRelicIntegrationDao.instance.getMCollection().updateOne(
            new BasicDBObject(),
            combineUpdates,
            new UpdateOptions().upsert(true)
        );

        logger.infoAndAddToDb("Added New Relic integration successfully");

        return Action.SUCCESS.toUpperCase();
    }

    public String removeNewRelicIntegration() {
        NewRelicIntegrationDao.instance.deleteAll(new BasicDBObject());

        logger.infoAndAddToDb("Removed New Relic integration successfully");
        
        return Action.SUCCESS.toUpperCase();
    }

    @Getter @Setter
    private NewRelicIntegration newRelicIntegration;

    public String fetchNewRelicIntegration() {
        newRelicIntegration = NewRelicIntegrationDao.instance.findOne(
            new BasicDBObject(),
            Projections.exclude(NewRelicIntegration.API_KEY)
        );
        return Action.SUCCESS.toUpperCase();
    }
}
