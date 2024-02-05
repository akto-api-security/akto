package com.akto.log;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import com.akto.dao.context.Context;

public class CacheLoggerMaker extends LoggerMaker {

    private static final int CACHE_LIMIT = 50;
    private static final int LOG_FLUSH_INTERVAL = 60;  // 1 minute
    private static int lastLogFlushTime = 0;
    private static HashMap<String, Integer> errorLogCache = new HashMap<>();

    static {
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    clearErrorCache();
                } catch (Exception e) {
                    internalLogger.error("ERROR: In clearing log cache: " + e.toString());
                }
            }
        }, 2, 5, TimeUnit.MINUTES);
    }

    public CacheLoggerMaker(Class<?> c) {
        super(c);
    }

    public static void clearErrorCache() {

        for (Map.Entry<String, Integer> err : errorLogCache.entrySet()) {
            String errorMessage = err.getKey() + " : logged " + err.getValue() + " times";
            sendToSlack(errorMessage);
        }

        errorLogCache.clear();
    }

    @Override
    public void errorAndAddToDb(String err, LogDb db) {
        basicError(err, db);

        if (db.equals(LogDb.BILLING) || db.equals(LogDb.DASHBOARD)) {
            try {
                errorLogCache.put(err, errorLogCache.getOrDefault(err, 0) + 1);
            } catch (Exception e) {
                internalLogger.error("ERROR: In adding to log cache: " + e.toString());
            }
        }

        if ((errorLogCache.size() > CACHE_LIMIT) ||
                ((lastLogFlushTime + LOG_FLUSH_INTERVAL) < Context.now())) {
            try {
                lastLogFlushTime = Context.now();
                clearErrorCache();
            } catch (Exception e) {
                internalLogger.error("ERROR: In clearing log cache: " + e.toString());
            }
        }
    }

}
