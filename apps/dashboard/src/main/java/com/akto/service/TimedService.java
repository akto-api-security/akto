package com.akto.service;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * Base of the services that run heavy computations: wrap each one in {@link #timed} and the log says
 * which step of which class took how long (and how many rows it produced), the way
 * InsightDataLoader's per step "took Nms, N rows" lines do, without every service re-writing them.
 *
 * Logs go through {@link #logger} at info level (with the account id prefix and stored like the other
 * dashboard logs), named after the extending class.
 */
public abstract class TimedService {

    protected final LoggerMaker logger = new LoggerMaker(getClass(), LogDb.DASHBOARD);

    protected <T> T timed(String label, Supplier<T> body) {
        return timed(label, body, null);
    }

    /** @param rowCount how many rows the result holds, for the log; null to leave it out */
    protected <T> T timed(String label, Supplier<T> body, ToIntFunction<T> rowCount) {
        long startMs = System.currentTimeMillis();
        T result;
        try {
            result = body.get();
        } catch (RuntimeException e) {
            logger.errorAndAddToDb(describe(label, startMs) + " and failed: " + e.getMessage());
            throw e;
        }
        logger.infoAndAddToDb(describe(label, startMs) + (rowCount == null ? "" : ", " + rowCount.applyAsInt(result) + " rows"));
        return result;
    }

    protected void timed(String label, Runnable body) {
        timed(label, () -> {
            body.run();
            return null;
        });
    }

    private String describe(String label, long startMs) {
        return getClass().getSimpleName() + ": " + label + " took " + (System.currentTimeMillis() - startMs)
                + "ms";
    }
}
