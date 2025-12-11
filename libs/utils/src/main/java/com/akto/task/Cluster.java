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
    public static final String MCP_MALICIOUSNESS_CRON_INFO = "mcp-maliciousness-cron-info";
    public static final String TOKEN_GENERATOR_CRON = "token-generator-cron";
    public static final String AUTOMATED_API_GROUPS_CRON = "automated-api-groups-cron";
    public static final String DEPENDENCY_FLOW_CRON= "dependency-flow-cron";
    public static final String DELETE_TESTING_RUN_RESULTS = "delete-testing-run-results";
    public static final String ALERTS_CRON = "alerts-cron";

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

        Dibs dibs = null;
        
        // Try to acquire lock if: lock doesn't exist OR lock has expired OR lock is held by us (renewal)
        Bson acquireLockFilter = Filters.and(
            Filters.eq("_id", prize),
            Filters.or(
                Filters.exists("expiryTs", false),  // Lock doesn't exist or is very old format
                Filters.lte("expiryTs", now),        // Lock has expired
                Filters.and(                         // Lock is held by us (renewal)
                    Filters.eq("winner", winnerId),
                    Filters.gt("expiryTs", now)      // But not expired yet
                )
            )
        );

        try {    
            dibs = DibsDao.instance.getMCollection().findOneAndUpdate(acquireLockFilter, updates, options);
            logger.info("Dibs acquired: " + dibs.toString());
        } catch (MongoCommandException e) {
            // Update failed - likely because another instance has the lock
            // Read the current state to check if we already have it
            try {
                dibs = DibsDao.instance.findOne(Filters.eq("_id", prize));
                if (dibs != null) {
                    logger.info("Dibs check existing: " + dibs.toString());
                } else {
                    logger.warn("Dibs document not found after update failure");
                }
            } catch (Exception readException) {
                logger.error("Error reading dibs after update failure", readException);
            }
        }

        // Return true only if we successfully acquired/renewed the lock
        // Check: dibs is not null, winner is us, and expiryTs matches what we set
        if (dibs == null) {
            return false;
        }
        
        boolean acquired = dibs.getWinner().equals(winnerId) && dibs.getExpiryTs() == expiryTs;
        logger.info("Dibs final result: acquired=" + acquired + ", " + dibs.toString());
        return acquired;
    }

}
