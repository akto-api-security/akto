package com.akto.testing.kafka_utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

final class TestingExecutorLifecycle {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TestingExecutorLifecycle.class, LogDb.TESTING);

    private TestingExecutorLifecycle() {}

    static void shutdownQuietly(ExecutorService current, int waitSeconds, boolean force) {
        if (current == null || current.isTerminated()) {
            return;
        }
        if (force) {
            current.shutdownNow();
        } else {
            current.shutdown();
        }
        try {
            if (!current.awaitTermination(waitSeconds, TimeUnit.SECONDS)) {
                loggerMaker.warnAndAddToDb("executor did not terminate in " + waitSeconds + "s, calling shutdownNow");
                current.shutdownNow();
                if (!current.awaitTermination(5, TimeUnit.SECONDS)) {
                    loggerMaker.warnAndAddToDb("executor still alive after shutdownNow");
                }
            }
        } catch (InterruptedException e) {
            current.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
