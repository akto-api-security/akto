package com.akto.task;

import java.util.UUID;

import com.akto.dao.context.Context;
import com.akto.dao.DibsDao;
import com.akto.dto.Dibs;
import com.mongodb.MongoCommandException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;

import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Cluster {
    private static final Logger logger = LoggerFactory.getLogger(Cluster.class);
    public static final String RUNTIME_MERGER = "runtime-merger";
    public static final String TELEMETRY_CRON = "telemetry-cron";
    public static final String MAP_SENSITIVE_IN_INFO = "map-sensitiveInfo-in-ApiInfo";
    public static final String SYNC_CRON_INFO = "sync-cron-info";
    public static final String TOKEN_GENERATOR_CRON = "token-generator-cron";
    public static final String AUTOMATED_API_GROUPS_CRON = "automated-api-groups-cron";
    public static final String DEPENDENCY_FLOW_CRON= "dependency-flow-cron";
    public static final String DELETE_TESTING_RUN_RESULTS = "delete-testing-run-results";

    public static final String winnerId = UUID.randomUUID().toString();

    public static boolean callDibs(String prize, int expiryPeriod, int freqInSeconds) {
        int now = Context.now();
        int expiryTs = expiryPeriod + now;
        Bson setOnInsert = Updates.setOnInsert("_id", prize);

        Bson updates = Updates.combine(
            Updates.set("winner", winnerId),
            Updates.set("expiryTs", expiryTs),
            Updates.set("startTs", now),
            Updates.set("freqInSeconds", freqInSeconds),
            Updates.set("lastPing", now),
            setOnInsert
        );

        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER);

        Dibs dibs;
        Bson findKeyQ = Filters.and(
            Filters.eq("_id", prize),
            Filters.exists("expiryTs", false)
        );

        try {    
            dibs = DibsDao.instance.getMCollection().findOneAndUpdate(findKeyQ, updates, options);
            logger.info("try" + dibs);
        } catch (MongoCommandException e) {
            // already present
            Bson findExpiredKeyQ = Filters.and(
                Filters.eq("_id", prize),
                Filters.lte("expiryTs", now)
            );
    
            try {
                dibs = DibsDao.instance.getMCollection().findOneAndUpdate(findExpiredKeyQ, updates, options);
                logger.info("catch1" + dibs);
            } catch (MongoCommandException eInside) {
                dibs = DibsDao.instance.findOne(Filters.eq("_id", prize));
                logger.error("catch2" + dibs);
            }
        }

        logger.info("final: " + dibs);

        return (dibs == null || (dibs.getWinner().equals(winnerId) && dibs.getExpiryTs() == expiryTs));
    }

}
