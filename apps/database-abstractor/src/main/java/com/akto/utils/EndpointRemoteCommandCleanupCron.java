package com.akto.utils;

import com.akto.dao.EndpointRemoteCommandDao;
import com.akto.dao.EndpointRemoteCommandExecutionDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.EndpointRemoteCommand;
import com.akto.dto.EndpointRemoteCommandExecution;
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
 * Runs every 2 minutes across all accounts. Three ordered steps:
 *
 * A. Expire commands whose TTL has elapsed (must run first so agents never
 *    see expired commands in their ACTIVE poll):
 *    ACTIVE commands where createdAt + expirySeconds < now → EXPIRED
 *    Their PENDING executions → FAILED / expired
 *
 * B. RUNNING executions where the agent died:
 *    status=RUNNING AND updatedAt < now - (command.timeoutSec + 120)
 *    → FAILED / presumed_dead_agent
 *
 * C. Safety-net for any PENDING executions still under EXPIRED or CANCELLED
 *    commands (catches records that slipped past A or cancel):
 *    → FAILED / expired  or  FAILED / cancelled
 */
public class EndpointRemoteCommandCleanupCron {

    private static final LoggerMaker loggerMaker =
            new LoggerMaker(EndpointRemoteCommandCleanupCron.class, LogDb.CYBORG);
    private static final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);

    public void runCron() {
        scheduler.scheduleAtFixedRate(
                () -> AccountTask.instance.executeTask(
                        this::cleanupForAccount, "endpointRemoteCommandCleanup"),
                0, 2, TimeUnit.MINUTES);
    }

    private void cleanupForAccount(Account account) {
        try {
            Context.accountId.set(account.getId());
            long now = (long) Context.now();
            stepA_expireCommands(now);
            stepB_deadAgentExecutions(now);
            stepC_orphanedPendingExecutions(now);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(
                    "Error in endpointRemoteCommandCleanup for account "
                            + account.getId() + ": " + e.getMessage(), LogDb.CYBORG);
        }
    }

    // -------------------------------------------------------------------------
    // Step A: ACTIVE commands past TTL → EXPIRED + their PENDING executions → FAILED/expired
    // -------------------------------------------------------------------------

    // Normalise any timestamp to epoch-seconds.
    // Threshold: values > 10^10 are milliseconds (epoch-seconds won't reach 10^10 until year 2286).
    private static long toEpochSeconds(long ts) {
        return ts > 10_000_000_000L ? ts / 1000L : ts;
    }

    private void stepA_expireCommands(long now) {
        List<EndpointRemoteCommand> toExpire = EndpointRemoteCommandDao.instance.findAll(
                Filters.eq(EndpointRemoteCommand.STATUS, EndpointRemoteCommand.Status.ACTIVE.name()));

        loggerMaker.infoAndAddToDb(
                "stepA: now=" + now + " activeCommandCount=" + toExpire.size(), LogDb.CYBORG);

        // commandId (objectId hex) → ObjectId, collected for the _id filter
        List<String> expiredCommandIds = new ArrayList<>();
        List<org.bson.types.ObjectId> expiredObjectIds = new ArrayList<>();

        for (EndpointRemoteCommand cmd : toExpire) {
            // Prefer the pre-computed expiresAt field; fall back to createdAt + expirySeconds.
            long expiresAtSec = cmd.getExpiresAt() > 0
                    ? toEpochSeconds(cmd.getExpiresAt())
                    : toEpochSeconds(cmd.getCreatedAt()) + cmd.getExpirySeconds();
            boolean shouldExpire = expiresAtSec < now;
            loggerMaker.infoAndAddToDb(
                    "stepA: id=" + cmd.getId()
                            + " command=" + cmd.getCommand()
                            + " expiresAt=" + cmd.getExpiresAt()
                            + " expiresAtSec=" + expiresAtSec
                            + " now=" + now
                            + " shouldExpire=" + shouldExpire,
                    LogDb.CYBORG);
            if (!shouldExpire) continue;
            if (cmd.getObjectId() == null) {
                loggerMaker.infoAndAddToDb(
                        "stepA: skipping command with null _id, command=" + cmd.getCommand(), LogDb.CYBORG);
                continue;
            }
            expiredCommandIds.add(cmd.getId()); // objectId.toHexString() — used for execution FK
            expiredObjectIds.add(cmd.getObjectId());
            loggerMaker.infoAndAddToDb(
                    "stepA: marking EXPIRED id=" + cmd.getId()
                            + " expiredAgo=" + (now - expiresAtSec) + "s",
                    LogDb.CYBORG);
        }
        if (expiredObjectIds.isEmpty()) return;

        // Mark commands EXPIRED by _id
        EndpointRemoteCommandDao.instance.getMCollection().updateMany(
                Filters.in("_id", expiredObjectIds),
                Updates.combine(
                        Updates.set(EndpointRemoteCommand.STATUS, EndpointRemoteCommand.Status.EXPIRED.name()),
                        Updates.set(EndpointRemoteCommand.UPDATED_AT, System.currentTimeMillis())));

        // Mark their PENDING executions FAILED/expired
        EndpointRemoteCommandExecutionDao.instance.getMCollection().updateMany(
                Filters.and(
                        Filters.in(EndpointRemoteCommandExecution.COMMAND_ID, expiredCommandIds),
                        Filters.eq(EndpointRemoteCommandExecution.STATUS,
                                EndpointRemoteCommandExecution.Status.PENDING.name())),
                Updates.combine(
                        Updates.set(EndpointRemoteCommandExecution.STATUS,
                                EndpointRemoteCommandExecution.Status.FAILED.name()),
                        Updates.set(EndpointRemoteCommandExecution.ERROR_REASON, "expired"),
                        Updates.set(EndpointRemoteCommandExecution.UPDATED_AT, System.currentTimeMillis())));

        loggerMaker.infoAndAddToDb("stepA: expired " + expiredObjectIds.size() + " commands", LogDb.CYBORG);
    }

    // -------------------------------------------------------------------------
    // Step B: RUNNING executions where updatedAt < now - (command.timeoutSec + 120)
    // -------------------------------------------------------------------------
    private void stepB_deadAgentExecutions(long now) {
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(
                Filters.eq(EndpointRemoteCommandExecution.STATUS,
                        EndpointRemoteCommandExecution.Status.RUNNING.name())));
        pipeline.add(Aggregates.lookup(
                EndpointRemoteCommandDao.COLLECTION_NAME,
                EndpointRemoteCommandExecution.COMMAND_ID,
                EndpointRemoteCommand.ID,
                "cmd"));
        pipeline.add(Aggregates.unwind("$cmd"));

        List<String> staleIds = new ArrayList<>();
        EndpointRemoteCommandExecutionDao.instance.getMCollection()
                .aggregate(pipeline, BasicDBObject.class)
                .forEach((BasicDBObject row) -> {
                    BasicDBObject cmd = (BasicDBObject) row.get("cmd");
                    if (cmd == null) return;
                    int timeoutSec = cmd.getInt(EndpointRemoteCommand.TIMEOUT_SEC, 60);
                    long updatedAt = row.getLong(EndpointRemoteCommandExecution.UPDATED_AT, 0L);
                    if (updatedAt < now - (timeoutSec + 120)) {
                        staleIds.add(row.getString(EndpointRemoteCommandExecution.ID));
                    }
                });

        if (staleIds.isEmpty()) return;

        List<WriteModel<EndpointRemoteCommandExecution>> bulkOps = new ArrayList<>();
        for (String execId : staleIds) {
            bulkOps.add(new UpdateManyModel<>(
                    Filters.eq(EndpointRemoteCommandExecution.ID, execId),
                    Updates.combine(
                            Updates.set(EndpointRemoteCommandExecution.STATUS,
                                    EndpointRemoteCommandExecution.Status.FAILED.name()),
                            Updates.set(EndpointRemoteCommandExecution.ERROR_REASON, "presumed_dead_agent"),
                            Updates.set(EndpointRemoteCommandExecution.UPDATED_AT, now))));
        }
        EndpointRemoteCommandExecutionDao.instance.getMCollection().bulkWrite(bulkOps);
        loggerMaker.infoAndAddToDb(
                "Marked " + staleIds.size() + " executions as presumed_dead_agent", LogDb.CYBORG);
    }

    // -------------------------------------------------------------------------
    // Step C: Safety-net — PENDING executions whose parent is EXPIRED or CANCELLED
    // -------------------------------------------------------------------------
    private void stepC_orphanedPendingExecutions(long now) {
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(
                Filters.eq(EndpointRemoteCommandExecution.STATUS,
                        EndpointRemoteCommandExecution.Status.PENDING.name())));
        pipeline.add(Aggregates.lookup(
                EndpointRemoteCommandDao.COLLECTION_NAME,
                EndpointRemoteCommandExecution.COMMAND_ID,
                EndpointRemoteCommand.ID,
                "cmd"));
        pipeline.add(Aggregates.unwind("$cmd"));
        pipeline.add(Aggregates.match(
                Filters.in("cmd." + EndpointRemoteCommand.STATUS,
                        EndpointRemoteCommand.Status.EXPIRED.name(),
                        EndpointRemoteCommand.Status.CANCELLED.name())));

        List<WriteModel<EndpointRemoteCommandExecution>> bulkOps = new ArrayList<>();
        EndpointRemoteCommandExecutionDao.instance.getMCollection()
                .aggregate(pipeline, BasicDBObject.class)
                .forEach((BasicDBObject row) -> {
                    BasicDBObject cmd = (BasicDBObject) row.get("cmd");
                    if (cmd == null) return;
                    String cmdStatus = cmd.getString(EndpointRemoteCommand.STATUS, "");
                    String reason = EndpointRemoteCommand.Status.EXPIRED.name().equals(cmdStatus)
                            ? "expired" : "cancelled";
                    bulkOps.add(new UpdateManyModel<>(
                            Filters.eq(EndpointRemoteCommandExecution.ID,
                                    row.getString(EndpointRemoteCommandExecution.ID)),
                            Updates.combine(
                                    Updates.set(EndpointRemoteCommandExecution.STATUS,
                                            EndpointRemoteCommandExecution.Status.FAILED.name()),
                                    Updates.set(EndpointRemoteCommandExecution.ERROR_REASON, reason),
                                    Updates.set(EndpointRemoteCommandExecution.UPDATED_AT, now))));
                });

        if (bulkOps.isEmpty()) return;
        EndpointRemoteCommandExecutionDao.instance.getMCollection().bulkWrite(bulkOps);
        loggerMaker.infoAndAddToDb(
                "Safety-net: marked " + bulkOps.size() + " orphaned PENDING executions as FAILED", LogDb.CYBORG);
    }
}
