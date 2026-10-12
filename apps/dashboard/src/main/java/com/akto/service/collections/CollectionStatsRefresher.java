package com.akto.service.collections;

import com.akto.dao.ApiCollectionStatsMetaDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollectionStatsMeta;
import com.akto.log.LoggerMaker;
import com.akto.service.TimedService;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Refresh-on-read for api_collection_stats: a page request looks at when each metric was last
 * computed and, for the ones past their ttl, starts one background refresh.
 *
 * - at most one refresh per metric runs at a time across all dashboard instances: a refresh first
 *   claims its meta row with a compare-and-set, the same way EndpointInfoViewCron does;
 * - the request never waits for a refresh (except the very first attr sync, without which there is
 *   nothing to page) and is served from whatever the stats currently hold;
 * - nothing runs while nobody is looking at the page, and hammering the page costs no more than a
 *   refresh per metric per ttl.
 */
public class CollectionStatsRefresher {

    private static final LoggerMaker logger = new LoggerMaker(CollectionStatsRefresher.class, LogDb.DASHBOARD);

    @Getter
    @RequiredArgsConstructor
    public enum Metric {
        ATTRS_FULL(300), ATTRS_DELTA(30),
        ENDPOINTS_COUNT(120), RISK_SCORE(120), LAST_SEEN(60),
        SENSITIVE(300), SUMMARY(120);

        private final int ttlSeconds;

        /** The metrics a table column sorts or shows; the page reports "pending" until these exist. */
        boolean isTableMetric() {
            return this == ENDPOINTS_COUNT || this == RISK_SCORE || this == LAST_SEEN;
        }
    }

    // a refresh holding its claim longer than this is considered crashed and can be taken over
    static final int LOCK_TIMEOUT_SECONDS = 300;
    // the Refresh button may re-run a metric no more often than this
    static final int FORCE_COOLDOWN_SECONDS = 15;
    // after a failed refresh, wait this long before anyone retries it
    static final int FAILURE_BACKOFF_SECONDS = 30;
    static final String SUMMARY_ID = Metric.SUMMARY.name();

    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(2, 2, 60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), r -> {
                Thread t = new Thread(r, "collection-stats-refresh");
                t.setDaemon(true);
                return t;
            });

    @Getter
    @AllArgsConstructor
    public static class Freshness {
        /** metric name -> epoch seconds of its last completed refresh (0 if never) */
        private final Map<String, Integer> refreshedAt;
        /** true while a column's numbers have never been computed, so the page may still be unsorted */
        private final boolean pending;
    }

    public static Freshness ensureFresh(boolean force) {
        int now = Context.now();
        int accountId = Context.accountId.get();
        Map<Metric, ApiCollectionStatsMeta> meta = loadMeta();

        // nothing can be listed before the first attr sync, so that one the caller waits for
        if (refreshedAt(meta.get(Metric.ATTRS_FULL)) == 0 && tryClaim(Metric.ATTRS_FULL, now)) {
            runAndRelease(Metric.ATTRS_FULL);
            meta = loadMeta();
        }

        // with no rows yet (another instance is still on the first sync) a refresh would only record "fresh" over nothing
        boolean attrsReady = refreshedAt(meta.get(Metric.ATTRS_FULL)) > 0;
        for (Metric metric : Metric.values()) {
            if (metric != Metric.ATTRS_FULL && !attrsReady) continue;
            if (!isStale(meta.get(metric), metric, force, now)) continue;
            if (!tryClaim(metric, now)) continue;
            try {
                EXECUTOR.execute(() -> runInAccount(accountId, metric));
            } catch (RejectedExecutionException e) {
                release(metric, 0);
            }
        }

        Map<Metric, ApiCollectionStatsMeta> current = meta;
        Map<String, Integer> refreshedAt = Arrays.stream(Metric.values())
                .collect(Collectors.toMap(Metric::name, metric -> refreshedAt(current.get(metric))));
        boolean pending = !attrsReady || Arrays.stream(Metric.values())
                .anyMatch(metric -> metric.isTableMetric() && refreshedAt.get(metric.name()) == 0);
        return new Freshness(refreshedAt, pending);
    }

    /** Merges fields into the shared meta row that carries the summary card numbers. */
    static void saveSummary(Bson update) {
        ApiCollectionStatsMetaDao.instance.getMCollection().updateOne(
                Filters.eq(ApiCollectionStatsMeta.ID, SUMMARY_ID), update, new UpdateOptions().upsert(true));
    }

    public static ApiCollectionStatsMeta loadSummary() {
        ApiCollectionStatsMeta summary = ApiCollectionStatsMetaDao.instance.findOne(
                Filters.eq(ApiCollectionStatsMeta.ID, SUMMARY_ID));
        return summary != null ? summary : new ApiCollectionStatsMeta();
    }

    static boolean isStale(ApiCollectionStatsMeta meta, Metric metric, boolean force, int now) {
        boolean refreshButtonApplies = metric.isTableMetric() || metric == Metric.SUMMARY;
        int ttl = force && refreshButtonApplies ? FORCE_COOLDOWN_SECONDS : metric.getTtlSeconds();
        return now - refreshedAt(meta) > ttl;
    }

    private static int refreshedAt(ApiCollectionStatsMeta meta) {
        return meta == null ? 0 : meta.getRefreshedAt();
    }

    private static Map<Metric, ApiCollectionStatsMeta> loadMeta() {
        // rows are keyed by a metric name; anything else in the collection is not ours to read
        Set<String> metricNames = Arrays.stream(Metric.values()).map(Metric::name).collect(Collectors.toSet());
        return ApiCollectionStatsMetaDao.instance.findAll(Filters.empty()).stream()
                .filter(m -> metricNames.contains(m.getId()))
                .collect(Collectors.toMap(m -> Metric.valueOf(m.getId()), m -> m, (a, b) -> a, () -> new EnumMap<>(Metric.class)));
    }

    /**
     * Compare-and-set on the meta row: succeeds only if no refresh holds it (or the holder timed
     * out). Losing racers either find the filter unmatched or hit the duplicate _id while upserting.
     */
    static boolean tryClaim(Metric metric, int now) {
        // a row written only by saveSummary has no refreshStartedAt, and $lt never matches a missing field
        Bson filter = Filters.and(Filters.eq(ApiCollectionStatsMeta.ID, metric.name()),
                Filters.or(Filters.lt(ApiCollectionStatsMeta.REFRESH_STARTED_AT, now - LOCK_TIMEOUT_SECONDS),
                        Filters.exists(ApiCollectionStatsMeta.REFRESH_STARTED_AT, false)));
        try {
            return ApiCollectionStatsMetaDao.instance.getMCollection().findOneAndUpdate(filter,
                    Updates.set(ApiCollectionStatsMeta.REFRESH_STARTED_AT, now),
                    new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static void runInAccount(int accountId, Metric metric) {
        Context.accountId.set(accountId);
        try {
            runAndRelease(metric);
        } finally {
            Context.accountId.remove();
        }
    }

    private static void runAndRelease(Metric metric) {
        try {
            new MetricRunner().run(metric);
            ApiCollectionStatsMetaDao.instance.getMCollection().updateOne(
                    Filters.eq(ApiCollectionStatsMeta.ID, metric.name()),
                    Updates.combine(Updates.set(ApiCollectionStatsMeta.REFRESHED_AT, Context.now()),
                            Updates.set(ApiCollectionStatsMeta.REFRESH_STARTED_AT, 0)));
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error refreshing api_collection_stats " + metric + ": " + e.getMessage());
            // keep the claim for the backoff period so a failing refresh is not retried by every request
            release(metric, Context.now() - LOCK_TIMEOUT_SECONDS + FAILURE_BACKOFF_SECONDS);
        }
    }

    private static void release(Metric metric, int refreshStartedAt) {
        try {
            ApiCollectionStatsMetaDao.instance.getMCollection().updateOne(
                    Filters.eq(ApiCollectionStatsMeta.ID, metric.name()),
                    Updates.set(ApiCollectionStatsMeta.REFRESH_STARTED_AT, refreshStartedAt));
        } catch (Exception ignored) {
        }
    }

    /** Runs one metric's refresh and logs how long it took, like every other heavy step of this feature. */
    private static class MetricRunner extends TimedService {

        void run(Metric metric) {
            timed("refresh " + metric, () -> {
                switch (metric) {
                    case ATTRS_FULL:
                        new CollectionAttrsSync().syncAll();
                        break;
                    case ATTRS_DELTA:
                        new CollectionAttrsSync().syncRecent();
                        break;
                    case ENDPOINTS_COUNT:
                        CollectionStatsSource.forAccount().refreshEndpointsCount();
                        break;
                    case SENSITIVE:
                        CollectionStatsSource.forAccount().refreshSensitive();
                        break;
                    case RISK_SCORE:
                        new ApiInfoMetricsRefresh().refreshRiskScore();
                        break;
                    case LAST_SEEN:
                        new ApiInfoMetricsRefresh().refreshLastSeen();
                        break;
                    case SUMMARY:
                        new SummaryRefresh().refresh();
                        break;
                    default:
                        throw new IllegalStateException("unhandled metric " + metric);
                }
            });
        }
    }
}
