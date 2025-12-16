package com.akto.threat.backend.cron;

import com.akto.log.LoggerMaker;
import com.akto.dao.context.Context;
import com.akto.threat.backend.dao.MaliciousEventDao;
import com.akto.threat.backend.dao.ThreatConfigurationDao;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.Document;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ArchiveOldMaliciousEventsCron implements Runnable {

    private static final LoggerMaker logger = new LoggerMaker(ArchiveOldMaliciousEventsCron.class);

    private static final int BATCH_SIZE = 5000;
    private static final long DEFAULT_RETENTION_DAYS = 60L; // default, can be overridden from DB
    private static final long MIN_RETENTION_DAYS = 30L;
    private static final long MAX_RETENTION_DAYS = 90L;
    private static final long MAX_DELETES_PER_ITERATION = 100_000L; // cap per cron iteration

    private final MongoClient mongoClient;
    private final ScheduledExecutorService scheduler;

    public ArchiveOldMaliciousEventsCron(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
        this.scheduler = Executors.newScheduledThreadPool(1);
    }

    public void cron() {
        long initialDelaySeconds = 0;
        long periodSeconds = Duration.ofHours(6).getSeconds();
        scheduler.scheduleAtFixedRate(this, initialDelaySeconds, periodSeconds, TimeUnit.SECONDS);
        logger.infoAndAddToDb("Scheduled ArchiveOldMaliciousEventsCron every 6 hours", LoggerMaker.LogDb.RUNTIME);
    }

    @Override
    public void run() {
        try {
            runOnce();
        } catch (Throwable t) {
            logger.errorAndAddToDb("Archive cron failed unexpectedly: " + t.getMessage(), LoggerMaker.LogDb.RUNTIME);
        }
    }

    public void runOnce() {
        long nowSeconds = System.currentTimeMillis() / 1000L; // epoch seconds

        try (MongoCursor<String> dbNames = mongoClient.listDatabaseNames().cursor()) {
            while (dbNames.hasNext()) {
                String dbName = dbNames.next();
                if (shouldSkipDatabase(dbName)) continue;

                Integer accId = null;
                try {
                    try {
                        accId = Integer.parseInt(dbName);
                        Context.accountId.set(accId);
                    } catch (Exception ignore) {
                        // leave context unset for non-numeric db names
                    }
                    if (accId != null) {
                        archiveOldMaliciousEvents(dbName, nowSeconds);
                    } else {
                        logger.infoAndAddToDb("Skipping archive for db as context wasn't set: " + dbName, LoggerMaker.LogDb.RUNTIME);
                    }
                } catch (Exception e) {
                    logger.errorAndAddToDb("Error processing database: " + dbName + " : " + e.getMessage(), LoggerMaker.LogDb.RUNTIME);
                } finally {
                    Context.resetContextThreadLocals();
                }
            }
        }
    }

    private boolean shouldSkipDatabase(String dbName) {
        return dbName == null || dbName.isEmpty()
                || "admin".equals(dbName)
                || "local".equals(dbName)
                || "config".equals(dbName)
                || "1669322524".equals(dbName);
    }

    private void archiveOldMaliciousEvents(String dbName, long nowSeconds) {
        String accountId = dbName;

        // Check if deletion is enabled for this account
        if (!isDeletionEnabled(accountId)) {
            logger.infoAndAddToDb("Deletion is disabled for account " + accountId + ", skipping", LoggerMaker.LogDb.RUNTIME);
            return;
        }

        long retentionDays = fetchRetentionDays(accountId);
        long threshold = nowSeconds - (retentionDays * 24 * 60 * 60);

        MongoCollection<Document> source = MaliciousEventDao.instance.getDocumentCollection(accountId);

        int totalDeleted = 0;
        long deletesThisIteration = 0L;

        while (true) {
            long iterationStartNanos = System.nanoTime();
            List<Document> batch = new ArrayList<>(BATCH_SIZE);
            try (MongoCursor<Document> cursor = source
                    .find(Filters.lte("detectedAt", threshold))
                    .sort(Sorts.ascending("detectedAt"))
                    .limit(BATCH_SIZE)
                    .cursor()) {
                while (cursor.hasNext()) {
                    batch.add(cursor.next());
                }
            }

            if (batch.isEmpty()) break;

            Set<Object> ids = new HashSet<>();
            for (Document doc : batch) {
                ids.add(doc.get("_id"));
            }

            long deleted = deleteByIds(source, ids, accountId);
            totalDeleted += (int) deleted;
            deletesThisIteration += deleted;

            long iterationElapsedMs = (System.nanoTime() - iterationStartNanos) / 1_000_000L;
            logger.infoAndAddToDb("Delete loop iteration in db " + dbName + ": batch=" + batch.size() + ", deleted=" + deleted + ", tookMs=" + iterationElapsedMs, LoggerMaker.LogDb.RUNTIME);

            if (batch.size() < BATCH_SIZE) {
                break;
            }

            if (deletesThisIteration >= MAX_DELETES_PER_ITERATION) {
                logger.infoAndAddToDb("Reached delete cap (" + MAX_DELETES_PER_ITERATION + ") for this iteration in db " + dbName + ", stopping further deletes", LoggerMaker.LogDb.RUNTIME);
                break;
            }
        }

        if (totalDeleted > 0) {
            logger.infoAndAddToDb("Completed deletion for db " + dbName + ", total deleted: " + totalDeleted, LoggerMaker.LogDb.RUNTIME);
        }
    }

    private boolean isDeletionEnabled(String accountId) {
        try {
            Document doc = ThreatConfigurationDao.instance.getCollection(accountId).find().first();
            if (doc == null) return false; // disabled by default
            Object val = doc.get("archivalEnabled");
            if (val instanceof Boolean) {
                return (Boolean) val;
            }
        } catch (Exception e) {
            logger.errorAndAddToDb("Failed fetching archivalEnabled from threat_configuration for account " + accountId + ": " + e.getMessage(), LoggerMaker.LogDb.RUNTIME);
        }
        return false; // disabled by default
    }

    private long fetchRetentionDays(String accountId) {
        try {
            Document doc = ThreatConfigurationDao.instance.getCollection(accountId).find().first();
            if (doc == null) return DEFAULT_RETENTION_DAYS;
            Object val = doc.get("archivalDays");
            if (val instanceof Number) {
                long days = ((Number) val).longValue();
                if (days < MIN_RETENTION_DAYS || days > MAX_RETENTION_DAYS) {
                    return DEFAULT_RETENTION_DAYS;
                }
                return days;
            }
        } catch (Exception e) {
            logger.errorAndAddToDb("Failed fetching archivalDays from threat_configuration for account " + accountId + ": " + e.getMessage(), LoggerMaker.LogDb.RUNTIME);
        }
        return DEFAULT_RETENTION_DAYS;
    }


    private long deleteByIds(MongoCollection<Document> source, Set<Object> ids, String accountId) {
        if (ids == null || ids.isEmpty()) return 0L;
        try {
            long deleted = source.deleteMany(Filters.in("_id", ids)).getDeletedCount();
            logger.infoAndAddToDb("Deleted " + deleted + " documents from source in account " + accountId, LoggerMaker.LogDb.RUNTIME);
            return deleted;
        } catch (Exception e) {
            logger.errorAndAddToDb("Failed to delete documents from source in account " + accountId + ": " + e.getMessage(), LoggerMaker.LogDb.RUNTIME);
            return 0L;
        }
    }
}


