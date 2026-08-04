package com.akto.jobs.executors.copilotstudio;

import com.akto.jobs.executors.AIAgentConnectorConstants;
import com.akto.util.Constants;
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
import java.util.List;
import java.util.Map;
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
    /** Must match extractor.go's host construction exactly, or inventory and transcript data for the same agent land in different collections. */
    private static final String AI_AGENT_HOST_INFIX = ".ai-agent.";
    /** Informational only — carried for traceability back to the inventory row, nothing reads it. */
    private static final String BOT_ID_TAG = "bot-id";
    private static final String TAG_VALUE_TRUE = "true";
    private static final String HEADER_HOST = "host";
    private static final String AGENT_PATH_PREFIX = "/copilot/bot/";

    /** Kept at "{}" on purpose — over 3 chars triggers an unwanted AgentQueryRecord write downstream. */
    private static final String EMPTY_REQUEST_PAYLOAD = "{}";

    /** Builds one traffic sample per agent. Agents without a usable display name are skipped. */
    public List<Map<String, Object>> buildSamples(List<JsonNode> agents, String environmentId,
                                                   Map<String, String> botIdToDataverseName, int accountId,
                                                   Map<String, String> ownerIdToUserId) {
        List<Map<String, Object>> samples = new ArrayList<>();

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

            // Host carries the identity now — same convention as extractor.go's ExtractBotMessage/ExtractTranscriptMessages, so this lands in the same collection.
            String ownerId = properties.path("ownerId").asText("");
            String resolvedOwnerId = ownerIdToUserId != null ? ownerIdToUserId.get(ownerId) : null;
            String host = (resolvedOwnerId != null && !resolvedOwnerId.isEmpty())
                ? resolvedOwnerId + AI_AGENT_HOST_INFIX + botName : botName;

            Map<String, String> tags = new HashMap<>();
            tags.put(Constants.AKTO_ENDPOINT_SOURCE_TAG, Constants.AKTO_ENDPOINT_SOURCE_VALUE);
            tags.put(Constants.AKTO_GEN_AI_TAG, AIAgentConnectorConstants.DATA_TAG_GEN_AI);
            tags.put(Constants.AKTO_AI_AGENT_TAG, Constants.COPILOT_STUDIO_AI_AGENT_NAME);
            tags.put(BOT_ID_TAG, agentId);
            tags.put(Constants.AKTO_COPILOT_BOT_ENVIRONMENT_TAG, environmentId != null ? environmentId : "");
            tags.put(Constants.AKTO_COPILOT_INVENTORY_TAG, TAG_VALUE_TRUE);

            Map<String, String> requestHeaders = new HashMap<>();
            requestHeaders.put(HEADER_HOST, host);
            requestHeaders.put("content-type", AIAgentConnectorConstants.CONTENT_TYPE_JSON);

            Map<String, Object> sample = new HashMap<>();
            sample.put("path", AGENT_PATH_PREFIX + agentId);
            sample.put("method", AIAgentConnectorConstants.HTTP_METHOD_GET);
            // Headers/tags go on the wire as JSON strings — OriginalHttpRequest.buildHeadersMap casts the field to String.
            sample.put("requestHeaders", toJson(requestHeaders));
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
            sample.put("tag", toJson(tags));

            samples.add(sample);
        }

        return samples;
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

    /** Port of sanitizeBotName in extractor.go; must stay identical to the binary's version or one agent yields two collections. */
    static String sanitizeBotName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        String sanitized = name.replaceAll("[^\\p{L}\\p{N}-]+", "-");
        sanitized = sanitized.replaceAll("-+", "-");
        return sanitized.replaceAll("^-+", "").replaceAll("-+$", "");
    }

    private String toJson(Map<String, String> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            logger.error("CopilotStudioInventory: failed to serialize tags: " + e.getMessage());
            return "{}";
        }
    }
}
