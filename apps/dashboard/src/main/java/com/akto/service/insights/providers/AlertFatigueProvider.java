package com.akto.service.insights.providers;

import com.akto.action.threat_detection.AbstractThreatDetectionAction;
import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.action.threat_detection.ThreatCategoryCount;
import com.akto.dto.GuardrailPolicies;
import com.akto.service.insights.DataGap;
import com.akto.service.insights.EvidenceRow;
import com.akto.service.insights.EvidenceTable;
import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightCta;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightId;
import com.akto.service.insights.InsightMetric;
import com.akto.service.insights.InsightProvider;
import com.akto.service.insights.InsightResult;
import com.akto.service.insights.InsightScope;
import com.akto.service.insights.InsightStatus;
import com.akto.service.insights.Loaded;
import com.akto.service.insights.MetricFormat;
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
public class AlertFatigueProvider implements InsightProvider {

    private static final int EVENT_PAGE_LIMIT = 3000;
    private static final int EVIDENCE_ROW_CAP = 20;
    private static final long WINDOW_SECONDS = 24L * 60 * 60;
    private static final int REPEAT_THRESHOLD = 5;

    @Override
    public InsightId getInsightId() {
        return InsightId.ALERT_FATIGUE;
    }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, InsightScope scope,
                                  AbstractThreatDetectionAction threatClient) {
        Loaded<List<ThreatCategoryCount>> subCatLoaded = bundle.getSubCategoryCounts();
        if (!subCatLoaded.isOk()) {
            return failed("Could not load violation counts: " + subCatLoaded.getFailureReason());
        }

        long totalViolations = subCatLoaded.getValue().stream().mapToLong(ThreatCategoryCount::getCount).sum();
        long distinctBuckets = subCatLoaded.getValue().stream()
                .map(c -> lower(c.getCategory()) + " " + lower(c.getSubCategory()))
                .distinct()
                .count();

        InsightMetric totalMetric = new InsightMetric(
                "total_violations", "Total violations",
                totalViolations, null, "count", MetricFormat.count(totalViolations, "violation"), null);

        InsightResult result = base();

        if (totalViolations == 0) {
            result.setStatus(InsightStatus.READY.name());
            result.setHeadline("No violations in this window.");
            result.setMetrics(Collections.singletonList(totalMetric));
            result.setMetricsComplete(true);
            result.setDataGaps(new ArrayList<>());
            result.setEvidence(new ArrayList<>());
            result.setCtas(new ArrayList<>());
            return result;
        }

        InsightMetric bucketMetric = new InsightMetric(
                "policy_evidence_type_combinations", "Distinct policy + evidence-type combinations",
                distinctBuckets, null, "count", MetricFormat.count(distinctBuckets, "combination"), null);
        List<InsightMetric> baseMetrics = new ArrayList<>(Arrays.asList(totalMetric, bucketMetric));

        if (scope == InsightScope.LIST) {
            result.setStatus(InsightStatus.PARTIAL.name());
            result.setHeadline(MetricFormat.count(totalViolations, "violation") + " across "
                    + MetricFormat.count(distinctBuckets, "combination")
                    + "; collapsing into per-user incidents requires the detail view.");
            result.setMetrics(baseMetrics);
            result.setMetricsComplete(false);
            result.setDataGaps(Collections.singletonList(new DataGap(
                    "PROVIDER", "DEFERRED_TO_DETAIL",
                    "Incident collapsing (user x policy x evidence type) requires paging raw events, done only in the detail view.")));
            result.setEvidence(new ArrayList<>());
            result.setCtas(new ArrayList<>());
            return result;
        }

        // DETAIL: page raw events and collapse to (actor, policy, evidence-type) incidents.
        List<DashboardMaliciousEvent> events;
        try {
            events = threatClient.fetchAllMaliciousEvents(ctx.getStartTs(), ctx.getEndTs(), EVENT_PAGE_LIMIT, null);
        } catch (Exception e) {
            events = new ArrayList<>();
        }

        if (events.isEmpty()) {
            result.setStatus(InsightStatus.PARTIAL.name());
            result.setHeadline(MetricFormat.count(totalViolations, "violation") + " across "
                    + MetricFormat.count(distinctBuckets, "combination") + ".");
            result.setMetrics(baseMetrics);
            result.setMetricsComplete(false);
            result.setDataGaps(Collections.singletonList(new DataGap(
                    "THREAT_BACKEND", "NO_ROWS",
                    "Could not collapse into incidents — the violation lookup returned no rows.")));
            result.setEvidence(new ArrayList<>());
            result.setCtas(new ArrayList<>());
            return result;
        }

        Map<String, GuardrailPolicies> policyByLowerName = new HashMap<>();
        Loaded<List<GuardrailPolicies>> policiesLoaded = bundle.getPolicies();
        if (policiesLoaded.isOk()) {
            for (GuardrailPolicies p : policiesLoaded.getValue()) {
                if (p.getName() != null) {
                    policyByLowerName.put(lower(p.getName()), p);
                }
            }
        }
        Map<String, String> deviceIdToUsername = bundle.getDeviceIdToUsername().hasValue()
                ? bundle.getDeviceIdToUsername().getValue() : Collections.emptyMap();

        Map<String, Incident> incidents = new HashMap<>();
        for (DashboardMaliciousEvent event : events) {
            String actor = StringUtils.defaultIfBlank(event.getActor(), "(unattributed)");
            String policy = StringUtils.defaultIfBlank(event.getFilterId(), "(unknown policy)");
            String evidenceType = StringUtils.defaultIfBlank(event.getSubCategory(), "(unknown)");
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

        List<InsightMetric> metrics = new ArrayList<>(baseMetrics);
        metrics.add(new InsightMetric(
                "total_incidents", "Distinct (user, policy, evidence type) incidents",
                totalIncidents, null, "count", MetricFormat.count(totalIncidents, "incident"), null));
        metrics.add(new InsightMetric(
                "flagged_incidents", "High-repeat incidents (more than " + REPEAT_THRESHOLD + " in 24h)",
                flaggedCount, totalIncidents, "count",
                MetricFormat.ofTotal(flaggedCount, totalIncidents), null));
        if (flaggedCount > 0) {
            metrics.add(new InsightMetric(
                    "violations_in_flagged_incidents", "Violations from high-repeat incidents",
                    violationsInFlagged, observedTotal, "count",
                    MetricFormat.ofTotal(violationsInFlagged, observedTotal), null));
        }

        boolean anyFlaggedBlocking = incidents.values().stream()
                .anyMatch(i -> i.flagged && isBlocking(policyByLowerName.get(lower(i.policy))));

        String severity;
        String headline;
        if (flaggedCount > 0) {
            severity = anyFlaggedBlocking ? "HIGH" : "MEDIUM";
            headline = String.format(Locale.US,
                    "%s collapse to %s; %s repeat more than %d times in 24h%s.",
                    MetricFormat.count(observedTotal, "violation"), MetricFormat.count(totalIncidents, "incident"),
                    MetricFormat.count(flaggedCount, "incident"), REPEAT_THRESHOLD,
                    anyFlaggedBlocking ? ", including at least one policy in block mode" : "");
        } else {
            severity = "LOW";
            headline = MetricFormat.count(observedTotal, "violation") + " collapse to "
                    + MetricFormat.count(totalIncidents, "incident") + "; none repeat more than "
                    + REPEAT_THRESHOLD + " times in 24h.";
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

        List<EvidenceRow> rows = new ArrayList<>();
        for (Incident incident : topIncidents) {
            String displayActor = deviceIdToUsername.getOrDefault(incident.actor, incident.actor);
            GuardrailPolicies policy = policyByLowerName.get(lower(incident.policy));
            String mode = policy != null ? StringUtils.defaultIfBlank(policy.getBehaviour(), "unknown") : "unknown";

            Map<String, Object> raw = new HashMap<>();
            raw.put("actor", incident.actor);
            raw.put("displayActor", displayActor);
            raw.put("policyId", policy != null ? policy.getHexId() : null);
            raw.put("policyName", incident.policy);
            raw.put("evidenceType", incident.evidenceType);
            raw.put("count", incident.timestamps.size());
            raw.put("maxInWindow", incident.maxInWindow);
            raw.put("flagged", incident.flagged);

            rows.add(new EvidenceRow(Arrays.asList(
                    displayActor, incident.policy, incident.evidenceType,
                    MetricFormat.count(incident.timestamps.size(), "violation"),
                    String.valueOf(incident.maxInWindow), mode
            ), raw));
        }
        List<EvidenceTable> evidence = new ArrayList<>();
        evidence.add(new EvidenceTable("alert_fatigue_incidents", "Top incidents by repeat count",
                Arrays.asList("User", "Policy", "Evidence type", "Total", "Max in 24h", "Mode"),
                rows, incidents.size()));

        result.setStatus(InsightStatus.READY.name());
        result.setHeadline(headline);
        result.setSeverity(severity);
        result.setMetrics(metrics);
        result.setMetricsComplete(true);
        result.setDataGaps(new ArrayList<>());
        result.setCaveats(caveats);
        result.setEvidence(evidence);
        result.setCtas(buildCtas(topIncidents, policyByLowerName));
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
        r.setDataGaps(new ArrayList<>(Collections.singletonList(
                new DataGap("THREAT_BACKEND", "REQUEST_FAILED", reason))));
        return r;
    }

    private static List<InsightCta> buildCtas(List<Incident> topIncidents, Map<String, GuardrailPolicies> policyByLowerName) {
        Incident worst = topIncidents.stream().filter(i -> i.flagged).findFirst().orElse(null);
        if (worst == null) {
            return new ArrayList<>();
        }
        GuardrailPolicies policy = policyByLowerName.get(lower(worst.policy));
        Map<String, Object> params = new HashMap<>();
        params.put("actor", worst.actor);
        params.put("policyId", policy != null ? policy.getHexId() : null);
        params.put("policyName", worst.policy);

        List<InsightCta> ctas = new ArrayList<>();
        ctas.add(new InsightCta("remove_user_from_scope", "Remove user from policy scope",
                "BULK_ACTION", "/dashboard/guardrails/policies", params, true));
        ctas.add(new InsightCta("add_exception", "Add exception",
                "NAVIGATE", "/dashboard/guardrails/policies", params, false));
        ctas.add(new InsightCta("switch_to_incident_view", "Switch to incident view",
                "NAVIGATE", "/dashboard/guardrails/violations", Collections.emptyMap(), false));
        return ctas;
    }
}
