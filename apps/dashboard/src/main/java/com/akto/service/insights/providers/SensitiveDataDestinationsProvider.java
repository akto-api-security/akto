package com.akto.service.insights.providers;

import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.action.threat_detection.ThreatCategoryCount;
import com.akto.service.insights.*;
import org.json.JSONObject;

import java.util.*;

/** Where sensitive (PII) data is actually going — from PII guardrail violation events. */
public class SensitiveDataDestinationsProvider extends AbstractInsightProvider {

    public SensitiveDataDestinationsProvider() { super(InsightId.SENSITIVE_DATA_DESTINATIONS, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        InsightResult r = skeleton();

        if (!bundle.threatBackendAvailable) {
            r.setStatus(InsightResult.Status.PARTIAL.name());
            r.addDataGap(new InsightResult.Gap("THREAT_BACKEND", "REQUEST_FAILED", "Violation data is unavailable; sensitive-data destinations cannot be computed."));
            r.setHeadline("Sensitive-data destination data unavailable");
            return r;
        }

        long piiTotal = 0;
        List<String> piiSubCategories = new ArrayList<>();
        for (ThreatCategoryCount c : bundle.subCategoryCounts) {
            if (c.getSubCategory() != null && c.getSubCategory().startsWith("PII-")) {
                piiTotal += c.getCount();
                piiSubCategories.add(c.getSubCategory());
            }
        }

        r.addMetric(new InsightResult.Metric("piiEventTotal", "PII detections", piiTotal, "count", InsightUtil.count(piiTotal, "detections")));

        if (piiTotal == 0) {
            r.setStatus(InsightResult.Status.NO_DATA.name());
            r.setHeadline("No PII guardrail detections in this window");
            r.addCta(new InsightResult.Cta("view_policies", "View guardrail policies", "NAVIGATE", InsightRoutes.GUARDRAIL_POLICIES, new HashMap<>(), true));
            return r;
        }

        r.setStatus(InsightResult.Status.READY.name());
        r.setMetricsComplete(scope == Scope.DETAIL);
        r.setHeadline(InsightUtil.count(piiTotal, "PII detections") + " across " + piiSubCategories.size() + " types");

        if (scope == Scope.DETAIL) {
            List<DashboardMaliciousEvent> events = bundle.fetchPiiEvents(scope, piiSubCategories, 2000);
            if (events != null) {
                Map<String, int[]> byDestination = new HashMap<>(); // [count, blockedOrWarn]
                Set<String> distinctSessions = new HashSet<>();
                for (DashboardMaliciousEvent e : events) {
                    String dest = destinationOf(e.getHost());
                    byDestination.computeIfAbsent(dest, k -> new int[2])[0]++;
                    if (isBlockingOrWarn(e.getMetadata())) byDestination.get(dest)[1]++;
                    if (e.getSessionId() != null && !e.getSessionId().isEmpty()) distinctSessions.add(e.getSessionId());
                }
                Set<String> allowlist = bundle.allowlistNamesLower;
                List<Map<String, Object>> rows = new ArrayList<>();
                for (Map.Entry<String, int[]> e : byDestination.entrySet()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("destination", e.getKey());
                    row.put("events", e.getValue()[0]);
                    row.put("approved", allowlist.contains(e.getKey().toLowerCase(Locale.ROOT)));
                    rows.add(row);
                }
                rows.sort((a, b) -> ((Integer) b.get("events")).compareTo((Integer) a.get("events")));
                List<Map<String, Object>> bounded = rows.subList(0, Math.min(20, rows.size()));

                r.addMetric(new InsightResult.Metric("distinctDestinations", "Distinct destinations", byDestination.size(), "count",
                        InsightUtil.count(byDestination.size(), "destinations")));
                r.addMetric(new InsightResult.Metric("sessionCountLowerBound", "Sessions carrying PII (lower bound)", distinctSessions.size(), "count",
                        "≥ " + distinctSessions.size() + " sessions"));
                r.addEvidence(new InsightResult.Evidence("destinations", "PII destinations",
                        Arrays.asList("destination", "events", "approved"), bounded, byDestination.size()));
                r.addCaveat("Session counts are a lower bound: they only include violation events whose session record had already synced when this was computed.");
            } else {
                r.addDataGap(new InsightResult.Gap("THREAT_BACKEND", "REQUEST_FAILED", "Destination breakdown is unavailable; only the total PII detection count is shown."));
            }
        } else {
            r.addDataGap(new InsightResult.Gap("THREAT_BACKEND", "DEFERRED_TO_DETAIL", "Destination breakdown is computed only when this insight is opened."));
        }

        r.addCta(new InsightResult.Cta("view_policies", "Review guardrail policies", "NAVIGATE", InsightRoutes.GUARDRAIL_POLICIES, new HashMap<>(), true));
        r.addCta(new InsightResult.Cta("view_violations", "View violations", "NAVIGATE", InsightRoutes.GUARDRAIL_VIOLATIONS, new HashMap<>(), false));
        return r;
    }

    private String destinationOf(String host) {
        if (host == null || !host.contains(".")) return host == null ? "unknown" : host;
        String[] parts = host.split("\\.");
        return parts[parts.length - 1];
    }

    /** behaviour empty -> redaction (data scrubbed); block/warn/alert -> data went through unscrubbed. */
    private boolean isBlockingOrWarn(String metadataJson) {
        if (metadataJson == null || metadataJson.isEmpty()) return false;
        try {
            String behaviour = new JSONObject(metadataJson).optString("behaviour", "");
            return "block".equalsIgnoreCase(behaviour) || "warn".equalsIgnoreCase(behaviour) || "alert".equalsIgnoreCase(behaviour);
        } catch (Exception e) {
            return false;
        }
    }
}
