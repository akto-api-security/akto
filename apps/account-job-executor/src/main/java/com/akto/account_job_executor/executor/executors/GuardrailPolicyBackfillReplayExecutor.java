package com.akto.account_job_executor.executor.executors;

import com.akto.account_job_executor.client.CyborgApiClient;
import com.akto.dto.jobs.AccountJob;
import com.akto.account_job_executor.executor.AccountJobExecutor;
import com.akto.jobs.exception.RetryableJobException;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.utils.guardrails.GuardrailBackfillReplayRunner;
import com.akto.utils.guardrails.GuardrailsServiceClient;
import com.mongodb.BasicDBObject;

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

        // contextSource is not an indexed Elasticsearch field — there is nothing to filter on here
        // beyond the time range. contextSourceForEvents is used only to tag the malicious_events
        // records this job writes. The actual fetch/replay/record cycle for one page lives in
        // GuardrailBackfillReplayRunner (libs/utils) — see its javadoc for why searchPrompts is
        // used over fetchMessages (no hard cap on how much of the range can be covered) and why a
        // failed page persists no partial progress (retrying it is safe and re-does it in full).
        String bearerToken = "Bearer " + rawApiToken;
        int pageIndex = 0;
        while (true) {
            GuardrailBackfillReplayRunner.PageResult result;
            try {
                result = GuardrailBackfillReplayRunner.runOnePage(accountId, policyPayload, policyName,
                    contextSourceForEvents, bearerToken, startTimestampMs, effectiveEndTimestampMs,
                    searchAfterJson, atlasTrafficFilter);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Guardrail backfill job " + job.getId() + ": page " + (pageIndex + 1)
                    + " failed (cursor unchanged, will retry in full) - " + e.getMessage());
                persistCheckpoint(job.getId(), config, searchAfterJson, processedCount, detectedCount, e.getMessage());
                throw new RetryableJobException("backfill page failed: " + e.getMessage(), e);
            }

            pageIndex++;
            if (pageIndex == 1) {
                loggerMaker.warn("Guardrail backfill job " + job.getId() + ": " + result.total
                    + " total messages in range [" + startTimestampMs + ", " + effectiveEndTimestampMs + ")");
            }
            if (result.batch.isEmpty()) {
                break;
            }

            processedCount += result.replayed;
            detectedCount += result.detected;
            loggerMaker.warn("Guardrail backfill job " + job.getId() + ": batch " + pageIndex
                + " picked up (" + result.batch.size() + " records), processed - replayed=" + result.replayed
                + " detected=" + result.detected + " skipped=" + result.skipped
                + " (running totals: replayed=" + processedCount + " detected=" + detectedCount + ")");

            searchAfterJson = result.nextSearchAfterJson;
            persistCheckpoint(job.getId(), config, searchAfterJson, processedCount, detectedCount, null);
            updateJobHeartbeat(job);

            if (result.batch.size() < GuardrailsServiceClient.PAGE_SIZE) {
                break; // short page: nothing left in range
            }
        }

        long timeTakenMs = System.currentTimeMillis() - jobStartMs;
        loggerMaker.warn("Guardrail backfill replay job " + job.getId() + " completed for policy " + policyName
            + ": totalReplayed=" + processedCount + " totalDetected=" + detectedCount
            + " timeTakenMs=" + timeTakenMs);
    }

    /**
     * Persists progress by updating the checkpoint keys on a copy of the job's <em>full</em>
     * original config and writing that whole map back — {@code CyborgApiClient.updateJob} does a
     * plain field replace, not a per-key merge, so sending only the checkpoint keys here would
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

        Map<String, Object> updates = new HashMap<>();
        updates.put(AccountJob.CONFIG, config);
        CyborgApiClient.updateJob(jobId, updates);
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
