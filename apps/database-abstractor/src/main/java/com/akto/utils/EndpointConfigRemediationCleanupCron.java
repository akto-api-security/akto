package com.akto.utils;

import com.akto.dao.EndpointConfigRemediationDao;
import com.akto.dao.EndpointConfigRemediationExecutionDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.EndpointConfigRemediation;
import com.akto.dto.EndpointConfigRemediationExecution;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.AccountTask;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateManyModel;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Runs every 2 minutes across all accounts. Three ordered steps, mirroring
 * EndpointRemoteCommandCleanupCron:
 *
 * A. Expire remediation jobs whose TTL has elapsed (must run first so agents never
 *    see expired jobs in their ACTIVE poll):
 *    ACTIVE jobs where createdAt + expirySeconds < now → EXPIRED
 *    Their PENDING executions → FAILED / expired
 *
 * B. RUNNING executions where the agent died. Config remediations don't have a
 *    per-job timeoutSec like shell commands, so a fixed grace window is used instead.
 *    status=RUNNING AND updatedAt < now - RUNNING_GRACE_SECONDS
 *    → FAILED / presumed_dead_agent
 *
 * C. Safety-net for any PENDING executions still under EXPIRED or CANCELLED
 *    jobs (catches records that slipped past A or cancel):
 *    → FAILED / expired  or  FAILED / cancelled
 */
public class EndpointConfigRemediationCleanupCron {

    private static final LoggerMaker loggerMaker =
            new LoggerMaker(EndpointConfigRemediationCleanupCron.class, LogDb.CYBORG);
    private static final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);

    // File read/parse/write should never take long; anything RUNNING past this is a dead agent.
    private static final int RUNNING_GRACE_SECONDS = 180;

    public void runCron() {
        scheduler.scheduleAtFixedRate(
                () -> AccountTask.instance.executeTask(
                        this::cleanupForAccount, "endpointConfigRemediationCleanup"),
                0, 2, TimeUnit.MINUTES);
    }

    private void cleanupForAccount(Account account) {
        try {
            Context.accountId.set(account.getId());
            long now = (long) Context.now();
            stepA_expireJobs(now);
            stepB_deadAgentExecutions(now);
            stepC_orphanedPendingExecutions(now);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(
                    "Error in endpointConfigRemediationCleanup for account "
                            + account.getId() + ": " + e.getMessage(), LogDb.CYBORG);
        }
    }

    // -------------------------------------------------------------------------
    // Step A: ACTIVE jobs past TTL → EXPIRED + their PENDING executions → FAILED/expired
    // -------------------------------------------------------------------------

    // Normalise any timestamp to epoch-seconds.
    // Threshold: values > 10^10 are milliseconds (epoch-seconds won't reach 10^10 until year 2286).
    private static long toEpochSeconds(long ts) {
        return ts > 10_000_000_000L ? ts / 1000L : ts;
    }

    private void stepA_expireJobs(long now) {
        List<EndpointConfigRemediation> toExpire = EndpointConfigRemediationDao.instance.findAll(
                Filters.eq(EndpointConfigRemediation.STATUS, EndpointConfigRemediation.Status.ACTIVE.name()));

        List<String> expiredJobIds = new ArrayList<>();
        List<org.bson.types.ObjectId> expiredObjectIds = new ArrayList<>();

        for (EndpointConfigRemediation job : toExpire) {
            long expiresAtSec = job.getExpiresAt() > 0
                    ? toEpochSeconds(job.getExpiresAt())
                    : toEpochSeconds(job.getCreatedAt()) + job.getExpirySeconds();
            if (expiresAtSec >= now) continue;
            if (job.getObjectId() == null) continue;
            expiredJobIds.add(job.getId()); // objectId.toHexString() — used for execution FK
            expiredObjectIds.add(job.getObjectId());
        }
        if (expiredObjectIds.isEmpty()) return;

        // Mark jobs EXPIRED by _id
        EndpointConfigRemediationDao.instance.getMCollection().updateMany(
                Filters.in("_id", expiredObjectIds),
                Updates.combine(
                        Updates.set(EndpointConfigRemediation.STATUS, EndpointConfigRemediation.Status.EXPIRED.name()),
                        Updates.set(EndpointConfigRemediation.UPDATED_AT, System.currentTimeMillis())));

        // Mark their PENDING executions FAILED/expired
        EndpointConfigRemediationExecutionDao.instance.getMCollection().updateMany(
                Filters.and(
                        Filters.in(EndpointConfigRemediationExecution.REMEDIATION_ID, expiredJobIds),
                        Filters.eq(EndpointConfigRemediationExecution.STATUS,
                                EndpointConfigRemediationExecution.Status.PENDING.name())),
                Updates.combine(
                        Updates.set(EndpointConfigRemediationExecution.STATUS,
                                EndpointConfigRemediationExecution.Status.FAILED.name()),
                        Updates.set(EndpointConfigRemediationExecution.ERROR_REASON, "expired"),
                        Updates.set(EndpointConfigRemediationExecution.UPDATED_AT, System.currentTimeMillis())));

        loggerMaker.infoAndAddToDb("stepA: expired " + expiredObjectIds.size() + " config remediation jobs", LogDb.CYBORG);
    }

    // -------------------------------------------------------------------------
    // Step B: RUNNING executions stuck past the grace window
    // -------------------------------------------------------------------------
    private void stepB_deadAgentExecutions(long now) {
        List<String> staleIds = new ArrayList<>();
        EndpointConfigRemediationExecutionDao.instance.findAll(
                Filters.eq(EndpointConfigRemediationExecution.STATUS,
                        EndpointConfigRemediationExecution.Status.RUNNING.name())
        ).forEach(exec -> {
            if (exec.getUpdatedAt() < now - RUNNING_GRACE_SECONDS) {
                staleIds.add(exec.getId());
            }
        });

        if (staleIds.isEmpty()) return;

        List<WriteModel<EndpointConfigRemediationExecution>> bulkOps = new ArrayList<>();
        for (String execId : staleIds) {
            bulkOps.add(new UpdateManyModel<>(
                    Filters.eq(EndpointConfigRemediationExecution.ID, execId),
                    Updates.combine(
                            Updates.set(EndpointConfigRemediationExecution.STATUS,
                                    EndpointConfigRemediationExecution.Status.FAILED.name()),
                            Updates.set(EndpointConfigRemediationExecution.ERROR_REASON, "presumed_dead_agent"),
                            Updates.set(EndpointConfigRemediationExecution.UPDATED_AT, now))));
        }
        EndpointConfigRemediationExecutionDao.instance.getMCollection().bulkWrite(bulkOps);
        loggerMaker.infoAndAddToDb(
                "Marked " + staleIds.size() + " config remediation executions as presumed_dead_agent", LogDb.CYBORG);
    }

    // -------------------------------------------------------------------------
    // Step C: Safety-net — PENDING executions whose parent is EXPIRED or CANCELLED
    // -------------------------------------------------------------------------
    private void stepC_orphanedPendingExecutions(long now) {
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(
                Filters.eq(EndpointConfigRemediationExecution.STATUS,
                        EndpointConfigRemediationExecution.Status.PENDING.name())));
        pipeline.add(Aggregates.lookup(
                EndpointConfigRemediationDao.COLLECTION_NAME,
                EndpointConfigRemediationExecution.REMEDIATION_ID,
                EndpointConfigRemediation.ID,
                "job"));
        pipeline.add(Aggregates.unwind("$job"));
        pipeline.add(Aggregates.match(
                Filters.in("job." + EndpointConfigRemediation.STATUS,
                        EndpointConfigRemediation.Status.EXPIRED.name(),
                        EndpointConfigRemediation.Status.CANCELLED.name())));

        List<WriteModel<EndpointConfigRemediationExecution>> bulkOps = new ArrayList<>();
        EndpointConfigRemediationExecutionDao.instance.getMCollection()
                .aggregate(pipeline, BasicDBObject.class)
                .forEach((BasicDBObject row) -> {
                    BasicDBObject job = (BasicDBObject) row.get("job");
                    if (job == null) return;
                    String jobStatus = job.getString(EndpointConfigRemediation.STATUS, "");
                    String reason = EndpointConfigRemediation.Status.EXPIRED.name().equals(jobStatus)
                            ? "expired" : "cancelled";
                    bulkOps.add(new UpdateManyModel<>(
                            Filters.eq(EndpointConfigRemediationExecution.ID,
                                    row.getString(EndpointConfigRemediationExecution.ID)),
                            Updates.combine(
                                    Updates.set(EndpointConfigRemediationExecution.STATUS,
                                            EndpointConfigRemediationExecution.Status.FAILED.name()),
                                    Updates.set(EndpointConfigRemediationExecution.ERROR_REASON, reason),
                                    Updates.set(EndpointConfigRemediationExecution.UPDATED_AT, now))));
                });

        if (bulkOps.isEmpty()) return;
        EndpointConfigRemediationExecutionDao.instance.getMCollection().bulkWrite(bulkOps);
        loggerMaker.infoAndAddToDb(
                "Safety-net: marked " + bulkOps.size() + " orphaned PENDING config remediation executions as FAILED", LogDb.CYBORG);
    }
}
