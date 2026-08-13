package com.akto.test_editor.execution;

import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.AccountSettings;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public class TestRateLimitGuard {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TestRateLimitGuard.class, LogDb.TESTING);
    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    private static final int SETTINGS_CACHE_TTL_SECONDS = 300;

    private static volatile int cachedGlobalRateLimit = 0;
    private static volatile int cachedGlobalRateLimitAgentic = 0;
    private static volatile int cachedAtSecond = 0;
    private static volatile int lastSeenDayKey = -1;

    private static final Set<String> stoppedSummaryIds = ConcurrentHashMap.newKeySet();

    private TestRateLimitGuard() {
    }


    public static boolean isDailyLimitExceededAndStop(String testingRunResultSummaryIdHex, String dashboardContext) {
        boolean agentic = "AGENTIC".equalsIgnoreCase(dashboardContext);
        int limit = agentic ? getGlobalRateLimitAgentic() : getGlobalRateLimit();
        if (limit <= 0) {
            return false;
        }

        resetIfNewDay();

        int usedToday;
        try {
            usedToday = dataActor.incrementAndGetTestRateLimitUsage(limit, dashboardContext);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error incrementing test rate limit usage: " + e.getMessage(), LogDb.TESTING);
            return false;
        }
        if (usedToday <= limit) {
            return false;
        }

        loggerMaker.infoAndAddToDb("Daily test rate limit exceeded (" + usedToday + "/" + limit + ") for account "
                + Context.accountId.get(), LogDb.TESTING);

        if (testingRunResultSummaryIdHex != null && stoppedSummaryIds.add(testingRunResultSummaryIdHex)) {
            try {
                dataActor.markTestRunResultSummaryFailed(testingRunResultSummaryIdHex);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error stopping run after daily test rate limit exceeded: " + e.getMessage(), LogDb.TESTING);
            }
        }
        return true;
    }

    private static void refreshRateLimitsIfStale() {
        int now = Context.now();
        if (now - cachedAtSecond > SETTINGS_CACHE_TTL_SECONDS) {
            try {
                AccountSettings settings = dataActor.fetchAccountSettings();
                cachedGlobalRateLimit = settings != null ? settings.getGlobalRateLimit() : 0;
                cachedGlobalRateLimitAgentic = settings != null ? settings.getGlobalRateLimitAgentic() : 0;
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error refreshing global rate limit for tests: " + e.getMessage(), LogDb.TESTING);
            } finally {
                cachedAtSecond = now;
            }
        }
    }

    private static int getGlobalRateLimit() {
        refreshRateLimitsIfStale();
        return cachedGlobalRateLimit;
    }

    private static int getGlobalRateLimitAgentic() {
        refreshRateLimitsIfStale();
        return cachedGlobalRateLimitAgentic;
    }

    private static void resetIfNewDay() {
        int today = Context.today();
        if (today != lastSeenDayKey) {
            stoppedSummaryIds.clear();
            lastSeenDayKey = today;
        }
    }
}
