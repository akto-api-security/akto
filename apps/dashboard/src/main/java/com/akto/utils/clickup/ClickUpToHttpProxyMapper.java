package com.akto.utils.clickup;

import com.akto.dto.HttpResponseParams;
import com.akto.util.Constants;
import com.google.gson.Gson;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import okhttp3.HttpUrl;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds a single http-proxy / Kafka ingest JSON object per ClickUp trace summary row.
 * {@code requestPayload} is the real list-API request body (typically {@code {}} for GET).
 * {@code responsePayload} wraps the trace summary under {@link Constants#CLICKUP_TRACE_METADATA_KEY} for mini-runtime.
 */
public final class ClickUpToHttpProxyMapper {

    private static final Gson GSON = new Gson();

    public static final String ARGUS_GEN_AI_DISPLAY_VALUE = "Gen AI";

    private ClickUpToHttpProxyMapper() {}

    /**
     * Tag JSON for Kafka ingest: vendor ({@link Constants#INGEST_TAG_SERVICE}), Argus ({@link Constants#AKTO_GEN_AI_TAG}),
     * and AI-agent fields ({@link Constants#AI_AGENT_TAG_SOURCE}, {@link Constants#AI_AGENT_TAG_BOT_NAME}) so mini-runtime
     * treats traffic like other agent sources (hostname / agentic pipeline).
     *
     * @param botName value for {@code bot-name} (e.g. ClickUp {@code agentViewId})
     */
    public static String buildIngestTagJson(String botName) {
        Map<String, String> tag = new LinkedHashMap<>();
        tag.put(Constants.INGEST_TAG_SERVICE, Constants.INGEST_SERVICE_VALUE_CLICKUP);
        tag.put(Constants.AKTO_GEN_AI_TAG, ARGUS_GEN_AI_DISPLAY_VALUE);
        tag.put(Constants.AI_AGENT_TAG_SOURCE, Constants.AI_AGENT_SOURCE_CLICKUP);
        String bot = botName != null && !botName.isEmpty() ? botName : "clickup-trace";
        tag.put(Constants.AI_AGENT_TAG_BOT_NAME, bot);
        return GSON.toJson(tag);
    }

    /**
     * @param listTraceSummariesRequestBodyJson real HTTP request body of the ClickUp list call (typically {@code "{}"} for GET)
     * @param traceSummaryRowJson JSON of one trace summary row (wrapped into {@code responsePayload} for the parser)
     */
    public static String toHttpProxyJsonLine(
            String traceSummaryId,
            String workspaceId,
            String listTraceSummariesRequestBodyJson,
            String traceSummaryRowJson,
            int timeEpochSeconds,
            String aktoAccountId,
            String aktoVxlanId,
            String hostHeader,
            String ingestTagBotName
    ) {
        String path = "/clickup/workspaces/" + workspaceId + "/trace-summaries/" + traceSummaryId;
        Map<String, String> reqHeaders = new LinkedHashMap<>();
        reqHeaders.put("host", hostHeader);
        reqHeaders.put("content-type", "application/json");
        reqHeaders.put("user-agent", "Akto-ClickUp-Ingest/1.0");
        Map<String, String> respHeaders = new LinkedHashMap<>();
        respHeaders.put("content-type", "application/json");

        JsonObject o = new JsonObject();
        o.addProperty("path", path);
        o.addProperty("requestHeaders", GSON.toJson(reqHeaders));
        o.addProperty("responseHeaders", GSON.toJson(respHeaders));
        o.addProperty("method", "POST");
        o.addProperty(
                "requestPayload",
                StringUtils.isBlank(listTraceSummariesRequestBodyJson) ? "{}" : listTraceSummariesRequestBodyJson.trim());
        o.addProperty("responsePayload", buildResponsePayloadWithTraceMetadata(traceSummaryRowJson));
        o.addProperty("ip", "127.0.0.1");
        o.add("destIp", JsonNull.INSTANCE);
        o.addProperty("time", String.valueOf(timeEpochSeconds));
        o.addProperty("statusCode", "200");
        o.addProperty("type", "HTTP/1.1");
        o.addProperty("status", "OK");
        o.addProperty("akto_account_id", aktoAccountId);
        o.addProperty("akto_vxlan_id", aktoVxlanId);
        o.addProperty("is_pending", "false");
        o.addProperty("source", HttpResponseParams.Source.MIRRORING.name());
        o.add("direction", JsonNull.INSTANCE);
        o.add("process_id", JsonNull.INSTANCE);
        o.add("socket_id", JsonNull.INSTANCE);
        o.add("daemonset_id", JsonNull.INSTANCE);
        o.add("enabled_graph", JsonNull.INSTANCE);
        o.addProperty("tag", buildIngestTagJson(ingestTagBotName));
        return GSON.toJson(o);
    }

    static String buildResponsePayloadWithTraceMetadata(String traceSummaryRowJson) {
        JsonObject wrap = new JsonObject();
        try {
            if (traceSummaryRowJson != null && !traceSummaryRowJson.isEmpty()) {
                wrap.add(Constants.CLICKUP_TRACE_METADATA_KEY, JsonParser.parseString(traceSummaryRowJson).getAsJsonObject());
            } else {
                wrap.add(Constants.CLICKUP_TRACE_METADATA_KEY, new JsonObject());
            }
        } catch (Exception e) {
            wrap.add(Constants.CLICKUP_TRACE_METADATA_KEY, new JsonObject());
        }
        return GSON.toJson(wrap);
    }

    /**
     * {@code Host} for synthetic ingest: the real ClickUp API host from {@code CLICKUP_FETCH_URL} (if set) or
     * {@code CLICKUP_API_BASE_URL}, not a derived {@code *.ingest.akto} name.
     */
    public static String resolveIngestHostHeader(String fetchUrl, String apiBaseUrl) {
        String host = hostFromUrl(fetchUrl);
        if (StringUtils.isNotBlank(host)) {
            return host;
        }
        return hostFromUrl(apiBaseUrl);
    }

    private static String hostFromUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return "";
        }
        HttpUrl u = HttpUrl.parse(url.trim());
        return u != null && StringUtils.isNotBlank(u.host()) ? u.host() : "";
    }

    public static Map<String, String> tagMapForTests(String botName) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(Constants.INGEST_TAG_SERVICE, Constants.INGEST_SERVICE_VALUE_CLICKUP);
        m.put(Constants.AKTO_GEN_AI_TAG, ARGUS_GEN_AI_DISPLAY_VALUE);
        m.put(Constants.AI_AGENT_TAG_SOURCE, Constants.AI_AGENT_SOURCE_CLICKUP);
        m.put(Constants.AI_AGENT_TAG_BOT_NAME, botName != null ? botName : "t1");
        return Collections.unmodifiableMap(m);
    }
}
