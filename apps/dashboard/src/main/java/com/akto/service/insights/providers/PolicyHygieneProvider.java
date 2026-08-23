package com.akto.service.insights.providers;

import com.akto.action.threat_detection.AbstractThreatDetectionAction;
import com.akto.action.threat_detection.ThreatCategoryCount;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.GuardrailPolicies;
import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightId;
import com.akto.service.insights.InsightProvider;
import com.akto.service.insights.InsightResult;
import com.akto.service.insights.MetricFormat;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Actionable item #8 — "Policy hygiene: dead policies and uncovered assets". See
 * akto_insights_actionable_items card 8 (the "6 policies have never fired... 9 actively-used
 * surfaces have no policy attached... Akto-Guardrail-Eng covers 75% of all violations" worked
 * example).
 *
 * Like item #7, both halves are fully computable at LIST scope — no raw event paging needed for a
 * complete answer:
 * - "Dead policies" is just policies + the free subCategoryCounts aggregate, same as items #1/#7.
 * - "Uncovered assets" needs two NEW bundle fields (activeCollections, collectionLastTrafficSeen),
 *   because — a real finding from this item's own research, not assumed — a guardrails-native
 *   proxy (violation events, mcp_audit_info) is structurally disqualified: the Go enforcement layer
 *   only ever runs a scanner against a host AFTER resolving which policies are in scope for it
 *   (GetPoliciesForMcpServer), so a host with zero scoped policies can never produce a violation by
 *   construction. Using violation data to find "hosts with no scoped policy" would always report
 *   zero, silently. Asset discovery (ApiCollectionsDao) is unconditional, not policy-gated, so it's
 *   the only source that actually sees traffic a policy never touched.
 *
 * The scope-matching logic is intentionally a faithful mirror of the real Go validator's
 * GetPoliciesForMcpServer (mcp/policy_manager.go in the pinned akto-endpoint-shield dependency),
 * confirmed fresh for this item rather than trusted from an older, partially-stale memory of a
 * differently-named function: case-insensitive EXACT match only (no suffix/substring matching),
 * and — the trap most likely to be gotten backwards — an empty (non-null) configured server list
 * with applyToAllServers=false means the policy applies to NO host, not "all hosts". Only
 * applyToAllServers=true (or a YAML-sourced policy, which this doesn't distinguish — see caveat in
 * the result) makes a policy apply everywhere.
 */
public class PolicyHygieneProvider implements InsightProvider {

    private static final int EVIDENCE_ROW_CAP = 20;

    @Override
    public InsightId getInsightId() {
        return InsightId.POLICY_HYGIENE;
    }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope,
                                  AbstractThreatDetectionAction threatClient) {
        List<GuardrailPolicies> policies = bundle.getPolicies();
        if (policies == null) {
            return failed("Could not load guardrail policies");
        }
        List<ThreatCategoryCount> subCategoryCounts = bundle.getSubCategoryCounts();
        if (subCategoryCounts == null) {
            return failed("Could not load violation counts");
        }

        List<GuardrailPolicies> activePolicies = policies.stream()
                .filter(GuardrailPolicies::isActive)
                .collect(Collectors.toList());

        Map<String, Long> hitsByPolicyLower = new HashMap<>();
        long totalViolations = 0;
        for (ThreatCategoryCount c : subCategoryCounts) {
            totalViolations += c.getCount();
            hitsByPolicyLower.merge(lower(c.getCategory()), (long) c.getCount(), Long::sum);
        }

        List<GuardrailPolicies> deadPolicies = activePolicies.stream()
                .filter(p -> hitsByPolicyLower.getOrDefault(lower(p.getName()), 0L) == 0L)
                .collect(Collectors.toList());

        GuardrailPolicies topPolicy = activePolicies.stream()
                .max(Comparator.comparingLong(p -> hitsByPolicyLower.getOrDefault(lower(p.getName()), 0L)))
                .orElse(null);
        long topPolicyHits = topPolicy != null ? hitsByPolicyLower.getOrDefault(lower(topPolicy.getName()), 0L) : 0;
        double topPolicyShare = totalViolations > 0 ? (double) topPolicyHits / totalViolations : 0.0;

        InsightResult result = base();
        List<InsightResult.Metric> metrics = new ArrayList<>();
        metrics.add(new InsightResult.Metric(
                "dead_policy_count", "Active policies with zero hits this window",
                deadPolicies.size(), activePolicies.size(), "count",
                MetricFormat.ofTotal(deadPolicies.size(), activePolicies.size()), null));
        if (topPolicy != null && totalViolations > 0) {
            metrics.add(new InsightResult.Metric(
                    "top_policy_violation_share", "Share of all violations from the single busiest policy",
                    Math.round(topPolicyShare * 100), null, "percent", MetricFormat.percent(topPolicyShare), null));
        }

        // Uncovered assets — see class doc for why this can't come from violation data.
        List<ApiCollection> activeCollections = bundle.getActiveCollections();
        Map<Integer, Integer> collectionLastTrafficSeen = bundle.getCollectionLastTrafficSeen();
        List<String> caveats = new ArrayList<>();
        caveats.add("Coverage checks a policy's configured server scope (applyToAllServers / selectedMcpServersV2 / selectedAgentServersV2) case-insensitively by exact hostname match, mirroring the real enforcement matcher exactly — it does not distinguish YAML/CI-CD-sourced policies, which the real matcher always treats as applying everywhere.");

        List<ApiCollection> uncoveredAssets = new ArrayList<>();
        long activelyUsedCount = 0;
        if (activeCollections != null) {
            Map<Integer, Integer> lastSeen = collectionLastTrafficSeen != null ? collectionLastTrafficSeen : new HashMap<>();
            if (collectionLastTrafficSeen == null) {
                caveats.add("Traffic recency data was unavailable; falling back to each asset's creation time to decide whether it's actively used.");
            }

            java.util.Set<String> coveredHostsLower = new java.util.HashSet<>();
            boolean anyApplyToAll = activePolicies.stream().anyMatch(GuardrailPolicies::isApplyToAllServers);
            for (GuardrailPolicies p : activePolicies) {
                for (GuardrailPolicies.SelectedServer s : p.getEffectiveSelectedMcpServers()) {
                    if (s.getName() != null) {
                        coveredHostsLower.add(lower(s.getName()));
                    }
                }
                for (GuardrailPolicies.SelectedServer s : p.getEffectiveSelectedAgentServers()) {
                    if (s.getName() != null) {
                        coveredHostsLower.add(lower(s.getName()));
                    }
                }
            }

            for (ApiCollection c : activeCollections) {
                if (StringUtils.isBlank(c.getHostName())) {
                    continue;
                }
                Integer lastActive = lastSeen.get(c.getId());
                int effectiveTs = lastActive != null ? lastActive : c.getStartTs();
                if (!isWithinWindow(effectiveTs, ctx.getStartTs(), ctx.getEndTs())) {
                    continue;
                }
                activelyUsedCount++;
                boolean covered = anyApplyToAll || coveredHostsLower.contains(lower(c.getHostName()));
                if (!covered) {
                    uncoveredAssets.add(c);
                }
            }
        } else {
            caveats.add("Could not load the asset inventory — the uncovered-assets half of this insight is unavailable; dead-policy results above are unaffected.");
        }

        if (activeCollections != null) {
            metrics.add(new InsightResult.Metric(
                    "uncovered_asset_count", "Actively-used surfaces with no guardrail in scope",
                    uncoveredAssets.size(), activelyUsedCount, "count",
                    MetricFormat.ofTotal(uncoveredAssets.size(), activelyUsedCount), null));
        }

        String severity;
        String headline;
        if (!uncoveredAssets.isEmpty()) {
            severity = "HIGH";
            headline = String.format(Locale.US, "%s %s no guardrail in scope, and %s %s never fired.",
                    MetricFormat.count(uncoveredAssets.size(), "actively-used surface"), hasHave(uncoveredAssets.size()),
                    MetricFormat.count(deadPolicies.size(), "policy"), hasHave(deadPolicies.size()));
        } else if (!deadPolicies.isEmpty() || topPolicyShare >= 0.5) {
            severity = "MEDIUM";
            headline = String.format(Locale.US, "%s %s never fired%s.",
                    MetricFormat.count(deadPolicies.size(), "policy"), hasHave(deadPolicies.size()),
                    topPolicy != null && topPolicyShare >= 0.5
                            ? String.format(Locale.US, "; \"%s\" alone covers %s of all violations", topPolicy.getName(), MetricFormat.percent(topPolicyShare))
                            : "");
        } else {
            severity = "LOW";
            headline = "No dead policies and no uncovered actively-used surfaces found.";
        }

        List<InsightResult.Evidence> evidence = new ArrayList<>();
        if (!deadPolicies.isEmpty()) {
            List<InsightResult.Evidence.Row> rows = new ArrayList<>();
            for (GuardrailPolicies p : deadPolicies.stream().limit(EVIDENCE_ROW_CAP).collect(Collectors.toList())) {
                Map<String, Object> raw = new HashMap<>();
                raw.put("policyId", p.getHexId());
                raw.put("policyName", p.getName());
                rows.add(new InsightResult.Evidence.Row(Arrays.asList(
                        p.getName(), StringUtils.defaultIfBlank(p.getBehaviour(), "unknown"),
                        ageDescription(p.getCreatedTimestamp())), raw));
            }
            evidence.add(new InsightResult.Evidence("dead_policies", "Policies with zero hits",
                    Arrays.asList("Policy", "Mode", "Created"), rows, deadPolicies.size()));
        }
        if (!uncoveredAssets.isEmpty()) {
            List<InsightResult.Evidence.Row> rows = new ArrayList<>();
            for (ApiCollection c : uncoveredAssets.stream().limit(EVIDENCE_ROW_CAP).collect(Collectors.toList())) {
                Map<String, Object> raw = new HashMap<>();
                raw.put("apiCollectionId", c.getId());
                raw.put("hostName", c.getHostName());
                rows.add(new InsightResult.Evidence.Row(Arrays.asList(c.getHostName(), ageDescription(c.getStartTs())), raw));
            }
            evidence.add(new InsightResult.Evidence("uncovered_assets", "Actively-used surfaces with no guardrail",
                    Arrays.asList("Asset", "First seen"), rows, uncoveredAssets.size()));
        }

        result.setStatus(InsightResult.Status.READY.name());
        result.setHeadline(headline);
        result.setSeverity(severity);
        result.setMetrics(metrics);
        result.setMetricsComplete(true);
        result.setDataGaps(new ArrayList<>());
        result.setCaveats(caveats);
        result.setEvidence(evidence);
        result.setCtas(buildCtas(deadPolicies, uncoveredAssets));
        return result;
    }

    private static boolean isWithinWindow(int ts, int startTs, int endTs) {
        if (startTs <= 0 && endTs <= 0) {
            return true; // "all time" — same convention AgenticObserveAction uses for startTimestamp <= 0
        }
        if (startTs > 0 && ts < startTs) {
            return false;
        }
        if (endTs > 0 && ts > endTs) {
            return false;
        }
        return true;
    }

    private static String ageDescription(int epochSeconds) {
        if (epochSeconds <= 0) {
            return "unknown";
        }
        long days = Math.max(0, (Context.now() - epochSeconds) / 86400L);
        if (days == 0) {
            return "today";
        }
        return MetricFormat.count(days, "day") + " ago";
    }

    private static String hasHave(long count) {
        return count == 1 ? "has" : "have";
    }

    private static String lower(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.US);
    }

    private InsightResult base() {
        InsightResult r = new InsightResult();
        r.setInsightId(getInsightId().name());
        r.setTitle(getInsightId().getDefaultTitle());
        r.setCategory(getInsightId().getCategory().name());
        r.setCaveats(new ArrayList<>());
        return r;
    }

    private InsightResult failed(String reason) {
        InsightResult r = InsightResult.noData(getInsightId(), reason);
        r.setDataGaps(new ArrayList<>(java.util.Collections.singletonList(
                new InsightResult.Gap("POLICY_STORE", "REQUEST_FAILED", reason))));
        return r;
    }

    private static List<InsightResult.Cta> buildCtas(List<GuardrailPolicies> deadPolicies, List<ApiCollection> uncoveredAssets) {
        List<InsightResult.Cta> ctas = new ArrayList<>();
        if (!deadPolicies.isEmpty()) {
            Map<String, Object> params = new HashMap<>();
            params.put("policyIds", deadPolicies.stream().map(GuardrailPolicies::getHexId).collect(Collectors.toList()));
            ctas.add(new InsightResult.Cta("retire_dead_policies", "Retire dead policies",
                    "BULK_ACTION", "/dashboard/guardrails/policies", params, true));
        }
        if (!uncoveredAssets.isEmpty()) {
            Map<String, Object> params = new HashMap<>();
            params.put("hostNames", uncoveredAssets.stream().map(ApiCollection::getHostName).collect(Collectors.toList()));
            ctas.add(new InsightResult.Cta("apply_baseline_policy", "Apply baseline policy to uncovered assets",
                    "BULK_ACTION", "/dashboard/guardrails/policies", params, deadPolicies.isEmpty()));
        }
        ctas.add(new InsightResult.Cta("review_scope", "Review scope",
                "NAVIGATE", "/dashboard/guardrails/policies", java.util.Collections.emptyMap(), false));
        return ctas;
    }
}
