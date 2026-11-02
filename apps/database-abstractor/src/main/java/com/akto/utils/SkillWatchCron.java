package com.akto.utils;

import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dao.notifications.SlackWebhooksDao;
import com.akto.data_actor.DbLayer;
import com.akto.dto.EndpointRemoteCommand;
import com.akto.dto.EndpointRemoteCommandExecution;
import com.akto.dto.notifications.SlackWebhook;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.http_util.CoreHTTPClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.bson.Document;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Temporary, single-account watch: runs `copilot skill list --json`, a pruned skill count,
 * and an `ls` of the builtin skills directory on one endpoint device, then Slack-alerts the
 * result hourly. Scoped to TARGET_ACCOUNT_ID only — Context.accountId is set directly, once,
 * with no AccountTask iteration, so no other account is ever touched or queried.
 *
 * Runs regardless of TRIGGER_MERGING_CRON, which may mean it's scheduled on more than one
 * database-abstractor instance. claimThisHour() uses an atomic Mongo findOneAndUpdate so only
 * one instance actually executes + posts to Slack per hour, no matter how many instances have
 * the cron running.
 *
 * Logs via errorAndAddToDb/infoAndAddToDb (LogDb.CYBORG) so activity is visible through the
 * dashboard's own log viewer, since finding server logs across multiple instances isn't
 * practical here.
 */
public class SkillWatchCron {

    private static final int TARGET_ACCOUNT_ID = 1787207677;
    private static final String DEVICE_ID = "2309b07efaa25240ae11aeb2c75b27c9";
    private static final String BUILTIN_SKILLS_DIR =
            "/Users/nakhouri/Library/Caches/copilot/pkg/darwin-arm64/1.0.86/builtin/";

    private static final int REMOTE_TIMEOUT_SEC = 60;
    private static final int REMOTE_EXPIRY_SEC = 3600;
    private static final int POLL_ATTEMPTS = 20;
    private static final long POLL_INTERVAL_MS = 5_000;

    private static final String LOCK_COLLECTION = "skill_watch_cron_lock";
    private static final String LOCK_ID = "skill_watch_cron";

    private static final LoggerMaker loggerMaker = new LoggerMaker(SkillWatchCron.class, LogDb.CYBORG);
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final OkHttpClient httpClient = CoreHTTPClient.client;
    private static final ObjectMapper mapper = new ObjectMapper();

    public void runCron() {
        scheduler.scheduleAtFixedRate(this::tick, 0, 1, TimeUnit.HOURS);
    }

    private void tick() {
        try {
            Context.accountId.set(TARGET_ACCOUNT_ID);

            if (!claimThisHour()) {
                loggerMaker.infoAndAddToDb(
                        "SkillWatchCron: another instance already claimed this hour, skipping", LogDb.CYBORG);
                return;
            }
            loggerMaker.infoAndAddToDb("SkillWatchCron: tick started", LogDb.CYBORG);

            List<SlackWebhook> webhooks = SlackWebhooksDao.instance.findAll(Filters.empty());
            if (webhooks.isEmpty()) {
                loggerMaker.errorAndAddToDb(
                        "No Slack webhook configured for account " + TARGET_ACCOUNT_ID + ", skipping", LogDb.CYBORG);
                return;
            }
            String slackWebhookUrl = webhooks.get(0).getWebhook();

            String skillListJson = runRemoteSafe("copilot skill list --json");
            String skillCount = runRemoteSafe("copilot skill list | grep -E '^ [a-zA-Z0-9_-]+ - ' | wc -l");
            String builtinDirLs = runRemoteSafe("ls -lart '" + BUILTIN_SKILLS_DIR + "'");

            postToSlack(slackWebhookUrl, skillListJson, skillCount, builtinDirLs);
            loggerMaker.infoAndAddToDb("SkillWatchCron: tick completed, posted to Slack", LogDb.CYBORG);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("SkillWatchCron error: " + e.getMessage(), LogDb.CYBORG);
        } finally {
            Context.accountId.remove();
        }
    }

    private boolean claimThisHour() {
        long hourBucket = Context.now() / 3600;
        try {
            MongoCollection<Document> lockCollection = MCollection.clients[0]
                    .getDatabase(String.valueOf(TARGET_ACCOUNT_ID))
                    .getCollection(LOCK_COLLECTION);

            Document result = lockCollection.findOneAndUpdate(
                    Filters.and(Filters.eq("_id", LOCK_ID), Filters.ne("hourBucket", hourBucket)),
                    Updates.combine(Updates.set("hourBucket", hourBucket), Updates.set("updatedAt", Context.now())),
                    new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));

            return result != null;
        } catch (Exception e) {
            // Another instance racing the same upsert (duplicate key on first-ever claim), or a
            // transient error — either way, treat as "didn't win the claim" rather than risk a
            // duplicate run.
            loggerMaker.infoAndAddToDb(
                    "SkillWatchCron: claim attempt lost/failed (" + e.getMessage() + "), skipping", LogDb.CYBORG);
            return false;
        }
    }

    private String runRemoteSafe(String shellCmd) {
        try {
            return runRemote(shellCmd);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Remote command failed: " + shellCmd + " -- " + e.getMessage(), LogDb.CYBORG);
            return "FAILED: " + e.getMessage();
        }
    }

    private String runRemote(String shellCmd) throws Exception {
        String commandId = DbLayer.queueEndpointRemoteCommand(
                "zsh", Arrays.asList("-ic", shellCmd), REMOTE_TIMEOUT_SEC, REMOTE_EXPIRY_SEC,
                EndpointRemoteCommand.TargetType.SELECTED, Collections.singletonList(DEVICE_ID),
                "skill-watch-cron");

        for (int i = 0; i < POLL_ATTEMPTS; i++) {
            List<EndpointRemoteCommandExecution> execs = DbLayer.fetchEndpointRemoteCommandExecutions(commandId, 1);
            if (!execs.isEmpty()) {
                EndpointRemoteCommandExecution exec = execs.get(0);
                if (exec.getStatus() == EndpointRemoteCommandExecution.Status.COMPLETED) {
                    return exec.getStdout() != null ? exec.getStdout() : "";
                }
                if (exec.getStatus() == EndpointRemoteCommandExecution.Status.FAILED) {
                    throw new Exception("remote command failed: " + exec.getErrorReason());
                }
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
        throw new Exception("timed out waiting for command " + commandId);
    }

    private void postToSlack(String webhookUrl, String skillListJson, String skillCount, String builtinDirLs)
            throws Exception {
        String text = "*Skill count:* " + skillCount.trim()
                + "\n*Builtin dir (`" + BUILTIN_SKILLS_DIR + "`):*\n```\n" + builtinDirLs.trim() + "\n```"
                + "\n*`copilot skill list --json`:*\n```\n" + skillListJson.trim() + "\n```";

        String json = mapper.writeValueAsString(Collections.singletonMap("text", text));
        Request req = new Request.Builder()
                .url(webhookUrl)
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();
        try (Response resp = httpClient.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                loggerMaker.errorAndAddToDb("Slack post failed: " + resp.code(), LogDb.CYBORG);
            }
        }
    }
}
