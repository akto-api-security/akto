package com.akto.service.insights.providers;

import com.akto.dto.GuardrailPolicies;
import com.akto.dto.McpAuditInfo;
import com.akto.service.insights.*;

import java.util.*;

/**
 * Users reachable through MCP tools carrying a dangerous capability, and how many of
 * those tools have no blocking guardrail. Built-in agent tools (Bash, Read, ...) are
 * NOT included — that inventory needs an account-wide ApiInfo /tool/* scan this pass
 * deliberately left out; reported as a data gap rather than silently omitted.
 */
public class DangerousCapabilityExposureProvider extends AbstractInsightProvider {

    public DangerousCapabilityExposureProvider() { super(InsightId.DANGEROUS_CAPABILITY_EXPOSURE, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        InsightResult r = skeleton();

        List<McpAuditInfo> tools = new ArrayList<>();
        for (McpAuditInfo a : bundle.auditRows) {
            if ("mcp-tool".equals(a.getType()) && a.getResourceName() != null) tools.add(a);
        }

        int classified = 0, unclassified = 0, dangerous = 0, ungated = 0;
        Set<String> usersReachable = new HashSet<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        List<GuardrailPolicies> policies = bundle.policies;

        for (McpAuditInfo tool : tools) {
            String capability = InsightUtil.builtinToolCapability(tool.getResourceName());
            if (capability == null) {
                if (scope == Scope.DETAIL) {
                    capability = InsightClassificationHelper.classifyToolCapability(tool.getResourceName(), null);
                } else {
                    unclassified++;
                    continue;
                }
            }
            classified++;
            if (!InsightUtil.isDangerousCapability(capability)) continue;
            dangerous++;

            boolean gated = false;
            for (GuardrailPolicies p : policies) {
                if (InsightUtil.isBlockingPolicy(p) && InsightUtil.policyCoversHost(p, tool.getMcpHost())) { gated = true; break; }
            }
            if (!gated) ungated++;

            for (com.akto.dto.ApiCollection c : bundle.collectionsForServiceName(tool.getMcpHost())) {
                String deviceId = InsightUtil.deviceIdOf(c);
                String username = bundle.usernameForDevice(deviceId);
                if (username != null) usersReachable.add(username);
            }

            if (rows.size() < 20) {
                Map<String, Object> row = new HashMap<>();
                row.put("tool", tool.getResourceName());
                row.put("capability", capability);
                row.put("mcpHost", tool.getMcpHost());
                row.put("ungated", !gated);
                rows.add(row);
            }
        }

        r.addMetric(new InsightResult.Metric("dangerousTools", "Dangerous-capability tools", dangerous, "count", InsightUtil.count(dangerous, "tools")));
        r.addMetric(new InsightResult.Metric("ungatedDangerousTools", "Ungated dangerous tools", ungated, "count", InsightUtil.count(ungated, "tools")));
        r.addMetric(new InsightResult.Metric("usersReachable", "Users reachable via dangerous tools", usersReachable.size(), "count",
                InsightUtil.count(usersReachable.size(), "users")));

        r.setMetricsComplete(scope == Scope.DETAIL);
        r.setStatus((dangerous == 0 ? InsightResult.Status.NO_DATA : InsightResult.Status.PARTIAL).name());
        r.addDataGap(new InsightResult.Gap("TOOL_INVENTORY", "NOT_CONFIGURED",
                "Built-in agent tools (Bash, Read, Write, ...) are not included — only MCP server tools are classified."));
        if (unclassified > 0) {
            r.addDataGap(new InsightResult.Gap("TOOL_INVENTORY", "DEFERRED_TO_DETAIL", unclassified + " tools are classified only when this insight is opened."));
        }
        r.setHeadline(dangerous == 0 ? "No dangerous-capability MCP tools detected"
                : InsightUtil.count(dangerous, "dangerous tools") + ", " + ungated + " with no blocking guardrail");

        r.addEvidence(new InsightResult.Evidence("dangerous_tools", "Dangerous-capability tools",
                Arrays.asList("tool", "capability", "mcpHost", "ungated"), rows, dangerous));

        r.addCta(new InsightResult.Cta("apply_policy", "Require approval on exec tools", "NAVIGATE", InsightRoutes.GUARDRAIL_POLICIES, new HashMap<>(), true));
        r.addCta(new InsightResult.Cta("view_assets", "View agentic assets", "NAVIGATE", InsightRoutes.AGENTIC_ASSETS, new HashMap<>(), false));
        return r;
    }
}
