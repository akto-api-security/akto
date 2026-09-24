package com.akto.service.posture;

import com.akto.dao.context.Context;
import com.akto.dao.insights.InsightNarrativeCacheDao;
import com.akto.dto.insights.InsightNarrativeCache;
import com.akto.gpt.handlers.gpt_prompts.InsightNarrativeHandler;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.service.insights.InsightResult;
import com.akto.service.insights.InsightUtil;
import com.mongodb.BasicDBObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * AI narrative for a posture drilldown level — the same cache and the same
 * {@link InsightNarrativeHandler} InsightService already uses for insight detail narratives
 * (see InsightService#generateAndCacheNarrative), generalized so every one of the five
 * drilldown panels' every level (not just insights) gets one, without a parallel DTO/handler
 * family.
 *
 * Deliberately NOT part of {@link PostureService}: that class is a pure function over its
 * inputs (no Mongo, no HTTP — see its own javadoc) so its 26-test unit suite never touches a
 * real cache or a real LLM call. This class is the one place in the posture package allowed to
 * do either, same split InsightService (orchestration + narrative) already draws against its own
 * pure InsightProviders.
 *
 * Unlike InsightService's narrative generation (which blocks the request on the LLM call), this
 * is fire-and-forget: {@link #attachNarrative} always returns immediately. A cache hit attaches
 * the prose synchronously; a cache miss marks the result PENDING and submits the actual
 * generation to a background executor, so a slow/rate-limited LLM call never adds latency to
 * fetchPostureDrill's own response. The frontend re-fetches the same drillId/path a few seconds
 * later and gets OK once the background job has written the cache.
 */
public class PostureDrillNarrativeService {

    private static final LoggerMaker logger = new LoggerMaker(PostureDrillNarrativeService.class, LogDb.DASHBOARD);
    // Matches InsightService's own NARRATIVE_TTL_DAYS — a GC backstop, not the real invalidation
    // (the fingerprint below already changes whenever the underlying data does).
    private static final long NARRATIVE_TTL_DAYS = 7;
    // Posture drills have no "provider" the way insights do (InsightProvider#providerVersion) —
    // this is the one knob to bump if buildNarrativeInput's own shape changes meaningfully enough
    // that old cached prose should stop being served.
    private static final int DRILL_NARRATIVE_INPUT_VERSION = 1;
    private static final int EVIDENCE_ROW_CAP = 20;

    // Small, separate from SecurityPostureAction's own request-scoped EXECUTOR: those futures are
    // always awaited with a timeout inside the same request; narrative jobs deliberately outlive
    // it, so mixing the two pools would let a slow LLM call eat a worker fetchPostureDrill itself
    // needs.
    private static final ExecutorService NARRATIVE_EXECUTOR = Executors.newFixedThreadPool(4);

    // Guards against a thundering herd for the same fingerprint: every fetchPostureDrill call for
    // an as-yet-uncached level submits a generation job, and nothing else stops N near-simultaneous
    // requests (a poll every 3s from one open flyout, plus another tab, plus a re-click) from each
    // submitting their own — this was a real, observed bug (multiple pool threads all calling the
    // LLM for the identical EVIDENCE at once). A fingerprint already being generated is skipped;
    // whoever's already in flight will populate the cache for everyone.
    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private PostureDrillNarrativeService() {}

    /** Attaches whatever's already cached (OK) or marks PENDING and kicks off generation in the
     *  background — never blocks on the LLM call. No-op (leaves the UNAVAILABLE default) when
     *  this level has nothing to narrate (no summary, no rows, no gaps). */
    public static void attachNarrative(PostureDrillResult result, int accountId, String drillId, String path) {
        if (result == null) return;
        try {
            BasicDBObject narrativeInput = buildNarrativeInput(result, drillId, path);
            if (isEmpty(narrativeInput)) return; // nothing grounded to say — not worth an LLM call

            String fingerprint = fingerprint(accountId, drillId, path, narrativeInput);
            InsightNarrativeCache cached = InsightNarrativeCacheDao.instance.get(fingerprint);
            if (cached != null) {
                applyCached(result, cached);
                return;
            }

            result.setNarrativeStatus("PENDING");
            if (!IN_FLIGHT.add(fingerprint)) return; // someone else is already generating this exact level

            final int capturedAccountId = accountId;
            NARRATIVE_EXECUTOR.submit(withAccountContext(capturedAccountId, () -> {
                try {
                    generateAndCache(drillId, path, narrativeInput, fingerprint);
                } finally {
                    IN_FLIGHT.remove(fingerprint);
                }
                return null;
            }));
        } catch (Exception e) {
            logger.errorAndAddToDb("PostureDrillNarrativeService: failed to attach narrative for "
                    + drillId + ": " + e.getMessage());
            result.setNarrativeStatus("UNAVAILABLE");
        }
    }

    private static boolean isEmpty(BasicDBObject narrativeInput) {
        List<?> metrics = (List<?>) narrativeInput.get("metrics");
        List<?> evidence = (List<?>) narrativeInput.get("evidence");
        List<?> gaps = (List<?>) narrativeInput.get("dataGaps");
        return metrics.isEmpty() && evidence.isEmpty() && gaps.isEmpty();
    }

    private static void applyCached(PostureDrillResult result, InsightNarrativeCache cached) {
        result.setNarrativeStatus("OK");
        result.setNarrativeMarkdown(cached.getNarrativeMarkdown());
        result.setNarrativeConcern(cached.getNarrativeConcern());
        result.setNarrativeImpact(cached.getNarrativeImpact());
        result.setNarrativeRemediation(cached.getNarrativeRemediation());
    }

    private static void generateAndCache(String drillId, String path, BasicDBObject narrativeInput, String fingerprint) {
        try {
            BasicDBObject input = new BasicDBObject(InsightNarrativeHandler.NARRATIVE_INPUT, narrativeInput.toJson());
            BasicDBObject out = new InsightNarrativeHandler().handle(input);
            if (out.containsField("error")) {
                // Deliberately left uncached (not even as an UNAVAILABLE sentinel) — the next
                // request for this same level just retries generation, self-healing rather than
                // wedging a transient failure (a rate limit, a timeout) in place for NARRATIVE_TTL_DAYS.
                logger.error("PostureDrillNarrativeService: handler failed for " + drillId + ": " + out.getString("error"));
                return;
            }

            long now = System.currentTimeMillis() / 1000;
            String label = "posture-drill:" + drillId + (path == null || path.isEmpty() ? "" : ":" + path);
            InsightNarrativeCache cache = new InsightNarrativeCache(fingerprint, label,
                    DRILL_NARRATIVE_INPUT_VERSION, InsightNarrativeHandler.PROMPT_VERSION,
                    out.getString("markdown"), out.getString("concern"), out.getString("impact"), out.getString("remediation"),
                    now, new Date((now + TimeUnit.DAYS.toSeconds(NARRATIVE_TTL_DAYS)) * 1000L));
            InsightNarrativeCacheDao.instance.put(cache);
        } catch (Exception e) {
            logger.errorAndAddToDb("PostureDrillNarrativeService: generation failed for " + drillId + ": " + e.getMessage());
        }
    }

    /** Same shape InsightService#buildNarrativeInput sends the model — metrics/evidence/caveats/
     *  dataGaps — sourced from a PostureDrillResult's own summary/rows/dataGaps instead of an
     *  InsightResult's. No draftConcern/Impact/Remediation: unlike insights, a drill has no
     *  provider-computed draft to ground — the model writes those fields from EVIDENCE alone. */
    private static BasicDBObject buildNarrativeInput(PostureDrillResult r, String drillId, String path) {
        List<BasicDBObject> metrics = new ArrayList<>();
        for (InsightResult.Metric m : r.getSummary()) {
            metrics.add(new BasicDBObject("key", m.getKey()).append("label", m.getLabel()).append("formatted", m.getFormatted()));
        }

        List<BasicDBObject> evidence = new ArrayList<>();
        List<Map<String, Object>> rows = r.getRows();
        if (rows != null && !rows.isEmpty()) {
            List<Map<String, Object>> capped = rows.size() > EVIDENCE_ROW_CAP ? rows.subList(0, EVIDENCE_ROW_CAP) : rows;
            evidence.add(new BasicDBObject("id", "rows").append("title", r.getTitle())
                    .append("rows", capped).append("totalRowCount", r.getTotal()));
        }

        List<BasicDBObject> gaps = new ArrayList<>();
        for (InsightResult.Gap g : r.getDataGaps()) {
            gaps.add(new BasicDBObject("source", g.getSource()).append("reason", g.getReason()).append("impact", g.getImpact()));
        }

        return new BasicDBObject("drillId", drillId)
                .append("path", path == null ? "" : path)
                .append("metrics", metrics)
                .append("evidence", evidence)
                .append("caveats", new ArrayList<>())
                .append("dataGaps", gaps)
                .append("draftConcern", "")
                .append("draftImpact", "")
                .append("draftRemediation", "");
    }

    /** Over the exact bytes sent to the LLM, same as InsightService#fingerprint — a changed row,
     *  metric, or gap changes the key, so stale prose can never outlive the data it describes. */
    private static String fingerprint(int accountId, String drillId, String path, BasicDBObject narrativeInput) {
        String raw = accountId + "|posture-drill|" + drillId + "|" + (path == null ? "" : path) + "|"
                + DRILL_NARRATIVE_INPUT_VERSION + "|" + InsightNarrativeHandler.PROMPT_VERSION + "|" + narrativeInput.toJson();
        return InsightUtil.md5(raw);
    }

    /** InsightNarrativeCacheDao is an AccountsContextDao — its collection lives in the
     *  per-account DB named after Context.accountId.get(), so a background task that outlives the
     *  request thread must re-set it itself (same convention SecurityPostureAction's own
     *  withContext / InsightDataLoader's already follow). */
    private static Callable<Void> withAccountContext(int accountId, Callable<Void> body) {
        return () -> {
            Context.accountId.set(accountId);
            try {
                return body.call();
            } finally {
                Context.accountId.remove();
            }
        };
    }
}
