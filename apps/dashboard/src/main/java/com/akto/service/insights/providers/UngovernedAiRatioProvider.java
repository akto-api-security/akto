package com.akto.service.insights.providers;

import com.akto.dto.ApiCollection;
import com.akto.dto.McpAuditInfo;
import com.akto.service.insights.*;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Ungoverned AI ratio — sanctioned vs shadow/personal/local/unapproved surfaces, by team. */
public class UngovernedAiRatioProvider extends AbstractInsightProvider {

    private static final String MALICIOUS = "MALICIOUS", REJECTED = "REJECTED", PERSONAL = "PERSONAL",
            LOCAL = "LOCAL", SHADOW = "SHADOW", UNAPPROVED = "UNAPPROVED", SANCTIONED = "SANCTIONED";

    public UngovernedAiRatioProvider() { super(InsightId.UNGOVERNED_AI_RATIO, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        InsightResult r = skeleton();

        Set<String> allowlistLower = bundle.allowlistNamesLower;
        Map<String, String> remarksByService = new HashMap<>();
        for (McpAuditInfo a : bundle.auditRows) {
            if (a.getMcpHost() != null && a.getRemarks() != null && !a.getRemarks().isEmpty()) {
                remarksByService.put(a.getMcpHost().toLowerCase(Locale.ROOT), a.getRemarks());
            }
        }

        Map<String, Integer> byBucket = new HashMap<>();
        Map<String, Integer> ungovernedByTeam = new HashMap<>();
        int untaggedUngoverned = 0;
        int total = 0;

        for (ApiCollection c : bundle.collections) {
            if (c.isDeactivated() || c.getHostName() == null) continue;
            total++;
            String bucket = classify(c, allowlistLower, remarksByService);
            byBucket.merge(bucket, 1, Integer::sum);

            if (!SANCTIONED.equals(bucket)) {
                String deviceId = InsightUtil.deviceIdOf(c);
                String team = bundle.teamForUser(bundle.usernameForDevice(deviceId));
                if (team == null) untaggedUngoverned++; else ungovernedByTeam.merge(team, 1, Integer::sum);
            }
        }

        int sanctioned = byBucket.getOrDefault(SANCTIONED, 0);
        int ungoverned = total - sanctioned;
        double ratio = total > 0 ? (double) ungoverned / total : 0;

        r.addMetric(new InsightResult.Metric("totalAssets", "Agentic assets", total, "count", InsightUtil.count(total, "assets")));
        r.addMetric(new InsightResult.Metric("ungovernedCount", "Ungoverned assets", ungoverned, total, "count",
                InsightUtil.ofTotal(ungoverned, total, "assets"), null));
        r.addMetric(new InsightResult.Metric("ungovernedRatio", "Ungoverned ratio", ratio, "percent", InsightUtil.percent(ratio)));

        r.setStatus((total == 0 ? InsightResult.Status.NO_DATA : InsightResult.Status.READY).name());
        if (ungovernedByTeam.isEmpty() && untaggedUngoverned > 0) {
            r.addDataGap(new InsightResult.Gap("TEAM_TAGS", "NO_ROWS", "No devices carry a 'team' tag; this ratio is account-wide, not broken down by team."));
        }
        r.setHeadline(total == 0 ? "No agentic assets found" : InsightUtil.percent(ratio) + " of AI surfaces are ungoverned");

        java.util.List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (String bucket : new String[]{SANCTIONED, UNAPPROVED, SHADOW, LOCAL, PERSONAL, REJECTED, MALICIOUS}) {
            Map<String, Object> row = new HashMap<>();
            row.put("bucket", bucket);
            row.put("count", byBucket.getOrDefault(bucket, 0));
            rows.add(row);
        }
        r.addEvidence(new InsightResult.Evidence("breakdown", "Governance breakdown",
                java.util.Arrays.asList("bucket", "count"), rows, rows.size()));

        r.addCta(new InsightResult.Cta("view_agentic_assets", "View agentic assets", "NAVIGATE", InsightRoutes.AGENTIC_ASSETS, new HashMap<>(), true));
        r.addCta(new InsightResult.Cta("view_audit", "Review audit data", "NAVIGATE", InsightRoutes.AUDIT, new HashMap<>(), false));
        return r;
    }

    private String classify(ApiCollection c, Set<String> allowlistLower, Map<String, String> remarksByService) {
        if (InsightUtil.isMaliciousMcpServer(c)) return MALICIOUS;
        String serviceName = InsightUtil.serviceNameOf(c);
        String remarks = serviceName != null ? remarksByService.get(serviceName.toLowerCase(Locale.ROOT)) : null;
        if (McpAuditInfo.REMARKS_REJECTED.equals(remarks)) return REJECTED;
        if (InsightUtil.isPersonalAccount(c)) return PERSONAL;
        if (InsightUtil.isLocalMcp(c)) return LOCAL;
        boolean inAllowlist = serviceName != null && allowlistLower.contains(serviceName.toLowerCase(Locale.ROOT));
        if (inAllowlist || McpAuditInfo.REMARKS_APPROVED.equals(remarks)) return SANCTIONED;
        if (remarks == null) return SHADOW; // no allowlist entry, no audit row at all
        return UNAPPROVED; // audit row exists with blank remarks -> pending
    }
}
