package com.akto.action.threat_detection;

import com.akto.dao.GuardrailPoliciesDao;
import com.akto.dao.context.Context;
import com.akto.dao.jobs.AccountJobDao;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.jobs.AccountJob;
import com.akto.dto.jobs.JobStatus;
import com.akto.dto.jobs.ScheduleType;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.utils.elasticsearch.AgentQueryRecord;
import com.akto.utils.guardrails.GuardrailsServiceClient;
import com.akto.utils.guardrails.PromptSnippet;
import com.akto.utils.search.SearchClientFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Compares how many of a policy's recent violations the currently-saved policy catches against how
 * many an edited version would catch, over the same events.
 *
 * <p><b>Why a comparison rather than a per-violation verdict.</b> Stored payloads are anonymized
 * before they are persisted, so asking "would this violation still be caught?" is frequently
 * unanswerable: the triggering text may be gone, and a rule with a high {@code minMatchCount} can be
 * arithmetically unable to fire on what survives. Measured across six production policies, only ~9%
 * of violations were individually re-detectable, so a per-violation answer mostly reported fiction.
 * Running both policies over identical payloads makes that suppression common-mode — it lowers both
 * counts equally and cancels out of the difference.
 *
 * <p><b>Trigger and poll.</b> {@link #startPolicyReplay()} kicks off one background run and returns
 * a run id; {@link #pollPolicyReplay()} reads its progress. The alternative — having the browser
 * drive one request per page — meant ten HTTP round trips per comparison and put the paging loop
 * somewhere it could not be resumed or rate-limited.
 */
public class GuardrailPolicyReplayAction extends AbstractThreatDetectionAction {

    private static final LoggerMaker loggerMaker =
        new LoggerMaker(GuardrailPolicyReplayAction.class, LogDb.DASHBOARD);

    /** Must not exceed maxReplayItems in the guardrails service's replay handler. */
    private static final int PAGE_SIZE = GuardrailsServiceClient.PAGE_SIZE;
    /** Items examined per run, newest first. */
    private static final int MAX_VIOLATIONS = 100;

    /**
     * How far back to look for traces. Violations are fetched with no lower bound because they are
     * scarce; agent traffic is not, so a bounded window keeps the search cheap.
     */
    private static final long TRACE_LOOKBACK_MS = TimeUnit.DAYS.toMillis(30);

    /** Which recent sample to compare over. */
    static final String SOURCE_VIOLATIONS = "VIOLATIONS";
    static final String SOURCE_TRACES = "TRACES";

    /** Cap on prompt rows retained, so a wholesale regression cannot bloat the response. */
    private static final int MAX_MISSED_ROWS = 50;

    /**
     * Bounded so several users comparing at once cannot stampede the scanners. Runs are minutes-long
     * at worst, so a small pool with queuing is the right shape.
     */
    private static final ExecutorService executor = Executors.newFixedThreadPool(2);

    private static final long RUN_TTL_MS = TimeUnit.MINUTES.toMillis(15);
    private static final long BASELINE_TTL_MS = TimeUnit.MINUTES.toMillis(30);

    /** jobType this action schedules for {@link #startPolicyBackfillReplay()}; must match the
     *  key {@code GuardrailPolicyBackfillReplayExecutor} is registered under in
     *  {@code AccountJobExecutorFactory}. */
    public static final String BACKFILL_JOB_TYPE = "GUARDRAIL_POLICY_BACKFILL_REPLAY";

    /**
     * How coarsely the violation window is bucketed for baseline caching, in seconds.
     *
     * <p>The window's upper bound is "now", which differs on every click — including it verbatim in
     * the cache key meant every run missed the cache and re-scanned the saved policy, defeating the
     * cache entirely. Bucketing makes clicks within the same window share an entry; the cost is that
     * a baseline can lag the newest violations by up to one bucket, which is exactly the staleness
     * the TTL already accepts.
     */
    private static final int BASELINE_BUCKET_SECONDS = (int) TimeUnit.MINUTES.toSeconds(30);

    /** In-flight and recently-finished runs, keyed by run id. */
    private static final Map<String, ReplayRun> runs = new ConcurrentHashMap<>();

    /**
     * Baseline detections for the saved policy, keyed by policy version and window bucket.
     *
     * <p>Stores the detected ids rather than a count, so a cached run can still say <em>which</em>
     * violations the draft stopped catching without re-scanning the baseline to rebuild the list.
     * Keyed on {@code updatedTimestamp} so saving the policy invalidates the entry implicitly.
     * In-memory and per-instance on purpose: a miss costs one extra evaluation.
     */
    private static final Map<String, CachedBaseline> baselineCache = new ConcurrentHashMap<>();

    private static class CachedBaseline {
        final Set<String> detectedIds;
        final long storedAtMs;

        CachedBaseline(Set<String> detectedIds, long storedAtMs) {
            this.detectedIds = detectedIds;
            this.storedAtMs = storedAtMs;
        }
    }

    /**
     * One item to compare, normalised so the comparison loop does not care which source it came
     * from: recorded violations or recent agent traffic.
     */
    private static class ReplaySample {
        final String id;
        final String envelope;

        ReplaySample(String id, String envelope) {
            this.id = id;
            this.envelope = envelope;
        }
    }

    /** Mutable progress for one comparison run, read by polling while the worker writes it. */
    private static class ReplayRun {
        volatile String status = "RUNNING"; // RUNNING | DONE | FAILED
        volatile int currentDetected;
        volatile int modifiedDetected;
        volatile int compared;
        volatile int examined;
        volatile boolean baselineFromCache;
        volatile String error;
        final List<BasicDBObject> missed = Collections.synchronizedList(new ArrayList<>());
        final long startedAtMs = System.currentTimeMillis();
    }

    /** The edited policy to evaluate — sent inline, never read from Mongo. */
    @Setter
    private GuardrailPolicies policy;

    /**
     * Name the violations were recorded under. Violations join to a policy by name (a guardrail
     * event's filterId <em>is</em> the policy name), so this must be the policy's pre-edit name when
     * the caller has renamed it.
     */
    @Setter
    private String policyName;

    /** Optional: the saved policy's id, used to load the baseline. Falls back to policyName. */
    @Setter
    private String hexId;

    /**
     * Which sample to compare over: {@code VIOLATIONS} (this policy's recorded violations) or
     * {@code TRACES} (recent agent traffic, whether or not it was blocked).
     *
     * <p>They answer different questions. Violations only contain traffic that already matched, so
     * they can only show detections an edit loses. Traces contain traffic that was never blocked
     * too, and their payloads are not put through the capture-time anonymization that violations
     * are — so the counts mean more.
     */
    @Setter
    private String source;

    @Setter
    @Getter
    private String runId;

    @Getter
    private BasicDBObject replayResult;

    // ---------------------------------------------------------------- backfill replay

    /** Backfill window lower bound, epoch seconds. Defaults to 0 (all history) when unset/&lt;=0. */
    @Setter
    private Integer backfillStartTimestamp;

    /** Backfill window upper bound, epoch seconds. Defaults to "now" when unset/&lt;=0. */
    @Setter
    private Integer backfillEndTimestamp;

    @Getter
    private String backfillJobId;

    // ---------------------------------------------------------------- start

    public String startPolicyReplay() {
        if (policy == null) {
            addActionError("Policy is required");
            return ERROR.toUpperCase();
        }
        if (StringUtils.isBlank(policyName)) {
            addActionError("Policy name is required");
            return ERROR.toUpperCase();
        }

        evictExpiredRuns();

        int accountId = Context.accountId.get();
        CONTEXT_SOURCE contextSource = contextSource();
        // Serialize on the request thread: it reads the saved policy and mutates the draft, and the
        // draft object is request-scoped.
        GuardrailPolicies saved = loadSavedPolicy();
        BasicDBObject editedPayload = GuardrailsServiceClient.serializePolicy(policy, policyName, contextSource);
        // Snap the window's upper bound to the same bucket the baseline cache is keyed on. If this
        // were plain Context.now(), every run would examine a slightly different set of events while
        // looking up ids cached against the previous set: violations arriving between runs would be
        // absent from the cached ids and counted as "the saved policy missed this", so the baseline
        // drifted downward and the cache looked broken. Bucketing both keeps the event set and the
        // cached verdicts describing the same window.
        int endTimestamp = (Context.now() / BASELINE_BUCKET_SECONDS) * BASELINE_BUCKET_SECONDS;
        boolean useTraces = SOURCE_TRACES.equalsIgnoreCase(source);

        // Fail loudly rather than reporting a clean zero: when trace search is not configured every
        // query returns empty, which would render as "your policy catches nothing".
        if (useTraces && !SearchClientFactory.instance().isConfigured()) {
            addActionError("Trace search is not configured for this environment");
            return ERROR.toUpperCase();
        }

        String id = UUID.randomUUID().toString();
        ReplayRun run = new ReplayRun();
        runs.put(id, run);
        this.runId = id;

        executor.submit(() -> {
            // The worker runs outside the request, so the account context has to be re-established
            // or every Mongo and token call resolves against the wrong tenant.
            Context.accountId.set(accountId);
            Context.contextSource.set(contextSource);
            try {
                execute(run, saved, editedPayload, contextSource, endTimestamp, useTraces);
                run.status = "DONE";
            } catch (Exception e) {
                run.status = "FAILED";
                run.error = e.getMessage();
                loggerMaker.errorAndAddToDb(e,
                    "Guardrail comparison run failed for policy " + policyName + ": " + e.getMessage());
            }
        });

        replayResult = new BasicDBObject("runId", id).append("status", "RUNNING");
        return SUCCESS.toUpperCase();
    }

    /**
     * Schedules a background job that replays historical traffic through the currently active,
     * saved policy and, for anything it now catches, records a real guardrail activity
     * (malicious_events, label GUARDRAIL) timestamped at the original traffic time — unlike
     * {@link #startPolicyReplay()}, which only ever reports a comparison and never persists.
     *
     * <p>Runs as one {@link AccountJob} (jobType {@link #BACKFILL_JOB_TYPE}) picked up by
     * {@code AccountJobsCron} in apps/account-job-executor, not the in-process executor above:
     * a backfill can cover a large window and must survive a restart without reprocessing or
     * losing progress, which the ephemeral {@link #executor} here does not guarantee.
     */
    public String startPolicyBackfillReplay() {
        if (StringUtils.isBlank(policyName)) {
            addActionError("Policy name is required");
            return ERROR.toUpperCase();
        }

        GuardrailPolicies saved = loadSavedPolicy();
        if (saved == null || !saved.isActive()) {
            addActionError("An active saved policy named '" + policyName + "' is required to backfill against");
            return ERROR.toUpperCase();
        }

        int now = Context.now();
        int startTs = (backfillStartTimestamp != null && backfillStartTimestamp > 0) ? backfillStartTimestamp : 0;
        int rawEndTs = (backfillEndTimestamp != null && backfillEndTimestamp > 0) ? backfillEndTimestamp : now;
        // Nothing to backfill from the future: a range picker's "All time"/preset upper bound can
        // land past "now", so clamp rather than reject — the practical window is always [start, now].
        int endTs = Math.min(rawEndTs, now);

        if (endTs <= startTs) {
            addActionError("endTimestamp must be after startTimestamp");
            return ERROR.toUpperCase();
        }

        // Computed once, at creation time: a RUN_ONCE job sitting briefly SCHEDULED before being
        // claimed should still cover up through when it was queued, even if the caller's endTs
        // default ("now" at request time) was computed a moment earlier.
        int effectiveEndTimestamp = Math.max(now, endTs);

        // account-job-executor has no direct MongoDB connection (it only talks to Cyborg/HTTP —
        // see its Main.java), so the policy is serialized once here, on the request thread that
        // already has full Mongo access, and carried inline in job.config — exactly the same
        // "sent inline, never read from Mongo" convention startPolicyReplay() already uses for
        // its edited-draft payload. A side effect a long backfill actually wants: the run stays
        // pinned to the policy version that was active when it was queued, even if someone edits
        // the policy again while the backfill is still in progress.
        BasicDBObject policyPayload = GuardrailsServiceClient.serializePolicy(saved, policyName, contextSource());
        // serializePolicy fills in a default contextSource when the policy has none of its own, so
        // this is always a real value after the call above — the caller never chooses it, the
        // policy's own configuration decides.
        String resolvedContextSource = saved.getContextSource().name();

        // Minted once, here, instead of per-call inside the executor: a backfill can make
        // hundreds of guardrails-service/threat-detection-backend calls over its lifetime, and
        // signing a fresh JWT for every one of them is pure waste when a single longer-lived
        // token (verified the same way — RSA signature + accountId claim, nothing subject- or
        // scope-specific) works for the whole job.
        String apiToken;
        try {
            apiToken = GuardrailsServiceClient.createLongLivedAuthToken(
                Collections.singletonList("GUARDRAIL"), 1);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Could not create auth token for backfill job: " + e.getMessage());
            addActionError("Could not start backfill");
            return ERROR.toUpperCase();
        }

        Map<String, Object> config = new HashMap<>();
        config.put("policyName", policyName);
        config.put("policyPayload", policyPayload);
        config.put("contextSource", resolvedContextSource);
        config.put("apiToken", apiToken);
        config.put("startTimestamp", startTs);
        config.put("endTimestamp", endTs);
        config.put("effectiveEndTimestamp", effectiveEndTimestamp);
        // Resumable checkpoint: an opaque Elasticsearch search_after cursor, empty until the
        // executor commits its first page. See GuardrailPolicyBackfillReplayExecutor.
        config.put("searchAfterJson", "");
        config.put("processedCount", 0);
        config.put("detectedCount", 0);
        config.put("lastBatchError", null);

        AccountJob accountJob = new AccountJob(
            Context.accountId.get(),
            BACKFILL_JOB_TYPE,
            policyName,
            config,
            0,      // recurringIntervalSeconds: this is a RUN_ONCE job
            now,
            now
        );
        accountJob.setJobStatus(JobStatus.SCHEDULED);
        accountJob.setScheduleType(ScheduleType.RUN_ONCE);
        accountJob.setScheduledAt(now);
        accountJob.setHeartbeatAt(0);
        accountJob.setStartedAt(0);
        accountJob.setFinishedAt(0);

        AccountJobDao.instance.insertOne(accountJob);
        backfillJobId = accountJob.getId().toHexString();
        loggerMaker.info("Created guardrail backfill replay job " + backfillJobId
            + " for policy " + policyName + " window=[" + startTs + "," + endTs + "]");

        replayResult = new BasicDBObject("jobId", backfillJobId).append("status", "SCHEDULED");
        return SUCCESS.toUpperCase();
    }

    // ---------------------------------------------------------------- poll

    public String pollPolicyReplay() {
        if (StringUtils.isBlank(runId)) {
            addActionError("runId is required");
            return ERROR.toUpperCase();
        }
        ReplayRun run = runs.get(runId);
        if (run == null) {
            // Expired or unknown: tell the client to start over rather than hang on a dead id.
            replayResult = new BasicDBObject("status", "EXPIRED");
            return SUCCESS.toUpperCase();
        }

        List<BasicDBObject> missedSnapshot;
        synchronized (run.missed) {
            missedSnapshot = new ArrayList<>(run.missed);
        }

        replayResult = new BasicDBObject()
            .append("status", run.status)
            .append("examined", run.examined)
            .append("compared", run.compared)
            .append("currentDetected", run.currentDetected)
            .append("modifiedDetected", run.modifiedDetected)
            .append("missedByDraft", missedSnapshot)
            .append("baselineFromCache", run.baselineFromCache)
            .append("error", run.error == null ? "" : run.error);
        return SUCCESS.toUpperCase();
    }

    // ---------------------------------------------------------------- worker

    /** Walks the violation window a page at a time, updating {@code run} as it goes. */
    private void execute(ReplayRun run, GuardrailPolicies saved, BasicDBObject editedPayload,
                         CONTEXT_SOURCE contextSource, int endTimestamp, boolean useTraces) throws Exception {
        // filterId == the policy's name is the join, and is already exact: only the guardrails flow
        // writes events under a guardrail policy's name. Deliberately NOT filtered by label —
        // production guardrail events are recorded as THREAT, not GUARDRAIL.
        Map<String, Object> filters = new HashMap<>();
        filters.put("latestAttack", Collections.singletonList(policyName));

        String cacheKey = baselineCacheKey(saved, endTimestamp, useTraces);
        Set<String> cachedBaselineIds = readCachedBaseline(cacheKey);
        run.baselineFromCache = cachedBaselineIds != null;
        loggerMaker.info("Baseline cache " + (cachedBaselineIds == null ? "MISS" : "HIT")
            + " key=" + cacheKey
            + (cachedBaselineIds == null ? "" : " cachedIds=" + cachedBaselineIds.size()));

        BasicDBObject baselinePayload = cachedBaselineIds != null ? null : serializeBaseline(saved);
        Set<String> baselineIds = new LinkedHashSet<>();

        // One fetch for the whole window, newest first. The guardrails service caps a replay
        // request at PAGE_SIZE items, but that limits the *evaluation* call, not reading the
        // sample — so this pages the POSTs, not the fetch.
        List<ReplaySample> allSamples = useTraces
            ? fetchTraceSamples(endTimestamp)
            : fetchViolationSamples(endTimestamp, filters);

        for (int from = 0; from < allSamples.size(); from += PAGE_SIZE) {
            List<ReplaySample> batch =
                allSamples.subList(from, Math.min(from + PAGE_SIZE, allSamples.size()));
            run.examined += batch.size();

            Map<String, String> promptById = new HashMap<>();
            List<BasicDBObject> items = new ArrayList<>();
            for (ReplaySample sample : batch) {
                promptById.put(sample.id, PromptSnippet.of(sample.envelope));
                items.add(new BasicDBObject("id", sample.id).append("envelope", sample.envelope));
            }

            if (!items.isEmpty()) {
                for (JsonNode verdict : GuardrailsServiceClient.replay(
                        items, editedPayload, baselinePayload, contextSource.name())) {
                    if (!verdict.path("skipReason").asText("").isEmpty()) {
                        continue;
                    }
                    run.compared++;
                    String id = verdict.path("id").asText("");
                    boolean nowDetected = verdict.path("detected").asBoolean(false);
                    if (nowDetected) {
                        run.modifiedDetected++;
                    }
                    boolean wasDetected = cachedBaselineIds != null
                        ? cachedBaselineIds.contains(id)
                        : verdict.path("baselineDetected").asBoolean(false);
                    if (wasDetected) {
                        baselineIds.add(id);
                        run.currentDetected++;
                        if (!nowDetected && run.missed.size() < MAX_MISSED_ROWS) {
                            run.missed.add(new BasicDBObject("id", id)
                                .append("prompt", promptById.getOrDefault(id, "")));
                        }
                    }
                }
            }

        }

        if (baselinePayload != null && cacheKey != null) {
            baselineCache.put(cacheKey, new CachedBaseline(baselineIds, System.currentTimeMillis()));
        }

        loggerMaker.info("Compared guardrail violations for policy " + policyName
            + " examined=" + run.examined + " compared=" + run.compared
            + " current=" + run.currentDetected + " modified=" + run.modifiedDetected
            + " missed=" + run.missed.size() + " baselineCached=" + run.baselineFromCache);
    }

    // ---------------------------------------------------------------- helpers

    // ------------------------------------------------- sources

    /** This policy's recorded violations, newest first. Rows with no stored payload are dropped. */
    private List<ReplaySample> fetchViolationSamples(int endTimestamp, Map<String, Object> filters) {
        List<ReplaySample> out = new ArrayList<>();
        for (DashboardMaliciousEvent event : fetchAllMaliciousEvents(0, endTimestamp, MAX_VIOLATIONS, filters)) {
            if (StringUtils.isBlank(event.getPayload())) {
                continue;
            }
            out.add(new ReplaySample(event.getId(), event.getPayload()));
        }
        return out;
    }

    /**
     * Recent agent traffic, newest first, whether or not it was blocked.
     *
     * <p>Uses {@code fetchMessages}, which aggregates by traceId and returns one row per trace
     * carrying that trace's first prompt. That de-duplication is wanted here: a single chatty
     * session should not dominate the sample the way it would with a flat per-message fetch.
     *
     * <p>No session filter is passed, so this spans recent traffic account-wide. The client returns
     * up to 500 trace buckets ordered newest-first and has no limit parameter, so the cap is applied
     * here.
     *
     * <p>Goes through {@link SearchClientFactory} rather than Elasticsearch directly so accounts on
     * the Azure Data Explorer backend work too.
     */
    private List<ReplaySample> fetchTraceSamples(int endTimestamp) {
        long endMs = endTimestamp * 1000L;
        List<Map<String, Object>> rows = SearchClientFactory.instance().fetchMessages(
            Context.accountId.get(), endMs - TRACE_LOOKBACK_MS, endMs,
            null,   // no filters: not scoped to a session, user or service
            null);  // atlasTrafficFilter unset: include both

        List<ReplaySample> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (out.size() >= MAX_VIOLATIONS) {
                break;
            }
            String prompt = asText(row.get(AgentQueryRecord.F_QUERY_PAYLOAD));
            if (StringUtils.isBlank(prompt)) {
                continue;
            }
            // traceId is the identifier: these rows are one-per-trace and carry no document id.
            String id = asText(row.get(AgentQueryRecord.F_TRACE_ID));
            if (StringUtils.isBlank(id)) {
                id = "trace-" + out.size();
            }
            out.add(new ReplaySample(id,
                GuardrailsServiceClient.traceEnvelope(prompt, asText(row.get(AgentQueryRecord.F_RESPONSE_PAYLOAD)))));
        }
        return out;
    }

    private static String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** The saved version of the policy being edited, or null when it cannot be resolved. */
    private GuardrailPolicies loadSavedPolicy() {
        try {
            if (StringUtils.isNotBlank(hexId) && ObjectId.isValid(hexId)) {
                GuardrailPolicies byId = GuardrailPoliciesDao.instance.findOne(
                    Filters.eq(Constants.ID, new ObjectId(hexId)));
                if (byId != null) {
                    return byId;
                }
            }
            return GuardrailPoliciesDao.instance.findOne(Filters.eq("name", policyName));
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Could not load saved policy for baseline: " + policyName);
            return null;
        }
    }

    /** Null when there is no saved policy — a brand-new policy has no baseline to compare against. */
    private BasicDBObject serializeBaseline(GuardrailPolicies saved) {
        return saved == null ? null : GuardrailsServiceClient.serializePolicy(saved, policyName, contextSource());
    }

    private String baselineCacheKey(GuardrailPolicies saved, int endTimestamp, boolean useTraces) {
        if (saved == null) {
            return null;
        }
        // endTimestamp is already snapped to a bucket boundary by the caller, so it identifies the
        // window exactly rather than approximately.
        // The source is part of the key: the two samples carry different id spaces (violation ids
        // vs traceIds), so sharing an entry would apply one sample's baseline ids to the other's
        // items, match nothing, and report a regression that never happened.
        return Context.accountId.get() + "|" + policyName + "|" + saved.getUpdatedTimestamp()
            + "|" + endTimestamp
            + "|" + (useTraces ? SOURCE_TRACES : SOURCE_VIOLATIONS);
    }

    private Set<String> readCachedBaseline(String key) {
        if (key == null) {
            return null;
        }
        CachedBaseline hit = baselineCache.get(key);
        if (hit == null) {
            return null;
        }
        if (System.currentTimeMillis() - hit.storedAtMs > BASELINE_TTL_MS) {
            baselineCache.remove(key);
            return null;
        }
        return hit.detectedIds;
    }

    /** Runs are per-draft and short-lived; drop finished ones so the map cannot grow without bound. */
    private static void evictExpiredRuns() {
        long now = System.currentTimeMillis();
        runs.entrySet().removeIf(e -> now - e.getValue().startedAtMs > RUN_TTL_MS);
    }

    /** The context source to evaluate under, defaulting to AGENTIC when the request carries none. */
    private static CONTEXT_SOURCE contextSource() {
        CONTEXT_SOURCE fromRequest = Context.contextSource.get();
        return fromRequest != null ? fromRequest : CONTEXT_SOURCE.AGENTIC;
    }
}
