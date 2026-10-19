package com.akto.service.trafficcleanup;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

import org.bson.conversions.Bson;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.traffic.Key;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.usage.UsageMetricCalculator;
import com.akto.utils.Utils;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

/**
 * Deletes APIs whose last observed traffic is older than a cutoff. See CLAUDE.md in this package
 * for the source of truth, the delete ordering and the accepted risks.
 *
 * <p>Preview is synchronous and deletes nothing. Delete runs on one dedicated background thread.
 * The delete thread has no Context.userId, so it is account-wide: callers must already have been
 * authorised for an account-wide action (struts: ADMIN_ACTIONS + collectionScope ACCOUNT_WIDE).
 */
public class TrafficRetentionService {

    public static final int MIN_DAYS = 30;
    public static final int MAX_DAYS = 3650;
    static final int DELETE_BATCH_SIZE = 500;
    private static final int SECONDS_PER_DAY = 24 * 60 * 60;
    private static final int MAX_ERROR_LENGTH = 200;

    interface StaleApiFetcher {
        List<Key> fetch(int apiCollectionId, int cutoffEpoch);
    }

    interface ApiDeleter {
        void delete(List<Key> keys);
    }

    public static final TrafficRetentionService instance = new TrafficRetentionService(
            SingleTypeInfoDao.instance::fetchApisNotSeenSince,
            Utils::deleteApisStiLast,
            TrafficRetentionService::fetchEligibleCollections);

    private static final LoggerMaker logger = new LoggerMaker(TrafficRetentionService.class, LogDb.DASHBOARD);

    // One purpose, one thread: deletes from different accounts queue behind each other.
    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "traffic-retention-cleanup");
        t.setDaemon(true);
        return t;
    });

    // Accounts with a delete queued or running. In-memory, so per JVM only; deletes are idempotent.
    private static final Set<Integer> runningAccounts = ConcurrentHashMap.newKeySet();

    private final StaleApiFetcher fetcher;
    private final ApiDeleter deleter;
    private final Supplier<List<ApiCollection>> collections;

    TrafficRetentionService(StaleApiFetcher fetcher, ApiDeleter deleter, Supplier<List<ApiCollection>> collections) {
        this.fetcher = fetcher;
        this.deleter = deleter;
        this.collections = collections;
    }

    public static int cutoffForDays(int days, int now) {
        validateDays(days);
        return now - days * SECONDS_PER_DAY;
    }

    public static void validateDays(int days) {
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new IllegalArgumentException("days must be between " + MIN_DAYS + " and " + MAX_DAYS);
        }
    }

    /** The cutoff comes back from the client, so re-apply the same floor the preview enforces. */
    public static void validateCutoff(int cutoffEpoch, int now) {
        if (cutoffEpoch <= 0 || cutoffEpoch > now - MIN_DAYS * SECONDS_PER_DAY) {
            throw new IllegalArgumentException("cutoffEpoch must be at least " + MIN_DAYS + " days in the past");
        }
    }

    /** Throws if the collection list itself cannot be read; a per-collection failure is a FAILED row. */
    public TrafficRetentionPreview preview(int days, int now) {
        int cutoffEpoch = cutoffForDays(days, now);
        List<CollectionCleanupResult> results = new ArrayList<>();
        for (ApiCollection collection : collections.get()) {
            String name = collection.getDisplayName();
            try {
                List<Key> stale = fetcher.fetch(collection.getId(), cutoffEpoch);
                results.add(CollectionCleanupResult.ok(collection.getId(), name, stale.size(), 0));
            } catch (Exception e) {
                logger.errorAndAddToDb(e, "Traffic retention preview failed for collection " + collection.getId());
                results.add(CollectionCleanupResult.failed(collection.getId(), name, 0, errorSummary(e)));
            }
        }
        return TrafficRetentionPreview.from(days, cutoffEpoch, results);
    }

    /** @return false if this account already has a delete queued or running. */
    public boolean startDelete(int accountId, int cutoffEpoch, int now) {
        validateCutoff(cutoffEpoch, now);
        if (!runningAccounts.add(accountId)) return false;
        try {
            executor.submit(() -> runDeleteInBackground(accountId, cutoffEpoch));
        } catch (RejectedExecutionException e) {
            runningAccounts.remove(accountId);
            throw e;
        }
        return true;
    }

    private void runDeleteInBackground(int accountId, int cutoffEpoch) {
        Context.accountId.set(accountId);
        try {
            logger.infoAndAddToDb("Traffic retention cleanup started for APIs with no traffic since " + cutoffEpoch);
            List<CollectionCleanupResult> results = runDelete(cutoffEpoch);
            long deleted = 0;
            int failed = 0;
            for (CollectionCleanupResult r : results) {
                deleted += r.getDeletedApis();
                if (r.getState() != CollectionCleanupResult.State.OK) failed++;
            }
            logger.infoAndAddToDb("Traffic retention cleanup finished: " + deleted + " APIs deleted, "
                    + failed + " of " + results.size() + " collections failed");
        } catch (Throwable t) {
            logger.errorAndAddToDb(new Exception(t), "Traffic retention cleanup aborted");
        } finally {
            runningAccounts.remove(accountId);
            Context.accountId.remove();
        }
    }

    /**
     * Synchronous body of the delete. A failing collection is recorded and the next one still runs;
     * a re-run is safe because STI is deleted last, so anything left over is still a candidate.
     */
    List<CollectionCleanupResult> runDelete(int cutoffEpoch) {
        List<CollectionCleanupResult> results = new ArrayList<>();
        for (ApiCollection collection : collections.get()) {
            String name = collection.getDisplayName();
            int deleted = 0;
            try {
                List<Key> stale = fetcher.fetch(collection.getId(), cutoffEpoch);
                for (int i = 0; i < stale.size(); i += DELETE_BATCH_SIZE) {
                    List<Key> batch = stale.subList(i, Math.min(i + DELETE_BATCH_SIZE, stale.size()));
                    deleter.delete(batch);
                    deleted += batch.size();
                }
                results.add(CollectionCleanupResult.ok(collection.getId(), name, stale.size(), deleted));
                logger.infoAndAddToDb("Traffic retention cleanup: collection " + name + " (" + collection.getId()
                        + ") deleted " + deleted + " of " + stale.size() + " stale APIs");
            } catch (Exception e) {
                logger.errorAndAddToDb(e, "Traffic retention cleanup failed for collection " + collection.getId()
                        + " after deleting " + deleted + " APIs");
                results.add(CollectionCleanupResult.failed(collection.getId(), name, deleted, errorSummary(e)));
            }
        }
        return results;
    }

    /** Active, non-demo host collections. Groups, deactivated collections and no-hostName ones are out of scope. */
    static List<ApiCollection> fetchEligibleCollections() {
        Bson filter = Filters.and(
                Filters.ne(ApiCollection.HOST_NAME, null),
                Filters.ne(ApiCollection._DEACTIVATED, true),
                Filters.ne(ApiCollection._TYPE, ApiCollection.Type.API_GROUP.name()),
                Filters.nin(ApiCollection.ID, new ArrayList<>(UsageMetricCalculator.getDemos())));
        return ApiCollectionsDao.instance.findAll(filter,
                Projections.include(ApiCollection.ID, ApiCollection.NAME, ApiCollection.HOST_NAME, ApiCollection.SERVICE_TAG));
    }

    static boolean tryAcquire(int accountId) {
        return runningAccounts.add(accountId);
    }

    static void release(int accountId) {
        runningAccounts.remove(accountId);
    }

    private static String errorSummary(Exception e) {
        String message = e.getMessage() == null ? "" : ": " + e.getMessage();
        String summary = e.getClass().getSimpleName() + message;
        return summary.length() > MAX_ERROR_LENGTH ? summary.substring(0, MAX_ERROR_LENGTH) : summary;
    }
}
