package com.akto.service.insights.providers;

import com.akto.dto.ApiCollection;
import com.akto.service.insights.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where open issues are concentrated — the few collections carrying most of the open findings,
 * so there's a clear "start here" instead of a flat list. Ask Akto overlay's TESTING_POSTURE
 * group.
 */
public class IssueConcentrationProvider extends AbstractInsightProvider {

    private static final int TOP_N = 5;

    public IssueConcentrationProvider() { super(InsightId.ISSUE_CONCENTRATION, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        InsightResult r = skeleton();

        Map<Integer, Map<String, Integer>> severityByCollection = bundle.openIssueSeverityByCollection();
        Map<Integer, String> hostNameById = new HashMap<>();
        for (ApiCollection c : bundle.collections) hostNameById.put(c.getId(), c.getHostName());

        boolean anyCritical = false;
        long totalOpen = 0;
        List<Map.Entry<Integer, Integer>> totalsByCollection = new ArrayList<>();
        for (Map.Entry<Integer, Map<String, Integer>> e : severityByCollection.entrySet()) {
            int collectionTotal = 0;
            for (int c : e.getValue().values()) collectionTotal += c;
            totalsByCollection.add(new java.util.AbstractMap.SimpleEntry<>(e.getKey(), collectionTotal));
            totalOpen += collectionTotal;
            if (e.getValue().getOrDefault("CRITICAL", 0) > 0) anyCritical = true;
        }
        totalsByCollection.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        long topShare = 0;
        if (!totalsByCollection.isEmpty() && totalOpen > 0) {
            topShare = Math.round(100.0 * totalsByCollection.get(0).getValue() / totalOpen);
        }

        r.addMetric(new InsightResult.Metric("totalOpenIssues", "Open issues", totalOpen, "count", InsightUtil.count(totalOpen, "issues")));
        if (!totalsByCollection.isEmpty()) {
            r.addMetric(new InsightResult.Metric("topCollectionShare", "Share held by the top collection", topShare, "percent", topShare + "%"));
        }

        r.setStatus((totalOpen == 0 ? InsightResult.Status.NO_DATA : InsightResult.Status.READY).name());
        r.setHeadline(totalOpen == 0 ? "No open issues"
                : "The top collection holds " + topShare + "% of open issues");

        if (totalOpen > 0) {
            r.setSeverity(anyCritical ? "CRITICAL" : topShare >= 50 ? "HIGH" : "MEDIUM");
            r.setConcern("Open issues are concentrated rather than evenly spread — the top collection alone accounts for "
                    + topShare + "% of them.");
            r.setImpact("Fixing the top few collections moves the account-wide number more than working the list top-to-bottom.");
            r.setRemediation("Start with the top collection below, then work down the ranked list.");
        }

        List<Map<String, Object>> evidenceRows = new ArrayList<>();
        for (int i = 0; i < Math.min(TOP_N, totalsByCollection.size()); i++) {
            Map.Entry<Integer, Integer> entry = totalsByCollection.get(i);
            Map<String, Integer> severities = severityByCollection.getOrDefault(entry.getKey(), new HashMap<>());
            Map<String, Object> row = new HashMap<>();
            row.put("collection", hostNameById.getOrDefault(entry.getKey(), String.valueOf(entry.getKey())));
            row.put("critical", severities.getOrDefault("CRITICAL", 0));
            row.put("high", severities.getOrDefault("HIGH", 0));
            row.put("medium", severities.getOrDefault("MEDIUM", 0));
            row.put("low", severities.getOrDefault("LOW", 0));
            evidenceRows.add(row);
        }
        r.addEvidence(new InsightResult.Evidence("issueConcentration", "Open issues by collection",
                java.util.Arrays.asList("collection", "critical", "high", "medium", "low"),
                evidenceRows, totalsByCollection.size()));

        Map<String, Object> ctaParams = new HashMap<>();
        ctaParams.put("status", java.util.Collections.singletonList("OPEN"));
        r.addCta(new InsightResult.Cta("view_issues", "View open issues", "NAVIGATE", InsightRoutes.ISSUES, ctaParams, true));
        return r;
    }
}
