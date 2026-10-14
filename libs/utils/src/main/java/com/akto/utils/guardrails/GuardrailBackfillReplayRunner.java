package com.akto.utils.guardrails;

import com.akto.utils.elasticsearch.AgentQueryRecord;
import com.akto.utils.search.SearchClient;
import com.akto.utils.search.SearchClientFactory;
import com.akto.utils.threat_detection.ThreatDetectionBackendClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.BasicDBObject;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs one page of a guardrail policy backfill: fetch a page of stored traffic via
 * {@link SearchClient#searchPrompts}, replay it against a policy through the guardrails service,
 * and record any detections as real {@code malicious_events}, backdated to each row's own traffic
 * timestamp. This is the fetch/replay/record cycle {@code GuardrailPolicyBackfillReplayExecutor}
 * (apps/account-job-executor) drives in a loop.
 *
 * <p>Lives here rather than in the job-executor module so it depends only on
 * {@code SearchClientFactory}/{@link GuardrailsServiceClient}/{@link ThreatDetectionBackendClient}
 * (all already in this module) and not on {@code AccountJob}-specific checkpoint/heartbeat
 * machinery — a caller outside the job-executor (e.g. a future dashboard preview) can reuse this
 * cycle without depending on that module or duplicating it.
 *
 * <p>Deliberately does no partial-page bookkeeping: {@link #runOnePage} either fully replays and
 * records every item in the page, or throws with nothing counted — the caller's own checkpoint
 * only advances (and running totals only increase) once a whole page has fully succeeded, so a
 * retried page after a transient failure re-does the same items instead of double-counting a
 * partial success. Recording itself stays safe to redo: each detection's {@code refId} is
 * deterministic per (policyName, item id).
 */
public final class GuardrailBackfillReplayRunner {

    /** Malicious events built here are always rule-based: guardrails-service's replay path only
     *  ever runs rule/regex/schema filters and LLM-scanner filters, never the settings/skill
     *  scanners that are the only other source of a detection in this pipeline — mirrors
     *  buildMaliciousEvent's own hardcoded "Rule-Based" (mcp-endpoint-shield's threat_reporter.go). */
    private static final String DETECTION_TYPE_RULE_BASED = "Rule-Based";

    /** Live guardrail detections are stored as THREAT. */
    private static final String LABEL_THREAT = "THREAT";

    /** Akamai's Kong gateway. Its agent-query rows do not keep the request line or host, and
     *  every backfilled call on this account went through this deployment. */
    private static final int AKAMAI_ACCOUNT_ID = 1783401773;
    private static final String AKAMAI_PATH = "/llm/chat/completions";
    private static final String AKAMAI_HOST = "mcp-gateway-kong-proxy.mcp-gateway.svc.cluster.local:8000";
    /** Client addresses seen on this account's live guardrail events. A row has no source IP,
     *  so one of these is chosen from its id and reused for actor, latestApiIp, and the envelope. */
    private static final String[] AKAMAI_SOURCE_IPS = {
        "10.2.6.109",
        "10.2.23.78",
        "10.2.25.226",
        "10.2.6.37",
        "10.2.23.90",
        "10.2.24.4",
        "10.2.25.183"
    };
    private static final String GUARDRAIL_RESPONSE_MESSAGE = "This has been reported by Akto";

    private GuardrailBackfillReplayRunner() {
    }

    /** Outcome of one page: how many rows were fetched, how many were replayed/detected/skipped,
     *  and the next {@code search_after} cursor to resume from. An empty {@code batch} means the
     *  range is exhausted. */
    public static final class PageResult {
        public final List<Map<String, Object>> batch;
        public final long total;
        public final int replayed;
        public final int detected;
        public final int skipped;
        public final String nextSearchAfterJson;

        PageResult(List<Map<String, Object>> batch, long total, int replayed, int detected, int skipped,
                   String nextSearchAfterJson) {
            this.batch = batch;
            this.total = total;
            this.replayed = replayed;
            this.detected = detected;
            this.skipped = skipped;
            this.nextSearchAfterJson = nextSearchAfterJson;
        }
    }

    /**
     * @param searchAfterJson opaque cursor from the previous page's {@link PageResult#nextSearchAfterJson},
     *     or {@code ""} to start from {@code startTimestampMs}.
     * @param bearerToken already-minted "Bearer &lt;jwt&gt;" header value, reused across every page
     *     of a backfill job rather than minted per call — see
     *     {@link GuardrailsServiceClient#createLongLivedAuthToken}.
     */
    public static PageResult runOnePage(int accountId, BasicDBObject policyPayload, String policyName,
                                        String contextSourceForEvents, String bearerToken,
                                        long startTimestampMs, long effectiveEndTimestampMs,
                                        String searchAfterJson, boolean atlasTrafficFilter) throws Exception {
        SearchClient.SearchResult page = SearchClientFactory.instance().searchPrompts(
            accountId, startTimestampMs, effectiveEndTimestampMs,
            0, GuardrailsServiceClient.PAGE_SIZE,
            "timestamp", true, searchAfterJson.isEmpty() ? null : searchAfterJson,
            null, atlasTrafficFilter, null, true);

        List<Map<String, Object>> batch = page.hits;
        if (batch.isEmpty()) {
            return new PageResult(batch, page.total, 0, 0, 0, searchAfterJson);
        }

        TrafficShape shape = trafficShape(accountId);
        List<BasicDBObject> items = new ArrayList<>();
        Map<String, Map<String, Object>> rowById = new HashMap<>();
        for (Map<String, Object> row : batch) {
            String prompt = asText(row.get(AgentQueryRecord.F_QUERY_PAYLOAD));
            if (StringUtils.isBlank(prompt)) {
                continue;
            }
            // Document id is unique per stored message. traceId is not: one trace can hold several rows.
            String id = asText(row.get("id"));
            if (StringUtils.isBlank(id)) {
                id = asText(row.get(AgentQueryRecord.F_TRACE_ID));
            }
            if (StringUtils.isBlank(id)) {
                continue;
            }
            String envelope = GuardrailsServiceClient.traceEnvelope(
                prompt, asText(row.get(AgentQueryRecord.F_RESPONSE_PAYLOAD)),
                shape.method, shape.path, shape.host);
            items.add(new BasicDBObject("id", id).append("envelope", envelope));
            rowById.put(id, row);
        }

        // Computed even when replay/record below throws, so a caller that wants to log progress
        // knows how far this page would have advanced — it must NOT persist this on failure,
        // since nothing in the page actually succeeded (see class javadoc).
        Object lastSortValues = batch.get(batch.size() - 1).get("_sortValues");
        String nextSearchAfterJson = lastSortValues == null ? searchAfterJson : lastSortValues.toString();

        int replayed = 0, detected = 0, skipped = 0;
        if (!items.isEmpty()) {
            Iterable<JsonNode> verdicts = GuardrailsServiceClient.replay(
                items, policyPayload, null, contextSourceForEvents, bearerToken, accountId, true);

            for (JsonNode verdict : verdicts) {
                if (!verdict.path("skipReason").asText("").isEmpty()) {
                    skipped++;
                    continue;
                }
                replayed++;
                if (verdict.path("detected").asBoolean(false)) {
                    detected++;
                    String id = verdict.path("id").asText("");
                    Map<String, Object> row = rowById.get(id);
                    if (row != null) {
                        recordDetection(accountId, policyName, contextSourceForEvents, row, shape, verdict, bearerToken);
                    }
                }
            }
        }

        return new PageResult(batch, page.total, replayed, detected, skipped, nextSearchAfterJson);
    }

    /** Builds and sends the record_malicious_event call for one detected row, backdated to the
     *  row's own traffic timestamp rather than "now". Method, path, and host come from
     *  {@code shape}. The stored payload uses the guardrail alert body a live detection writes,
     *  not the model completion that was scored. Category, subCategory, and severity come from
     *  the verdict only. Collection id is the detection timestamp, matching live guardrail events. */
    private static void recordDetection(int accountId, String policyName, String contextSource,
                                        Map<String, Object> row, TrafficShape shape,
                                        JsonNode verdict, String bearerToken) throws Exception {
        String id = verdict.path("id").asText("");
        long detectedAtSec = timestampMillis(row.get(AgentQueryRecord.F_TIMESTAMP)) / 1000L;

        String category = verdict.path("category").asText("");
        String subCategory = verdict.path("subCategory").asText("");
        String severity = verdict.path("severity").asText("");
        String reason = verdict.path("reason").asText("");
        String behaviour = verdict.path("behaviour").asText("");

        String sourceIp = sourceIp(accountId, id);
        String actor = StringUtils.isNotBlank(sourceIp) ? sourceIp : asText(row.get(AgentQueryRecord.F_USER_NAME));
        String sessionId = asText(row.get(AgentQueryRecord.F_SESSION_IDENTIFIER));
        String storedPayload = activityEnvelope(
            asText(row.get(AgentQueryRecord.F_QUERY_PAYLOAD)), shape, sourceIp, detectedAtSec, behaviour, reason);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("policyName", policyName);
        if (StringUtils.isNotBlank(subCategory)) {
            metadata.put("ruleViolated", subCategory);
        }
        if (StringUtils.isNotBlank(reason)) {
            metadata.put("reason", reason);
        }
        if (StringUtils.isNotBlank(behaviour)) {
            metadata.put("behaviour", behaviour);
        }

        Map<String, Object> maliciousEvent = new HashMap<>();
        maliciousEvent.put("filterId", policyName);
        maliciousEvent.put("label", LABEL_THREAT);
        maliciousEvent.put("detectedAt", detectedAtSec);
        maliciousEvent.put("actor", actor);
        maliciousEvent.put("latestApiMethod", shape.method);
        maliciousEvent.put("latestApiEndpoint", shape.path);
        maliciousEvent.put("latestApiCollectionId", detectedAtSec);
        maliciousEvent.put("latestApiPayload", storedPayload);
        if (StringUtils.isNotBlank(sourceIp)) {
            maliciousEvent.put("latestApiIp", sourceIp);
        }
        maliciousEvent.put("eventType", "EVENT_TYPE_SINGLE");
        if (StringUtils.isNotBlank(category)) {
            maliciousEvent.put("category", category);
        }
        if (StringUtils.isNotBlank(subCategory)) {
            maliciousEvent.put("subCategory", subCategory);
        }
        if (StringUtils.isNotBlank(severity)) {
            maliciousEvent.put("severity", severity);
        }
        maliciousEvent.put("type", DETECTION_TYPE_RULE_BASED);
        maliciousEvent.put("metadata", metadata);
        maliciousEvent.put("successfulExploit", true);
        if (StringUtils.isNotBlank(shape.host)) {
            maliciousEvent.put("host", shape.host);
        }
        maliciousEvent.put("contextSource", contextSource);
        if (StringUtils.isNotBlank(sessionId)) {
            maliciousEvent.put("sessionId", sessionId);
        }
        // Deterministic, not random: a page re-sent after a crash/retry lands on the same refId
        // instead of creating a duplicate malicious_events document.
        maliciousEvent.put("refId", "backfill|" + policyName + "|" + id);

        ThreatDetectionBackendClient.recordMaliciousEvent(accountId, maliciousEvent, bearerToken);
    }

    /** Same address for a given row on every retry, spread across the account's known clients. */
    private static String sourceIp(int accountId, String id) {
        if (accountId != AKAMAI_ACCOUNT_ID || StringUtils.isBlank(id)) {
            return "";
        }
        return AKAMAI_SOURCE_IPS[Math.floorMod(id.hashCode(), AKAMAI_SOURCE_IPS.length)];
    }

    /** Live guardrail activity envelope. The response is the alert body the gateway stores after
     *  a detection, not the model completion replay scored. */
    private static String activityEnvelope(String requestPayload, TrafficShape shape, String ip,
                                           long detectedAtSec, String behaviour, String reason) {
        BasicDBObject envelope = new BasicDBObject();
        envelope.put("akto_account_id", "");
        envelope.put("akto_vxlan_id", "");
        if (StringUtils.isNotBlank(ip)) {
            envelope.put("destIp", ip);
            envelope.put("ip", ip);
        }
        envelope.put("method", shape.method);
        envelope.put("path", shape.path);
        if (StringUtils.isNotBlank(shape.host)) {
            envelope.put("requestHeaders", new BasicDBObject("host", shape.host).toJson());
        }
        envelope.put("requestPayload", requestPayload);
        envelope.put("responsePayload", guardrailResponse(detectedAtSec, behaviour, reason));
        envelope.put("source", "OTHER");
        envelope.put("status", "OK");
        envelope.put("statusCode", 200);
        envelope.put("time", 0);
        envelope.put("type", "http");
        return envelope.toJson();
    }

    private static String guardrailResponse(long detectedAtSec, String behaviour, String reason) {
        BasicDBObject data = new BasicDBObject();
        if (StringUtils.isNotBlank(behaviour)) {
            data.put("behaviour", behaviour);
        }
        if (detectedAtSec > 0) {
            data.put("blocked_at", Instant.ofEpochSecond(detectedAtSec).toString());
        }
        data.put("blocked_by", "policy_validator");
        if (StringUtils.isNotBlank(reason)) {
            data.put("reason", reason);
        }
        BasicDBObject error = new BasicDBObject();
        error.put("code", -32000);
        error.put("data", data);
        error.put("message", GUARDRAIL_RESPONSE_MESSAGE);
        return new BasicDBObject()
            .append("error", error)
            .append("id", null)
            .append("jsonrpc", "2.0")
            .toJson();
    }

    private static TrafficShape trafficShape(int accountId) {
        if (accountId == AKAMAI_ACCOUNT_ID) {
            return new TrafficShape(GuardrailsServiceClient.TRACE_METHOD, AKAMAI_PATH, AKAMAI_HOST);
        }
        return new TrafficShape(GuardrailsServiceClient.TRACE_METHOD, GuardrailsServiceClient.TRACE_PATH, "");
    }

    /** Request line and host used both to evaluate a row and to record a detection for it. */
    private static final class TrafficShape {
        final String method;
        final String path;
        final String host;

        TrafficShape(String method, String path, String host) {
            this.method = method;
            this.path = path;
            this.host = host;
        }
    }

    private static String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** Epoch milliseconds. Elasticsearch stores a number; ADX search rows can carry a datetime.
     *  A numeric value below 10^12 is epoch seconds. */
    private static long timestampMillis(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return epochMillis(((Number) value).longValue());
        }
        if (value instanceof Date) {
            return ((Date) value).getTime();
        }
        if (value instanceof Instant) {
            return ((Instant) value).toEpochMilli();
        }
        if (value instanceof OffsetDateTime) {
            return ((OffsetDateTime) value).toInstant().toEpochMilli();
        }
        if (value instanceof ZonedDateTime) {
            return ((ZonedDateTime) value).toInstant().toEpochMilli();
        }
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).toInstant(ZoneOffset.UTC).toEpochMilli();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return 0L;
        }
        try {
            return epochMillis(Long.parseLong(text));
        } catch (NumberFormatException ignored) {
        }
        try {
            return Instant.parse(text).toEpochMilli();
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (Exception ignored) {
        }
        return 0L;
    }

    private static long epochMillis(long value) {
        if (value > 0 && value < 1_000_000_000_000L) {
            return value * 1000L;
        }
        return value;
    }
}
