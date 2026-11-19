package com.akto.utils.jobs;

import com.akto.dao.ApiTokensDao;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Adds the key index to api_tokens (common DB, shared by all accounts). Runs once per process start on
 * its own thread, so the index build cannot hold up startup. Idempotent: once the index exists, a
 * restart is a no-op.
 */
public class ApiTokensIndexJob {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ApiTokensIndexJob.class, LogDb.DASHBOARD);

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static void runOnce() {
        executor.submit(() -> {
            try {
                ApiTokensDao.instance.createIndicesIfAbsent();
                loggerMaker.warnAndAddToDb("api_tokens key index ensured");
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error creating api_tokens key index: " + e.getMessage());
            }
        });
    }
}
