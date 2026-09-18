package com.akto.account_job_executor.executor.executors;

import com.akto.account_job_executor.executor.AccountJobExecutor;
import com.akto.account_job_executor.client.CyborgApiClient;
import com.akto.dto.jobs.AccountJob;
import com.akto.jobs.exception.RetryableJobException;
import com.akto.log.LoggerMaker;
import com.akto.util.http_util.CoreHTTPClient;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Watches the `copilot skill list` output on one endpoint device (via the Endpoint Remote
 * Command feature) and Slack-alerts a personal channel when a skill shows up that isn't in
 * the known baseline. Talks to the dashboard's public API (X-API-KEY) rather than
 * database-abstractor directly — see .claude/plans/endpoint-remote-command-skill-watch-slack-alert.md
 * for why (database-abstractor's internal remote-command action is scoped by this service's
 * single fixed JWT, not per-job accountId).
 */
public class EndpointRemoteCommandWatchExecutor extends AccountJobExecutor {

    public static final EndpointRemoteCommandWatchExecutor INSTANCE = new EndpointRemoteCommandWatchExecutor();

    private static final LoggerMaker logger = new LoggerMaker(EndpointRemoteCommandWatchExecutor.class);
    private static final ObjectMapper mapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final OkHttpClient httpClient = CoreHTTPClient.client.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build();

    private static final long POLL_INTERVAL_MS = 5_000;
    private static final long HEARTBEAT_INTERVAL_MS = 30_000;

    private static final String SKILL_LIST_COMMAND = "copilot skill list";
    private static final String SKILL_COUNT_COMMAND =
        "copilot skill list | grep -E '^ [a-zA-Z0-9_-]+ - ' | wc -l";

    // Matches the same "  name - description" rows the count command greps for.
    private static final Pattern SKILL_LINE = Pattern.compile("^ ([a-zA-Z0-9_-]+) - ", Pattern.MULTILINE);

    private EndpointRemoteCommandWatchExecutor() {}

    @Override
    @SuppressWarnings("unchecked")
    protected void runJob(AccountJob job) throws Exception {
        Map<String, Object> config = job.getConfig();
        if (config == null || config.isEmpty()) {
            throw new IllegalArgumentException("Job config is null or empty for job: " + job.getId());
        }

        String baseUrl = getOrDefault(config, "aktoDashboardBaseUrl", "https://app.akto.io");
        String apiKey = requireString(config, "aktoApiKey");
        String deviceId = requireString(config, "deviceId");
        String slackWebhookUrl = requireString(config, "slackWebhookUrl");
        int timeoutSec = getOrDefaultInt(config, "timeoutSec", 60);
        int expirySeconds = getOrDefaultInt(config, "expirySeconds", 3600);

        List<String> knownSkills = new ArrayList<>();
        Object knownSkillsObj = config.get("knownSkills");
        if (knownSkillsObj instanceof List) {
            for (Object o : (List<Object>) knownSkillsObj) {
                if (o != null) knownSkills.add(o.toString());
            }
        }

        String listOutput = runRemoteZshCommand(job, baseUrl, apiKey, deviceId, SKILL_LIST_COMMAND, timeoutSec, expirySeconds);
        String countOutput = runRemoteZshCommand(job, baseUrl, apiKey, deviceId, SKILL_COUNT_COMMAND, timeoutSec, expirySeconds);

        List<String> currentSkills = parseSkillNames(listOutput);
        List<String> newSkills = new ArrayList<>();
        for (String s : currentSkills) {
            if (!knownSkills.contains(s)) newSkills.add(s);
        }

        postToSlack(slackWebhookUrl, buildSlackMessage(deviceId, listOutput, countOutput, newSkills, currentSkills));

        // Merge, never shrink, the baseline so a flaky/partial listing on one run can't
        // make an already-seen skill look "new" again on the next run.
        Set<String> mergedBaseline = new LinkedHashSet<>(knownSkills);
        mergedBaseline.addAll(currentSkills);

        Map<String, Object> updatedConfig = new HashMap<>(config);
        updatedConfig.put("knownSkills", new ArrayList<>(mergedBaseline));
        Map<String, Object> updates = new HashMap<>();
        updates.put(AccountJob.CONFIG, updatedConfig);
        CyborgApiClient.updateJob(job.getId(), updates);
    }

    private String runRemoteZshCommand(AccountJob job, String baseUrl, String apiKey, String deviceId,
                                        String shellCommand, int timeoutSec, int expirySeconds) throws Exception {
        String commandId = queueEndpointRemoteCommand(baseUrl, apiKey, deviceId, shellCommand, timeoutSec, expirySeconds);
        return pollForCompletion(job, baseUrl, apiKey, commandId, timeoutSec);
    }

    private String queueEndpointRemoteCommand(String baseUrl, String apiKey, String deviceId, String shellCommand,
                                               int timeoutSec, int expirySeconds) throws IOException {
        Map<String, Object> body = new HashMap<>();
        // zsh -ic "<shellCommand>" — interactive mode so ~/.zshrc runs and `copilot` is on PATH.
        body.put("command", "zsh");
        body.put("args", Arrays.asList("-ic", shellCommand));
        body.put("targetType", "SELECTED");
        body.put("targetDeviceIds", Collections.singletonList(deviceId));
        body.put("timeoutSec", timeoutSec);
        body.put("expirySeconds", expirySeconds);

        String responseBody = postJson(baseUrl + "/api/queueEndpointRemoteCommand", apiKey, body);
        Map<String, Object> parsed = mapper.readValue(responseBody, Map.class);
        Object commandId = parsed.get("commandId");
        if (commandId == null) {
            throw new RetryableJobException("queueEndpointRemoteCommand returned no commandId: " + responseBody);
        }
        return commandId.toString();
    }

    @SuppressWarnings("unchecked")
    private String pollForCompletion(AccountJob job, String baseUrl, String apiKey, String commandId, int timeoutSec)
            throws Exception {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(Math.min(timeoutSec, 120)) + 30_000;
        long lastHeartbeat = System.currentTimeMillis();

        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> body = new HashMap<>();
            body.put("commandId", commandId);
            String responseBody = postJson(baseUrl + "/api/fetchEndpointRemoteCommandExecutions", apiKey, body);
            Map<String, Object> parsed = mapper.readValue(responseBody, Map.class);
            List<Map<String, Object>> executions = (List<Map<String, Object>>) parsed.get("executions");

            if (executions != null && !executions.isEmpty()) {
                Map<String, Object> exec = executions.get(0);
                String status = String.valueOf(exec.get("status"));
                if ("COMPLETED".equals(status)) {
                    Object stdout = exec.get("stdout");
                    return stdout != null ? stdout.toString() : "";
                }
                if ("FAILED".equals(status)) {
                    Object errorReason = exec.get("errorReason");
                    Object stderr = exec.get("stderr");
                    throw new Exception("Remote command " + commandId + " failed: "
                        + (errorReason != null ? errorReason : stderr));
                }
            }

            if (System.currentTimeMillis() - lastHeartbeat > HEARTBEAT_INTERVAL_MS) {
                updateJobHeartbeat(job);
                lastHeartbeat = System.currentTimeMillis();
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
        throw new RetryableJobException("Timed out waiting for remote command " + commandId + " to complete");
    }

    private String postJson(String url, String apiKey, Map<String, Object> body) throws IOException {
        String jsonBody = mapper.writeValueAsString(body);
        RequestBody requestBody = RequestBody.create(jsonBody, MediaType.parse("application/json"));
        Request request = new Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("X-API-KEY", apiKey)
            .addHeader("Content-Type", "application/json")
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("Dashboard API request to " + url + " failed. Status: "
                    + response.code() + ", Body: " + responseBody);
            }
            return responseBody;
        }
    }

    private List<String> parseSkillNames(String listOutput) {
        List<String> names = new ArrayList<>();
        if (listOutput == null) return names;
        Matcher m = SKILL_LINE.matcher(listOutput);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    private String buildSlackMessage(String deviceId, String listOutput, String countOutput,
                                      List<String> newSkills, List<String> currentSkills) {
        StringBuilder sb = new StringBuilder();
        if (!newSkills.isEmpty()) {
            sb.append(":rotating_light: *New copilot skill(s) detected* on device `").append(deviceId).append("`: ")
              .append(String.join(", ", newSkills)).append("\n\n");
        } else {
            sb.append(":white_check_mark: No new copilot skills on device `").append(deviceId).append("` (")
              .append(currentSkills.size()).append(" known)\n\n");
        }
        sb.append("*Skill count:* ").append(countOutput == null ? "" : countOutput.trim()).append("\n");
        sb.append("*`copilot skill list`:*\n```\n").append(listOutput == null ? "" : listOutput.trim()).append("\n```");
        return sb.toString();
    }

    private void postToSlack(String webhookUrl, String text) throws IOException {
        Map<String, Object> payload = Collections.singletonMap("text", text);
        String jsonBody = mapper.writeValueAsString(payload);
        RequestBody requestBody = RequestBody.create(jsonBody, MediaType.parse("application/json"));
        Request request = new Request.Builder()
            .url(webhookUrl)
            .post(requestBody)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                logger.error("Slack webhook post failed. Status: {}, Body: {}", response.code(), body);
            }
        }
    }

    private String requireString(Map<String, Object> config, String key) {
        Object v = config.get(key);
        if (v == null || v.toString().isEmpty()) {
            throw new IllegalArgumentException("Missing required config field: " + key);
        }
        return v.toString();
    }

    private String getOrDefault(Map<String, Object> config, String key, String def) {
        Object v = config.get(key);
        return v == null ? def : v.toString();
    }

    private int getOrDefaultInt(Map<String, Object> config, String key, int def) {
        Object v = config.get(key);
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return Integer.parseInt(v.toString());
        } catch (Exception e) {
            return def;
        }
    }
}
