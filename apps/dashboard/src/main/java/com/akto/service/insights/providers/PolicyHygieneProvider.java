package com.akto.service.insights.providers;

import com.akto.action.threat_detection.ThreatCategoryCount;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.GuardrailPolicies;
import com.akto.service.insights.*;
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
public class PolicyHygieneProvider extends AbstractInsightProvider {

    private static final int EVIDENCE_ROW_CAP = 20;

    public PolicyHygieneProvider() { super(InsightId.POLICY_HYGIENE, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        List<GuardrailPolicies> policies = bundle.policies;
        if (policies == null) {
            return failed("POLICY_STORE", "Could not load guardrail policies");
        }
        List<ThreatCategoryCount> subCategoryCounts = bundle.subCategoryCounts;
        // subCategoryCounts is a threat-backend aggregate and the bundle never returns null
        // for it — a failed call is signalled by threatBackendAvailable instead, so an empty
        // list here can't be told apart from a real zero unless that flag is also checked.
        if (!bundle.threatBackendAvailable) {
            return failed("THREAT_BACKEND", "Could not load violation counts");
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

        InsightResult result = skeleton();
        List<InsightResult.Metric> metrics = new ArrayList<>();
        metrics.add(new InsightResult.Metric(
                "dead_policy_count", "Active policies with zero hits this window",
                deadPolicies.size(), activePolicies.size(), "count",
                InsightUtil.ofTotal(deadPolicies.size(), activePolicies.size(), "policies"), null));
        // Two independent findings can both be true at once (dead policies AND a concentrated
        // busiest policy) — build concern/impact/remediation as a combined draft covering whichever
        // apply, rather than only ever describing one. These feed InsightResult's concern/impact/
        // remediation (the deterministic draft the narrative model may enrich with real evidence)
        // — not caveats, which are reserved for data-quality/methodology notes.
        List<String> concernParts = new ArrayList<>();
        List<String> impactParts = new ArrayList<>();
        List<String> remediationParts = new ArrayList<>();

        if (!deadPolicies.isEmpty()) {
            concernParts.add(InsightUtil.count(deadPolicies.size(), "active policies") + " caught zero violations this window.");
            impactParts.add("A policy with no hits either means the risk it targets genuinely isn't occurring here, "
                    + "or that its scope or rules have drifted from real usage — either way, that coverage is unverified.");
            remediationParts.add("Investigate why each zero-hit policy is quiet, and retire or rescope the ones that no longer apply.");
        }

        if (topPolicy != null && totalViolations > 0) {
            metrics.add(new InsightResult.Metric(
                    "top_policy_violation_share", "Enforcement concentration risk",
                    Math.round(topPolicyShare * 100), null, "percent",
                    InsightUtil.percent(topPolicyShare) + " of violations caught by \"" + topPolicy.getName() + "\"", null));
            // Always name the concentrated policy and state the concern — not gated behind a share
            // threshold, since "which policy is busiest and why does it matter" should be answered
            // every time a busiest policy exists, not only once it crosses a severity line.
            boolean concentrated = topPolicyShare >= 0.3;
            concernParts.add(String.format(Locale.US, "\"%s\" is the busiest policy this window, accounting for %s of all violations.",
                    topPolicy.getName(), InsightUtil.percent(topPolicyShare)));
            impactParts.add(concentrated
                    ? "Concentration like this is a single point of failure for detection coverage — if that policy "
                            + "is disabled, misconfigured, or narrowed in scope, a majority of current enforcement "
                            + "could disappear at once."
                    : "Not yet a concentration risk on its own, but worth tracking as it grows.");
            remediationParts.add(concentrated
                    ? String.format(Locale.US, "Consider splitting \"%s\" into narrower, single-purpose policies "
                            + "(e.g. separate sensitive-data detection from banned-topic detection) so enforcement "
                            + "isn't concentrated in one broad rule.", topPolicy.getName())
                    : "Periodically confirm its scope and rules still match what it's actually catching.");
        }

        String topPolicyConcern = concernParts.isEmpty() ? null : String.join(" ", concernParts);
        String topPolicyImpact = impactParts.isEmpty() ? null : String.join(" ", impactParts);
        String topPolicyRemediation = remediationParts.isEmpty() ? null : String.join(" ", remediationParts);

        // Uncovered assets — see class doc for why this can't come from violation data.
        List<ApiCollection> activeCollections = bundle.activeCollections;
        Map<Integer, Integer> collectionLastTrafficSeen = bundle.collectionLastTrafficSeen;
        List<String> caveats = new ArrayList<>();
        caveats.add("\"Assets with no guardrail\" means an actively-used API or agent endpoint (an \"asset\") that no active policy's server scope currently covers.");
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
            // "0 of N" reads as ambiguous partial data at a glance — when it's a real, clean zero,
            // say so plainly instead of making the reader do the division. The frontend renders
            // this exact wording as a checkmark, keyed off this metric's id — see MetricCardComp.
            String uncoveredFormatted = uncoveredAssets.isEmpty()
                    ? "All " + InsightUtil.count(activelyUsedCount, "assets") + " covered"
                    : InsightUtil.ofTotal(uncoveredAssets.size(), activelyUsedCount, "assets");
            metrics.add(new InsightResult.Metric(
                    "uncovered_asset_count", "Actively-used assets with no guardrail in scope",
                    uncoveredAssets.size(), activelyUsedCount, "count", uncoveredFormatted, null));
        }

        String severity;
        String headline;
        if (!uncoveredAssets.isEmpty()) {
            severity = "HIGH";
            headline = String.format(Locale.US, "%s %s no guardrail in scope, and %s %s never fired.",
                    InsightUtil.count(uncoveredAssets.size(), "actively-used assets"), hasHave(uncoveredAssets.size()),
                    InsightUtil.count(deadPolicies.size(), "policies"), hasHave(deadPolicies.size()));
        } else if (!deadPolicies.isEmpty() || topPolicyShare >= 0.5) {
            severity = "MEDIUM";
            headline = String.format(Locale.US, "%s %s never fired%s.",
                    InsightUtil.count(deadPolicies.size(), "policies"), hasHave(deadPolicies.size()),
                    topPolicy != null && topPolicyShare >= 0.5
                            ? String.format(Locale.US, "; \"%s\" alone covers %s of all violations", topPolicy.getName(), InsightUtil.percent(topPolicyShare))
                            : "");
        } else {
            severity = "LOW";
            headline = "No dead policies and no uncovered actively-used assets found.";
        }

        List<InsightResult.Evidence> evidence = new ArrayList<>();
        if (!deadPolicies.isEmpty()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (GuardrailPolicies p : deadPolicies.stream().limit(EVIDENCE_ROW_CAP).collect(Collectors.toList())) {
                Map<String, Object> row = new HashMap<>();
                row.put("Policy", p.getName());
                row.put("Mode", InsightUtil.humanizePolicyMode(p.getBehaviour()));
                row.put("Created", ageDescription(p.getCreatedTimestamp()));
                rows.add(row);
            }
            evidence.add(new InsightResult.Evidence("dead_policies", "Policies with zero hits",
                    Arrays.asList("Policy", "Mode", "Created"), rows, deadPolicies.size()));
        }
        if (!uncoveredAssets.isEmpty()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (ApiCollection c : uncoveredAssets.stream().limit(EVIDENCE_ROW_CAP).collect(Collectors.toList())) {
                Map<String, Object> row = new HashMap<>();
                row.put("Asset", c.getHostName());
                row.put("First seen", ageDescription(c.getStartTs()));
                rows.add(row);
            }
            evidence.add(new InsightResult.Evidence("uncovered_assets", "Actively-used assets with no guardrail",
                    Arrays.asList("Asset", "First seen"), rows, uncoveredAssets.size()));
        }

        result.setStatus(InsightResult.Status.READY.name());
        result.setHeadline(headline);
        result.setSeverity(severity);
        result.setMetrics(metrics);
        result.setMetricsComplete(true);
        result.setDataGaps(new ArrayList<>());
        result.setCaveats(caveats);
        result.setConcern(topPolicyConcern);
        result.setImpact(topPolicyImpact);
        result.setRemediation(topPolicyRemediation);
        result.setEvidence(evidence);
        result.setCtas(buildCtas(deadPolicies, uncoveredAssets, topPolicy, topPolicyShare));
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
        return InsightUtil.count(days, "days") + " ago";
    }

    private static String hasHave(long count) {
        return count == 1 ? "has" : "have";
    }

    private static String lower(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.US);
    }

    private static List<InsightResult.Cta> buildCtas(List<GuardrailPolicies> deadPolicies, List<ApiCollection> uncoveredAssets,
                                                       GuardrailPolicies topPolicy, double topPolicyShare) {
        List<InsightResult.Cta> ctas = new ArrayList<>();
        if (!deadPolicies.isEmpty()) {
            Map<String, Object> params = new HashMap<>();
            params.put("policyIds", deadPolicies.stream().map(GuardrailPolicies::getHexId).collect(Collectors.toList()));
            ctas.add(new InsightResult.Cta("retire_dead_policies", "Retire dead policies",
                    "BULK_ACTION", "/dashboard/guardrails/policies", params, true));
        }
        if (!uncoveredAssets.isEmpty()) {
            // No "bulk apply a policy to these new hosts" action exists on the destination page
            // today (unlike retiring existing policies, which the bulk-select below genuinely
            // supports) — this stays a plain navigate rather than promising a bulk action that
            // isn't real yet; the evidence table below already lists every uncovered asset.
            ctas.add(new InsightResult.Cta("create_policy_for_uncovered_assets", "Create a policy for uncovered assets",
                    "NAVIGATE", "/dashboard/guardrails/policies", java.util.Collections.emptyMap(), deadPolicies.isEmpty()));
        }
        // "Review scope" opens the busiest policy directly (its edit drawer) rather than just
        // landing on an unfiltered policies list — that's the one policy this insight is actually
        // asking the reader to look at. Falls back to a bare navigate only when there's no busiest
        // policy to open (e.g. zero violations this window).
        Map<String, Object> reviewParams = topPolicy != null
                ? InsightUtil.guardrailPolicyParams(topPolicy.getName())
                : java.util.Collections.emptyMap();
        ctas.add(new InsightResult.Cta("review_scope", "Review scope",
                "NAVIGATE", "/dashboard/guardrails/policies", reviewParams,
                deadPolicies.isEmpty() && uncoveredAssets.isEmpty()));
        return ctas;
    }
}
