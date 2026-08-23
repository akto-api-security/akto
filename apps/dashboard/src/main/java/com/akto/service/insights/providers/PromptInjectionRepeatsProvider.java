package com.akto.service.insights.providers;

import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.action.threat_detection.ThreatCategoryCount;
import com.akto.dto.GuardrailPolicies;
import com.akto.service.insights.*;
import com.akto.util.AgenticObserveUtil;
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
 * Actionable item #5 — "Prompt injection attempts, grouped by source". See
 * akto_insights_actionable_items card 5 (the "arcade -> Dev: 'Ignore previous instructions', 3
 * attempts... minutes apart" worked example): "a single accidental trip and a user probing the
 * guard three times are different events, and repetition is the only signal that separates them."
 *
 * Research findings this is built around:
 * - The doc's own pseudocode filter ("type = injection") doesn't work — the Go validator hardcodes
 *   the malicious-event `type` field to the literal "Rule-Based" for every violation kind, so it
 *   carries no discriminating signal at all. The real, queryable signal is subCategory ==
 *   "PromptInjection" (the exact RuleViolated string the built-in Content Filtering "Prompt
 *   Attacks" toggle emits) — same shape as item #4's "Secrets" discovery, so LIST scope reuses the
 *   same free get_subcategory_wise_count aggregate already in the shared bundle.
 * - Unlike item #4 (where a mislabeled credential could be hiding under any subCategory), an
 *   injection attempt IS reliably labeled, so DETAIL scope can narrow the raw-event page
 *   server-side via the filter's subCategory field, rather than sweeping every violation.
 * - No text-similarity library exists anywhere in this repo. "Evidence similarity" is implemented
 *   as exact equality after normalization (lowercase, trim, collapse whitespace — see
 *   EvidenceText.normalize), the same idiom already used elsewhere in this package.
 * - "Asset" is the destination service segment of host (AgenticObserveUtil.extractServiceName),
 *   distinct from "source"/actor (the device/user segment) — confirmed against the dashboard's own
 *   "Agentic Asset" vs "User" violation-table columns, which parse the same host string the same way.
 */
public class PromptInjectionRepeatsProvider extends AbstractInsightProvider {

    private static final String INJECTION_SUBCATEGORY = "PromptInjection";
    private static final int EVENT_PAGE_LIMIT = 1000;
    private static final int EVIDENCE_ROW_CAP = 20;
    private static final int HIGH_SEVERITY_REPEAT_COUNT = 3;

    public PromptInjectionRepeatsProvider() { super(InsightId.PROMPT_INJECTION_REPEATS, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        List<ThreatCategoryCount> subCategoryCounts = bundle.subCategoryCounts;
        if (subCategoryCounts == null) {
            return failed("THREAT_BACKEND", "Could not load violation counts");
        }

        long totalAttempts = subCategoryCounts.stream()
                .filter(c -> INJECTION_SUBCATEGORY.equalsIgnoreCase(c.getSubCategory()))
                .mapToLong(ThreatCategoryCount::getCount)
                .sum();

        InsightResult.Metric totalMetric = new InsightResult.Metric(
                "total_injection_attempts", "Prompt injection attempts",
                totalAttempts, null, "count", InsightUtil.count(totalAttempts, "attempts"), null);

        InsightResult result = skeleton();

        if (totalAttempts == 0) {
            result.setStatus(InsightResult.Status.READY.name());
            result.setHeadline("No prompt injection attempts detected in this window.");
            result.setMetrics(Collections.singletonList(totalMetric));
            result.setMetricsComplete(true);
            result.setDataGaps(new ArrayList<>());
            result.setEvidence(new ArrayList<>());
            result.setCtas(new ArrayList<>());
            return result;
        }

        if (scope == Scope.LIST) {
            result.setStatus(InsightResult.Status.PARTIAL.name());
            result.setHeadline(InsightUtil.count(totalAttempts, "attempts")
                    + "; grouping by source and finding repeats requires the detail view.");
            result.setMetrics(Collections.singletonList(totalMetric));
            result.setMetricsComplete(false);
            result.setDataGaps(Collections.singletonList(new InsightResult.Gap(
                    "PROVIDER", "DEFERRED_TO_DETAIL",
                    "Repeat detection (same source, asset, and phrasing) requires paging raw events, done only in the detail view.")));
            result.setEvidence(new ArrayList<>());
            result.setCtas(new ArrayList<>());
            return result;
        }

        // DETAIL: unlike item #4, this subCategory is reliably labeled, so the raw-event page can
        // be narrowed server-side instead of sweeping every violation.
        List<DashboardMaliciousEvent> events = bundle.fetchViolationEvents(scope, EVENT_PAGE_LIMIT,
                Collections.singletonMap("subCategory", Collections.singletonList(INJECTION_SUBCATEGORY)), null);
        if (events == null) events = new ArrayList<>();

        if (events.isEmpty()) {
            result.setStatus(InsightResult.Status.PARTIAL.name());
            result.setHeadline(InsightUtil.count(totalAttempts, "attempts") + ".");
            result.setMetrics(Collections.singletonList(totalMetric));
            result.setMetricsComplete(false);
            result.setDataGaps(Collections.singletonList(new InsightResult.Gap(
                    "THREAT_BACKEND", "NO_ROWS",
                    "Could not group by source — the violation lookup returned no rows despite "
                            + totalAttempts + " known attempts from the aggregate count.")));
            result.setEvidence(new ArrayList<>());
            result.setCtas(new ArrayList<>());
            return result;
        }

        Map<String, GuardrailPolicies> policyByLowerName = new HashMap<>();
        List<GuardrailPolicies> policies = bundle.policies;
        if (policies != null) {
            for (GuardrailPolicies p : policies) {
                if (p.getName() != null) {
                    policyByLowerName.put(lower(p.getName()), p);
                }
            }
        }
        Map<String, String> deviceIdToUsername = bundle.deviceIdToUsername != null
                ? bundle.deviceIdToUsername : Collections.emptyMap();

        Map<String, Attempt> groups = new HashMap<>();
        for (DashboardMaliciousEvent event : events) {
            String actor = StringUtils.defaultIfBlank(event.getActor(), "(unattributed)");
            String host = StringUtils.defaultIfBlank(event.getHost(), "");
            String asset = StringUtils.defaultIfBlank(AgenticObserveUtil.extractServiceName(host), "(unknown)");
            String evidenceText = EvidenceText.extract(event);
            String normalized = EvidenceText.normalize(evidenceText);
            String key = lower(actor) + " " + lower(asset) + " " + normalized;

            Attempt attempt = groups.computeIfAbsent(key, k -> new Attempt(actor, asset, evidenceText, event.getFilterId()));
            attempt.count++;
            attempt.firstSeen = Math.min(attempt.firstSeen, event.getTimestamp());
            attempt.lastSeen = Math.max(attempt.lastSeen, event.getTimestamp());
        }

        List<Attempt> repeatGroups = groups.values().stream()
                .filter(a -> a.count >= 2)
                .collect(Collectors.toList());

        Set<String> distinctSources = groups.values().stream().map(a -> a.actor).collect(Collectors.toCollection(HashSet::new));
        Set<String> distinctAssets = groups.values().stream().map(a -> a.asset).collect(Collectors.toCollection(HashSet::new));
        long attemptsInRepeats = repeatGroups.stream().mapToLong(a -> a.count).sum();
        long observedTotal = events.size();

        List<InsightResult.Metric> metrics = new ArrayList<>();
        metrics.add(totalMetric);
        metrics.add(new InsightResult.Metric(
                "distinct_sources", "Distinct sources",
                distinctSources.size(), null, "count", InsightUtil.count(distinctSources.size(), "sources"), null));
        metrics.add(new InsightResult.Metric(
                "distinct_assets", "Distinct assets targeted",
                distinctAssets.size(), null, "count", InsightUtil.count(distinctAssets.size(), "assets"), null));
        metrics.add(new InsightResult.Metric(
                "repeat_group_count", "Repeating attempts (same source, asset, phrasing)",
                repeatGroups.size(), groups.size(), "count",
                InsightUtil.ofTotal(repeatGroups.size(), groups.size(), "groups"), null));
        if (!repeatGroups.isEmpty()) {
            metrics.add(new InsightResult.Metric(
                    "attempts_in_repeat_groups", "Attempts belonging to a repeat",
                    attemptsInRepeats, observedTotal, "count",
                    InsightUtil.ofTotal(attemptsInRepeats, observedTotal, "attempts"), null));
        }

        int maxRepeatCount = repeatGroups.stream().mapToInt(a -> a.count).max().orElse(0);
        String severity;
        String headline;
        if (maxRepeatCount >= HIGH_SEVERITY_REPEAT_COUNT) {
            severity = "HIGH";
            headline = String.format(Locale.US,
                    "%s of the %s seen are repeats from the same source, asset, and phrasing — the worst repeats %d times.",
                    InsightUtil.count(attemptsInRepeats, "attempts"), InsightUtil.count(observedTotal, "attempts"), maxRepeatCount);
        } else if (!repeatGroups.isEmpty()) {
            severity = "MEDIUM";
            headline = String.format(Locale.US,
                    "%s repeat from the same source, asset, and phrasing (of %s seen) — repetition, not just severity, is what separates probing from an accident.",
                    InsightUtil.count(repeatGroups.size(), "cases"), InsightUtil.count(observedTotal, "attempts"));
        } else {
            severity = "LOW";
            headline = InsightUtil.count(observedTotal, "attempts") + " seen from "
                    + InsightUtil.count(distinctSources.size(), "sources") + "; none repeat with the same phrasing.";
        }

        List<String> caveats = new ArrayList<>();
        caveats.add("\"Source\" may be a device identifier when no live agent could resolve it to a username.");
        caveats.add("Repeats are detected by exact phrase match after normalizing case/whitespace — paraphrased or reworded repeat attempts are not linked together.");
        if (events.size() == EVENT_PAGE_LIMIT) {
            caveats.add("Attempts examined are capped at " + EVENT_PAGE_LIMIT + "; more may exist beyond this page.");
        }

        List<Attempt> topRepeats = repeatGroups.stream()
                .sorted(Comparator.comparingInt((Attempt a) -> a.count).reversed())
                .limit(EVIDENCE_ROW_CAP)
                .collect(Collectors.toList());

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Attempt attempt : topRepeats) {
            String displaySource = deviceIdToUsername.getOrDefault(attempt.actor, attempt.actor);
            Map<String, Object> row = new HashMap<>();
            row.put("Source", displaySource);
            row.put("Asset", attempt.asset);
            row.put("Sample phrase", truncateForDisplay(attempt.sampleText));
            row.put("Count", InsightUtil.count(attempt.count, "attempts"));
            row.put("Span", formatSpan(attempt.lastSeen - attempt.firstSeen));
            rows.add(row);
        }
        List<InsightResult.Evidence> evidence = new ArrayList<>();
        evidence.add(new InsightResult.Evidence("prompt_injection_repeats", "Repeating injection attempts",
                Arrays.asList("Source", "Asset", "Sample phrase", "Count", "Span"), rows, repeatGroups.size()));

        result.setStatus(InsightResult.Status.READY.name());
        result.setHeadline(headline);
        result.setSeverity(severity);
        result.setMetrics(metrics);
        result.setMetricsComplete(true);
        result.setDataGaps(new ArrayList<>());
        result.setCaveats(caveats);
        result.setEvidence(evidence);
        result.setCtas(buildCtas(topRepeats, policyByLowerName));
        return result;
    }

    private static class Attempt {
        final String actor;
        final String asset;
        final String sampleText;
        final String policyName;
        int count;
        long firstSeen = Long.MAX_VALUE;
        long lastSeen = Long.MIN_VALUE;

        Attempt(String actor, String asset, String sampleText, String policyName) {
            this.actor = actor;
            this.asset = asset;
            this.sampleText = sampleText;
            this.policyName = StringUtils.defaultIfBlank(policyName, "(unknown policy)");
        }
    }

    private static String truncateForDisplay(String text) {
        if (StringUtils.isBlank(text)) {
            return "(no evidence text)";
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= 160 ? flat : flat.substring(0, 160) + "…";
    }

    private static String formatSpan(long spanSeconds) {
        if (spanSeconds < 60) {
            return spanSeconds + " sec apart";
        }
        if (spanSeconds < 3600) {
            return (spanSeconds / 60) + " min apart";
        }
        if (spanSeconds < 86400) {
            return (spanSeconds / 3600) + " hr apart";
        }
        return (spanSeconds / 86400) + " days apart";
    }

    private static String lower(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.US);
    }


    private static List<InsightResult.Cta> buildCtas(List<Attempt> topRepeats, Map<String, GuardrailPolicies> policyByLowerName) {
        if (topRepeats.isEmpty()) {
            return new ArrayList<>();
        }
        Attempt worst = topRepeats.get(0); // already sorted by count desc
        GuardrailPolicies policy = policyByLowerName.get(lower(worst.policyName));
        Map<String, Object> params = new HashMap<>();
        params.put("source", worst.actor);
        params.put("asset", worst.asset);
        params.put("policyId", policy != null ? policy.getHexId() : null);
        params.put("policyName", worst.policyName);

        List<InsightResult.Cta> ctas = new ArrayList<>();
        ctas.add(new InsightResult.Cta("view_source_sessions", "View source sessions",
                "NAVIGATE", "/dashboard/guardrails/violations", params, true));
        ctas.add(new InsightResult.Cta("escalate_to_block_mode", "Escalate to block mode",
                "BULK_ACTION", "/dashboard/guardrails/policies", params, false));
        ctas.add(new InsightResult.Cta("investigate_user", "Investigate user",
                "NAVIGATE", "/dashboard/observe/users-and-devices",
                Collections.singletonMap("username", worst.actor), false));
        return ctas;
    }
}
