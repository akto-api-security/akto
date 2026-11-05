package com.akto.service.guardrails;

import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.dto.threat_detection_backend.MaliciousEventDto.Status;
import com.akto.service.insights.InsightDataBundle;
import com.akto.util.ThreatDetectionConstants;
import com.akto.util.enums.GlobalEnums.Severity;
import com.mongodb.BasicDBObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AtlasGuardrailUtils {

    private AtlasGuardrailUtils() {}

    static final int TRIAGE_TARGET_HOURS = 4;
    static final String BEHAVIOUR_BLOCK = "block";
    static final String BEHAVIOUR_APPROVAL = "approval";
    static final int DECISION_TARGET_BARS = 30;
    private static final String UNASSIGNED_OWNER = "__unassigned__";
    private static final String CONFIG_ENDPOINT_SEGMENT = "/config/";

    static List<DashboardMaliciousEvent> collapseConfigFindings(List<DashboardMaliciousEvent> events) {
        Map<String, DashboardMaliciousEvent> latestConfigFinding = new LinkedHashMap<>();
        List<DashboardMaliciousEvent> out = new ArrayList<>();
        for (DashboardMaliciousEvent e : events) {
            if (e == null) continue;
            if (e.getUrl() == null || !e.getUrl().contains(CONFIG_ENDPOINT_SEGMENT)) {
                out.add(e);
                continue;
            }
            String key = e.getHost() + "|" + e.getActor() + "|" + e.getUrl();
            DashboardMaliciousEvent kept = latestConfigFinding.get(key);
            if (kept == null || e.getTimestamp() > kept.getTimestamp()) {
                latestConfigFinding.put(key, e);
            }
        }
        out.addAll(latestConfigFinding.values());
        return out;
    }

    static long periodDays(InsightDataBundle bundle) {
        return Math.round((bundle.ctx.getEndTs() - bundle.ctx.getStartTs()) / 86400.0);
    }

    static Long medianOf(List<Long> values) {
        if (values.isEmpty()) return null;
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(mid) : Math.round((sorted.get(mid - 1) + sorted.get(mid)) / 2.0);
    }

    static BasicDBObject triageStatus(String key, String label, long count) {
        BasicDBObject status = new BasicDBObject();
        status.put("key", key);
        status.put("label", label);
        status.put("count", count);
        return status;
    }

    static long countWithStatus(List<DashboardMaliciousEvent> events, Status status) {
        return events.stream().filter(e -> status.name().equalsIgnoreCase(e.getStatus())).count();
    }

    static boolean isOpenCriticalUnassigned(DashboardMaliciousEvent e) {
        return isOpen(e) && Severity.CRITICAL.name().equalsIgnoreCase(e.getSeverity()) && isUnassigned(e.getCaseOwner());
    }

    static boolean isOpen(DashboardMaliciousEvent e) {
        String status = e.getStatus();
        return status == null || status.isEmpty() || ThreatDetectionConstants.ACTIVE.equalsIgnoreCase(status);
    }

    static boolean isUnassigned(String owner) {
        return owner == null || owner.trim().isEmpty() || UNASSIGNED_OWNER.equals(owner);
    }

    static boolean hasTicket(DashboardMaliciousEvent e) {
        return e.getJiraTicketUrl() != null && !e.getJiraTicketUrl().isEmpty();
    }
}
