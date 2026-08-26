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
        int totalComponentRows = 0; // rows.size() is capped at 20 mid-loop; this tracks the real total for Evidence.totalRowCount

        for (String serviceName : maliciousServiceNames) {
            List<ApiCollection> occurrences = bundle.collectionsForServiceName(serviceName);
            int deviceCount = 0;
            Set<String> serviceUsers = new LinkedHashSet<>();
            for (ApiCollection c : occurrences) {
                collectionIds.add(c.getId());
                String deviceId = InsightUtil.deviceIdOf(c);
                if (deviceId != null) { devices.add(deviceId); deviceCount++; }
                String username = bundle.usernameForDevice(deviceId);
                if (username != null) {
                    users.add(username);
                    serviceUsers.add(username);
                    String team = bundle.teamForUser(username);
                    if (team != null) teams.add(team);
                }
            }

            Set<String> resourceNames = maliciousResourceNamesByService.get(serviceName);
            boolean confirmedInvoked = resourceNames != null && !resourceNames.isEmpty()
                    ? resourceNames.stream().anyMatch(confirmedInvokedTerms::contains)
                    : confirmedInvokedTerms.contains(serviceName);
            if (confirmedInvoked) confirmedInvokedServices++;

            // One row per exact malicious tool/skill name when known (from componentRiskAnalysis) —
            // a bare service bucket like "claude" or "vscode" isn't an actionable identifier on its
            // own. Falls back to the service name only when no finer-grained name was ever reported.
            String usersDisplay = serviceUsers.isEmpty() ? "unknown" : String.join(", ", serviceUsers);
            if (resourceNames != null && !resourceNames.isEmpty()) {
                for (String resourceName : resourceNames) {
                    totalComponentRows++;
                    if (rows.size() >= 20) continue;
                    Map<String, Object> row = new HashMap<>();
                    row.put("component", resourceName);
                    row.put("server", serviceName);
                    row.put("devices", deviceCount);
                    row.put("users", usersDisplay);
                    rows.add(row);
                }
            } else {
                totalComponentRows++;
                if (rows.size() < 20) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("component", serviceName);
                    row.put("server", serviceName);
                    row.put("devices", deviceCount);
                    row.put("users", usersDisplay);
                    rows.add(row);
                }
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
        r.addCaveat("\"Malicious\" here means Akto's own threat-intel tagging or MCP audit risk analysis flagged this component's name/signature — "
                + "not a live behavioral judgment based on what it actually did on your network.");

        r.setSeverity((confirmedInvokedServices > 0 ? "CRITICAL" : devices.isEmpty() ? "MEDIUM" : "HIGH"));
        r.setConcern(confirmedInvokedServices > 0
                ? InsightUtil.count(confirmedInvokedServices, "of these components") + " were actually called in the last 15 days, not just registered."
                : "These components are present and reachable, but no real invocation has been confirmed yet in the last 15 days.");
        r.setImpact("A known-malicious tool that's reachable can exfiltrate data, run unapproved actions, or serve as a foothold for further attacks the moment it's invoked."
                + (users.isEmpty() ? "" : " Right now it's reachable by " + InsightUtil.count(users.size(), "users") + " across " + InsightUtil.count(teams.size(), "teams") + "."));
        r.setRemediation("Remove or block these components at the source (uninstall, deregister, or revoke access), then apply a blocking guardrail policy on the servers that hosted them so any re-registration is caught automatically.");

        r.addEvidence(new InsightResult.Evidence("malicious", "Malicious components",
                Arrays.asList("component", "server", "devices", "users"), rows, totalComponentRows));
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
