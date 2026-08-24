package com.akto.service.insights.providers;

import com.akto.dto.ApiCollection;
import com.akto.dto.McpAuditInfo;
import com.akto.service.insights.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// TODO: NHI identity cross-reference (dormant servers still holding a live credential) was
// removed here for now — bundle.nhiIdentities/hasLiveCredential and the
// dormantWithLiveCredsCount metric need to come back once that data source is ready again.
/** Unapproved & local MCP sprawl, by team, plus dormant assets. */
public class McpSprawlProvider extends AbstractInsightProvider {

    private static final long DORMANT_THRESHOLD_SECONDS = 90L * 24 * 3600;

    public McpSprawlProvider() { super(InsightId.MCP_SPRAWL, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        InsightResult r = skeleton();
        // Dormancy is relative to real wall-clock time, not ctx's endTs (a date-range filter
        // bound the caller controls — e.g. AgenticAssetsPage's "All time" preset sends a
        // far-future sentinel). Using that as "now" inflated the elapsed-time gap for every
        // server and wrongly flagged most of them dormant.
        long now = System.currentTimeMillis() / 1000;

        // Approved service names: allowlist, or an mcp-server audit row with remarks=Approved.
        Set<String> approvedApprovedLower = new HashSet<>(bundle.allowlistNamesLower);
        for (McpAuditInfo a : bundle.auditRows) {
            if (McpAuditInfo.REMARKS_APPROVED.equals(a.getRemarks()) && a.getMcpHost() != null) {
                approvedApprovedLower.add(a.getMcpHost().toLowerCase(Locale.ROOT));
            }
        }

        List<ApiCollection> flagged = new ArrayList<>();
        for (ApiCollection c : bundle.collections) {
            if (c.isDeactivated()) continue;
            boolean local = InsightUtil.isLocalMcp(c);
            String serviceName = InsightUtil.serviceNameOf(c);
            boolean unapproved = serviceName != null && !approvedApprovedLower.contains(serviceName.toLowerCase(Locale.ROOT));
            if (local || unapproved) flagged.add(c);
        }

        Map<String, Integer> byTeam = new HashMap<>();
        int untagged = 0;
        int dormant = 0;
        List<Map<String, Object>> rows = new ArrayList<>();

        for (ApiCollection c : flagged) {
            String deviceId = InsightUtil.deviceIdOf(c);
            String username = bundle.usernameForDevice(deviceId);
            String team = bundle.teamForUser(username);
            if (team == null) untagged++; else byTeam.merge(team, 1, Integer::sum);

            // Dormant: only claim it when an audit row for this host gives a real last-detected
            // signal — never guess dormancy from absence of data.
            McpAuditInfo matchingAudit = findAuditRowForHost(bundle, c);
            boolean isDormant = matchingAudit != null && (now - matchingAudit.getLastDetected()) > DORMANT_THRESHOLD_SECONDS;
            if (isDormant) dormant++;

            if (rows.size() < 20) {
                Map<String, Object> row = new HashMap<>();
                row.put("host", c.getHostName());
                row.put("firstSeen", c.getStartTs());
                row.put("team", team != null ? team : "untagged");
                row.put("dormant", isDormant);
                rows.add(row);
            }
        }

        r.addMetric(new InsightResult.Metric("flaggedCount", "Local/unapproved MCP servers", flagged.size(), "count",
                InsightUtil.count(flagged.size(), "servers")));
        r.addMetric(new InsightResult.Metric("dormantCount", "Dormant (90d+) with known last activity", dormant, "count",
                InsightUtil.count(dormant, "servers")));

        r.setStatus((flagged.isEmpty() ? InsightResult.Status.NO_DATA : InsightResult.Status.READY).name());
        if (untagged > 0 && byTeam.isEmpty()) {
            r.addDataGap(new InsightResult.Gap("TEAM_TAGS", "NO_ROWS", "No devices carry a 'team' tag; team breakdown is unavailable."));
        }
        r.setHeadline(flagged.isEmpty() ? "No local or unapproved MCP servers found"
                : InsightUtil.count(flagged.size(), "local/unapproved MCP servers") + " found, " + dormant + " dormant");

        r.addEvidence(new InsightResult.Evidence("sprawl", "Local/unapproved MCP servers",
                java.util.Arrays.asList("host", "firstSeen", "team", "dormant"), rows, flagged.size()));

        Map<String, Object> assetsParams = new HashMap<>();
        r.addCta(new InsightResult.Cta("view_agentic_assets", "View agentic assets", "NAVIGATE", InsightRoutes.AGENTIC_ASSETS, assetsParams, true));
        r.addCta(new InsightResult.Cta("view_endpoint_shield", "View Endpoint Shield", "NAVIGATE", InsightRoutes.ENDPOINT_SHIELD, new HashMap<>(), false));
        return r;
    }

    private McpAuditInfo findAuditRowForHost(InsightDataBundle bundle, ApiCollection c) {
        String serviceName = InsightUtil.serviceNameOf(c);
        if (serviceName == null) return null;
        for (McpAuditInfo a : bundle.auditRows) {
            if (serviceName.equalsIgnoreCase(a.getMcpHost())) return a;
        }
        return null;
    }
}
