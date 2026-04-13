package com.akto.utils;

import java.util.concurrent.ConcurrentHashMap;

import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

public class StiCountAlert {

    private static final LoggerMaker loggerMaker = new LoggerMaker(StiCountAlert.class, LogDb.DB_ABS);

    private static final int CHECK_INTERVAL_SECONDS = 30 * 60; // 30 minutes
    private static final int STALE_THRESHOLD_SECONDS = 60 * 60; // 60 minutes

    // accountId -> last STI count
    private static final ConcurrentHashMap<Integer, Long> lastStiCountMap = new ConcurrentHashMap<>();
    // accountId -> epoch when count last changed
    private static final ConcurrentHashMap<Integer, Integer> lastCountChangeTs = new ConcurrentHashMap<>();
    // accountId -> epoch of last check
    private static final ConcurrentHashMap<Integer, Integer> lastCheckTs = new ConcurrentHashMap<>();
    public static void checkStiCount() {
        int accountId = Context.accountId.get();
        int now = Context.now();

        // Throttle: only check once every 30 minutes per account
        Integer lastCheck = lastCheckTs.get(accountId);
        if (lastCheck != null && (now - lastCheck) < CHECK_INTERVAL_SECONDS) {
            return;
        }
        lastCheckTs.put(accountId, now);

        try {
            long currentCount = SingleTypeInfoDao.instance.getEstimatedCount();

            Long previousCount = lastStiCountMap.get(accountId);
            if (previousCount == null) {
                // First time seeing this account, initialize and return
                lastStiCountMap.put(accountId, currentCount);
                lastCountChangeTs.put(accountId, now);
                return;
            }

            lastStiCountMap.put(accountId, currentCount);

            if (currentCount != previousCount) {
                lastCountChangeTs.put(accountId, now);
                return;
            }

            Integer lastChangeTs = lastCountChangeTs.get(accountId);
            if (lastChangeTs == null) {
                lastCountChangeTs.put(accountId, now);
                return;
            }

            int staleDuration = now - lastChangeTs;
            if (staleDuration < STALE_THRESHOLD_SECONDS) {
                return;
            }

            // Send alert
            String message = String.format(
                    "STI count stale for account %d: count=%d, unchanged for %d minutes",
                    accountId, currentCount, staleDuration / 60);
            loggerMaker.infoAndAddToDb(message);
            RedactAlert.sendToCyborgSlack(message);

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error checking STI count for account " + accountId);
        }
    }
}
