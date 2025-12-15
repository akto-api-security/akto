package com.akto.threat.backend.cron;

import com.akto.log.LoggerMaker;
import com.akto.dao.context.Context;
import com.akto.threat.backend.dao.ArchivedMaliciousEventDao;
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
    private static final long MAX_SOURCE_DOCS = 400_000L; // cap size
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

        // Check if archival is enabled for this account
        if (!isArchivalEnabled(accountId)) {
            logger.infoAndAddToDb("Archival is disabled for account " + accountId + ", skipping", LoggerMaker.LogDb.RUNTIME);
            return;
        }

        long retentionDays = fetchRetentionDays(accountId);
        long threshold = nowSeconds - (retentionDays * 24 * 60 * 60);

        MongoCollection<Document> source = MaliciousEventDao.instance.getDocumentCollection(accountId);
        MongoCollection<Document> dest = ArchivedMaliciousEventDao.instance.getCollection(accountId);

        int totalMoved = 0;
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

            asyncUpsertToArchive(batch, accountId);

            long deleted = deleteByIds(source, ids, accountId);
            totalMoved += (int) deleted;
            deletesThisIteration += deleted;

            long iterationElapsedMs = (System.nanoTime() - iterationStartNanos) / 1_000_000L;
            logger.infoAndAddToDb("Archive loop iteration in db " + dbName + ": batch=" + batch.size() + ", deleted=" + deleted + ", tookMs=" + iterationElapsedMs, LoggerMaker.LogDb.RUNTIME);

            if (batch.size() < BATCH_SIZE) {
                break;
            }

            if (deletesThisIteration >= MAX_DELETES_PER_ITERATION) {
                logger.infoAndAddToDb("Reached delete cap (" + MAX_DELETES_PER_ITERATION + ") for this iteration in db " + dbName + ", stopping further deletes", LoggerMaker.LogDb.RUNTIME);
                break;
            }
        }

        if (totalMoved > 0) {
            logger.infoAndAddToDb("Completed archiving for db " + dbName + ", total moved: " + totalMoved, LoggerMaker.LogDb.RUNTIME);
        }

        // Enforce collection size cap by trimming oldest docs beyond 400k.
        try {
            if (deletesThisIteration < MAX_DELETES_PER_ITERATION) {
                trimCollectionIfExceedsCap(accountId, source, dest);
            } else {
                logger.infoAndAddToDb("Skipping trim step as delete cap reached in db " + dbName, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            logger.errorAndAddToDb("Error trimming collection to cap in db " + dbName + ": " + e.getMessage(), LoggerMaker.LogDb.RUNTIME);
        }
    }

    private boolean isArchivalEnabled(String accountId) {
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

    private void trimCollectionIfExceedsCap(String accountId, MongoCollection<Document> source, MongoCollection<Document> dest) {
        long approxCount = source.countDocuments();

        if (approxCount <= MAX_SOURCE_DOCS) return;

        long totalDeleted = 0L;
        logger.infoAndAddToDb("Starting overflow trim in account " + accountId + ": approxCount=" + approxCount + ", overCap=" + (approxCount - MAX_SOURCE_DOCS), LoggerMaker.LogDb.RUNTIME);

        while (true) {
            int batch = BATCH_SIZE;

            List<Document> oldestDocs = new ArrayList<>(batch);
            try (MongoCursor<Document> cursor = source
                    .find()
                    .sort(Sorts.ascending("detectedAt"))
                    .limit(batch)
                    .cursor()) {
                while (cursor.hasNext()) {
                    oldestDocs.add(cursor.next());
                }
            }

            if (oldestDocs.isEmpty()) break;

            asyncUpsertToArchive(oldestDocs, accountId);

            Set<Object> ids = new HashSet<>();
            for (Document d : oldestDocs) {
                ids.add(d.get("_id"));
            }
            long deleted = deleteByIds(source, ids, accountId);

            totalDeleted += deleted;

            if (deleted < batch) break; 
            if (totalDeleted >= MAX_DELETES_PER_ITERATION) break;
        }

        if (totalDeleted > 0) {
            logger.infoAndAddToDb("Completed overflow trim in account " + accountId + ": deleted=" + totalDeleted, LoggerMaker.LogDb.RUNTIME);
        }
    }

    private void asyncUpsertToArchive(List<Document> docs, String accountId) {
        if (docs == null || docs.isEmpty()) return;
        final List<Document> docsSnapshot = new ArrayList<>(docs);
        final String accountIdFinal = accountId;
        this.scheduler.submit(() -> {
            try {
                ArchivedMaliciousEventDao.instance.bulkInsert(accountIdFinal, docsSnapshot);
            } catch (Exception e) {
                logger.errorAndAddToDb("Async error writing archive batch in account " + accountIdFinal + ": " + e.getMessage(), LoggerMaker.LogDb.RUNTIME);
            }
        });
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


