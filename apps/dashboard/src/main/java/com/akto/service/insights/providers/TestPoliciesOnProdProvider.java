package com.akto.service.insights.providers;

import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.action.threat_detection.ThreatCategoryCount;
import com.akto.dto.GuardrailPolicies;
import com.akto.service.insights.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Actionable item #2 — "Test policies running against production users": policies named for
 * testing/QA/temp work are still live against real traffic. See akto_insights_actionable_items
 * card 2 for the worked example this mirrors (karan-test, vrt-test, test-guardrail, ...).
 */
public class TestPoliciesOnProdProvider extends AbstractInsightProvider {

    private static final List<String> TEST_NAME_MARKERS = Arrays.asList("test", "qa", "temp", "delete");
    private static final int EVIDENCE_ROW_CAP = 20;
    private static final int EVENT_PAGE_LIMIT = 500;

    public TestPoliciesOnProdProvider() { super(InsightId.TEST_POLICIES_ON_PROD, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        List<GuardrailPolicies> policies = bundle.policies;
        if (policies == null) {
            return failed("POLICY_STORE", "Could not load guardrail policies");
        }

        List<GuardrailPolicies> allPolicies = policies;
        List<GuardrailPolicies> matched = allPolicies.stream()
                .filter(p -> isTestNamedPolicy(p.getName()))
                .collect(Collectors.toList());

        InsightResult.Metric policyCountMetric = new InsightResult.Metric(
                "test_named_policy_count", "Test-named policies",
                matched.size(), allPolicies.size(), "count",
                InsightUtil.ofTotal(matched.size(), allPolicies.size(), "policies"), null);

        InsightResult result = skeleton();
        if (matched.isEmpty()) {
            // A confident zero — no policy name matched, not a gap.
            result.setStatus(InsightResult.Status.READY.name());
            result.setHeadline("No test, QA, or temp-named policies found.");
            result.setMetrics(Collections.singletonList(policyCountMetric));
            result.setMetricsComplete(true);
            result.setDataGaps(new ArrayList<>());
            result.setEvidence(new ArrayList<>());
            result.setCtas(new ArrayList<>());
            return result;
        }

        Map<String, GuardrailPolicies> matchedByLowerName = new HashMap<>();
        for (GuardrailPolicies p : matched) {
            matchedByLowerName.put(lower(p.getName()), p);
        }

        List<ThreatCategoryCount> subCategoryCounts = bundle.subCategoryCounts;
        // subCategoryCounts is a threat-backend aggregate and the bundle never returns null
        // for it — a failed call is signalled by threatBackendAvailable instead, so an empty
        // list here can't be told apart from a real zero unless that flag is also checked.
        if (!bundle.threatBackendAvailable) {
            result.setStatus(InsightResult.Status.PARTIAL.name());
            result.setHeadline(matched.size() + " " + (matched.size() == 1 ? "policy is" : "policies are")
                    + " named for testing; violation counts are unavailable right now.");
            result.setMetrics(Collections.singletonList(policyCountMetric));
            result.setMetricsComplete(false);
            result.setDataGaps(Collections.singletonList(new InsightResult.Gap(
                    "THREAT_BACKEND", "REQUEST_FAILED",
                    "Violation counts unavailable; showing matched policy names only.")));
            result.setEvidence(Collections.singletonList(namesOnlyEvidence(matched)));
            result.setCtas(buildCtas(matched));
            return result;
        }

        List<ThreatCategoryCount> allCounts = subCategoryCounts;
        long totalViolations = allCounts.stream().mapToLong(ThreatCategoryCount::getCount).sum();
        Map<String, Long> violationsByPolicyLower = new HashMap<>();
        for (ThreatCategoryCount c : allCounts) {
            String key = lower(c.getCategory());
            if (matchedByLowerName.containsKey(key)) {
                violationsByPolicyLower.merge(key, (long) c.getCount(), Long::sum);
            }
        }
        long testViolations = violationsByPolicyLower.values().stream().mapToLong(Long::longValue).sum();
        double fraction = totalViolations > 0 ? (double) testViolations / totalViolations : 0.0;

        InsightResult.Metric violationCountMetric = new InsightResult.Metric(
                "test_policy_violation_count", "Violations from test-named policies",
                testViolations, totalViolations, "count",
                InsightUtil.ofTotal(testViolations, totalViolations, "violations"), null);
        InsightResult.Metric violationPercentMetric = new InsightResult.Metric(
                "test_policy_violation_percent", "Share of all violations",
                Math.round(fraction * 100), null, "percent",
                InsightUtil.percent(fraction), null);

        List<InsightResult.Metric> metrics = new ArrayList<>(Arrays.asList(
                policyCountMetric, violationCountMetric, violationPercentMetric));

        String headline = String.format(Locale.US,
                "%s (%s of all violations) come from %d %s named for testing.",
                InsightUtil.ofTotal(testViolations, totalViolations, "violations"), InsightUtil.percent(fraction),
                matched.size(), matched.size() == 1 ? "policy" : "policies");

        String severity = severityFromShare(fraction);

        if (scope == Scope.LIST) {
            result.setStatus(InsightResult.Status.PARTIAL.name());
            result.setHeadline(headline);
            result.setSeverity(severity);
            result.setMetrics(metrics);
            result.setMetricsComplete(false);
            result.setDataGaps(Collections.singletonList(new InsightResult.Gap(
                    "PROVIDER", "DEFERRED_TO_DETAIL",
                    "Real users affected and per-policy blocking behaviour require opening this insight's detail view.")));
            result.setEvidence(Collections.singletonList(namesOnlyEvidence(matched)));
            result.setCtas(buildCtas(matched));
            return result;
        }

        // DETAIL: page bounded raw events to attribute real users per matched policy.
        List<String> matchedNames = matched.stream().map(GuardrailPolicies::getName).collect(Collectors.toList());
        List<DashboardMaliciousEvent> events = bundle.fetchViolationEvents(scope, EVENT_PAGE_LIMIT,
                Collections.singletonMap("latestAttack", matchedNames), null);
        if (events == null) events = new ArrayList<>();

        // fetchAllMaliciousEvents swallows its own failures into an empty list, so an empty page
        // when the free aggregation above already counted testViolations > 0 is a signal the call
        // failed, not that there are genuinely zero matching events — treat it as a gap rather
        // than a confident zero (see plan.md's fetchAllMaliciousEvents correctness trap).
        if (events.isEmpty() && testViolations > 0) {
            result.setStatus(InsightResult.Status.PARTIAL.name());
            result.setHeadline(headline);
            result.setSeverity(severity);
            result.setMetrics(metrics);
            result.setMetricsComplete(false);
            result.setDataGaps(Collections.singletonList(new InsightResult.Gap(
                    "THREAT_BACKEND", "REQUEST_FAILED",
                    "Could not verify affected users — violation lookup returned no rows despite "
                            + testViolations + " known violations from the aggregate count.")));
            result.setEvidence(Collections.singletonList(namesOnlyEvidence(matched)));
            result.setCtas(buildCtas(matched));
            return result;
        }

        Map<String, Set<String>> actorsByPolicyLower = new HashMap<>();
        Set<String> allActors = new LinkedHashSet<>();
        for (DashboardMaliciousEvent event : events) {
            String actor = event.getActor();
            if (actor == null || actor.trim().isEmpty()) {
                continue;
            }
            allActors.add(actor);
            String policyKey = lower(event.getFilterId());
            if (matchedByLowerName.containsKey(policyKey)) {
                actorsByPolicyLower.computeIfAbsent(policyKey, k -> new LinkedHashSet<>()).add(actor);
            }
        }

        InsightResult.Metric realUsersMetric = new InsightResult.Metric(
                "real_users_affected", "Real users affected",
                allActors.size(), null, "count",
                InsightUtil.count(allActors.size(), "users"), null);
        metrics.add(realUsersMetric);

        // A policy literally named for testing, running in BLOCK mode, actively blocking real
        // production users, is arguably the single worst misconfiguration this insight can surface
        // — worse than any raw violation-share number — so it overrides the share-based severity
        // outright rather than just nudging it to HIGH.
        boolean anyBlockingWithUsers = matched.stream().anyMatch(p ->
                "block".equalsIgnoreCase(p.getBehaviour())
                        && !actorsByPolicyLower.getOrDefault(lower(p.getName()), Collections.emptySet()).isEmpty());
        String detailSeverity = anyBlockingWithUsers ? "CRITICAL" : severity;
        String detailHeadline = anyBlockingWithUsers
                ? headline + " At least one is in block mode and is actively blocking real production users."
                : headline;

        String concern;
        String impact;
        String remediation;
        if (anyBlockingWithUsers) {
            GuardrailPolicies blockingWithUsers = matched.stream()
                    .filter(p -> "block".equalsIgnoreCase(p.getBehaviour())
                            && !actorsByPolicyLower.getOrDefault(lower(p.getName()), Collections.emptySet()).isEmpty())
                    .findFirst().orElse(null);
            int affectedUsers = blockingWithUsers != null
                    ? actorsByPolicyLower.getOrDefault(lower(blockingWithUsers.getName()), Collections.emptySet()).size() : 0;
            concern = String.format(Locale.US,
                    "\"%s\" is named for testing but is running in block mode and has actively blocked %s.",
                    blockingWithUsers != null ? blockingWithUsers.getName() : "a test-named policy",
                    InsightUtil.count(affectedUsers, "real users"));
            impact = "A policy meant for testing is actively taking a production action against real users — "
                    + "whatever it was testing is now live, unreviewed enforcement.";
            remediation = "Switch this policy to alert mode or retire it immediately, then re-create it as a "
                    + "properly-scoped, non-test policy if the underlying rule is actually meant for production.";
        } else {
            concern = String.format(Locale.US,
                    "%s come from %d %s named for testing, still running against production traffic.",
                    InsightUtil.ofTotal(testViolations, totalViolations, "violations"), matched.size(),
                    matched.size() == 1 ? "policy" : "policies");
            impact = "A policy left over from testing wasn't necessarily reviewed for production scope or rules — "
                    + "it's either dead weight or an unreviewed control, neither of which should stay by accident.";
            remediation = "Retire the test-named policies that are no longer needed, or rename and rescope the ones "
                    + "that turned out to be doing real work so they're not mistaken for leftover test config.";
        }

        result.setStatus(InsightResult.Status.READY.name());
        result.setHeadline(detailHeadline);
        result.setSeverity(detailSeverity);
        result.setMetrics(metrics);
        result.setMetricsComplete(true);
        result.setDataGaps(new ArrayList<>());
        result.setCaveats(Collections.singletonList(
                "\"Real users affected\" is a lower bound: it only counts the most recent "
                        + EVENT_PAGE_LIMIT + " matching violations with a resolved actor."));
        result.setConcern(concern);
        result.setImpact(impact);
        result.setRemediation(remediation);
        result.setEvidence(Collections.singletonList(
                fullEvidence(matched, violationsByPolicyLower, actorsByPolicyLower)));
        result.setCtas(buildCtas(matched));
        return result;
    }

    private static boolean isTestNamedPolicy(String name) {
        if (name == null) {
            return false;
        }
        String n = lower(name);
        for (String marker : TEST_NAME_MARKERS) {
            if (n.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static String lower(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.US);
    }

    private static String severityFromShare(double fraction) {
        if (fraction >= 0.15) {
            return "HIGH";
        }
        if (fraction >= 0.05) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private static InsightResult.Evidence namesOnlyEvidence(List<GuardrailPolicies> matched) {
        List<GuardrailPolicies> capped = matched.stream().limit(EVIDENCE_ROW_CAP).collect(Collectors.toList());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (GuardrailPolicies p : capped) {
            Map<String, Object> row = new HashMap<>();
            row.put("Policy", p.getName());
            row.put("Mode", InsightUtil.humanizePolicyMode(p.getBehaviour()));
            rows.add(row);
        }
        return new InsightResult.Evidence("test_named_policies", "Policies named for testing",
                Arrays.asList("Policy", "Mode"), rows, matched.size());
    }

    private static InsightResult.Evidence fullEvidence(List<GuardrailPolicies> matched,
                                                         Map<String, Long> violationsByPolicyLower,
                                                         Map<String, Set<String>> actorsByPolicyLower) {
        List<GuardrailPolicies> sorted = matched.stream()
                .sorted((a, b) -> Long.compare(
                        violationsByPolicyLower.getOrDefault(lower(b.getName()), 0L),
                        violationsByPolicyLower.getOrDefault(lower(a.getName()), 0L)))
                .collect(Collectors.toList());
        List<GuardrailPolicies> capped = sorted.stream().limit(EVIDENCE_ROW_CAP).collect(Collectors.toList());

        List<Map<String, Object>> rows = new ArrayList<>();
        for (GuardrailPolicies p : capped) {
            String key = lower(p.getName());
            long violations = violationsByPolicyLower.getOrDefault(key, 0L);
            Set<String> actors = actorsByPolicyLower.getOrDefault(key, Collections.emptySet());
            String sample = actors.stream().limit(3).collect(Collectors.joining(", "));

            Map<String, Object> row = new HashMap<>();
            row.put("Policy", p.getName());
            row.put("Violations", InsightUtil.count(violations, "violations"));
            row.put("Mode", InsightUtil.humanizePolicyMode(p.getBehaviour()));
            row.put("Real users affected", actors.isEmpty() ? "—" : InsightUtil.count(actors.size(), "users"));
            row.put("Sample users", sample.isEmpty() ? "—" : sample);
            rows.add(row);
        }
        return new InsightResult.Evidence("test_named_policies", "Policies named for testing",
                Arrays.asList("Policy", "Violations", "Mode", "Real users affected", "Sample users"),
                rows, matched.size());
    }

    private static List<InsightResult.Cta> buildCtas(List<GuardrailPolicies> matched) {
        List<String> policyIds = matched.stream()
                .map(GuardrailPolicies::getHexId)
                .filter(id -> id != null)
                .collect(Collectors.toList());
        Map<String, Object> params = Collections.singletonMap("policyIds", policyIds);

        List<InsightResult.Cta> ctas = new ArrayList<>();
        ctas.add(new InsightResult.Cta("retire_test_policies", "Retire policy", "BULK_ACTION",
                "/dashboard/guardrails/policies", params, true));
        ctas.add(new InsightResult.Cta("scope_to_test_assets", "Scope to test assets only", "NAVIGATE",
                "/dashboard/guardrails/policies", params, false));
        ctas.add(new InsightResult.Cta("reassign_owner", "Reassign owner", "NAVIGATE",
                "/dashboard/guardrails/policies", params, false));
        return ctas;
    }

}
