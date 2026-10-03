package com.akto.service.insights.providers;

import com.akto.service.insights.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sensitive data returned in responses — the PII classes exposed by the most distinct endpoints
 * in response bodies. Ask Akto overlay's API_POSTURE group. See
 * SingleTypeInfoDao.responseSensitiveSubtypeApiCounts() for why this is an API count rather than
 * a hit count — SingleTypeInfo.count isn't a reliable hit counter, so an "occurrences" framing
 * would overstate precision it doesn't have.
 */
public class SensitiveDataHotspotsProvider extends AbstractInsightProvider {

    private static final int TOP_N = 5;
    // Placeholder threshold for "enough exposure to call this HIGH" — tune once this has run
    // against real accounts; there's no existing convention to anchor to since this is a new
    // aggregation.
    private static final long HIGH_SEVERITY_API_THRESHOLD = 10;

    public SensitiveDataHotspotsProvider() { super(InsightId.SENSITIVE_DATA_HOTSPOTS, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        InsightResult r = skeleton();

        Map<String, Integer> apiCountBySubType = bundle.sensitiveApiCountBySubType();
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(apiCountBySubType.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        long totalApis = 0;
        for (int v : apiCountBySubType.values()) totalApis += v;

        r.setStatus((apiCountBySubType.isEmpty() ? InsightResult.Status.NO_DATA : InsightResult.Status.READY).name());

        if (!sorted.isEmpty()) {
            String topSubType = sorted.get(0).getKey();
            int topApiCount = sorted.get(0).getValue();

            // topSubType is a name, not a number — it isn't representable as a Metric (value must be
            // a Number); it's carried in the headline and as the evidence table's top row instead.
            r.addMetric(new InsightResult.Metric("topSubTypeApis", "Most-exposed sensitive data type: " + topSubType,
                    topApiCount, "count", InsightUtil.count(topApiCount, "APIs")));
            r.addMetric(new InsightResult.Metric("totalSensitiveApis", "Total sensitive-data-exposing APIs", totalApis, "count", InsightUtil.count(totalApis, "APIs")));

            r.setHeadline(topSubType + " is returned by " + InsightUtil.count(topApiCount, "APIs"));
            r.setSeverity(topApiCount >= HIGH_SEVERITY_API_THRESHOLD ? "HIGH" : "MEDIUM");
            r.setConcern(topSubType + " is the sensitive data type returned by the most distinct endpoints across your APIs — "
                    + InsightUtil.count(topApiCount, "APIs") + " expose it in their responses.");
            r.setImpact("Every endpoint returning this data type is a place a leak, log, or overly broad integration can expose it.");
            r.setRemediation("Confirm each of these endpoints actually needs to return this field, and add masking or a guardrail where it doesn't.");
        } else {
            r.setHeadline("No sensitive data detected in responses");
        }

        List<Map<String, Object>> evidenceRows = new ArrayList<>();
        for (int i = 0; i < Math.min(TOP_N, sorted.size()); i++) {
            Map.Entry<String, Integer> e = sorted.get(i);
            Map<String, Object> row = new HashMap<>();
            row.put("subType", e.getKey());
            row.put("apis", e.getValue());
            evidenceRows.add(row);
        }
        r.addEvidence(new InsightResult.Evidence("sensitiveHotspots", "Top sensitive data types",
                java.util.Arrays.asList("subType", "apis"), evidenceRows, sorted.size()));

        r.addCta(new InsightResult.Cta("view_sensitive", "View sensitive data", "NAVIGATE", InsightRoutes.SENSITIVE_DATA, new HashMap<>(), true));
        return r;
    }
}
