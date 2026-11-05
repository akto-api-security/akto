package com.akto.utils.jobs;

import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Starts {@link MismatchedSampleCleanupJob} from the dashboard's startup ({@code InitializerListener}),
 * driven entirely by env vars, so a dedicated dashboard container can run the cleanup on prod.
 *
 * <pre>
 * MISMATCH_CLEANUP_ENABLED=true                    required; anything else = this class does nothing
 * MISMATCH_CLEANUP_ACCOUNT_IDS=1000000             required; comma separated
 * MISMATCH_CLEANUP_DRY_RUN=true                    only the exact value "false" deletes (default: dry run)
 * MISMATCH_CLEANUP_COLLECTIONS=123,456,...         optional; limit to these apiCollectionIds
 * MISMATCH_CLEANUP_MAX_DELETES_PER_COLLECTION=0    optional; delete mode leaves a collection untouched when its
 *                                                  planned deletes exceed this (0 = no cap)
 * MISMATCH_CLEANUP_INTERVAL_HOURS=0                optional; 0 = run once at startup, N = every N hours
 * MISMATCH_CLEANUP_REPORT_DIR=/tmp/mismatch-cleanup  optional; TSV reports go to &lt;dir&gt;/&lt;runId&gt;/
 * MISMATCH_CLEANUP_ONLY=true                       default true when enabled: every other dashboard job/cron
 *                                                  is skipped in this container (see InitializerListener)
 * </pre>
 */
public class MismatchedSampleCleanupScheduler {

    private static final LoggerMaker logger = new LoggerMaker(MismatchedSampleCleanupScheduler.class, LogDb.DASHBOARD);
    private static final String P = "[mismatch-cleanup] ";

    private static String env(String k, String def) {
        String v = System.getenv(k);
        return v == null || v.trim().isEmpty() ? def : v.trim();
    }

    public static boolean isEnabled() {
        return "true".equalsIgnoreCase(env("MISMATCH_CLEANUP_ENABLED", "false"));
    }

    /** When the cleanup is enabled, the container runs only the cleanup unless MISMATCH_CLEANUP_ONLY=false. */
    public static boolean isCleanupOnly() {
        return isEnabled() && !"false".equalsIgnoreCase(env("MISMATCH_CLEANUP_ONLY", "true"));
    }

    private static Set<Integer> ints(String csv) {
        Set<Integer> out = new LinkedHashSet<>();
        for (String s : csv.split(",")) {
            if (!s.trim().isEmpty()) out.add(Integer.parseInt(s.trim()));
        }
        return out;
    }

    public static void start() {
        if (!isEnabled()) return;
        final Set<Integer> accounts;
        final Set<Integer> collections;
        final int maxDeletes;
        final int intervalHours;
        try {
            accounts = ints(env("MISMATCH_CLEANUP_ACCOUNT_IDS", ""));
            collections = ints(env("MISMATCH_CLEANUP_COLLECTIONS", ""));
            maxDeletes = Integer.parseInt(env("MISMATCH_CLEANUP_MAX_DELETES_PER_COLLECTION", "0"));
            intervalHours = Integer.parseInt(env("MISMATCH_CLEANUP_INTERVAL_HOURS", "0"));
        } catch (NumberFormatException e) {
            logger.error(P + "NOT STARTED: bad number in MISMATCH_CLEANUP_* env: " + e.getMessage());
            return;
        }
        if (accounts.isEmpty()) {
            logger.error(P + "NOT STARTED: MISMATCH_CLEANUP_ACCOUNT_IDS is empty");
            return;
        }
        final boolean dryRun = !"false".equalsIgnoreCase(env("MISMATCH_CLEANUP_DRY_RUN", "true"));
        final String reportBase = env("MISMATCH_CLEANUP_REPORT_DIR", "/tmp/mismatch-cleanup");

        logger.warn(P + "CONFIG accounts=" + accounts + " mode=" + (dryRun ? "DRY_RUN" : "DELETE") +
                " collections=" + (collections.isEmpty() ? "ALL" : collections) +
                " maxDeletesPerCollection=" + maxDeletes + " intervalHours=" + intervalHours +
                " reportDir=" + reportBase + " cleanupOnly=" + isCleanupOnly());

        // Single thread: runs never overlap, even with an interval shorter than a run.
        ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mismatch-cleanup");
            t.setDaemon(true);
            return t;
        });
        Runnable task = () -> {
            for (int accountId : accounts) {
                String runId = (dryRun ? "dry-" : "del-") + accountId + "-" + Context.now();
                try {
                    Context.accountId.set(accountId);
                    MismatchedSampleCleanupJob.Report r = MismatchedSampleCleanupJob.run(
                            dryRun, reportBase + "/" + runId, new HashSet<>(collections), maxDeletes, runId);
                    logger.warn(P + "run=" + runId + " RUN_COMPLETE reports=" + reportBase + "/" + runId + "\n" + r);
                } catch (Throwable t) {
                    logger.errorAndAddToDb(new Exception(t), P + "run=" + runId + " RUN_FAILED: " + t, LogDb.DASHBOARD);
                } finally {
                    Context.accountId.remove();
                }
            }
            if (intervalHours <= 0) logger.warn(P + "ALL_RUNS_DONE (run-once mode). The container can be stopped.");
        };
        if (intervalHours > 0) {
            ex.scheduleWithFixedDelay(task, 0, intervalHours, TimeUnit.HOURS);
        } else {
            ex.schedule(task, 0, TimeUnit.SECONDS);
            ex.shutdown(); // the scheduled run still executes; the thread exits afterwards
        }
    }
}
