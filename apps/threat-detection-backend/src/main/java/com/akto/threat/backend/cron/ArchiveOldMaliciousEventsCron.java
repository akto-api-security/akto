package com.akto.threat.backend.cron;

import com.akto.log.LoggerMaker;
import com.akto.dao.context.Context;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.WriteModel;
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

    private static final String SOURCE_COLLECTION = "malicious_events";
    private static final String DEST_COLLECTION = "archived_malicious_events";
    private static final int BATCH_SIZE = 5000;
    private static final long DEFAULT_RETENTION_DAYS = 60L; // default, can be overridden from DB
    private static final long MAX_SOURCE_DOCS = 400_000L; // cap size

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
                    processDatabase(dbName, nowSeconds);
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
                || "config".equals(dbName);
    }

    private void processDatabase(String dbName, long nowSeconds) {
        MongoDatabase db = mongoClient.getDatabase(dbName);
        ensureCollectionExists(db, DEST_COLLECTION);

        long retentionDays = fetchRetentionDays(db);
        if (!(retentionDays == 30L || retentionDays == 60L || retentionDays == 90L)) {
            retentionDays = DEFAULT_RETENTION_DAYS;
        }
        long threshold = nowSeconds - (retentionDays * 24 * 60 * 60);

        MongoCollection<Document> source = db.getCollection(SOURCE_COLLECTION, Document.class);
        MongoCollection<Document> dest = db.getCollection(DEST_COLLECTION, Document.class);

        int totalMoved = 0;

        while (true) {
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

            List<WriteModel<Document>> writes = new ArrayList<>(batch.size());
            Set<Object> ids = new HashSet<>();
            for (Document doc : batch) {
                Object id = doc.get("_id");
                ids.add(id);
                writes.add(new ReplaceOneModel<>(
                        Filters.eq("_id", id),
                        doc,
                        new ReplaceOptions().upsert(true)
                ));
            }

            // Insert into archive asynchronously, deletion proceeds synchronously
            final List<WriteModel<Document>> writeSnapshot = new ArrayList<>(writes);
            this.scheduler.submit(() -> {
                try {
                    dest.bulkWrite(writeSnapshot);
                } catch (MongoBulkWriteException bwe) {
                    logger.errorAndAddToDb("Async bulk write error archiving to " + DEST_COLLECTION + " in db " + dbName + ": " + bwe.getMessage(), LoggerMaker.LogDb.RUNTIME);
                } catch (Exception e) {
                    logger.errorAndAddToDb("Async error writing archive batch in db " + dbName + ": " + e.getMessage(), LoggerMaker.LogDb.RUNTIME);
                }
            });

            try {
                long deleted = source.deleteMany(Filters.in("_id", ids)).getDeletedCount();
                totalMoved += (int) deleted;
                logger.infoAndAddToDb("Archived and deleted " + deleted + " events from db " + dbName, LoggerMaker.LogDb.RUNTIME);
            } catch (Exception e) {
                logger.errorAndAddToDb("Failed to delete archived documents from source in db " + dbName + ": " + e.getMessage(), LoggerMaker.LogDb.RUNTIME);
            }

            if (batch.size() < BATCH_SIZE) {
                break;
            }
        }

        if (totalMoved > 0) {
            logger.infoAndAddToDb("Completed archiving for db " + dbName + ", total moved: " + totalMoved, LoggerMaker.LogDb.RUNTIME);
        }

        // Enforce collection size cap by trimming oldest docs beyond 400k.
        try {
            trimCollectionIfExceedsCap(dbName, source, dest);
        } catch (Exception e) {
            logger.errorAndAddToDb("Error trimming collection to cap in db " + dbName + ": " + e.getMessage(), LoggerMaker.LogDb.RUNTIME);
        }
    }

    private long fetchRetentionDays(MongoDatabase db) {
        try {
            MongoCollection<Document> cfg = db.getCollection("threat_configuration", Document.class);
            Document doc = cfg.find().first();
            if (doc == null) return DEFAULT_RETENTION_DAYS;
            Object val = doc.get("archivalDays");
            if (val instanceof Number) {
                long days = ((Number) val).longValue();
                if (days == 30L || days == 60L || days == 90L) return days;
            }
        } catch (Exception e) {
            logger.errorAndAddToDb("Failed fetching archivalDays from threat_configuration for db " + db.getName() + ": " + e.getMessage(), LoggerMaker.LogDb.RUNTIME);
        }
        return DEFAULT_RETENTION_DAYS;
    }

    private void ensureCollectionExists(MongoDatabase db, String collectionName) {
        boolean exists = false;
        try (MongoCursor<String> it = db.listCollectionNames().cursor()) {
            while (it.hasNext()) {
                if (collectionName.equals(it.next())) {
                    exists = true;
                    break;
                }
            }
        }
        if (!exists) {
            try {
                db.createCollection(collectionName);
            } catch (Exception e) {
                logger.infoAndAddToDb("Tried creating collection: " + collectionName + " -> " + e.getMessage(), LoggerMaker.LogDb.RUNTIME);
            }
        }
    }

    private void trimCollectionIfExceedsCap(String dbName, MongoCollection<Document> source, MongoCollection<Document> dest) {
        long approxCount = source.countDocuments();

        if (approxCount <= MAX_SOURCE_DOCS) return;

        long toRemove = approxCount - MAX_SOURCE_DOCS;
        long totalDeleted = 0L;
        logger.infoAndAddToDb("Starting overflow trim in db " + dbName + ": approxCount=" + approxCount + ", toRemove=" + toRemove, LoggerMaker.LogDb.RUNTIME);

        while (toRemove > 0) {
            int batch = (int) Math.min(BATCH_SIZE, toRemove);

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

            final List<Document> docsSnapshot = new ArrayList<>(oldestDocs);
            this.scheduler.submit(() -> {
                try {
                    dest.insertMany(docsSnapshot);
                } catch (Exception e) {
                    logger.errorAndAddToDb("Async archive insert failed in db " + dbName + ": " + e.getMessage(), LoggerMaker.LogDb.RUNTIME);
                }
            });

            Set<Object> ids = new HashSet<>();
            for (Document d : oldestDocs) {
                ids.add(d.get("_id"));
            }
            long deleted = 0L;
            try {
                deleted = source.deleteMany(Filters.in("_id", ids)).getDeletedCount();
            } catch (Exception e) {
                logger.errorAndAddToDb("Overflow trim delete failed in db " + dbName + ": " + e.getMessage(), LoggerMaker.LogDb.RUNTIME);
            }

            totalDeleted += deleted;
            toRemove -= deleted;

            if (deleted < batch) {
                break;
            }
        }

        if (totalDeleted > 0) {
            logger.infoAndAddToDb("Completed overflow trim in db " + dbName + ": deleted=" + totalDeleted, LoggerMaker.LogDb.RUNTIME);
        }
    }
}


