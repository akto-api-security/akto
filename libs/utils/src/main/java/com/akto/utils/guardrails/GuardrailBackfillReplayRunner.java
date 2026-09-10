package com.akto.utils.guardrails;

import com.akto.utils.elasticsearch.AgentQueryRecord;
import com.akto.utils.search.SearchClient;
import com.akto.utils.search.SearchClientFactory;
import com.akto.utils.threat_detection.ThreatDetectionBackendClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.BasicDBObject;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
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

        List<BasicDBObject> items = new ArrayList<>();
        Map<String, Map<String, Object>> rowById = new HashMap<>();
        for (Map<String, Object> row : batch) {
            String prompt = asText(row.get(AgentQueryRecord.F_QUERY_PAYLOAD));
            if (StringUtils.isBlank(prompt)) {
                continue;
            }
            String id = asText(row.get(AgentQueryRecord.F_TRACE_ID));
            if (StringUtils.isBlank(id)) {
                id = asText(row.get("id")); // ES document's own _id: always present, always unique
            }
            String envelope = GuardrailsServiceClient.traceEnvelope(
                prompt, asText(row.get(AgentQueryRecord.F_RESPONSE_PAYLOAD)));
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
                        recordDetection(accountId, policyName, contextSourceForEvents, row, verdict, bearerToken);
                    }
                }
            }
        }

        return new PageResult(batch, page.total, replayed, detected, skipped, nextSearchAfterJson);
    }

    /** Builds and sends the record_malicious_event call for one detected row, backdated to the
     *  row's own traffic timestamp rather than "now". Populates the same category/subCategory/
     *  severity/actor/host/metadata shape a live detection reports (see buildMaliciousEvent in
     *  mcp-endpoint-shield's threat_reporter.go) instead of the bare-minimum filterId/label/
     *  detectedAt a replay verdict alone would give — verdict must have been requested with
     *  includeDetectionDetails=true or category/subCategory/severity come back empty. */
    private static void recordDetection(int accountId, String policyName, String contextSource,
                                        Map<String, Object> row, JsonNode verdict, String bearerToken) throws Exception {
        String id = verdict.path("id").asText("");
        long detectedAtSec = asLong(row.get(AgentQueryRecord.F_TIMESTAMP)) / 1000L;

        String category = verdict.path("category").asText("");
        if (StringUtils.isBlank(category)) {
            category = policyName;
        }
        String subCategory = verdict.path("subCategory").asText("");
        if (StringUtils.isBlank(subCategory)) {
            subCategory = category;
        }
        String severity = verdict.path("severity").asText("");
        if (StringUtils.isBlank(severity)) {
            severity = "CRITICAL";
        }
        String reason = verdict.path("reason").asText("");
        String behaviour = verdict.path("behaviour").asText("");

        // Best-effort actor/host from the traffic record itself — a real detection gets these
        // from the live request's IP/host header, which a backfilled ES row does not carry, so
        // the closest equivalents on stored agent-query records are userName/deviceId.
        String actor = asText(row.get(AgentQueryRecord.F_USER_NAME));
        String host = asText(row.get(AgentQueryRecord.F_DEVICE_ID));
        String sessionId = asText(row.get(AgentQueryRecord.F_SESSION_IDENTIFIER));

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("policyName", policyName);
        metadata.put("ruleViolated", subCategory);
        if (StringUtils.isNotBlank(reason)) {
            metadata.put("reason", reason);
        }
        if (StringUtils.isNotBlank(behaviour)) {
            metadata.put("behaviour", behaviour);
        }

        Map<String, Object> maliciousEvent = new HashMap<>();
        maliciousEvent.put("filterId", policyName);
        maliciousEvent.put("label", "guardrail");
        maliciousEvent.put("detectedAt", detectedAtSec);
        maliciousEvent.put("actor", actor);
        maliciousEvent.put("latestApiIp", actor);
        maliciousEvent.put("latestApiMethod", GuardrailsServiceClient.TRACE_METHOD);
        maliciousEvent.put("latestApiEndpoint", GuardrailsServiceClient.TRACE_PATH);
        maliciousEvent.put("latestApiCollectionId", detectedAtSec);
        maliciousEvent.put("latestApiPayload", asText(row.get(AgentQueryRecord.F_QUERY_PAYLOAD)));
        maliciousEvent.put("eventType", "EVENT_TYPE_SINGLE");
        maliciousEvent.put("category", category);
        maliciousEvent.put("subCategory", subCategory);
        maliciousEvent.put("severity", severity);
        maliciousEvent.put("type", DETECTION_TYPE_RULE_BASED);
        maliciousEvent.put("metadata", metadata);
        maliciousEvent.put("successfulExploit", true);
        maliciousEvent.put("host", host);
        maliciousEvent.put("contextSource", contextSource);
        if (StringUtils.isNotBlank(sessionId)) {
            maliciousEvent.put("sessionId", sessionId);
        }
        // Deterministic, not random: a page re-sent after a crash/retry lands on the same refId
        // instead of creating a duplicate malicious_events document.
        maliciousEvent.put("refId", "backfill|" + policyName + "|" + id);

        ThreatDetectionBackendClient.recordMaliciousEvent(accountId, maliciousEvent, bearerToken);
    }

    private static String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
