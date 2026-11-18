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
 * Ensures the module_info indices (reboot poll, heartbeat delete, heartbeat upsert, expiresAt TTL) for
 * every active account. Runs once per process start on its own thread, so building the indices on a
 * large collection cannot hold up startup. Idempotent: once the indices exist, a restart is a no-op.
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
                    loggerMaker.warnAndAddToDb("Module info indices start for account " + accountId);
                    ModuleInfoDao.instance.createIndicesIfAbsent();
                    loggerMaker.warnAndAddToDb("Module info indices ensured for account " + accountId);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Error creating module info indices for account " + accountId
                            + ": " + e.getMessage());
                }
            }
        }, "module-info-indices"));
    }
}
