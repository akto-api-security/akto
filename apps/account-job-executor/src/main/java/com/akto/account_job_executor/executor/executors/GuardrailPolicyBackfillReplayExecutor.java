package com.akto.account_job_executor.executor.executors;

import com.akto.dto.jobs.AccountJob;
import com.akto.account_job_executor.executor.AccountJobExecutor;
import com.akto.dao.context.Context;
import com.akto.dao.jobs.AccountJobDao;
import com.akto.jobs.exception.RetryableJobException;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.utils.elasticsearch.AgentQueryRecord;
import com.akto.utils.guardrails.GuardrailsServiceClient;
import com.akto.utils.search.SearchClient;
import com.akto.utils.search.SearchClientFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;

import java.util.*;
public class GuardrailPolicyBackfillReplayExecutor extends AccountJobExecutor {

    public static final GuardrailPolicyBackfillReplayExecutor INSTANCE = new GuardrailPolicyBackfillReplayExecutor();

    private static final LoggerMaker loggerMaker = new LoggerMaker(GuardrailPolicyBackfillReplayExecutor.class, LogDb.DASHBOARD);

    private static final String CONFIG_POLICY_NAME = "policyName";
    private static final String CONFIG_POLICY_PAYLOAD = "policyPayload";
    private static final String CONFIG_CONTEXT_SOURCE = "contextSource";
    private static final String CONFIG_API_TOKEN = "apiToken";
    private static final String CONFIG_START_TIMESTAMP = "startTimestamp";
    private static final String CONFIG_EFFECTIVE_END_TIMESTAMP = "effectiveEndTimestamp";
    private static final String CONFIG_SEARCH_AFTER = "searchAfterJson";
    private static final String CONFIG_PROCESSED_COUNT = "processedCount";
    private static final String CONFIG_DETECTED_COUNT = "detectedCount";
    private static final String CONFIG_LAST_BATCH_ERROR = "lastBatchError";

    private static final String DEFAULT_CONTEXT_SOURCE = "AGENTIC";

    private GuardrailPolicyBackfillReplayExecutor() {
    }

    @Override
    protected void runJob(AccountJob job) throws Exception {
        long jobStartMs = System.currentTimeMillis();
        Map<String, Object> config = job.getConfig();
        int accountId = job.getAccountId();

        String policyName = asText(config.get(CONFIG_POLICY_NAME));
        @SuppressWarnings("unchecked")
        Map<String, Object> policyPayloadMap = (Map<String, Object>) config.get(CONFIG_POLICY_PAYLOAD);
        String rawApiToken = asText(config.get(CONFIG_API_TOKEN));
        if (StringUtils.isBlank(policyName) || policyPayloadMap == null || StringUtils.isBlank(rawApiToken)) {
            // Not a transient problem — the job was created without what it needs to run.
            throw new IllegalStateException(
                "Backfill job " + job.getId() + " is missing policyName/policyPayload/apiToken");
        }
        BasicDBObject policyPayload = new BasicDBObject(policyPayloadMap);

        String contextSourceFilter = asText(config.get(CONFIG_CONTEXT_SOURCE));
        String contextSourceForEvents = StringUtils.isNotBlank(contextSourceFilter)
            ? contextSourceFilter : DEFAULT_CONTEXT_SOURCE;
        // ENDPOINT-sourced traffic is Atlas/gateway traffic; every other context source is
        // recorded directly by an agent SDK/integration, never through Atlas.
        boolean atlasTrafficFilter = contextSourceForEvents.equals(CONTEXT_SOURCE.ENDPOINT.toString());

        loggerMaker.warn("Guardrail backfill job picked up: jobId=" + job.getId()
            + " accountId=" + accountId + " policyName=" + policyName
            + " source=" + contextSourceForEvents);

        long startTimestampMs = asLong(config.get(CONFIG_START_TIMESTAMP)) * 1000L;
        long effectiveEndTimestampMs = asLong(config.get(CONFIG_EFFECTIVE_END_TIMESTAMP)) * 1000L;
        String searchAfterJson = asText(config.get(CONFIG_SEARCH_AFTER));
        int processedCount = asInt(config.get(CONFIG_PROCESSED_COUNT));
        int detectedCount = asInt(config.get(CONFIG_DETECTED_COUNT));

        // contextSource is not an indexed Elasticsearch field (see AgentQueryRecord) — there is
        // nothing to filter on here beyond the time range. contextSourceForEvents is used only to
        // tag the malicious_events records this job writes.
        //
        // searchPrompts (not fetchMessages) is used deliberately: fetchMessages groups raw docs
        // into one row per trace and hard-caps its output at 500 trace groups total (see
        // ElasticSearchClient.MESSAGES_SIZE), silently dropping everything past the 500 most
        // recent groups in the window — fine for a "recent sample" (fetchTraceSamples), wrong for
        // an exhaustive backfill. searchPrompts gives real, uncapped search_after pagination over
        // every flat raw document instead.
        int pageIndex = 0;
        while (true) {
            SearchClient.SearchResult page;
            try {
                page = SearchClientFactory.instance().searchPrompts(
                    accountId, startTimestampMs, effectiveEndTimestampMs,
                    0, GuardrailsServiceClient.PAGE_SIZE,
                    "timestamp", true, searchAfterJson.isEmpty() ? null : searchAfterJson,
                    null, atlasTrafficFilter, null, true);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Guardrail backfill job " + job.getId()
                    + ": searchPrompts failed for range [" + startTimestampMs + ", " + effectiveEndTimestampMs
                    + ") searchAfter=" + searchAfterJson + " - " + e.getMessage());
                throw new RetryableJobException("searchPrompts failed: " + e.getMessage(), e);
            }

            List<Map<String, Object>> batch = page.hits;
            pageIndex++;
            if (pageIndex == 1) {
                loggerMaker.warn("Guardrail backfill job " + job.getId() + ": " + page.total
                    + " total messages in range [" + startTimestampMs + ", " + effectiveEndTimestampMs + ")");
            }
            if (batch.isEmpty()) {
                break;
            }
            loggerMaker.warn("Guardrail backfill job " + job.getId() + ": batch " + pageIndex
                + " picked up (" + batch.size() + " records)");

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

            // Computed up front so a mid-batch failure below still checkpoints past this page
            // instead of re-fetching it forever.
            Object lastSortValues = batch.get(batch.size() - 1).get("_sortValues");
            String nextSearchAfterJson = lastSortValues == null ? searchAfterJson : lastSortValues.toString();

            if (!items.isEmpty()) {
                Iterable<JsonNode> verdicts;
                try {
                    verdicts = GuardrailsServiceClient.replay(
                        items, policyPayload, null, contextSourceForEvents, rawApiToken, accountId, true);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Guardrail backfill job " + job.getId() + ": batch "
                        + pageIndex + " (" + items.size() + " items) replay failed - " + e.getMessage());
                    // Transient: guardrails-service unreachable/erroring. Persist progress made so
                    // far before asking the framework to retry from here.
                    persistCheckpoint(job.getId(), config, searchAfterJson, processedCount, detectedCount, e.getMessage());
                    throw new RetryableJobException("guardrails-service replay failed: " + e.getMessage(), e);
                }

                int batchReplayed = 0;
                int batchDetected = 0;
                int batchSkipped = 0;
                for (JsonNode verdict : verdicts) {
                    if (!verdict.path("skipReason").asText("").isEmpty()) {
                        batchSkipped++;
                        continue;
                    }
                    batchReplayed++;
                    processedCount++;
                    if (verdict.path("detected").asBoolean(false)) {
                        batchDetected++;
                        detectedCount++;
                        String id = verdict.path("id").asText("");
                        Map<String, Object> row = rowById.get(id);
                        if (row != null) {
                            try {
                                recordDetection(accountId, policyName, contextSourceForEvents, row, verdict, "Bearer " + rawApiToken);
                            } catch (Exception e) {
                                loggerMaker.errorAndAddToDb(e, "Guardrail backfill job " + job.getId()
                                    + ": batch " + pageIndex + " failed to record detection id=" + id
                                    + " - " + e.getMessage());
                                persistCheckpoint(job.getId(), config, searchAfterJson, processedCount, detectedCount, e.getMessage());
                                throw new RetryableJobException(
                                    "record_malicious_event failed for " + id + ": " + e.getMessage(), e);
                            }
                        }
                    }
                }
                loggerMaker.warn("Guardrail backfill job " + job.getId() + ": batch " + pageIndex
                    + " processed - replayed=" + batchReplayed + " detected=" + batchDetected
                    + " skipped=" + batchSkipped
                    + " (running totals: replayed=" + processedCount + " detected=" + detectedCount + ")");
            }

            searchAfterJson = nextSearchAfterJson;
            persistCheckpoint(job.getId(), config, searchAfterJson, processedCount, detectedCount, null);
            updateHeartbeatDirect(job.getId());

            if (batch.size() < GuardrailsServiceClient.PAGE_SIZE) {
                break; // short page: nothing left in range
            }
        }

        long timeTakenMs = System.currentTimeMillis() - jobStartMs;
        loggerMaker.warn("Guardrail backfill replay job " + job.getId() + " completed for policy " + policyName
            + ": totalReplayed=" + processedCount + " totalDetected=" + detectedCount
            + " timeTakenMs=" + timeTakenMs);
    }

    /** Malicious events built here are always rule-based: guardrails-service's replay path only
     *  ever runs rule/regex/schema filters and LLM-scanner filters, never the settings/skill
     *  scanners that are the only other source of a detection in this pipeline — mirrors
     *  buildMaliciousEvent's own hardcoded "Rule-Based" (mcp-endpoint-shield's threat_reporter.go). */
    private static final String DETECTION_TYPE_RULE_BASED = "Rule-Based";

    /** Builds and sends the record_malicious_event call for one detected row, backdated to the
     *  row's own traffic timestamp rather than "now". Populates the same category/subCategory/
     *  severity/actor/host/metadata shape a live detection reports (see buildMaliciousEvent in
     *  mcp-endpoint-shield's threat_reporter.go) instead of the bare-minimum filterId/label/
     *  detectedAt a replay verdict alone would give — verdict must have been requested with
     *  includeDetectionDetails=true or category/subCategory/severity come back empty. */
    private void recordDetection(int accountId, String policyName, String contextSource,
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

        com.akto.utils.threat_detection.ThreatDetectionBackendClient.recordMaliciousEvent(
            accountId, maliciousEvent, bearerToken);
    }

    /**
     * Persists progress by updating the checkpoint keys on a copy of the job's <em>full</em>
     * original config and writing that whole map back directly via Mongo — a plain field replace
     * on {@code config}, not a per-key merge, so sending only the checkpoint keys here would
     * silently wipe policyName/policyPayload/contextSource/effectiveEndTimestamp off the document.
     */
    private void persistCheckpoint(ObjectId jobId, Map<String, Object> baseConfig,
                                   String searchAfterJson, int processedCount, int detectedCount,
                                   String lastBatchError) {
        Map<String, Object> config = new HashMap<>(baseConfig);
        config.put(CONFIG_SEARCH_AFTER, searchAfterJson);
        config.put(CONFIG_PROCESSED_COUNT, processedCount);
        config.put(CONFIG_DETECTED_COUNT, detectedCount);
        config.put(CONFIG_LAST_BATCH_ERROR, lastBatchError);

        AccountJobDao.instance.updateOne(Filters.eq(AccountJob.ID, jobId), Updates.set(AccountJob.CONFIG, config));
    }

    /** Direct Mongo heartbeat update. account-job-executor's real deployment has no Mongo
     *  connection of its own (see Main.java — Cyborg/HTTP only) and normally goes through
     *  AccountJobExecutor.updateJobHeartbeat/CyborgApiClient instead; this direct path only works
     *  when Mongo has been initialized in-process, e.g. via this class's own main() harness. */
    private void updateHeartbeatDirect(ObjectId jobId) {
        AccountJobDao.instance.updateOne(Filters.eq(AccountJob.ID, jobId), Updates.set(AccountJob.HEARTBEAT_AT, Context.now()));
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

    private static int asInt(Object value) {
        return (int) asLong(value);
    }
}
