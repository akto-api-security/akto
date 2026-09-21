package com.akto.service.insights.providers;

import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.service.insights.*;
import com.akto.util.enums.GlobalEnums.Severity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Issues open longer than 30 days — the "what's rotting" number. Ask Akto overlay's
 * TESTING_POSTURE group.
 */
public class AgingOpenCriticalsProvider extends AbstractInsightProvider {

    public AgingOpenCriticalsProvider() { super(InsightId.AGING_OPEN_CRITICALS, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        InsightResult r = skeleton();

        List<TestingRunIssues> agingIssues = bundle.agingOpenIssues();
        int nowSeconds = (int) (System.currentTimeMillis() / 1000);

        int criticalOrHighCount = 0;
        Map<String, Integer> bySeverity = new HashMap<>();
        List<Map<String, Object>> evidenceRows = new ArrayList<>();

        for (TestingRunIssues issue : agingIssues) {
            Severity sev = issue.getSeverity();
            String sevName = sev != null ? sev.name() : "UNKNOWN";
            bySeverity.merge(sevName, 1, Integer::sum);
            if (sev == Severity.CRITICAL || sev == Severity.HIGH) criticalOrHighCount++;

            if (evidenceRows.size() < 200) {
                TestingIssuesId id = issue.getId();
                Map<String, Object> row = new HashMap<>();
                row.put("collection", id.getApiInfoKey() != null ? id.getApiInfoKey().getApiCollectionId() : null);
                row.put("endpoint", id.getApiInfoKey() != null ? id.getApiInfoKey().getUrl() : null);
                row.put("method", id.getApiInfoKey() != null ? String.valueOf(id.getApiInfoKey().getMethod()) : null);
                row.put("severity", sevName);
                row.put("subCategory", id.getTestSubCategory());
                row.put("ageDays", Math.max(0, (nowSeconds - issue.getCreationTime()) / 86400));
                evidenceRows.add(row);
            }
        }

        int total = agingIssues.size();
        r.addMetric(new InsightResult.Metric("agingOpenIssues", "Issues open >30 days", total, "count", InsightUtil.count(total, "issues")));
        r.addMetric(new InsightResult.Metric("agingCriticalOrHigh", "...critical or high severity",
                criticalOrHighCount, total, "count", InsightUtil.ofTotal(criticalOrHighCount, total, "issues"), null));

        r.setStatus((total == 0 ? InsightResult.Status.NO_DATA : InsightResult.Status.READY).name());
        r.setHeadline(total == 0 ? "No open issues older than 30 days"
                : InsightUtil.count(total, "open issues") + " have been open for over 30 days");

        if (total > 0) {
            r.setSeverity(criticalOrHighCount > 0 ? "HIGH" : "MEDIUM");
            r.setConcern(InsightUtil.count(total, "issues") + " have sat open for more than 30 days"
                    + (criticalOrHighCount > 0 ? ", including " + InsightUtil.count(criticalOrHighCount, "critical or high severity issues") + "." : "."));
            r.setImpact("An issue that's been open this long usually means it's been deprioritized, not that it's been assessed as safe to leave.");
            r.setRemediation("Triage the critical/high ones first — either fix them or explicitly mark them as accepted risk with a reason.");
        }

        r.addEvidence(new InsightResult.Evidence("agingIssues", "Aging open issues",
                java.util.Arrays.asList("collection", "endpoint", "method", "severity", "subCategory", "ageDays"),
                evidenceRows, total));

        Map<String, Object> ctaParams = new HashMap<>();
        ctaParams.put("status", java.util.Collections.singletonList("OPEN"));
        r.addCta(new InsightResult.Cta("view_issues", "View open issues", "NAVIGATE", InsightRoutes.ISSUES, ctaParams, true));
        return r;
    }
}
