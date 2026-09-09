package com.akto.service.insights.providers;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.SampleDataDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.service.insights.*;
import com.mongodb.client.model.Filters;

import java.util.*;

/**
 * Users reachable through MCP tools whose sample traffic shows a dangerous intent, and how
 * many of those tools have no blocking guardrail. Scoped to ApiCollections tagged as MCP
 * servers (ApiCollection.isMcpCollection()) — built-in agent tools (Bash, Read, ...) are
 * NOT included, since they don't live in any ApiCollection; reported as a data gap rather
 * than silently omitted. Classification is deferred to DETAIL scope only, since it costs one
 * LLM call per unclassified tool (InsightClassificationHelper.classifyToolDanger).
 */
public class DangerousCapabilityExposureProvider extends AbstractInsightProvider {

    private static final int MAX_TOOLS_CLASSIFIED = 50;

    public DangerousCapabilityExposureProvider() { super(InsightId.DANGEROUS_CAPABILITY_EXPOSURE, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        InsightResult r = skeleton();

        Map<Integer, ApiCollection> mcpCollectionById = new HashMap<>();
        for (ApiCollection c : bundle.collections) {
            if (c.isMcpCollection()) mcpCollectionById.put(c.getId(), c);
        }

        List<ApiInfo> tools = mcpCollectionById.isEmpty() ? Collections.emptyList() :
                ApiInfoDao.instance.findAll(Filters.in(SingleTypeInfo._COLLECTION_IDS, mcpCollectionById.keySet()));

        int unclassified = 0, dangerous = 0, ungated = 0, classified = 0;
        Set<String> usersReachable = new HashSet<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        List<GuardrailPolicies> policies = bundle.policies;

        for (ApiInfo tool : tools) {
            ApiInfo.ApiInfoKey key = tool.getId();
            if (key == null || key.getUrl() == null || key.getMethod() == null) continue;
            ApiCollection collection = mcpCollectionById.get(key.getApiCollectionId());
            if (collection == null) continue;

            if (scope != Scope.DETAIL || classified >= MAX_TOOLS_CLASSIFIED) {
                unclassified++;
                continue;
            }
            classified++;

            String sampleData = SampleDataDao.getLatestSampleData(key.getApiCollectionId(), key.getUrl(), key.getMethod().name());
            if (sampleData == null) {
                unclassified++;
                continue;
            }

            String toolName = InsightUtil.toolDisplayName(key.getUrl());
            InsightClassificationHelper.ToolDangerVerdict verdict = InsightClassificationHelper.classifyToolDanger(toolName, sampleData);
            if (!verdict.dangerous) continue;
            dangerous++;

            String hostName = collection.getHostName();
            boolean gated = false;
            for (GuardrailPolicies p : policies) {
                if (InsightUtil.isBlockingPolicy(p) && InsightUtil.policyCoversHost(p, hostName)) { gated = true; break; }
            }
            if (!gated) ungated++;

            String deviceId = InsightUtil.deviceIdOf(collection);
            String username = bundle.usernameForDevice(deviceId);
            if (username != null) usersReachable.add(username);

            if (rows.size() < 20) {
                Map<String, Object> row = new HashMap<>();
                row.put("tool", toolName);
                row.put("capability", verdict.capability);
                row.put("mcpHost", hostName);
                row.put("ungated", !gated);
                row.put("example", truncate(sampleData, 200));
                rows.add(row);
            }
        }

        r.addMetric(new InsightResult.Metric("dangerousTools", "Dangerous-capability tools", dangerous, "count", InsightUtil.count(dangerous, "tools")));
        r.addMetric(new InsightResult.Metric("ungatedDangerousTools", "Ungated dangerous tools", ungated, "count", InsightUtil.count(ungated, "tools")));
        r.addMetric(new InsightResult.Metric("usersReachable", "Users reachable via dangerous tools", usersReachable.size(), "count",
                InsightUtil.count(usersReachable.size(), "users")));

        boolean fullyClassified = unclassified == 0;
        r.setMetricsComplete(scope == Scope.DETAIL);
        r.setStatus((dangerous > 0 || !fullyClassified ? InsightResult.Status.PARTIAL : InsightResult.Status.NO_DATA).name());
        r.addDataGap(new InsightResult.Gap("TOOL_INVENTORY", "NOT_CONFIGURED",
                "Built-in agent tools (Bash, Read, Write, ...) are not included — only MCP server tools are classified."));
        if (unclassified > 0) {
            r.addDataGap(new InsightResult.Gap("TOOL_INVENTORY", "DEFERRED_TO_DETAIL", unclassified + " tools are classified only when this insight is opened."));
        }
        // Never claim "none detected" while tools are still unclassified — LIST scope never
        // classifies anything (see the scope check above), so dangerous==0 there means
        // "not checked yet", not "confirmed safe".
        r.setHeadline(!fullyClassified
                ? InsightUtil.count(unclassified, "MCP tools") + " pending classification"
                : dangerous == 0 ? "No dangerous-capability MCP tools detected"
                : InsightUtil.count(dangerous, "dangerous tools") + ", " + InsightUtil.count(ungated, "with no blocking guardrail"));

        if (dangerous > 0) {
            r.setSeverity(ungated > 0 ? "CRITICAL" : "MEDIUM");
            r.setConcern(InsightUtil.count(dangerous, "MCP tools") + " can take a dangerous action (file/system access, code execution, or similar) based on what they actually returned in a real sample."
                    + (ungated > 0 ? " " + InsightUtil.count(ungated, "of them") + " have no blocking guardrail in front of them." : " All of them are currently covered by a blocking guardrail."));
            r.setImpact("A dangerous tool with no blocking policy will execute whatever an agent (or a prompt-injected agent) asks it to, with no check in between — see the real sample response below for what it can actually do.");
            r.setRemediation(ungated > 0
                    ? "Apply a blocking guardrail requiring approval before these tools run, starting with the ungated ones."
                    : "Guardrails are already in place — periodically re-review as new tools are added.");
        }

        r.addEvidence(new InsightResult.Evidence("dangerous_tools", "Dangerous-capability tools",
                Arrays.asList("tool", "capability", "mcpHost", "ungated", "example"), rows, dangerous));

        r.addCta(new InsightResult.Cta("apply_policy", "Require approval on exec tools", "NAVIGATE", InsightRoutes.GUARDRAIL_POLICIES, new HashMap<>(), true));
        r.addCta(new InsightResult.Cta("view_assets", "View agentic assets", "NAVIGATE", InsightRoutes.AGENTIC_ASSETS, new HashMap<>(), false));
        return r;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        String oneLine = s.replaceAll("\\s+", " ").trim();
        return oneLine.length() > max ? oneLine.substring(0, max) + "…" : oneLine;
    }
}
