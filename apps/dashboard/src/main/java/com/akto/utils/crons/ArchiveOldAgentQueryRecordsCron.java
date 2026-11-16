package com.akto.utils.crons;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.utils.elasticsearch.ElasticSearchClient;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Elasticsearch counterpart of ArchiveOldMaliciousEventsCron: every 6 hours, deletes agent
 * query records older than the retention window from ES_INDEX_AGENT_QUERY.
 */
public class ArchiveOldAgentQueryRecordsCron {

    private static final LoggerMaker logger = new LoggerMaker(ArchiveOldAgentQueryRecordsCron.class, LogDb.DASHBOARD);

    private static final long RETENTION_DAYS = 60L;
    private static final int BATCH_SIZE = 10_000;
    private static final long MAX_DELETES_PER_ITERATION = 100_000L; // cap per cron iteration
    private static final long PERIOD_HOURS = 6L;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public void setUpArchiveOldAgentQueryRecordsScheduler() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                runOnce();
            } catch (Throwable t) {
                logger.errorAndAddToDb("ES archive cron failed unexpectedly: " + t.getMessage());
            }
        }, 0, PERIOD_HOURS, TimeUnit.HOURS);
        logger.infoAndAddToDb("Scheduled ArchiveOldAgentQueryRecordsCron every " + PERIOD_HOURS + " hours");
    }

    void runOnce() {
        ElasticSearchClient es = ElasticSearchClient.instance();
        if (!es.isConfigured()) {
            logger.info("ES archive cron: Elasticsearch not configured, skipping");
            return;
        }

        long cutoffMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RETENTION_DAYS);
        long totalDeleted = 0L;

        while (totalDeleted < MAX_DELETES_PER_ITERATION) {
            long iterationStartNanos = System.nanoTime();
            long deleted = es.deleteOlderThan(cutoffMs, BATCH_SIZE);
            if (deleted < 0) {
                logger.errorAndAddToDb("ES archive cron: delete_by_query failed, stopping this iteration");
                break;
            }
            totalDeleted += deleted;

            long elapsedMs = (System.nanoTime() - iterationStartNanos) / 1_000_000L;
            logger.infoAndAddToDb("ES archive delete batch: deleted=" + deleted + ", tookMs=" + elapsedMs);

            if (deleted < BATCH_SIZE) break;
        }

        if (totalDeleted >= MAX_DELETES_PER_ITERATION) {
            logger.infoAndAddToDb("ES archive cron reached delete cap (" + MAX_DELETES_PER_ITERATION + "), rest deferred to next run");
        }
        logger.infoAndAddToDb("ES archive cron completed, total deleted: " + totalDeleted
            + " (records older than " + RETENTION_DAYS + " days)");
    }
}
