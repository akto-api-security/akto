package com.akto.service.insights.providers;

import com.akto.dto.ApiCollection;
import com.akto.dto.McpAuditInfo;
import com.akto.dto.traffic.CollectionTags;
import com.akto.service.insights.*;
import com.akto.util.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Everything first-seen in the last 7 days: new assets, new components, new tags. */
public class WhatChangedThisWeekProvider extends AbstractInsightProvider {

    private static final long WINDOW_SECONDS = 7L * 24 * 3600;

    public WhatChangedThisWeekProvider() { super(InsightId.WHAT_CHANGED_THIS_WEEK, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        InsightResult r = skeleton();
        long now = ctx.getEndTs() > 0 ? ctx.getEndTs() : System.currentTimeMillis() / 1000;
        long since = now - WINDOW_SECONDS;

        List<ApiCollection> newAssets = new ArrayList<>();
        for (ApiCollection c : bundle.collections) {
            if (c.isDeactivated()) continue;
            if (c.getStartTs() >= since && c.getStartTs() <= now) newAssets.add(c);
        }

        List<McpAuditInfo> newComponents = new ArrayList<>();
        for (McpAuditInfo a : bundle.auditRows) {
            long created = a.getId() != null ? a.getId().getTimestamp() : 0;
            if (created >= since && created <= now) newComponents.add(a);
        }

        int newTagCount = 0;
        List<String> newTagSamples = new ArrayList<>();
        for (ApiCollection c : bundle.collections) {
            if (c.getTagsList() == null) continue;
            for (CollectionTags t : c.getTagsList()) {
                if (t == null || Constants.AKTO_MALICIOUS_MCP_SERVER_TAG.equals(t.getKeyName())) continue;
                if (t.getLastUpdatedTs() >= since && t.getLastUpdatedTs() <= now) {
                    newTagCount++;
                    if (newTagSamples.size() < 20) newTagSamples.add(c.getHostName() + " -> " + t.getKeyName());
                }
            }
        }

        r.addMetric(new InsightResult.Metric("newAssets", "New assets", newAssets.size(), "count",
                InsightUtil.count(newAssets.size(), "assets")));
        r.addMetric(new InsightResult.Metric("newComponents", "New MCP components detected", newComponents.size(), "count",
                InsightUtil.count(newComponents.size(), "components")));
        r.addMetric(new InsightResult.Metric("newTags", "New tag detections", newTagCount, "count",
                InsightUtil.count(newTagCount, "tag detections")));

        boolean anyChange = !newAssets.isEmpty() || !newComponents.isEmpty() || newTagCount > 0;
        r.setStatus((anyChange ? InsightResult.Status.READY : InsightResult.Status.NO_DATA).name());
        r.setHeadline(anyChange
                ? InsightUtil.count(newAssets.size(), "new assets") + " and " + InsightUtil.count(newComponents.size(), "new components") + " this week"
                : "No new assets, components or tags in the last 7 days");

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ApiCollection c : newAssets.subList(0, Math.min(20, newAssets.size()))) {
            Map<String, Object> row = new HashMap<>();
            row.put("host", c.getHostName());
            row.put("firstSeen", c.getStartTs());
            rows.add(row);
        }
        r.addEvidence(new InsightResult.Evidence("new_assets", "New assets this week",
                java.util.Arrays.asList("host", "firstSeen"), rows, newAssets.size()));

        r.addCta(cta("view_agentic_assets", "View agentic assets", InsightRoutes.AGENTIC_ASSETS, true));
        r.addCta(cta("view_audit", "Review audit data", InsightRoutes.AUDIT, false));
        return r;
    }

    private InsightResult.Cta cta(String id, String label, String route, boolean primary) {
        return new InsightResult.Cta(id, label, "NAVIGATE", route, new HashMap<>(), primary);
    }
}
