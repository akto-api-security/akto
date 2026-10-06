package com.akto.utils.crons;

import com.akto.dao.context.Context;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.service.insights.InsightService;
import com.akto.service.posture.ArgusPostureChangesService;
import com.mongodb.BasicDBObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// On-demand run of the tool classification and posture score crons for one account, in the background.
public class ArgusPostureRegenerator {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ArgusPostureRegenerator.class, LogDb.DASHBOARD);

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Map<Integer, Status> STATUS = new ConcurrentHashMap<>();
    private static final ToolClassificationCron TOOL_CLASSIFICATION_CRON = new ToolClassificationCron();
    private static final AgenticPostureScoreCron POSTURE_SCORE_CRON = new AgenticPostureScoreCron();

    private ArgusPostureRegenerator() {}

    private static final class Status {
        volatile boolean running;
        volatile int startedAt;
        volatile int finishedAt;
        volatile String error;
    }

    // False when a run is already in progress for this account.
    public static boolean trigger() {
        final int accountId = Context.accountId.get();
        final int userId = Context.userId.get();
        final CONTEXT_SOURCE contextSource = Context.contextSource.get();
        Status status = STATUS.computeIfAbsent(accountId, k -> new Status());
        synchronized (status) {
            if (status.running) return false;
            status.running = true;
            status.startedAt = Context.now();
            status.error = null;
        }
        EXECUTOR.submit(Context.withContext(accountId, userId, contextSource, () -> {
            run(status);
            return null;
        }));
        return true;
    }

    public static BasicDBObject status(int accountId) {
        Status status = STATUS.get(accountId);
        BasicDBObject out = new BasicDBObject("running", status != null && status.running);
        if (status != null) {
            out.append("startedAt", status.startedAt).append("finishedAt", status.finishedAt).append("error", status.error);
        }
        return out;
    }

    // Tool classification runs first because the posture score reads its tool capabilities.
    private static void run(Status status) {
        int accountId = Context.accountId.get();
        try {
            TOOL_CLASSIFICATION_CRON.forceRunForAccount(accountId);
            POSTURE_SCORE_CRON.forceRunForAccount(accountId);
            InsightService.invalidateAccount(accountId);
            ArgusPostureChangesService.invalidateAccount(accountId);
        } catch (Exception e) {
            status.error = "Regeneration failed";
            loggerMaker.errorAndAddToDb(e, "Argus posture regenerate failed for accountId=" + accountId + ": " + e.getMessage());
        } finally {
            status.finishedAt = Context.now();
            status.running = false;
        }
    }
}
