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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Actionable item #1 — "Alert fatigue: same user, same policy, over and over". See
 * akto_insights_actionable_items card 1 (the "3,447 violations collapse to 61 incidents"
 * worked example) and its closing structural note: make the incident, not the violation, the
 * unit of the page.
 *
 * "evidence_type" in the doc's own pseudocode isn't a stored field anywhere (see the taxonomy gap
 * noted against items 5/6/9) — this uses {@code subCategory} instead, which for a guardrail-written
 * event is the actual rule that fired (e.g. "PII-email", "Secrets", "PromptInjection"), a real field
 * rather than the buggy frontend Other/Config heuristic.
 *
 * "user" is often a raw device label rather than a resolved person (the doc's own "device 6dd3166c"
 * example, and card 9's read-only complaint) — this best-effort resolves it via the same live
 * module_info join guardrail device targeting already relies on
 * (ModuleInfoDao.fetchUsernameToDeviceIdsForEndpointShield, inverted in the shared bundle). A lookup
 * miss just falls back to the raw actor string.
 */
public class AlertFatigueProvider extends AbstractInsightProvider {

    private static final int EVENT_PAGE_LIMIT = 3000;
    private static final int EVIDENCE_ROW_CAP = 20;
    private static final long WINDOW_SECONDS = 24L * 60 * 60;
    private static final int REPEAT_THRESHOLD = 5;
    // A blocking policy still lets this many repeats through before we call it CRITICAL rather
    // than HIGH — HIGH already covers "any blocking policy involved"; this is for the case where
    // that policy is being hit at a volume that reads as sustained, ongoing abuse rather than a
    // handful of retries.
    private static final int CRITICAL_REPEAT_THRESHOLD = 50;

    public AlertFatigueProvider() { super(InsightId.ALERT_FATIGUE, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        List<ThreatCategoryCount> subCategoryCounts = bundle.subCategoryCounts;
        // subCategoryCounts is a threat-backend aggregate and the bundle never returns null
        // for it — a failed call is signalled by threatBackendAvailable instead, so an empty
        // list here can't be told apart from a real zero unless that flag is also checked.
        if (!bundle.threatBackendAvailable) {
            return failed("THREAT_BACKEND", "Could not load violation counts");
        }

        long totalViolations = subCategoryCounts.stream().mapToLong(ThreatCategoryCount::getCount).sum();
        long distinctBuckets = subCategoryCounts.stream()
                .map(c -> lower(c.getCategory()) + " " + lower(c.getSubCategory()))
                .distinct()
                .count();

        InsightResult.Metric totalMetric = new InsightResult.Metric(
                "total_violations", "Total violations",
                totalViolations, null, "count", InsightUtil.count(totalViolations, "violations"), null);

        InsightResult result = skeleton();

        if (totalViolations == 0) {
            result.setStatus(InsightResult.Status.READY.name());
            result.setHeadline("No violations in this window.");
            result.setMetrics(Collections.singletonList(totalMetric));
            result.setMetricsComplete(true);
            result.setDataGaps(new ArrayList<>());
            result.setEvidence(new ArrayList<>());
            result.setCtas(new ArrayList<>());
            return result;
        }

        InsightResult.Metric bucketMetric = new InsightResult.Metric(
                "policy_evidence_type_combinations", "Distinct policy + evidence-type combinations",
                distinctBuckets, null, "count", InsightUtil.count(distinctBuckets, "combinations"), null);
        List<InsightResult.Metric> baseMetrics = new ArrayList<>(Arrays.asList(totalMetric, bucketMetric));

        if (scope == Scope.LIST) {
            result.setStatus(InsightResult.Status.PARTIAL.name());
            result.setHeadline(InsightUtil.count(totalViolations, "violations") + " across "
                    + InsightUtil.count(distinctBuckets, "combinations")
                    + "; collapsing into per-user incidents requires the detail view.");
            result.setMetrics(baseMetrics);
            result.setMetricsComplete(false);
            result.setDataGaps(Collections.singletonList(new InsightResult.Gap(
                    "PROVIDER", "DEFERRED_TO_DETAIL",
                    "Incident collapsing (user x policy x evidence type) requires paging raw events, done only in the detail view.")));
            result.setEvidence(new ArrayList<>());
            result.setCtas(new ArrayList<>());
            return result;
        }

        // DETAIL: page raw events and collapse to (actor, policy, evidence-type) incidents.
        // "exclude" drops Skills Evaluations traffic (a separate, non-violation activity type) from
        // the incident grouping — same convention ViolationsPage.jsx uses for its Active tab, and a
        // no-op on non-ENDPOINT accounts where the concept doesn't exist. The LIST-scope aggregate
        // above has no equivalent exclusion (the backend's cheap aggregate endpoint doesn't support
        // it), so its total can run slightly high until this DETAIL view resolves the real count.
        List<DashboardMaliciousEvent> events = bundle.fetchViolationEvents(scope, EVENT_PAGE_LIMIT, null, "exclude");
        if (events == null) events = new ArrayList<>();

        if (events.isEmpty()) {
            result.setStatus(InsightResult.Status.PARTIAL.name());
            result.setHeadline(InsightUtil.count(totalViolations, "violations") + " across "
                    + InsightUtil.count(distinctBuckets, "combinations") + ".");
            result.setMetrics(baseMetrics);
            result.setMetricsComplete(false);
            result.setDataGaps(Collections.singletonList(new InsightResult.Gap(
                    "THREAT_BACKEND", "NO_ROWS",
                    "Could not collapse into incidents — the violation lookup returned no rows.")));
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

        Map<String, Incident> incidents = new HashMap<>();
        for (DashboardMaliciousEvent event : events) {
            String actor = StringUtils.defaultIfBlank(event.getActor(), "(unattributed)");
            String policy = StringUtils.defaultIfBlank(event.getFilterId(), "(unknown policy)");
            String evidenceType = InsightUtil.humanizeSubCategory(event.getSubCategory());
            String key = lower(actor) + " " + lower(policy) + " " + lower(evidenceType);
            Incident incident = incidents.computeIfAbsent(key, k -> new Incident(actor, policy, evidenceType));
            incident.timestamps.add(event.getTimestamp());
        }

        int flaggedCount = 0;
        long violationsInFlagged = 0;
        for (Incident incident : incidents.values()) {
            Collections.sort(incident.timestamps);
            incident.maxInWindow = maxCountWithinWindow(incident.timestamps, WINDOW_SECONDS);
            incident.flagged = incident.maxInWindow > REPEAT_THRESHOLD;
            if (incident.flagged) {
                flaggedCount++;
                violationsInFlagged += incident.timestamps.size();
            }
        }

        long observedTotal = events.size();
        long totalIncidents = incidents.size();

        List<InsightResult.Metric> metrics = new ArrayList<>(baseMetrics);
        metrics.add(new InsightResult.Metric(
                "total_incidents", "Distinct (user, policy, evidence type) incidents",
                totalIncidents, null, "count", InsightUtil.count(totalIncidents, "incidents"), null));
        metrics.add(new InsightResult.Metric(
                "flagged_incidents", "High-repeat incidents (more than " + REPEAT_THRESHOLD + " in 24h)",
                flaggedCount, totalIncidents, "count",
                InsightUtil.ofTotal(flaggedCount, totalIncidents, "incidents"), null));
        if (flaggedCount > 0) {
            metrics.add(new InsightResult.Metric(
                    "violations_in_flagged_incidents", "Violations from high-repeat incidents",
                    violationsInFlagged, observedTotal, "count",
                    InsightUtil.ofTotal(violationsInFlagged, observedTotal, "violations"), null));
        }

        boolean anyFlaggedBlocking = incidents.values().stream()
                .anyMatch(i -> i.flagged && isBlocking(policyByLowerName.get(lower(i.policy))));
        boolean anyFlaggedBlockingAtCriticalVolume = incidents.values().stream()
                .anyMatch(i -> i.flagged && i.maxInWindow >= CRITICAL_REPEAT_THRESHOLD
                        && isBlocking(policyByLowerName.get(lower(i.policy))));

        String dateRange = InsightUtil.dateRangeDescription(ctx.getStartTs(), ctx.getEndTs());
        String severity;
        String headline;
        if (flaggedCount > 0) {
            severity = anyFlaggedBlockingAtCriticalVolume ? "CRITICAL" : (anyFlaggedBlocking ? "HIGH" : "MEDIUM");
            headline = String.format(Locale.US,
                    "%s collapse to %s %s; %s repeat more than %d times in 24h%s.",
                    InsightUtil.count(observedTotal, "violations"), InsightUtil.count(totalIncidents, "incidents"),
                    dateRange,
                    InsightUtil.count(flaggedCount, "incidents"), REPEAT_THRESHOLD,
                    anyFlaggedBlockingAtCriticalVolume
                            ? ", including a blocking policy being hit at sustained high volume"
                            : (anyFlaggedBlocking ? ", including at least one policy in block mode" : ""));
        } else {
            severity = "LOW";
            headline = InsightUtil.count(observedTotal, "violations") + " collapse to "
                    + InsightUtil.count(totalIncidents, "incidents") + " " + dateRange
                    + "; none repeat more than " + REPEAT_THRESHOLD + " times in 24h.";
        }

        List<String> caveats = new ArrayList<>();
        caveats.add("\"User\" may be a device identifier when no live agent could resolve it to a username.");
        if (events.size() == EVENT_PAGE_LIMIT) {
            caveats.add("Violations examined are capped at " + EVENT_PAGE_LIMIT + "; more may exist beyond this page.");
        }

        List<Incident> topIncidents = incidents.values().stream()
                .sorted(Comparator.comparingInt((Incident i) -> i.timestamps.size()).reversed())
                .limit(EVIDENCE_ROW_CAP)
                .collect(Collectors.toList());

        String concern = null;
        String impact = null;
        String remediation = null;
        Incident worstFlagged = topIncidents.stream().filter(i -> i.flagged).findFirst().orElse(null);
        if (worstFlagged != null) {
            String displayActor = deviceIdToUsername.getOrDefault(worstFlagged.actor, worstFlagged.actor);
            concern = String.format(Locale.US,
                    "%s repeat more than %d times in 24h — the worst is \"%s\" against policy \"%s\" (%s), repeating %d times.",
                    InsightUtil.count(flaggedCount, "incidents"), REPEAT_THRESHOLD, displayActor, worstFlagged.policy,
                    worstFlagged.evidenceType, worstFlagged.maxInWindow);
            impact = anyFlaggedBlocking
                    ? "The same user is repeatedly hitting a policy that's supposed to be blocking — either the "
                            + "policy isn't stopping them, or they keep trying anyway. Either way it's worth a look, "
                            + "and repeat noise like this makes it easier to miss a genuinely new threat."
                    : "Not a blocked action, so no harm is being actively prevented — but repeat noise like this "
                            + "makes it easier to miss a genuinely new incident buried in the same alerts.";
            remediation = anyFlaggedBlocking
                    ? String.format(Locale.US, "Investigate why \"%s\" keeps recurring against a blocking policy — "
                            + "confirm the block is actually taking effect, or add a scoped exception if this is expected.",
                            displayActor)
                    : String.format(Locale.US, "Add an exception or suppression rule for \"%s\" on \"%s\" if this "
                            + "is expected behavior, or investigate why it keeps repeating if it isn't.",
                            displayActor, worstFlagged.policy);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Incident incident : topIncidents) {
            String displayActor = deviceIdToUsername.getOrDefault(incident.actor, incident.actor);
            GuardrailPolicies policy = policyByLowerName.get(lower(incident.policy));
            String mode = InsightUtil.humanizePolicyMode(policy != null ? policy.getBehaviour() : null);

            Map<String, Object> row = new HashMap<>();
            row.put("User", displayActor);
            row.put("Policy", incident.policy);
            row.put("Evidence type", incident.evidenceType);
            row.put("Total", InsightUtil.count(incident.timestamps.size(), "violations"));
            row.put("Max in 24h", String.valueOf(incident.maxInWindow));
            row.put("Mode", mode);
            rows.add(row);
        }
        List<InsightResult.Evidence> evidence = new ArrayList<>();
        evidence.add(new InsightResult.Evidence("alert_fatigue_incidents", "Top incidents by repeat count",
                Arrays.asList("User", "Policy", "Evidence type", "Total", "Max in 24h", "Mode"),
                rows, incidents.size()));

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
        result.setCtas(buildCtas(topIncidents));
        return result;
    }

    private static boolean isBlocking(GuardrailPolicies policy) {
        return policy != null && "block".equalsIgnoreCase(policy.getBehaviour());
    }

    /** Max number of timestamps (ascending, epoch seconds) falling within any single windowSeconds span. */
    private static int maxCountWithinWindow(List<Long> sortedTimestamps, long windowSeconds) {
        int maxCount = 0;
        int left = 0;
        for (int right = 0; right < sortedTimestamps.size(); right++) {
            while (sortedTimestamps.get(right) - sortedTimestamps.get(left) > windowSeconds) {
                left++;
            }
            maxCount = Math.max(maxCount, right - left + 1);
        }
        return maxCount;
    }

    private static class Incident {
        final String actor;
        final String policy;
        final String evidenceType;
        final List<Long> timestamps = new ArrayList<>();
        int maxInWindow;
        boolean flagged;

        Incident(String actor, String policy, String evidenceType) {
            this.actor = actor;
            this.policy = policy;
            this.evidenceType = evidenceType;
        }
    }

    private static String lower(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.US);
    }

    private static List<InsightResult.Cta> buildCtas(List<Incident> topIncidents) {
        Incident worst = topIncidents.stream().filter(i -> i.flagged).findFirst().orElse(null);
        if (worst == null) {
            return new ArrayList<>();
        }
        // "?policy=<name>" is the one deep-link GuardrailPolicies.jsx reads (opens that policy's
        // edit drawer). Violations reads "?policy=" and "?user=" too (seeds its existing card-click
        // filters on arrival) — see ViolationsPage.jsx's deep-link effect.
        Map<String, Object> policyParams = InsightUtil.guardrailPolicyParams(worst.policy);
        Map<String, Object> incidentParams = new HashMap<>();
        incidentParams.put("policy", worst.policy);
        incidentParams.put("user", worst.actor);

        List<InsightResult.Cta> ctas = new ArrayList<>();
        ctas.add(new InsightResult.Cta("remove_user_from_scope", "Remove user from policy scope",
                "BULK_ACTION", "/dashboard/guardrails/policies", policyParams, true));
        ctas.add(new InsightResult.Cta("add_exception", "Add exception",
                "NAVIGATE", "/dashboard/guardrails/policies", policyParams, false));
        ctas.add(new InsightResult.Cta("switch_to_incident_view", "Switch to incident view",
                "NAVIGATE", "/dashboard/guardrails/violations", incidentParams, false));
        return ctas;
    }
}
