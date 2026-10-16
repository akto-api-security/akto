package com.akto.utils.jobs;

import com.akto.dao.context.Context;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.result.DeleteResult;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One-shot drain of stale module_info docs for a single account, ahead of adding the expiresAt TTL
 * index there. That account's collection grew to ~25M docs; building the TTL index on it and letting
 * the TTL monitor delete the backlog in one unpaced pass would spike CPU and replication lag, so the
 * backlog is removed here first in small, paced batches.
 *
 * Every query goes through the existing moduleType_1_lastHeartbeatReceived_1 index via hint(): an
 * unindexed filter (eg. on expiresAt) would collection-scan 25M docs per batch. If the index is
 * missing, hint() makes the query fail rather than fall back to a scan.
 *
 * Runs once per process start on its own thread, so a multi-hour drain cannot hold up the shared
 * cron scheduler. It is idempotent: once drained, a restart costs a few index seeks.
 */
public class ModuleInfoCleanupJob {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ModuleInfoCleanupJob.class, LogDb.DASHBOARD);

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final int ACCOUNT_ID = 1736798101;

    /** Docs with no heartbeat for this long are deleted. */
    private static final int STALE_DAYS = 4;

    private static final int BATCH = 15_000;

    private static final long SLEEP_BETWEEN_BATCHES_MS = 1000;

    private static final Bson INDEX_HINT = Indexes.ascending(ModuleInfo.MODULE_TYPE, ModuleInfo.LAST_HEARTBEAT_RECEIVED);

    public static void runOnce() {
        executor.submit(() -> {
            try {
                cleanup();
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error in module info cleanup job for account " + ACCOUNT_ID
                        + ": " + e.getMessage());
            }
        });
    }

    private static void cleanup() throws InterruptedException {
        Context.accountId.set(ACCOUNT_ID);
        int cutoff = Context.now() - STALE_DAYS * 86400;

        MongoCollection<Document> coll = ModuleInfoDao.instance.getMCollection().withDocumentClass(Document.class);
        // majority write concern makes each batch wait for secondaries, so the job slows down on its
        // own when replication lags instead of piling up oplog
        MongoCollection<Document> deleteColl = coll.withWriteConcern(WriteConcern.MAJORITY);

        List<BsonValue> types = coll.distinct(ModuleInfo.MODULE_TYPE, BsonValue.class).into(new ArrayList<>());
        loggerMaker.warnAndAddToDb("Module info cleanup START account=" + ACCOUNT_ID + " cutoff=" + cutoff
                + " moduleTypes=" + types);

        long total = 0;
        int batches = 0;
        for (BsonValue type : types) {
            // Stale heartbeats. Starts at 1: docs with lastHeartbeatReceived=0 never reported a
            // heartbeat and may be live.
            int from = 1;
            while (true) {
                List<Document> docs = coll.find(Filters.and(
                                Filters.eq(ModuleInfo.MODULE_TYPE, type),
                                Filters.gte(ModuleInfo.LAST_HEARTBEAT_RECEIVED, from),
                                Filters.lt(ModuleInfo.LAST_HEARTBEAT_RECEIVED, cutoff)))
                        .projection(Projections.include("_id", ModuleInfo.LAST_HEARTBEAT_RECEIVED))
                        .sort(Sorts.ascending(ModuleInfo.MODULE_TYPE, ModuleInfo.LAST_HEARTBEAT_RECEIVED))
                        .hint(INDEX_HINT)
                        .limit(BATCH)
                        .into(new ArrayList<>());
                if (docs.isEmpty()) break;

                // keyset cursor: resume from the last heartbeat seen instead of rescanning deleted keys
                from = ((Number) docs.get(docs.size() - 1).get(ModuleInfo.LAST_HEARTBEAT_RECEIVED)).intValue();

                // re-check the cutoff so a module that heartbeated since the find is not deleted
                long deleted = deleteIds(deleteColl, docs, Filters.lt(ModuleInfo.LAST_HEARTBEAT_RECEIVED, cutoff));
                total += deleted;
                batches++;
                if (deleted == 0) break;

                Thread.sleep(SLEEP_BETWEEN_BATCHES_MS);
            }

            // No heartbeat field at all (missing or null). eq(null) matches both and stays on the
            // index: missing values are indexed as null. No keyset needed, deleted docs drop out.
            Bson noHeartbeat = Filters.eq(ModuleInfo.LAST_HEARTBEAT_RECEIVED, null);
            while (true) {
                List<Document> docs = coll.find(Filters.and(Filters.eq(ModuleInfo.MODULE_TYPE, type), noHeartbeat))
                        .projection(Projections.include("_id"))
                        .hint(INDEX_HINT)
                        .limit(BATCH)
                        .into(new ArrayList<>());
                if (docs.isEmpty()) break;

                // re-check so a module that started heartbeating since the find is not deleted
                long deleted = deleteIds(deleteColl, docs, noHeartbeat);
                total += deleted;
                batches++;
                if (deleted == 0) break;

                Thread.sleep(SLEEP_BETWEEN_BATCHES_MS);
            }
        }

        loggerMaker.warnAndAddToDb("Module info cleanup DONE account=" + ACCOUNT_ID + " batches=" + batches
                + " deleted=" + total);
    }

    /** Deletes the given docs that still match `stillMatches`; returns how many were deleted. */
    private static long deleteIds(MongoCollection<Document> deleteColl, List<Document> docs, Bson stillMatches) {
        List<Object> ids = new ArrayList<>(docs.size());
        for (Document doc : docs) {
            ids.add(doc.get("_id"));
        }
        DeleteResult result = deleteColl.deleteMany(Filters.and(Filters.in("_id", ids), stillMatches));
        return result.getDeletedCount();
    }
}
