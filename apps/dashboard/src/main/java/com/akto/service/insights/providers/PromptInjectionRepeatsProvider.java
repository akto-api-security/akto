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
    // Past this many identical repeats, it reads as sustained, high-volume probing rather than
    // "happened a few times" — escalate past HIGH.
    private static final int CRITICAL_SEVERITY_REPEAT_COUNT = 10;

    public PromptInjectionRepeatsProvider() { super(InsightId.PROMPT_INJECTION_REPEATS, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        List<ThreatCategoryCount> subCategoryCounts = bundle.subCategoryCounts;
        // subCategoryCounts is a threat-backend aggregate and the bundle never returns null
        // for it — a failed call is signalled by threatBackendAvailable instead, so an empty
        // list here can't be told apart from a real zero unless that flag is also checked.
        if (!bundle.threatBackendAvailable) {
            return failed("THREAT_BACKEND", "Could not load violation counts");
        }

        // Taxonomy-mismatch fallback (confirmed on real accounts): subCategory frequently carries
        // the firing policy's own name instead of "PromptInjection", so a policy with prompt-attack
        // detection enabled can go entirely uncounted by an exact-literal match. Recognize its hits
        // too, via the policy's own config, not just the literal.
        Map<String, GuardrailPolicies> policyByLowerName = new HashMap<>();
        if (bundle.policies != null) {
            for (GuardrailPolicies p : bundle.policies) {
                if (p.getName() != null) {
                    policyByLowerName.put(lower(p.getName()), p);
                }
            }
        }
        long totalAttempts = subCategoryCounts.stream()
                .filter(c -> INJECTION_SUBCATEGORY.equalsIgnoreCase(c.getSubCategory())
                        || InsightUtil.policyHasPromptInjectionDetection(policyByLowerName.get(lower(c.getCategory()))))
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
            // A real, if approximate, severity from the volume alone — the actual repeat pattern
            // still requires paging raw events (below), but a reader shouldn't see "Coming soon" for
            // a card that already has real attempts behind it. Same template as
            // TestPoliciesOnProdProvider's LIST-scope severityFromShare; DETAIL can still move this
            // up (e.g. to CRITICAL for sustained repeats) or down once the real grouping is known.
            result.setStatus(InsightResult.Status.PARTIAL.name());
            result.setHeadline(InsightUtil.count(totalAttempts, "attempts")
                    + "; grouping by source and finding repeats requires the detail view.");
            result.setSeverity(severityFromVolume(totalAttempts));
            result.setMetrics(Collections.singletonList(totalMetric));
            result.setMetricsComplete(false);
            result.setDataGaps(Collections.singletonList(new InsightResult.Gap(
                    "PROVIDER", "DEFERRED_TO_DETAIL",
                    "Repeat detection (same source, asset, and phrasing) requires paging raw events, done only in the detail view.")));
            result.setEvidence(new ArrayList<>());
            result.setCtas(new ArrayList<>());
            return result;
        }

        // DETAIL: same taxonomy-mismatch fallback as the LIST-scope count above means this can no
        // longer narrow the server-side query to subCategory == "PromptInjection" alone (a policy's
        // real hits may not carry that literal) — sweep unfiltered instead, like item #4's
        // credential scan, and keep only events that are actually injection-shaped by either signal.
        // "exclude" drops Skills Evaluations traffic — same convention as AlertFatigueProvider (see
        // its comment): a skill's own safety-scan verdict isn't a live-traffic injection attempt, and
        // on a real account it can outnumber genuine attempts by 70:1, drowning any real repeat
        // pattern under near-unique per-skill evidence text that structurally never repeats.
        List<DashboardMaliciousEvent> rawEvents = bundle.fetchViolationEvents(scope, EVENT_PAGE_LIMIT, null, "exclude");
        if (rawEvents == null) rawEvents = new ArrayList<>();
        boolean rawSweepCapped = rawEvents.size() == EVENT_PAGE_LIMIT;
        List<DashboardMaliciousEvent> events = rawEvents.stream()
                .filter(e -> INJECTION_SUBCATEGORY.equalsIgnoreCase(e.getSubCategory())
                        || InsightUtil.policyHasPromptInjectionDetection(policyByLowerName.get(lower(e.getFilterId()))))
                .collect(Collectors.toList());

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
        if (maxRepeatCount >= CRITICAL_SEVERITY_REPEAT_COUNT) {
            severity = "CRITICAL";
            headline = String.format(Locale.US,
                    "%s of the %s seen are repeats from the same source, asset, and phrasing — the worst repeats %d times, sustained high-volume probing rather than a handful of attempts.",
                    InsightUtil.count(attemptsInRepeats, "attempts"), InsightUtil.count(observedTotal, "attempts"), maxRepeatCount);
        } else if (maxRepeatCount >= HIGH_SEVERITY_REPEAT_COUNT) {
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
        caveats.add("A hit counts as prompt injection if its subCategory says so, or if the policy that caught it has prompt-attack detection enabled — subCategory alone understates this on some accounts.");
        if (rawSweepCapped) {
            caveats.add("Examined the most recent " + EVENT_PAGE_LIMIT + " violations account-wide (not just injection ones) to apply that fallback; more injection attempts may exist beyond this page.");
        }

        List<Attempt> topRepeats = repeatGroups.stream()
                .sorted(Comparator.comparingInt((Attempt a) -> a.count).reversed())
                .limit(EVIDENCE_ROW_CAP)
                .collect(Collectors.toList());

        String concern = null;
        String impact = null;
        String remediation = null;
        if (!topRepeats.isEmpty()) {
            Attempt worstRepeat = topRepeats.get(0);
            String displaySource = deviceIdToUsername.getOrDefault(worstRepeat.actor, worstRepeat.actor);
            concern = String.format(Locale.US,
                    "\"%s\" has repeated the same prompt-injection phrasing against \"%s\" %d times.",
                    displaySource, worstRepeat.asset, worstRepeat.count);
            impact = "A single accidental trip doesn't repeat with identical phrasing — this pattern reads as "
                    + "deliberate probing to find a gap in the guardrail, not a one-off mistake.";
            remediation = String.format(Locale.US,
                    "Escalate the policy protecting \"%s\" to block mode if it isn't already, and investigate \"%s\" directly.",
                    worstRepeat.asset, displaySource);
        }

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
        result.setConcern(concern);
        result.setImpact(impact);
        result.setRemediation(remediation);
        result.setEvidence(evidence);
        result.setCtas(buildCtas(topRepeats));
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

    /** LIST-scope approximation from raw volume alone, before repeats are known — an injection
     *  attempt is a stronger per-event signal than routine PII noise, so even a single one is
     *  worth flagging rather than waiting for a volume threshold. */
    private static String severityFromVolume(long totalAttempts) {
        if (totalAttempts >= HIGH_SEVERITY_REPEAT_COUNT * 3L) {
            return "HIGH";
        }
        if (totalAttempts >= HIGH_SEVERITY_REPEAT_COUNT) {
            return "MEDIUM";
        }
        return "LOW";
    }


    private static List<InsightResult.Cta> buildCtas(List<Attempt> topRepeats) {
        if (topRepeats.isEmpty()) {
            return new ArrayList<>();
        }
        Attempt worst = topRepeats.get(0); // already sorted by count desc
        // ViolationsPage.jsx reads "?user=" and "?policy=" and seeds its existing card-click
        // filters on arrival. There's no working "asset" filter on that page today (the Agentic
        // Asset column has no column filter at all), so it's left out rather than passed as dead
        // weight.
        Map<String, Object> params = new HashMap<>();
        params.put("user", worst.actor);
        params.put("policy", worst.policyName);

        List<InsightResult.Cta> ctas = new ArrayList<>();
        ctas.add(new InsightResult.Cta("view_source_sessions", "View source sessions",
                "NAVIGATE", "/dashboard/guardrails/violations", params, true));
        ctas.add(new InsightResult.Cta("escalate_to_block_mode", "Escalate to block mode",
                "BULK_ACTION", "/dashboard/guardrails/policies", InsightUtil.guardrailPolicyParams(worst.policyName), false));
        ctas.add(new InsightResult.Cta("investigate_user", "Investigate user",
                "NAVIGATE", "/dashboard/observe/users-and-devices",
                InsightUtil.usersAndDevicesFilterParams(Collections.singletonList(worst.actor)), false));
        return ctas;
    }
}
