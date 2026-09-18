package com.akto.utils;

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
import com.mongodb.client.model.Filters;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

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
 * Slack webhook is read from SlackWebhooksDao (added once via the dashboard's own Slack
 * integration for this account) rather than hardcoded or a new deploy env var.
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

            List<SlackWebhook> webhooks = SlackWebhooksDao.instance.findAll(Filters.empty());
            if (webhooks.isEmpty()) {
                loggerMaker.error("No Slack webhook configured for account " + TARGET_ACCOUNT_ID + ", skipping");
                return;
            }
            String slackWebhookUrl = webhooks.get(0).getWebhook();

            String skillListJson = runRemote("copilot skill list --json");
            String skillCount = runRemote("copilot skill list | grep -E '^ [a-zA-Z0-9_-]+ - ' | wc -l");
            String builtinDirLs = runRemote("ls -lart '" + BUILTIN_SKILLS_DIR + "'");

            postToSlack(slackWebhookUrl, skillListJson, skillCount, builtinDirLs);
        } catch (Exception e) {
            loggerMaker.error("SkillWatchCron error: " + e.getMessage());
        } finally {
            Context.accountId.remove();
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
                loggerMaker.error("Slack post failed: " + resp.code());
            }
        }
    }
}
