package com.akto.service.posture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.akto.dao.GuardrailPoliciesDao;
import com.akto.dao.context.Context;
import com.akto.dao.threat_detection.ComplianceClauseCoverageDao;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.threat_detection.ComplianceClauseCoverage;
import com.akto.dto.threat_detection.ComplianceClauseCoverage.ClauseHit;
import com.akto.dto.threat_detection.ComplianceClauseCoverage.SuggestedClause;
import com.akto.gpt.handlers.gpt_prompts.GuardrailClauseAttributionHandler;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListGuardrailViolationPayloadsResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListGuardrailViolationPayloadsResponse.ViolationPayload;
import com.akto.util.compliance.ComplianceSubClauseCatalog;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.utils.threat_detection.ThreatDetectionBackendClient;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

/**
 * Worker for ComplianceClauseScanAction's fire-and-forget background job: reads real
 * guardrail-violation payloads (via the list_guardrail_violation_payloads endpoint), feeds them in
 * small batches to GuardrailClauseAttributionHandler, and stores which of each framework's
 * ComplianceSubClauseCatalog sub-clauses were actually observed. PostureService#frameworkReadiness
 * reads the result. There is no run registry or poll endpoint — progress is logged (LoggerMaker,
 * LogDb.DASHBOARD) as the scan proceeds; the stored ComplianceClauseCoverage docs are themselves
 * the durable record of outcome.
 *
 * Lives in the posture package (not action/threat_detection, alongside its caller) specifically to
 * reuse PostureService#policyEnforcing / #policyHasComplianceMapping — the exact same "enforcing
 * and mapped to a framework" definition RiskScoreCalculator's compliance sub-score already depends
 * on, so this scan can't quietly disagree with either.
 */
public class ComplianceClauseScanService {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ComplianceClauseScanService.class, LogDb.DASHBOARD);

    /** Mongo page size for the payload endpoint — independent of the LLM batch size below. */
    private static final int PAGE_LIMIT = 200;
    /** Must not exceed GuardrailClauseAttributionHandler's own per-call cap. */
    private static final int LLM_BATCH_SIZE = 10;
    /** Hard stop per framework so a very high-volume account can't turn one scan into an unbounded
     *  loop of LLM calls; the next scan simply picks up a fresher window. */
    private static final int MAX_PAGES_PER_FRAMEWORK = 200;
    /** Cap on retained hits per clause — only the distinct-clause count drives the readiness %,
     *  so unbounded growth buys nothing but Mongo document size. */
    private static final int MAX_HITS_PER_CLAUSE = 500;
    /** Cap on retained catalog-improvement suggestions per framework per scan — these are a
     *  curation hint, not something that needs every occurrence kept. */
    private static final int MAX_SUGGESTIONS_PER_FRAMEWORK = 100;

    private ComplianceClauseScanService() {}

    /** Entry point for ComplianceClauseScanAction's worker lambda. */
    public static void run(int accountId, CONTEXT_SOURCE contextSource, int startTimestamp, int endTimestamp) {
        long runStartMs = System.currentTimeMillis();
        List<GuardrailPolicies> policies = GuardrailPoliciesDao.instance.findAllSortedByCreatedTimestamp(0, 5000);

        // framework -> distinct policy names (== event filterId) mapped to it, restricted to
        // policies that are active/enabled AND actually carry a compliance mapping — the same
        // definition frameworkReadiness/complianceGapsSubScore already use.
        Map<String, Set<String>> policyNamesByFramework = new LinkedHashMap<>();
        for (GuardrailPolicies p : policies) {
            if (!PostureService.policyHasComplianceMapping(p)) continue;
            for (String rawFramework : p.getLlmRule().getCompliance().keySet()) {
                String canonical = ComplianceSubClauseCatalog.canonicalFramework(rawFramework);
                if (canonical == null) continue; // not a framework this catalog knows sub-clauses for
                policyNamesByFramework.computeIfAbsent(canonical, k -> new LinkedHashSet<>()).add(p.getName());
            }
        }

        loggerMaker.infoAndAddToDb(String.format(
                "ComplianceClauseScan[account=%d] starting: %d framework(s) mapped from %d polic(y/ies), window=[%d,%d]",
                accountId, policyNamesByFramework.size(), policies.size(), startTimestamp, endTimestamp));

        int frameworksSucceeded = 0;
        int totalExaminedAcrossRun = 0;
        int totalHitSamplesAcrossRun = 0;
        for (Map.Entry<String, Set<String>> entry : policyNamesByFramework.entrySet()) {
            String framework = entry.getKey();
            try {
                FrameworkScanResult result = scanFramework(accountId, contextSource, framework,
                        new ArrayList<>(entry.getValue()), startTimestamp, endTimestamp);
                frameworksSucceeded++;
                totalExaminedAcrossRun += result.examined;
                totalHitSamplesAcrossRun += result.hitSamples;
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, String.format(
                        "ComplianceClauseScan[account=%d] failed for framework %s: %s",
                        accountId, framework, e.getMessage()));
            }
        }

        long elapsedMs = System.currentTimeMillis() - runStartMs;
        loggerMaker.infoAndAddToDb(String.format(
                "ComplianceClauseScan[account=%d] finished in %dms: %d/%d framework(s) succeeded, "
                        + "%d message(s) examined, %s overall insertion success ratio",
                accountId, elapsedMs, frameworksSucceeded, policyNamesByFramework.size(),
                totalExaminedAcrossRun, ratioString(totalHitSamplesAcrossRun, totalExaminedAcrossRun)));
    }

    /** Per-framework outcome, rolled up into the run-level summary log. */
    private static class FrameworkScanResult {
        final int examined;
        final int hitSamples;
        FrameworkScanResult(int examined, int hitSamples) {
            this.examined = examined;
            this.hitSamples = hitSamples;
        }
    }

    private static FrameworkScanResult scanFramework(int accountId, CONTEXT_SOURCE contextSource, String framework,
                                       List<String> filterIds, int startTimestamp, int endTimestamp) throws Exception {
        Map<String, List<ClauseHit>> clauseHits = new LinkedHashMap<>();
        List<SuggestedClause> suggestedClauses = new ArrayList<>();
        Map<String, String> refIdToFilterId = new HashMap<>();
        Map<String, Integer> refIdToTimestamp = new HashMap<>();
        List<BasicDBObject> pendingBatch = new ArrayList<>();

        int examined = 0;
        int hitSamples = 0;
        int batchIndex = 0;
        String cursor = null;
        int pages = 0;
        while (pages < MAX_PAGES_PER_FRAMEWORK) {
            // newestFirst=false: the scan must walk oldest-first so paging via cursor can't skip a
            // row that existed before the scan started — see the client method's own javadoc.
            ListGuardrailViolationPayloadsResponse resp = ThreatDetectionBackendClient.listGuardrailViolationPayloads(
                    accountId, startTimestamp, endTimestamp, filterIds, cursor, PAGE_LIMIT, false, contextSource.name());
            if (resp == null) {
                // Endpoint missing/unreachable (e.g. threat-detection-backend not yet redeployed) —
                // degrade to a gap on this framework rather than failing the whole run.
                loggerMaker.error(String.format(
                        "ComplianceClauseScan[account=%d] framework=%s: payload fetch unavailable "
                                + "(page=%d, cursor=%s) — stopping this framework's scan",
                        accountId, framework, pages, cursor));
                break;
            }
            List<ViolationPayload> payloads = resp.getPayloadsList();
            if (payloads.isEmpty()) break;

            loggerMaker.info(String.format(
                    "ComplianceClauseScan[account=%d] framework=%s: polled page %d — %d message(s) fetched "
                            + "(%d examined so far)",
                    accountId, framework, pages, payloads.size(), examined));

            for (ViolationPayload vp : payloads) {
                if (vp.getOrig() == null || vp.getOrig().isEmpty()) continue;
                BasicDBObject sample = new BasicDBObject();
                sample.put(GuardrailClauseAttributionHandler.REF_ID, vp.getRefId());
                sample.put(GuardrailClauseAttributionHandler.ORIG, vp.getOrig());
                pendingBatch.add(sample);
                refIdToFilterId.put(vp.getRefId(), vp.getFilterId());
                refIdToTimestamp.put(vp.getRefId(), (int) vp.getDetectedAt());
                examined++;

                if (pendingBatch.size() >= LLM_BATCH_SIZE) {
                    batchIndex++;
                    hitSamples += attributeBatch(accountId, framework, batchIndex, pendingBatch,
                            refIdToFilterId, refIdToTimestamp, clauseHits, suggestedClauses);
                    pendingBatch.clear();
                }
            }

            cursor = payloads.get(payloads.size() - 1).getCursor();
            pages++;
        }
        if (!pendingBatch.isEmpty()) {
            batchIndex++;
            hitSamples += attributeBatch(accountId, framework, batchIndex, pendingBatch,
                    refIdToFilterId, refIdToTimestamp, clauseHits, suggestedClauses);
        }

        ComplianceClauseCoverage doc = new ComplianceClauseCoverage();
        doc.setId(framework);
        doc.setClauseHits(clauseHits);
        doc.setSuggestedClauses(suggestedClauses);
        doc.setTotalClauses(ComplianceSubClauseCatalog.totalClauses(framework));
        doc.setLastScannedAt(Context.now());
        doc.setScanStartTs(startTimestamp);
        doc.setScanEndTs(endTimestamp);

        boolean insertSucceeded = true;
        try {
            ComplianceClauseCoverageDao.instance.replaceOne(Filters.eq("_id", framework), doc);
        } catch (Exception e) {
            insertSucceeded = false;
            loggerMaker.errorAndAddToDb(e, String.format(
                    "ComplianceClauseScan[account=%d] framework=%s: DB upsert failed after examining %d message(s)",
                    accountId, framework, examined));
        }

        loggerMaker.infoAndAddToDb(String.format(
                "ComplianceClauseScan[account=%d] framework=%s: done — %d message(s) examined across %d batch(es), "
                        + "%d/%d clause(s) covered, %d catalog-improvement suggestion(s), DB insertion %s, "
                        + "%s message-level insertion success ratio",
                accountId, framework, examined, batchIndex, clauseHits.size(),
                doc.getTotalClauses(), suggestedClauses.size(), insertSucceeded ? "succeeded" : "FAILED",
                ratioString(hitSamples, examined)));

        return new FrameworkScanResult(examined, hitSamples);
    }

    /** @return how many samples in this batch had at least one clause hit actually recorded
     *  (survived GuardrailClauseAttributionHandler's own catalog filter) — the numerator for this
     *  framework's message-level insertion success ratio. */
    @SuppressWarnings("unchecked")
    private static int attributeBatch(int accountId, String framework, int batchIndex, List<BasicDBObject> batch,
                                        Map<String, String> refIdToFilterId, Map<String, Integer> refIdToTimestamp,
                                        Map<String, List<ClauseHit>> clauseHits, List<SuggestedClause> suggestedClauses) {
        BasicDBObject queryData = new BasicDBObject();
        queryData.put(GuardrailClauseAttributionHandler.FRAMEWORK, framework);
        queryData.put(GuardrailClauseAttributionHandler.SAMPLES, new ArrayList<>(batch));

        BasicDBObject result = new GuardrailClauseAttributionHandler().handle(queryData);
        Object hitsObj = result.get(GuardrailClauseAttributionHandler.HITS);
        int samplesWithHits = 0;
        int clauseHitsRecorded = 0;
        int suggestionsRecorded = 0;
        if (hitsObj instanceof List) {
            for (Object hitObj : (List<Object>) hitsObj) {
                if (!(hitObj instanceof BasicDBObject)) continue;
                BasicDBObject hit = (BasicDBObject) hitObj;
                String refId = hit.getString(GuardrailClauseAttributionHandler.REF_ID);
                if (refId == null) continue;

                int ts = refIdToTimestamp.getOrDefault(refId, 0);
                String policyName = refIdToFilterId.getOrDefault(refId, "");

                Object clausesObj = hit.get(GuardrailClauseAttributionHandler.CLAUSES);
                if (clausesObj instanceof List && !((List<?>) clausesObj).isEmpty()) {
                    boolean recordedAny = false;
                    for (Object clauseObj : (List<Object>) clausesObj) {
                        String clause = String.valueOf(clauseObj);
                        List<ClauseHit> hitsForClause = clauseHits.computeIfAbsent(clause, k -> new ArrayList<>());
                        if (hitsForClause.size() >= MAX_HITS_PER_CLAUSE) continue;
                        hitsForClause.add(new ClauseHit(refId, ts, policyName));
                        clauseHitsRecorded++;
                        recordedAny = true;
                    }
                    if (recordedAny) samplesWithHits++;
                }

                // Non-scoring: catalog-improvement candidates, capped so a chatty model can't turn
                // this into an unbounded doc — see MAX_SUGGESTIONS_PER_FRAMEWORK.
                Object suggestedObj = hit.get(GuardrailClauseAttributionHandler.SUGGESTED_CLAUSES);
                if (suggestedObj instanceof List) {
                    for (Object suggestionObj : (List<Object>) suggestedObj) {
                        if (suggestedClauses.size() >= MAX_SUGGESTIONS_PER_FRAMEWORK) break;
                        String text = String.valueOf(suggestionObj).trim();
                        if (text.isEmpty()) continue;
                        suggestedClauses.add(new SuggestedClause(text, refId, ts, policyName));
                        suggestionsRecorded++;
                    }
                }
            }
        }

        loggerMaker.info(String.format(
                "ComplianceClauseScan[account=%d] framework=%s: batch %d completed — %d sample(s) sent, "
                        + "%d clause hit(s) recorded across %d sample(s), %d suggestion(s)",
                accountId, framework, batchIndex, batch.size(), clauseHitsRecorded, samplesWithHits,
                suggestionsRecorded));

        return samplesWithHits;
    }

    private static String ratioString(int numerator, int denominator) {
        if (denominator <= 0) return "n/a";
        return String.format("%d/%d (%.1f%%)", numerator, denominator, (numerator * 100.0) / denominator);
    }
}
