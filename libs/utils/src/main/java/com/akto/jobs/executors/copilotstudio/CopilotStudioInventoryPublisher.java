package com.akto.jobs.executors.copilotstudio;

import com.akto.jobs.executors.AIAgentConnectorConstants;
import com.akto.util.Constants;
import com.akto.util.JSONUtils;
import com.akto.log.LoggerMaker;
import com.akto.util.http_util.CoreHTTPClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Turns inventory rows into traffic samples; shape must match the copilot-shield binary's or agents split across collections. */
public class CopilotStudioInventoryPublisher {

    private static final LoggerMaker logger = new LoggerMaker(CopilotStudioInventoryPublisher.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final OkHttpClient client = CoreHTTPClient.client.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build();

    private static final MediaType JSON = MediaType.parse("application/json");
    /** Informational only — carried for traceability back to the inventory row, nothing reads it. */
    private static final String BOT_ID_TAG = "bot-id";
    private static final String TAG_VALUE_TRUE = "true";
    private static final String HEADER_HOST = "host";
    private static final String AGENT_PATH_PREFIX = "/copilot/bot/";

    /** Kept at "{}" on purpose — over 3 chars triggers an unwanted AgentQueryRecord write downstream. */
    private static final String EMPTY_REQUEST_PAYLOAD = "{}";

    /** Same as the 7-arg overload below, without an environment-name lookup (bot-environment-name tag left unset). */
    public List<Map<String, Object>> buildSamples(List<JsonNode> agents, String environmentId,
                                                   Map<String, String> botIdToDataverseName, int accountId,
                                                   Map<String, String> ownerIdToUserId,
                                                   Map<String, Set<String>> botNameToKnownUserIds) {
        return buildSamples(agents, environmentId, botIdToDataverseName, accountId, ownerIdToUserId,
            botNameToKnownUserIds, null);
    }

    /** Builds one sample per (agent, known user) pair — reaches every user who talks to a bot, not just its owner. Agents with no usable name or no resolved owner/chatter are skipped. */
    public List<Map<String, Object>> buildSamples(List<JsonNode> agents, String environmentId,
                                                   Map<String, String> botIdToDataverseName, int accountId,
                                                   Map<String, String> ownerIdToUserId,
                                                   Map<String, Set<String>> botNameToKnownUserIds,
                                                   Map<String, String> environmentIdToName) {
        List<Map<String, Object>> samples = new ArrayList<>();

        String environmentName = (environmentIdToName != null && environmentId != null)
            ? environmentIdToName.get(environmentId.toLowerCase(Locale.ROOT)) : null;

        for (JsonNode agent : agents) {
            JsonNode properties = agent.path("properties");
            String agentId = agent.path("name").asText("");
            String displayName = properties.path("displayName").asText("");

            // Prefer the Dataverse name so this lands in bot discovery's existing collection; Lite agents fall back to displayName.
            String preferredName = botIdToDataverseName != null ? botIdToDataverseName.get(agentId) : null;
            String botName = sanitizeBotName(preferredName != null ? preferredName : displayName);
            if (botName.isEmpty()) {
                logger.warn("CopilotStudioInventory: skipping agent with no usable name, agentId={}", agentId);
                continue;
            }

            // Owner + every known chatter for this bot (from copilot-shield's output file), deduped.
            String ownerId = properties.path("ownerId").asText("");
            String resolvedOwnerId = ownerIdToUserId != null ? ownerIdToUserId.get(ownerId) : null;
            Set<String> userIds = new LinkedHashSet<>();
            if (resolvedOwnerId != null && !resolvedOwnerId.isEmpty()) {
                userIds.add(resolvedOwnerId);
            }
            Set<String> knownUsers = botNameToKnownUserIds != null ? botNameToKnownUserIds.get(botKey(environmentId, botName)) : null;
            if (knownUsers != null) {
                userIds.addAll(knownUsers);
            }

            if (userIds.isEmpty()) {
                logger.warn("CopilotStudioInventory: skipping agent, no resolved owner or known chatter, agentId={}", agentId);
                continue;
            }

            for (String userId : userIds) {
                // Only the owner's own sample gets the owner tag, not every chatter's.
                boolean isOwnerSample = userId.equals(resolvedOwnerId);
                samples.add(buildSample(agent, agentId, userId + AIAgentConnectorConstants.AI_AGENT_HOST_INFIX + botName, environmentId, accountId, botName, isOwnerSample, environmentName));
            }
        }

        return samples;
    }

    private Map<String, Object> buildSample(JsonNode agent, String agentId, String host, String environmentId, int accountId, String botName, boolean isOwnerSample, String environmentName) {
        Map<String, String> tags = new HashMap<>();
        tags.put(Constants.AKTO_ENDPOINT_SOURCE_TAG, Constants.AKTO_ENDPOINT_SOURCE_VALUE);
        tags.put(Constants.AKTO_GEN_AI_TAG, AIAgentConnectorConstants.DATA_TAG_GEN_AI);
        tags.put(Constants.AKTO_AI_AGENT_TAG, botName);
        tags.put(BOT_ID_TAG, agentId);
        tags.put(Constants.AKTO_COPILOT_BOT_ENVIRONMENT_TAG, environmentId != null ? environmentId : "");
        tags.put(Constants.AKTO_COPILOT_INVENTORY_TAG, TAG_VALUE_TRUE);
        tags.put(Constants.AKTO_SAAS_AGENT_TAG, Constants.COPILOT_STUDIO_AI_AGENT_NAME);
        if (isOwnerSample) {
            tags.put(Constants.AKTO_AI_AGENT_OWNER_TAG, TAG_VALUE_TRUE);
        }
        if (environmentName != null && !environmentName.isEmpty()) {
            tags.put(Constants.AKTO_COPILOT_BOT_ENVIRONMENT_NAME_TAG, environmentName);
        }

        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put(HEADER_HOST, host);
        requestHeaders.put("content-type", AIAgentConnectorConstants.CONTENT_TYPE_JSON);

        Map<String, Object> sample = new HashMap<>();
        sample.put("path", AGENT_PATH_PREFIX + agentId);
        sample.put("method", AIAgentConnectorConstants.HTTP_METHOD_GET);
        // Headers/tags go on the wire as JSON strings — OriginalHttpRequest.buildHeadersMap casts the field to String.
        sample.put("requestHeaders", JSONUtils.getString(requestHeaders));
        sample.put("responseHeaders", "{}");
        sample.put("requestPayload", EMPTY_REQUEST_PAYLOAD);
        sample.put("responsePayload", agent.toString());
        sample.put("ip", AIAgentConnectorConstants.IP_ADDRESS_DEFAULT);
        sample.put("time", String.valueOf(System.currentTimeMillis() / 1000L));
        sample.put("statusCode", String.valueOf(AIAgentConnectorConstants.HTTP_STATUS_200));
        sample.put("type", AIAgentConnectorConstants.HTTP_VERSION);
        sample.put("status", AIAgentConnectorConstants.HTTP_STATUS_OK);
        sample.put("akto_account_id", String.valueOf(accountId));
        sample.put("akto_vxlan_id", AIAgentConnectorConstants.AKTO_VXLAN_ID_DEFAULT);
        sample.put("is_pending", AIAgentConnectorConstants.IS_PENDING_FALSE);
        sample.put("source", AIAgentConnectorConstants.DATA_SOURCE_MIRRORING);
        sample.put("tag", JSONUtils.getString(tags));
        return sample;
    }

    /** Posts samples in batches; a failing batch is skipped since the next run republishes everything anyway. */
    public int publish(String ingestionUrl, String jwtToken, List<Map<String, Object>> samples) {
        if (samples == null || samples.isEmpty()) {
            return 0;
        }

        String url = ingestionUrl + AIAgentConnectorConstants.INGESTION_API_ENDPOINT;
        int batchSize = AIAgentConnectorConstants.INGESTION_DEFAULT_BATCH_SIZE;
        int published = 0;

        for (int start = 0; start < samples.size(); start += batchSize) {
            List<Map<String, Object>> batch = samples.subList(start, Math.min(start + batchSize, samples.size()));

            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put(AIAgentConnectorConstants.INGESTION_BATCH_DATA_KEY, batch);

                Request.Builder builder = new Request.Builder()
                    .url(url)
                    .header(AIAgentConnectorConstants.HEADER_CONTENT_TYPE, AIAgentConnectorConstants.CONTENT_TYPE_JSON)
                    .post(RequestBody.create(objectMapper.writeValueAsString(payload), JSON));
                if (jwtToken != null && !jwtToken.isEmpty()) {
                    // The ingestion service expects the raw token, with no "Bearer " prefix.
                    builder.header("authorization", jwtToken);
                }

                try (Response response = client.newCall(builder.build()).execute()) {
                    if (!response.isSuccessful()) {
                        String body = response.body() != null ? response.body().string() : "";
                        logger.error("CopilotStudioInventory: ingestion rejected batch: status={} body={}",
                            response.code(), body);
                        continue;
                    }
                }
                published += batch.size();
            } catch (Exception e) {
                logger.error("CopilotStudioInventory: failed to publish batch: {}", e.getMessage());
            }
        }

        logger.info("CopilotStudioInventory: published {} of {} agent samples to ingestion",
            published, samples.size());
        return published;
    }

    /** Scopes a bot name to its environment — keeps same-named bots in different environments from sharing chatters. Lowercased: Power Platform's environment-listing API and the inventory API disagree on casing ("Default-..." vs "default-...") for the same environment. */
    public static String botKey(String environmentId, String botName) {
        return (environmentId != null ? environmentId.toLowerCase(Locale.ROOT) : "") + "::" + botName;
    }

    /** Port of extractor.go's sanitizeBotName + its callers' strings.ToLower(...); must stay identical or one agent yields two collections. */
    public static String sanitizeBotName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        String sanitized = name.replaceAll("[^\\p{L}\\p{N}-]+", "-");
        sanitized = sanitized.replaceAll("-+", "-");
        return sanitized.replaceAll("^-+", "").replaceAll("-+$", "").toLowerCase(Locale.ROOT);
    }
}
