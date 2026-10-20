package com.akto.utils.jobs;

import com.akto.dao.context.Context;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dto.Account;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.AccountTask;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Adds the expiresAt TTL index to module_info for every active account. Runs once per process start
 * on its own thread, so building the index on a large collection cannot hold up startup. Idempotent:
 * once the index exists, a restart is a no-op.
 */
public class ModuleInfoCleanupJob {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ModuleInfoCleanupJob.class, LogDb.DASHBOARD);

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static void runOnce() {
        executor.submit(() -> AccountTask.instance.executeTask(new Consumer<Account>() {
            @Override
            public void accept(Account account) {
                int accountId = Context.accountId.get();
                try {
                    loggerMaker.warnAndAddToDb("Module info expiresAt TTL index start for account " + accountId);
                    ModuleInfoDao.instance.createTtlIndexIfAbsent();
                    loggerMaker.warnAndAddToDb("Module info expiresAt TTL index ensured for account " + accountId);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Error creating module info TTL index for account " + accountId
                            + ": " + e.getMessage());
                }
            }
        }, "module-info-ttl-index"));
    }
}
