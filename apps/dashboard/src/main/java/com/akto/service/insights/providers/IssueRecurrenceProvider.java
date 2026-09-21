package com.akto.service.insights.providers;

import com.akto.dto.ApiCollection;
import com.akto.service.insights.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Findings that keep recurring — the repetition score from InsightDataBundle.issueRecurrence():
 * how many distinct test runs have seen the same {collection, endpoint, method, testSubType}
 * finding. Deliberately NOT fix-then-reopen detection — see IssueRecurrenceRow's javadoc for why
 * that isn't derivable from what TestingRunIssues stores today. Ask Akto overlay's
 * TESTING_POSTURE group.
 */
public class IssueRecurrenceProvider extends AbstractInsightProvider {

    private static final int RECURRING_THRESHOLD = 2;   // seen in >=2 distinct runs
    private static final int HIGH_SEVERITY_THRESHOLD = 5;
    private static final int TOP_N = 10;

    public IssueRecurrenceProvider() { super(InsightId.ISSUE_RECURRENCE, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        InsightResult r = skeleton();

        Map<Integer, String> hostNameById = new HashMap<>();
        for (ApiCollection c : bundle.collections) hostNameById.put(c.getId(), c.getHostName());

        List<IssueRecurrenceRow> rows = bundle.issueRecurrence();
        List<IssueRecurrenceRow> recurring = new ArrayList<>();
        for (IssueRecurrenceRow row : rows) {
            if (row.getDistinctRuns() >= RECURRING_THRESHOLD) recurring.add(row);
        }
        recurring.sort((a, b) -> Integer.compare(b.getDistinctRuns(), a.getDistinctRuns()));

        int maxRecurrence = recurring.isEmpty() ? 0 : recurring.get(0).getDistinctRuns();

        r.addMetric(new InsightResult.Metric("recurringFindings", "Findings seen in 2+ runs", recurring.size(), "count",
                InsightUtil.count(recurring.size(), "findings")));
        if (maxRecurrence > 0) {
            r.addMetric(new InsightResult.Metric("maxRecurrence", "Most-repeated finding", maxRecurrence, "count",
                    InsightUtil.count(maxRecurrence, "runs")));
        }

        r.setStatus((recurring.isEmpty() ? InsightResult.Status.NO_DATA : InsightResult.Status.READY).name());
        r.setHeadline(recurring.isEmpty() ? "No findings recurring across test runs"
                : InsightUtil.count(recurring.size(), "findings") + " keep showing up across multiple test runs");

        if (!recurring.isEmpty()) {
            r.setSeverity(maxRecurrence >= HIGH_SEVERITY_THRESHOLD ? "HIGH" : "MEDIUM");
            r.setConcern(InsightUtil.count(recurring.size(), "findings") + " have been seen in more than one test run — "
                    + "the worst repeats in " + InsightUtil.count(maxRecurrence, "separate runs") + ".");
            r.setImpact("A finding that keeps recurring wasn't actually fixed, or the fix didn't hold — the same weakness is being rediscovered instead of closed.");
            r.setRemediation("Treat repeat offenders as a process gap, not a fresh bug: confirm the underlying cause is actually addressed, not just the last symptom.");
        }

        List<Map<String, Object>> evidenceRows = new ArrayList<>();
        for (int i = 0; i < Math.min(TOP_N, recurring.size()); i++) {
            IssueRecurrenceRow row = recurring.get(i);
            Map<String, Object> evidenceRow = new HashMap<>();
            evidenceRow.put("collection", hostNameById.getOrDefault(row.getApiCollectionId(), String.valueOf(row.getApiCollectionId())));
            evidenceRow.put("endpoint", row.getUrl());
            evidenceRow.put("method", row.getMethod());
            evidenceRow.put("testSubType", row.getTestSubType());
            evidenceRow.put("distinctRuns", row.getDistinctRuns());
            evidenceRow.put("firstSeen", row.getFirstSeen());
            evidenceRow.put("lastSeen", row.getLastSeen());
            evidenceRows.add(evidenceRow);
        }
        r.addEvidence(new InsightResult.Evidence("recurringFindings", "Recurring findings",
                java.util.Arrays.asList("collection", "endpoint", "method", "testSubType", "distinctRuns", "firstSeen", "lastSeen"),
                evidenceRows, recurring.size()));

        r.addCta(new InsightResult.Cta("view_issues", "View issues", "NAVIGATE", InsightRoutes.ISSUES, new HashMap<>(), true));
        return r;
    }
}
