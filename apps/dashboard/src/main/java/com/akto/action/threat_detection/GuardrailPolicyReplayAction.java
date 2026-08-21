package com.akto.action.threat_detection;

import com.akto.dao.GuardrailPoliciesDao;
import com.akto.dao.context.Context;
import com.akto.database_abstractor_authenticator.JwtAuthenticator;
import com.akto.dto.EnterpriseLicenseComplianceCatalog;
import com.akto.dto.GuardrailPolicies;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.util.http_util.CoreHTTPClient;
import com.akto.utils.elasticsearch.AgentQueryRecord;
import com.akto.utils.guardrails.PromptSnippet;
import com.akto.utils.search.SearchClient;
import com.akto.utils.search.SearchClientFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

import lombok.Getter;
import lombok.Setter;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Calendar;
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
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // A page evaluates every item against up to two policies, each of which can reach the LLM
    // scanners, so allow a generous read timeout; the guardrails service bounds each evaluation.
    private static final OkHttpClient httpClient = CoreHTTPClient.client.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build();

    /** Must not exceed maxReplayItems in the guardrails service's replay handler. */
    private static final int PAGE_SIZE = 25;
    /** Items examined per run, newest first. */
    private static final int MAX_VIOLATIONS = 100;

    /**
     * How far back to look for traces. Violations are fetched with no lower bound because they are
     * scarce; agent traffic is not, so a bounded window keeps the search cheap.
     */
    private static final long TRACE_LOOKBACK_MS = TimeUnit.DAYS.toMillis(30);

    /**
     * Placeholder request line for a trace-derived envelope. AgentQueryRecord records the prompt but
     * not the HTTP method/path, so there is nothing real to put here; with no field mapping matching
     * this path the guardrails service scans the raw payload, which is what we want.
     */
    private static final String TRACE_METHOD = "POST";
    private static final String TRACE_PATH = "/v1/messages";
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

    /** Account whose guardrails traffic is served by the shared ingest host rather than its own. */
    private static final int SHARED_INGEST_ACCOUNT_ID = 1768175789;
    private static final int TOKEN_VALIDITY_MINUTES = 120;

    private static final long RUN_TTL_MS = TimeUnit.MINUTES.toMillis(15);
    private static final long BASELINE_TTL_MS = TimeUnit.MINUTES.toMillis(30);

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
        BasicDBObject editedPayload = serializePolicy(policy, policyName);
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

        String cacheKey = baselineCacheKey(saved, endTimestamp);
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
                for (JsonNode verdict : replay(items, editedPayload, baselinePayload, contextSource)) {
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

    /** POSTs one page to the guardrails service and returns the verdict nodes. */
    private Iterable<JsonNode> replay(List<BasicDBObject> items, BasicDBObject editedPayload,
                                      BasicDBObject baselinePayload, CONTEXT_SOURCE contextSource) throws Exception {
        BasicDBObject body = new BasicDBObject()
            .append("policy", editedPayload)
            .append("contextSource", contextSource.name())
            .append("items", items);
        if (baselinePayload != null) {
            body.append("baselinePolicy", baselinePayload);
        }

        Request request = new Request.Builder()
            .url(guardrailsBaseUrl() + "/api/validate/replayWithPolicy")
            .post(RequestBody.create(body.toJson(), MediaType.parse("application/json")))
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", guardrailsAuthToken())
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String raw = responseBody != null ? responseBody.string() : "";
            if (!response.isSuccessful()) {
                throw new IllegalStateException(
                    "guardrails service returned " + response.code() + ": " + raw);
            }
            return objectMapper.readTree(raw).path("verdicts");
        }
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
            traceAccountId(), endMs - TRACE_LOOKBACK_MS, endMs,
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
                traceEnvelope(prompt, asText(row.get(AgentQueryRecord.F_RESPONSE_PAYLOAD)))));
        }
        return out;
    }

    /**
     * Wraps a recorded prompt in the stored-traffic envelope the guardrails service already parses.
     *
     * <p>{@code queryPayload} is passed through untouched because it is <em>already</em> the request
     * payload JSON — real rows look like {@code {"body": ...}} or {@code {"body":..., "toolName":...}},
     * the same shape live gateway traffic has. Wrapping it again would bury the prompt one level
     * deeper than any field mapping or extractor looks.
     */
    private static String traceEnvelope(String requestPayload, String responsePayload) {
        return new BasicDBObject()
            .append("method", TRACE_METHOD)
            .append("path", TRACE_PATH)
            .append("requestPayload", requestPayload)
            .append("responsePayload", responsePayload == null ? "" : responsePayload)
            .toJson();
    }

    /**
     * Account whose traffic the trace search reads.
     *
     * <p>Normally the caller's own account. {@code GUARDRAILS_TRACE_ACCOUNT_ID} overrides it, which
     * exists only so a local dashboard — whose account has no ingested traffic — can be pointed at
     * an account that does, to verify the comparison end to end. It makes the numbers meaningless
     * (the policy comes from one account, the traffic from another), so it must not be set outside
     * that check.
     */
    private static int traceAccountId() {
        String override = System.getenv("GUARDRAILS_TRACE_ACCOUNT_ID");
        if (StringUtils.isNotBlank(override)) {
            try {
                int parsed = Integer.parseInt(override.trim());
                loggerMaker.warn("GUARDRAILS_TRACE_ACCOUNT_ID is set: reading traces from account "
                    + parsed + " instead of " + Context.accountId.get() + " — counts are not meaningful");
                return parsed;
            } catch (NumberFormatException e) {
                loggerMaker.error("GUARDRAILS_TRACE_ACCOUNT_ID is not a number: " + override);
            }
        }
        return Context.accountId.get();
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
        return saved == null ? null : serializePolicy(saved, policyName);
    }

    private String baselineCacheKey(GuardrailPolicies saved, int endTimestamp) {
        if (saved == null) {
            return null;
        }
        // endTimestamp is already snapped to a bucket boundary by the caller, so it identifies the
        // window exactly rather than approximately.
        return Context.accountId.get() + "|" + policyName + "|" + saved.getUpdatedTimestamp()
            + "|" + endTimestamp;
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

    // ------------------------------------------------- guardrails service transport

    /**
     * Base URL of the calling account's guardrails service, without a trailing slash.
     *
     * <p>{@code GUARDRAILS_SERVICE_URL} overrides the per-account host — set it to point a local or
     * self-hosted dashboard at a specific guardrails service.
     */
    private static String guardrailsBaseUrl() {
        String override = System.getenv("GUARDRAILS_SERVICE_URL");
        if (StringUtils.isNotBlank(override)) {
            return StringUtils.stripEnd(override.trim(), "/");
        }
        int accountId = Context.accountId.get();
        if (accountId == SHARED_INGEST_ACCOUNT_ID) {
            return "https://ingest.akto.io";
        }
        return "https://" + accountId + "-guardrails.akto.io";
    }

    /** Short-lived JWT authenticating the dashboard to the guardrails service. */
    private static String guardrailsAuthToken() throws Exception {
        Map<String, Object> claims = new HashMap<>();
        claims.put("accountId", Context.accountId.get());
        return JwtAuthenticator.createJWT(claims, "Akto", "invite_user", Calendar.MINUTE, TOKEN_VALIDITY_MINUTES);
    }

    /** The context source to evaluate under, defaulting to AGENTIC when the request carries none. */
    private static CONTEXT_SOURCE contextSource() {
        CONTEXT_SOURCE fromRequest = Context.contextSource.get();
        return fromRequest != null ? fromRequest : CONTEXT_SOURCE.AGENTIC;
    }

    /**
     * Serializes a policy for transmission to the guardrails service, dropping Mongo-internal fields
     * and forcing {@code active} on — an inactive policy would allow everything, making the
     * comparison meaningless.
     *
     * <p>Mutates {@code policy}: expands enterprise-licence categories into denied topics and fills
     * in {@code contextSource} / {@code applyOnRequest} when unset, so both sides of the comparison
     * are shaped identically.
     *
     * @param fallbackName used when the policy has no name of its own (unsaved drafts)
     */
    private static BasicDBObject serializePolicy(GuardrailPolicies policy, String fallbackName) {
        policy.setActive(true);
        if (policy.getContextSource() == null) {
            policy.setContextSource(contextSource());
        }
        // Request validation is the common case; a policy targeting neither side would no-op.
        if (!policy.isApplyOnRequest() && !policy.isApplyOnResponse()) {
            policy.setApplyOnRequest(true);
        }
        EnterpriseLicenseComplianceCatalog.applyToPolicy(policy);

        @SuppressWarnings("unchecked")
        Map<String, Object> policyMap = objectMapper.convertValue(policy, Map.class);

        policyMap.remove("id");
        policyMap.remove("hexId");
        policyMap.remove("createdTimestamp");
        policyMap.remove("updatedTimestamp");
        policyMap.remove("createdBy");
        policyMap.remove("updatedBy");

        String name = policy.getName();
        if (StringUtils.isBlank(name)) {
            name = fallbackName;
        }
        policyMap.put("name", name);
        policyMap.put("active", true);
        if (policy.getContextSource() != null) {
            policyMap.put("contextSource", policy.getContextSource().name());
        }
        policyMap.put("policyVersion", "1.0");

        return new BasicDBObject(policyMap);
    }
}
