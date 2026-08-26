package com.akto.service.insights.providers;

import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.action.threat_detection.ThreatCategoryCount;
import com.akto.dto.GuardrailPolicies;
import com.akto.service.insights.*;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Actionable item #7 — "Policies in alert mode that are catching real things". See
 * akto_insights_actionable_items card 7 (the "claude_settings_risk fired 82 times in alert mode,
 * 14 involved credential or PII patterns — none were blocked" worked example): "the counterpart to
 * card 1 — card 1 says stop blocking what doesn't matter; this says start blocking what does."
 *
 * Unlike items #1-6, this one needs no raw-event paging for a complete answer: the "policy mode
 * (block vs alert) queryable per policy" dependency the doc's own Dependencies table lists as
 * blocking this card is already satisfied — GuardrailPolicies.behaviour ("block"/"warn"/"alert"/
 * "approval") was confirmed to exist back when item #1 needed it — and "involving credentials/PII/
 * injection" is answerable from the exact subCategory literals items #4/#5/#6 already established
 * ("Secrets", "PII-&lt;type&gt;", "PromptInjection"). Both pieces come straight out of the shared,
 * free bundle fields (policies + subCategoryCounts) with no per-event lookup at all, so LIST scope
 * is a complete, non-deferred answer (status READY, no data gap) — a deliberate contrast with every
 * prior item in this set. DETAIL scope adds one optional enrichment (which specific users triggered
 * the dangerous hits on the worst alert-mode policy) via bounded event paging, but a failure there
 * only drops that one bonus metric — it never degrades the already-complete LIST-scope answer.
 */
public class AlertModeRealHitsProvider extends AbstractInsightProvider {

    private static final String ALERT_BEHAVIOUR = "alert";
    private static final String SECRETS_SUBCATEGORY = "Secrets";
    private static final String PII_PREFIX = "PII-";
    private static final String PROMPT_INJECTION_SUBCATEGORY = "PromptInjection";
    private static final int EVENT_PAGE_LIMIT = 500;
    private static final int EVIDENCE_ROW_CAP = 20;
    // Any Secrets hit sitting in alert mode is CRITICAL on its own — a live credential going
    // undetected is categorically worse than an undetected PII/prompt-injection hit. High enough
    // dangerous-hit volume escalates too, even without a Secrets hit specifically.
    private static final int CRITICAL_DANGEROUS_HITS_THRESHOLD = 25;

    public AlertModeRealHitsProvider() { super(InsightId.ALERT_MODE_REAL_HITS, 1); }

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
        List<GuardrailPolicies> alertModePolicies = activePolicies.stream()
                .filter(p -> ALERT_BEHAVIOUR.equalsIgnoreCase(p.getBehaviour()))
                .collect(Collectors.toList());
        // Taxonomy-mismatch fallback (confirmed on real accounts): subCategory frequently carries
        // the firing policy's own name instead of "Secrets"/"PII-<type>"/"PromptInjection", so a
        // dangerous alert-mode policy can look entirely clean by exact-literal match alone.
        Map<String, GuardrailPolicies> policyByLowerName = new HashMap<>();
        for (GuardrailPolicies p : policies) {
            if (p.getName() != null) {
                policyByLowerName.put(lower(p.getName()), p);
            }
        }

        InsightResult result = skeleton();
        InsightResult.Metric policyCountMetric = new InsightResult.Metric(
                "alert_mode_policy_count", "Active policies in alert mode",
                alertModePolicies.size(), activePolicies.size(), "count",
                InsightUtil.ofTotal(alertModePolicies.size(), activePolicies.size(), "policies"), null);

        if (alertModePolicies.isEmpty()) {
            result.setStatus(InsightResult.Status.READY.name());
            result.setHeadline("No active policies are running in alert mode.");
            result.setMetrics(Collections.singletonList(policyCountMetric));
            result.setMetricsComplete(true);
            result.setDataGaps(new ArrayList<>());
            result.setEvidence(new ArrayList<>());
            result.setCtas(new ArrayList<>());
            return result;
        }

        Map<String, Long> hitsByPolicyLower = new HashMap<>();
        Map<String, Long> dangerousHitsByPolicyLower = new HashMap<>();
        Map<String, Long> secretsHitsByPolicyLower = new HashMap<>();
        long totalAllViolations = 0;
        for (ThreatCategoryCount c : subCategoryCounts) {
            long count = c.getCount();
            totalAllViolations += count;
            String policyKey = lower(c.getCategory());
            hitsByPolicyLower.merge(policyKey, count, Long::sum);
            GuardrailPolicies firingPolicy = policyByLowerName.get(policyKey);
            boolean secretsHit = SECRETS_SUBCATEGORY.equalsIgnoreCase(c.getSubCategory())
                    || InsightUtil.policyHasSecretsDetection(firingPolicy);
            boolean dangerousHit = isDangerous(c.getSubCategory()) || secretsHit
                    || InsightUtil.policyHasPiiDetection(firingPolicy)
                    || InsightUtil.policyHasPromptInjectionDetection(firingPolicy);
            if (dangerousHit) {
                dangerousHitsByPolicyLower.merge(policyKey, count, Long::sum);
            }
            if (secretsHit) {
                secretsHitsByPolicyLower.merge(policyKey, count, Long::sum);
            }
        }

        long alertModeTotalHits = 0;
        long alertModeDangerousHits = 0;
        long alertModeSecretsHits = 0;
        for (GuardrailPolicies p : alertModePolicies) {
            String key = lower(p.getName());
            alertModeTotalHits += hitsByPolicyLower.getOrDefault(key, 0L);
            alertModeDangerousHits += dangerousHitsByPolicyLower.getOrDefault(key, 0L);
            alertModeSecretsHits += secretsHitsByPolicyLower.getOrDefault(key, 0L);
        }
        double alertModeShare = totalAllViolations > 0 ? (double) alertModeTotalHits / totalAllViolations : 0.0;

        List<InsightResult.Metric> metrics = new ArrayList<>();
        metrics.add(policyCountMetric);
        metrics.add(new InsightResult.Metric(
                "alert_mode_violation_share", "Violation volume covered by alert-mode policies",
                Math.round(alertModeShare * 100), null, "percent", InsightUtil.percent(alertModeShare), null));
        metrics.add(new InsightResult.Metric(
                "credential_pii_injection_hits_in_alert_mode", "Credential/PII/injection hits never blocked",
                alertModeDangerousHits, alertModeTotalHits, "count",
                InsightUtil.ofTotal(alertModeDangerousHits, alertModeTotalHits, "hits"), null));

        boolean criticalDangerousHits = alertModeSecretsHits > 0
                || alertModeDangerousHits >= CRITICAL_DANGEROUS_HITS_THRESHOLD;
        String severity = alertModeDangerousHits > 0
                ? (criticalDangerousHits ? "CRITICAL" : "HIGH")
                : (alertModeTotalHits > 0 ? "MEDIUM" : "LOW");
        String headline = alertModeDangerousHits > 0
                ? String.format(Locale.US,
                    "%s involve credentials, PII, or prompt injection — detected, but never blocked%s.",
                    InsightUtil.ofTotal(alertModeDangerousHits, alertModeTotalHits, "alert-mode-policy hits"),
                    alertModeSecretsHits > 0 ? ", including live credential leaks" : "")
                : String.format(Locale.US,
                    "%s covering %s of all violation volume are in alert mode, but none of their hits involve credentials, PII, or prompt injection.",
                    InsightUtil.count(alertModePolicies.size(), "policies"), InsightUtil.percent(alertModeShare));

        List<GuardrailPolicies> sortedByDangerous = alertModePolicies.stream()
                .sorted(Comparator.comparingLong((GuardrailPolicies p) ->
                        dangerousHitsByPolicyLower.getOrDefault(lower(p.getName()), 0L)).reversed())
                .collect(Collectors.toList());

        List<String> caveats = new ArrayList<>();
        caveats.add("A hit counts as credential/PII/injection if its subCategory says Secrets, PII-<type>, or PromptInjection, or if the policy that caught it has that scanner enabled — subCategory alone understates this on some accounts. Other alert-mode hits (e.g. denied topics, banned substrings) still aren't counted here even though they also went undetected.");

        Set<String> distinctUsers = null;
        GuardrailPolicies worst = sortedByDangerous.isEmpty() ? null : sortedByDangerous.get(0);
        long worstDangerousHits = worst != null ? dangerousHitsByPolicyLower.getOrDefault(lower(worst.getName()), 0L) : 0;

        if (scope == Scope.DETAIL && worst != null && worstDangerousHits > 0) {
            distinctUsers = tryResolveDistinctUsers(bundle, scope, worst, subCategoryCounts);
            if (distinctUsers == null) {
                caveats.add("Could not resolve which users triggered the worst policy's undetected hits — the event lookup was unavailable.");
            }
        }

        List<String> columns = Arrays.asList("Policy", "Total hits", "Credential/PII/injection hits", "% dangerous", "Users (worst policy)");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (GuardrailPolicies p : sortedByDangerous.stream().limit(EVIDENCE_ROW_CAP).collect(Collectors.toList())) {
            String key = lower(p.getName());
            long total = hitsByPolicyLower.getOrDefault(key, 0L);
            long dangerous = dangerousHitsByPolicyLower.getOrDefault(key, 0L);
            double sharePct = total > 0 ? (double) dangerous / total : 0.0;

            List<String> cells = new ArrayList<>(Arrays.asList(
                    p.getName(), InsightUtil.count(total, "hits"), InsightUtil.count(dangerous, "hits"),
                    InsightUtil.percent(sharePct)));
            cells.add((p == worst && distinctUsers != null) ? InsightUtil.count(distinctUsers.size(), "users") : "—");

            Map<String, Object> row = new HashMap<>();
            for (int i = 0; i < columns.size(); i++) row.put(columns.get(i), cells.get(i));
            rows.add(row);
        }
        List<InsightResult.Evidence> evidence = new ArrayList<>();
        evidence.add(new InsightResult.Evidence("alert_mode_policies", "Alert-mode policies by risk",
                columns, rows, alertModePolicies.size()));

        String concern = null;
        String impact = null;
        String remediation = null;
        if (worst != null && worstDangerousHits > 0) {
            concern = String.format(Locale.US,
                    "\"%s\" is in alert mode and has caught %s that were never blocked.",
                    worst.getName(), InsightUtil.count(worstDangerousHits, "credential, PII, or prompt-injection hits"));
            impact = "Alert mode detects but doesn't stop the action — every one of these hits reached its "
                    + "destination anyway. Detection without enforcement here is the same as having no control at all.";
            remediation = "Simulate the impact of switching \"" + worst.getName() + "\" to block mode first, then "
                    + "make the switch if it doesn't disrupt legitimate traffic.";
        }

        result.setStatus(InsightResult.Status.READY.name());
        result.setHeadline(headline);
        result.setSeverity(severity);
        result.setMetrics(metrics);
        result.setMetricsComplete(true);
        result.setDataGaps(new ArrayList<>());
        result.setConcern(concern);
        result.setImpact(impact);
        result.setRemediation(remediation);
        result.setCaveats(caveats);
        result.setEvidence(evidence);
        result.setCtas(buildCtas(alertModePolicies, worst, worstDangerousHits));
        return result;
    }

    /** Bonus enrichment only — a failure here doesn't degrade the already-complete LIST-scope
     *  answer, unlike items #1-6 where DETAIL-scope paging was load-bearing. Returns null on any
     *  failure so the caller can tell "checked, found nobody" (empty set) apart from "couldn't check". */
    private Set<String> tryResolveDistinctUsers(InsightDataBundle bundle, Scope scope, GuardrailPolicies policy,
                                                 List<ThreatCategoryCount> subCategoryCounts) {
        String policyName = policy.getName();
        List<String> dangerousSubCategories = subCategoryCounts.stream()
                .filter(c -> policyName.equalsIgnoreCase(c.getCategory()) && isDangerous(c.getSubCategory()))
                .map(ThreatCategoryCount::getSubCategory)
                .distinct()
                .collect(Collectors.toList());
        Map<String, Object> filters = new HashMap<>();
        filters.put("latestAttack", Collections.singletonList(policyName));
        if (!dangerousSubCategories.isEmpty()) {
            // Precise path: this policy's own dangerous hits are exactly-labeled, narrow by them too.
            filters.put("subCategory", dangerousSubCategories);
        } else if (!(InsightUtil.policyHasSecretsDetection(policy) || InsightUtil.policyHasPiiDetection(policy)
                || InsightUtil.policyHasPromptInjectionDetection(policy))) {
            // Neither an exact-labeled dangerous hit nor a policy config that would explain the
            // fallback count — nothing to resolve users from.
            return null;
        }
        // Fallback path: the aggregate only recognized this policy as dangerous via its own scanner
        // config (subCategory didn't carry the expected literal) — fetch all of this policy's hits
        // instead of narrowing by a subCategory value we know doesn't apply here.
        List<DashboardMaliciousEvent> events = bundle.fetchViolationEvents(scope, EVENT_PAGE_LIMIT, filters, null);
        if (events == null || events.isEmpty()) {
            return null; // ambiguous per the same fetchAllMaliciousEvents trap as other items — don't claim zero
        }
        return events.stream()
                .map(e -> StringUtils.defaultIfBlank(e.getActor(), "(unattributed)"))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private static boolean isDangerous(String subCategory) {
        if (subCategory == null) {
            return false;
        }
        return SECRETS_SUBCATEGORY.equalsIgnoreCase(subCategory)
                || PROMPT_INJECTION_SUBCATEGORY.equalsIgnoreCase(subCategory)
                || subCategory.regionMatches(true, 0, PII_PREFIX, 0, PII_PREFIX.length());
    }

    private static String lower(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.US);
    }

    private static List<InsightResult.Cta> buildCtas(List<GuardrailPolicies> alertModePolicies, GuardrailPolicies worst, long worstDangerousHits) {
        if (worst == null || worstDangerousHits == 0) {
            return new ArrayList<>();
        }
        // "?policy=<name>" opens that one policy's edit drawer (GuardrailPolicies.jsx) or seeds
        // ViolationsPage.jsx's policy-filter — for the single most-critical policy specifically.
        Map<String, Object> worstPolicyParams = InsightUtil.guardrailPolicyParams(worst.getName());
        // "?policyIds=id1,id2,..." pre-selects (and scopes the table to) every alert-mode policy
        // for the bulk "switch to block" action — not just the worst one.
        Map<String, Object> allAlertModeParams = Collections.singletonMap("policyIds",
                alertModePolicies.stream().map(GuardrailPolicies::getHexId).filter(java.util.Objects::nonNull).collect(Collectors.toList()));
        // Violations has no generic "alert mode" filter — filter by the actual alert-mode policy
        // names instead, which gets the same practical result (their hits, all in one place).
        Map<String, Object> alertModeViolationsParams = Collections.singletonMap("policy",
                alertModePolicies.stream().map(GuardrailPolicies::getName).collect(Collectors.joining(",")));

        List<InsightResult.Cta> ctas = new ArrayList<>();
        ctas.add(new InsightResult.Cta("switch_to_block_mode", "Switch to block mode",
                "BULK_ACTION", "/dashboard/guardrails/policies", allAlertModeParams, true));
        ctas.add(new InsightResult.Cta("simulate_impact_first", "Simulate impact first",
                "NAVIGATE", "/dashboard/guardrails/policies", worstPolicyParams, false));
        ctas.add(new InsightResult.Cta("view_the_hits", "View the hits",
                "NAVIGATE", "/dashboard/guardrails/violations", alertModeViolationsParams, false));
        return ctas;
    }
}
