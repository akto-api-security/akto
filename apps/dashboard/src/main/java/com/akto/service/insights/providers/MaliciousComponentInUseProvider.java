package com.akto.service.insights.providers;

import com.akto.action.threat_detection.HostSeverityCount;
import com.akto.dto.ApiCollection;
import com.akto.dto.McpAuditInfo;
import com.akto.service.insights.*;

import java.util.*;

/** Malicious components in active use — blast radius across devices, users, teams, servers. */
public class MaliciousComponentInUseProvider extends AbstractInsightProvider {

    public MaliciousComponentInUseProvider() { super(InsightId.MALICIOUS_COMPONENT_IN_USE, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        InsightResult r = skeleton();

        Set<String> maliciousServiceNames = new HashSet<>();
        for (ApiCollection c : bundle.collections) {
            if (InsightUtil.isMaliciousMcpServer(c)) {
                String s = InsightUtil.serviceNameOf(c);
                if (s != null) maliciousServiceNames.add(s.toLowerCase(Locale.ROOT));
            }
        }
        for (McpAuditInfo a : bundle.auditRows) {
            if (a.getComponentRiskAnalysis() != null && a.getComponentRiskAnalysis().getIsComponentMalicious() && a.getMcpHost() != null) {
                maliciousServiceNames.add(a.getMcpHost().toLowerCase(Locale.ROOT));
            }
        }

        if (maliciousServiceNames.isEmpty()) {
            r.setStatus(InsightResult.Status.NO_DATA.name());
            r.setHeadline("No malicious components detected");
            r.addCta(new InsightResult.Cta("view_audit", "Review audit data", "NAVIGATE", InsightRoutes.AUDIT, new HashMap<>(), true));
            return r;
        }

        Set<Integer> collectionIds = new HashSet<>();
        Set<String> devices = new HashSet<>();
        Set<String> users = new HashSet<>();
        Set<String> teams = new HashSet<>();
        List<Map<String, Object>> rows = new ArrayList<>();

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
            if (rows.size() < 20) {
                Map<String, Object> row = new HashMap<>();
                row.put("server", serviceName);
                row.put("devices", deviceCount);
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

        r.setStatus((violationsAvailable ? InsightResult.Status.READY : InsightResult.Status.PARTIAL).name());
        if (!violationsAvailable) {
            r.addDataGap(new InsightResult.Gap("THREAT_BACKEND", "REQUEST_FAILED", "Violation counts are unavailable for these malicious components."));
        }
        r.setHeadline(InsightUtil.count(maliciousServiceNames.size(), "malicious components") + " reaching " + InsightUtil.count(devices.size(), "devices"));

        r.addEvidence(new InsightResult.Evidence("malicious", "Malicious components", Arrays.asList("server", "devices"), rows, maliciousServiceNames.size()));

        r.addCta(new InsightResult.Cta("view_audit", "Review in audit data", "NAVIGATE", InsightRoutes.AUDIT, new HashMap<>(), true));
        r.addCta(new InsightResult.Cta("apply_policy", "Apply a blocking guardrail", "NAVIGATE", InsightRoutes.GUARDRAIL_POLICIES, new HashMap<>(), false));
        return r;
    }
}
