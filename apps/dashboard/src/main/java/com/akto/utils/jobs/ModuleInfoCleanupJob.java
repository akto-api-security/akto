package com.akto.utils.jobs;

import com.akto.dao.context.Context;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Adds the expiresAt TTL index to module_info for a single account. Runs once per process start on
 * its own thread, so building the index on a large collection cannot hold up startup. Idempotent:
 * once the index exists, a restart is a no-op.
 */
public class ModuleInfoCleanupJob {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ModuleInfoCleanupJob.class, LogDb.DASHBOARD);

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final int ACCOUNT_ID = 1736798101;

    public static void runOnce() {
        executor.submit(() -> {
            try {
                Context.accountId.set(ACCOUNT_ID);
                ModuleInfoDao.instance.createTtlIndexIfAbsent();
                loggerMaker.warnAndAddToDb("Module info expiresAt TTL index ensured for account " + ACCOUNT_ID);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error creating module info TTL index for account " + ACCOUNT_ID
                        + ": " + e.getMessage());
            }
        });
    }
}
