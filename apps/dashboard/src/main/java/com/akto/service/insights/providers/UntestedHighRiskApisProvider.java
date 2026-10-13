package com.akto.service.insights.providers;

import com.akto.dao.ApiInfoDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.service.insights.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * High-risk APIs never tested — riskScore &gt;= 4 and either never tested or not tested in the
 * last 30 days, excluding collections explicitly marked out of testing scope. Ask Akto overlay's
 * API_POSTURE group.
 *
 * highRiskCount/untestedHighRiskCount are exact ApiInfoDao.count() queries, not a scan over
 * bundle.apiInfoRows(): that list is capped at InsightLazySources.API_INFO_ROW_CAP (50,000) and
 * an account can legitimately have well over 100,000 api_info rows, which would make the ratio
 * here a ratio over an arbitrary truncated subset rather than the real account-wide number — a
 * silently misleading percentage, not just an incomplete one. RISK_SCORE has its own index
 * (ApiInfoDao.createIndicesIfAbsent), so these counts are cheap regardless of account size. Only
 * the evidence table still needs an actual (small, separately bounded) row fetch.
 */
public class UntestedHighRiskApisProvider extends AbstractInsightProvider {

    // Same threshold ApiCollectionsAction's high-risk API counting already uses.
    private static final double HIGH_RISK_THRESHOLD = 4.0;
    private static final long UNTESTED_WINDOW_SECONDS = 30L * 24 * 3600;
    private static final int EVIDENCE_ROW_LIMIT = 200;

    public UntestedHighRiskApisProvider() { super(InsightId.UNTESTED_HIGH_RISK_APIS, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        InsightResult r = skeleton();

        Set<Integer> outOfScopeCollectionIds = new HashSet<>();
        for (ApiCollection c : bundle.collections) {
            if (c.getIsOutOfTestingScope()) outOfScopeCollectionIds.add(c.getId());
        }

        int nowSeconds = (int) (System.currentTimeMillis() / 1000);
        int cutoff = nowSeconds - (int) UNTESTED_WINDOW_SECONDS;

        Bson highRiskFilter = Filters.and(
                Filters.gte(ApiInfo.RISK_SCORE, HIGH_RISK_THRESHOLD),
                Filters.nin(ApiInfo.ID_API_COLLECTION_ID, outOfScopeCollectionIds));
        Bson untestedFilter = Filters.and(highRiskFilter,
                Filters.or(Filters.eq(ApiInfo.LAST_TESTED, 0), Filters.lt(ApiInfo.LAST_TESTED, cutoff)));

        long highRiskCount = ApiInfoDao.instance.count(highRiskFilter);
        long untestedHighRiskCount = ApiInfoDao.instance.count(untestedFilter);

        List<Map<String, Object>> evidenceRows = new ArrayList<>();
        if (untestedHighRiskCount > 0) {
            Bson projection = Projections.include(ApiInfo.ID_API_COLLECTION_ID, ApiInfo.ID_URL, ApiInfo.ID_METHOD,
                    ApiInfo.RISK_SCORE, ApiInfo.LAST_TESTED);
            List<ApiInfo> untestedRows = ApiInfoDao.instance.findAll(untestedFilter, 0, EVIDENCE_ROW_LIMIT, null, projection);
            for (ApiInfo info : untestedRows) {
                Map<String, Object> row = new HashMap<>();
                row.put("collection", info.getId().getApiCollectionId());
                row.put("endpoint", info.getId().getUrl());
                row.put("method", String.valueOf(info.getId().getMethod()));
                row.put("riskScore", info.getRiskScore());
                row.put("lastTested", info.getLastTested());
                evidenceRows.add(row);
            }
        }

        double ratio = highRiskCount > 0 ? (double) untestedHighRiskCount / highRiskCount : 0;

        r.addMetric(new InsightResult.Metric("highRiskApis", "High-risk APIs", highRiskCount, "count", InsightUtil.count(highRiskCount, "APIs")));
        r.addMetric(new InsightResult.Metric("untestedHighRiskApis", "...never tested (or stale >30d)",
                untestedHighRiskCount, highRiskCount, "count", InsightUtil.ofTotal(untestedHighRiskCount, highRiskCount, "APIs"), null));
        r.addMetric(new InsightResult.Metric("untestedHighRiskRatio", "Untested ratio", ratio, "percent", InsightUtil.percent(ratio)));

        r.setMetricsComplete(true);

        r.setStatus((highRiskCount == 0 ? InsightResult.Status.NO_DATA : InsightResult.Status.READY).name());
        r.setHeadline(untestedHighRiskCount == 0 ? "All high-risk APIs have recent test coverage"
                : InsightUtil.count(untestedHighRiskCount, "high-risk APIs") + " have never been tested or haven't been tested in 30 days");

        if (highRiskCount > 0 && untestedHighRiskCount > 0) {
            r.setSeverity(ratio >= 0.5 ? "CRITICAL" : ratio >= 0.25 ? "HIGH" : "MEDIUM");
            r.setConcern(InsightUtil.percent(ratio) + " of high-risk APIs (" + InsightUtil.count(untestedHighRiskCount, "APIs")
                    + ") have no recent test coverage — the riskiest surface is exactly where testing has lapsed.");
            r.setImpact("A high risk score with no recent test run means nobody has recently verified whether the known risk is still exploitable.");
            r.setRemediation("Schedule a test run against these collections, starting with whichever have never been tested at all.");
        }

        r.addEvidence(new InsightResult.Evidence("untestedHighRisk", "Untested high-risk APIs",
                java.util.Arrays.asList("collection", "endpoint", "method", "riskScore", "lastTested"),
                evidenceRows, (int) untestedHighRiskCount));

        r.addCta(new InsightResult.Cta("run_tests", "Go to testing", "NAVIGATE", InsightRoutes.TESTING, new HashMap<>(), true));
        r.addCta(new InsightResult.Cta("view_inventory", "View in inventory", "NAVIGATE", InsightRoutes.INVENTORY, new HashMap<>(), false));
        return r;
    }
}
