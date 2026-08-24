package com.akto.service.insights.providers;

import com.akto.action.threat_detection.HostSeverityCount;
import com.akto.dto.ApiCollection;
import com.akto.dto.McpAuditInfo;
import com.akto.service.insights.*;

import java.util.*;

/**
 * Malicious components in active use — blast radius across devices, users, teams, servers,
 * plus (DETAIL scope only) whether the specific malicious tool/skill was actually ever
 * invoked, not just registered/tagged. Invocation is checked by text-searching real trace
 * payloads (InsightDataBundle.fetchMaliciousComponentInvocations, ES/ADX-backed) for the
 * known-malicious name over the last 15 days — a hit is evidence, not proof, since it only
 * matches the name appearing in the payload, not a guaranteed tool-call semantic match.
 */
public class MaliciousComponentInUseProvider extends AbstractInsightProvider {

    private static final int MAX_INVOCATION_SEARCH_TERMS = 10;

    public MaliciousComponentInUseProvider() { super(InsightId.MALICIOUS_COMPONENT_IN_USE, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        InsightResult r = skeleton();

        Set<String> maliciousServiceNames = new HashSet<>();
        Map<String, Set<String>> maliciousResourceNamesByService = new HashMap<>();
        for (ApiCollection c : bundle.collections) {
            if (InsightUtil.isMaliciousMcpServer(c)) {
                String s = InsightUtil.serviceNameOf(c);
                if (s != null) maliciousServiceNames.add(s.toLowerCase(Locale.ROOT));
            }
        }
        for (McpAuditInfo a : bundle.auditRows) {
            if (a.getComponentRiskAnalysis() != null && a.getComponentRiskAnalysis().getIsComponentMalicious() && a.getMcpHost() != null) {
                String service = a.getMcpHost().toLowerCase(Locale.ROOT);
                maliciousServiceNames.add(service);
                if (a.getResourceName() != null) {
                    maliciousResourceNamesByService.computeIfAbsent(service, k -> new LinkedHashSet<>()).add(a.getResourceName());
                }
            }
        }

        if (maliciousServiceNames.isEmpty()) {
            r.setStatus(InsightResult.Status.NO_DATA.name());
            r.setHeadline("No malicious components detected");
            r.addCta(new InsightResult.Cta("view_audit", "Review audit data", "NAVIGATE", InsightRoutes.AUDIT, new HashMap<>(), true));
            return r;
        }

        // "term" is the specific malicious tool/skill name when known (from componentRiskAnalysis),
        // falling back to the bare service name for tag-only malicious servers with no per-tool
        // granularity — capped so a large account never fires more than MAX search calls.
        List<String> searchTerms = new ArrayList<>();
        for (String service : maliciousServiceNames) {
            Set<String> resourceNames = maliciousResourceNamesByService.get(service);
            if (resourceNames != null && !resourceNames.isEmpty()) searchTerms.addAll(resourceNames);
            else searchTerms.add(service);
        }
        List<String> boundedSearchTerms = searchTerms.subList(0, Math.min(searchTerms.size(), MAX_INVOCATION_SEARCH_TERMS));

        List<Map<String, Object>> invocationHits = bundle.fetchMaliciousComponentInvocations(scope, boundedSearchTerms);
        Set<String> confirmedInvokedTerms = new HashSet<>();
        List<Map<String, Object>> invocationRows = new ArrayList<>();
        if (invocationHits != null) {
            for (Map<String, Object> hit : invocationHits) {
                Object term = hit.get("term");
                if (term != null) confirmedInvokedTerms.add(String.valueOf(term));
                if (invocationRows.size() < 20) invocationRows.add(hit);
            }
        }

        Set<Integer> collectionIds = new HashSet<>();
        Set<String> devices = new HashSet<>();
        Set<String> users = new HashSet<>();
        Set<String> teams = new HashSet<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        int confirmedInvokedServices = 0;

        for (String serviceName : maliciousServiceNames) {
            List<ApiCollection> occurrences = bundle.collectionsForServiceName(serviceName);
            int deviceCount = 0;
            for (ApiCollection c : occurrences) {
                collectionIds.add(c.getId());
                String deviceId = InsightUtil.deviceIdOf(c);
                if (deviceId != null) { devices.add(deviceId); deviceCount++; }
                String username = bundle.usernameForDevice(deviceId);
                if (username != null) {
                    users.add(username);
                    String team = bundle.teamForUser(username);
                    if (team != null) teams.add(team);
                }
            }

            Set<String> serviceTerms = maliciousResourceNamesByService.getOrDefault(serviceName, Collections.singleton(serviceName));
            boolean confirmedInvoked = serviceTerms.stream().anyMatch(confirmedInvokedTerms::contains);
            if (confirmedInvoked) confirmedInvokedServices++;

            if (rows.size() < 20) {
                Map<String, Object> row = new HashMap<>();
                row.put("server", serviceName);
                row.put("devices", deviceCount);
                row.put("confirmedInvoked", confirmedInvoked);
                rows.add(row);
            }
        }

        long violations = 0;
        boolean violationsAvailable = bundle.threatBackendAvailable;
        if (violationsAvailable) {
            for (HostSeverityCount h : bundle.hostSeverityCounts) {
                if (h.getHost() == null) continue;
                String hostLower = h.getHost().toLowerCase(Locale.ROOT);
                for (String s : maliciousServiceNames) {
                    if (hostLower.equals(s) || hostLower.endsWith("." + s)) {
                        violations += h.getCritical() + h.getHigh() + h.getMedium() + h.getLow();
                        break;
                    }
                }
            }
        }

        r.addMetric(new InsightResult.Metric("maliciousComponents", "Malicious components", maliciousServiceNames.size(), "count",
                InsightUtil.count(maliciousServiceNames.size(), "components")));
        r.addMetric(new InsightResult.Metric("devicesAffected", "Devices reached", devices.size(), "count", InsightUtil.count(devices.size(), "devices")));
        r.addMetric(new InsightResult.Metric("usersAffected", "Users reached", users.size(), "count", InsightUtil.count(users.size(), "users")));
        r.addMetric(new InsightResult.Metric("teamsAffected", "Teams reached", teams.size(), "count", InsightUtil.count(teams.size(), "teams")));
        if (violationsAvailable) {
            r.addMetric(new InsightResult.Metric("violationsTotal", "Violations on these servers", violations, "count", InsightUtil.count(violations, "violations")));
        }
        if (invocationHits != null) {
            r.addMetric(new InsightResult.Metric("confirmedInvoked", "Confirmed actually invoked (last 15 days)", confirmedInvokedServices,
                    (long) maliciousServiceNames.size(), "count", InsightUtil.ofTotal(confirmedInvokedServices, maliciousServiceNames.size(), "components"), null));
        }

        r.setStatus((violationsAvailable ? InsightResult.Status.READY : InsightResult.Status.PARTIAL).name());
        if (!violationsAvailable) {
            r.addDataGap(new InsightResult.Gap("THREAT_BACKEND", "REQUEST_FAILED", "Violation counts are unavailable for these malicious components."));
        }
        if (invocationHits == null) {
            r.addDataGap(new InsightResult.Gap("REAL_INVOCATION", "DEFERRED_TO_DETAIL",
                    "Whether each malicious component was actually invoked (vs. just registered/tagged) is checked only when this insight is opened."));
        }
        r.setHeadline(InsightUtil.count(maliciousServiceNames.size(), "malicious components") + " reaching " + InsightUtil.count(devices.size(), "devices")
                + (invocationHits != null ? ", " + InsightUtil.count(confirmedInvokedServices, "confirmed actually invoked") : ""));

        r.addEvidence(new InsightResult.Evidence("malicious", "Malicious components", Arrays.asList("server", "devices", "confirmedInvoked"), rows, maliciousServiceNames.size()));
        if (!invocationRows.isEmpty()) {
            r.addEvidence(new InsightResult.Evidence("real_invocations", "Real invocation evidence (last 15 days)",
                    Arrays.asList("term", "traceId", "timestamp"), invocationRows, invocationRows.size()));
        }

        r.addCta(new InsightResult.Cta("view_audit", "Review in audit data", "NAVIGATE", InsightRoutes.AUDIT, new HashMap<>(), true));
        r.addCta(new InsightResult.Cta("apply_policy", "Apply a blocking guardrail", "NAVIGATE", InsightRoutes.GUARDRAIL_POLICIES, new HashMap<>(), false));
        if (!invocationRows.isEmpty()) {
            Map<String, Object> traceParams = new HashMap<>();
            traceParams.put("traceId", invocationRows.get(0).get("traceId"));
            r.addCta(new InsightResult.Cta("view_trace", "View a real invocation trace", "NAVIGATE", InsightRoutes.LLM_OBSERVABILITY, traceParams, false));
        }
        return r;
    }
}
