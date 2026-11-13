package com.akto.service.insights.providers;

import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.service.guardrails.AtlasGuardrailUtils;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightResult;
import com.akto.service.insights.InsightUtil;
import com.akto.util.AgenticObserveUtil;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

class ViolationGroup {

    static final Comparator<ViolationGroup> WORST_FIRST = Comparator.comparingInt((ViolationGroup g) -> InsightUtil.severityRank(g.severity))
            .thenComparing(Comparator.comparingInt((ViolationGroup g) -> g.people.size()).reversed())
            .thenComparing(Comparator.comparingLong((ViolationGroup g) -> g.count).reversed());

    final String name;
    final Set<String> devices = new HashSet<>();
    final Set<String> people = new HashSet<>();
    final Set<String> hosts = new LinkedHashSet<>();
    long count;
    long blocked;
    String severity;

    ViolationGroup(String name) {
        this.name = name;
    }

    void add(DashboardMaliciousEvent e, InsightDataBundle bundle) {
        count++;
        if (AtlasGuardrailUtils.BEHAVIOUR_BLOCK.equalsIgnoreCase(e.getBehaviour())) blocked++;
        if (severity == null || InsightUtil.severityRank(e.getSeverity()) < InsightUtil.severityRank(severity)) {
            severity = e.getSeverity();
        }
        if (e.getHost() != null) hosts.add(e.getHost());
        String deviceId = AgenticObserveUtil.extractEndpointId(e.getHost());
        if (deviceId == null) return;
        devices.add(deviceId);
        String username = bundle.usernameForDevice(deviceId);
        people.add(username != null ? username : deviceId);
    }

    String decision() {
        if (blocked == count) return "Blocked";
        if (blocked == 0) return "Alerted, not blocked";
        return InsightUtil.grouped(blocked) + " of " + InsightUtil.grouped(count) + " blocked";
    }

    String employees() {
        return InsightUtil.count(people.size(), "employees");
    }

    void addMetrics(InsightResult r) {
        r.addMetric(new InsightResult.Metric(AtlasGuardrailUtils.METRIC_VIOLATIONS, "Violations", count, "count",
                InsightUtil.count(count, "violations")));
        r.addMetric(new InsightResult.Metric(AtlasGuardrailUtils.METRIC_EMPLOYEES, "Employees", people.size(), "count", employees()));
    }

    Map<String, Object> evidenceRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("name", name);
        row.put("violations", count);
        row.put("employees", people.size());
        row.put("devices", devices.size());
        row.put("severity", severity);
        row.put("decision", decision());
        return row;
    }
}
