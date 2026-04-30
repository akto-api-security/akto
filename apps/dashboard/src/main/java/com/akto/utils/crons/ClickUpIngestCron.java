package com.akto.utils.crons;

import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.utils.clickup.ClickUpIngestService;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.akto.task.Cluster.callDibs;

/**
 * Periodically syncs ClickUp AI trace summaries into Akto via http-proxy ingest.
 */
public class ClickUpIngestCron {

    private static final LoggerMaker logger = new LoggerMaker(ClickUpIngestCron.class, LogDb.DASHBOARD);
    private static final String DIBS_KEY = "clickup-ingest-cron";
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public void schedule() {
        if (!ClickUpIngestService.syncEnabled()) {
            logger.infoAndAddToDb("ClickUp ingest cron not scheduled (CLICKUP_SYNC_ENABLED is false)", LogDb.DASHBOARD);
            return;
        }
        int intervalSec = readIntervalSeconds();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                int accountIdEnv = 1_000_000;
                String accountIdStr = System.getenv("AKTO_CLICKUP_ACCOUNT_ID");
                if (accountIdStr != null && !accountIdStr.trim().isEmpty()) {
                    try {
                        accountIdEnv = Integer.parseInt(accountIdStr.trim());
                    } catch (NumberFormatException ignored) {}
                }
                Context.accountId.set(accountIdEnv);
           
                if (!callDibs(DIBS_KEY, intervalSec, Math.min(60, intervalSec))) {
                    logger.debugAndAddToDb("ClickUp ingest cron: dibs not acquired, skip", LogDb.DASHBOARD);
                    return;
                }
                ClickUpIngestService.runOneSync();
            } catch (Exception e) {
                logger.errorAndAddToDb(e, "ClickUp ingest cron tick failed: " + e.getMessage());
            }
        }, 30, intervalSec, TimeUnit.SECONDS);
        logger.infoAndAddToDb("ClickUp ingest cron scheduled every " + intervalSec + "s", LogDb.DASHBOARD);
    }

    static int readIntervalSeconds() {
        try {
            String v = System.getenv("CLICKUP_CRON_INTERVAL_SECONDS");
            if (StringUtils.isBlank(v)) {
                return 300;
            }
            int sec = Integer.parseInt(v.trim());
            return Math.max(60, Math.min(3600, sec));
        } catch (NumberFormatException e) {
            return 300;
        }
    }
}
