package com.akto.service.guardrails;

import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.dao.context.Context;
import com.akto.dto.threat_detection_backend.MaliciousEventDto.Status;
import com.akto.service.insights.InsightDataBundle;
import com.akto.util.AgenticObserveUtil;
import com.mongodb.BasicDBObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class AtlasGuardrailsOverviewService {

    public BasicDBObject buildTriage(InsightDataBundle bundle) {
        BasicDBObject triage = new BasicDBObject();
        triage.put("periodDays", AtlasGuardrailUtils.periodDays(bundle));
        triage.put("staleAfterHours", AtlasGuardrailUtils.TRIAGE_TARGET_HOURS);
        List<DashboardMaliciousEvent> events = bundle.windowEvents();
        if (events == null) return triage;
        List<DashboardMaliciousEvent> current = AtlasGuardrailUtils.collapseConfigFindings(events);

        List<BasicDBObject> statuses = new ArrayList<>();
        statuses.add(AtlasGuardrailUtils.triageStatus("OPEN", "Open", current.stream().filter(AtlasGuardrailUtils::isOpen).count()));
        statuses.add(AtlasGuardrailUtils.triageStatus("IN_REVIEW", "In review", AtlasGuardrailUtils.countWithStatus(current, Status.UNDER_REVIEW)));
        statuses.add(AtlasGuardrailUtils.triageStatus("ASSIGNED", "Assigned", current.stream().filter(e -> !AtlasGuardrailUtils.isUnassigned(e.getCaseOwner())).count()));
        statuses.add(AtlasGuardrailUtils.triageStatus("TICKETED", "Ticketed", current.stream().filter(AtlasGuardrailUtils::hasTicket).count()));
        statuses.add(AtlasGuardrailUtils.triageStatus("IGNORED", "Ignored", AtlasGuardrailUtils.countWithStatus(current, Status.IGNORED)));
        statuses.add(AtlasGuardrailUtils.triageStatus("RESOLVED", "Resolved", AtlasGuardrailUtils.countWithStatus(current, Status.FIXED)));
        triage.put("statuses", statuses);

        long staleBefore = Context.now() - AtlasGuardrailUtils.TRIAGE_TARGET_HOURS * 3600L;
        triage.put("staleCriticalUnassigned", current.stream()
                .filter(e -> AtlasGuardrailUtils.isOpenCriticalUnassigned(e) && e.getTimestamp() < staleBefore)
                .count());
        return triage;
    }

    public BasicDBObject buildKpis(InsightDataBundle bundle) {
        int startTs = bundle.ctx.getStartTs();
        int endTs = bundle.ctx.getEndTs();
        int length = endTs - startTs;
        boolean hasPreviousPeriod = startTs > 0 && length > 0 && startTs - length > 0;

        List<DashboardMaliciousEvent> fetched = hasPreviousPeriod
                ? bundle.eventsBetween(startTs - length, endTs)
                : bundle.windowEvents();
        List<DashboardMaliciousEvent> current = null;
        List<DashboardMaliciousEvent> previous = null;
        if (fetched != null) {
            current = AtlasGuardrailUtils.collapseConfigFindings(fetched.stream()
                    .filter(e -> e != null && e.getTimestamp() >= startTs).collect(Collectors.toList()));
            if (hasPreviousPeriod) {
                previous = AtlasGuardrailUtils.collapseConfigFindings(fetched.stream()
                        .filter(e -> e != null && e.getTimestamp() < startTs).collect(Collectors.toList()));
            }
        }

        BasicDBObject kpis = new BasicDBObject();
        kpis.put("periodDays", AtlasGuardrailUtils.periodDays(bundle));
        kpis.put("managedDevices", bundle.deviceIdToUsername.size());
        kpis.put("openViolations", openViolations(current, previous));
        kpis.put("criticalUnassigned", criticalUnassigned(current));
        kpis.put("blocked", blocked(current));
        kpis.put("employeesInvolved", employeesInvolved(current, bundle));
        kpis.put("medianTriage", medianTriage(current));
        return kpis;
    }

    private static BasicDBObject openViolations(List<DashboardMaliciousEvent> current, List<DashboardMaliciousEvent> previous) {
        BasicDBObject kpi = new BasicDBObject();
        if (current == null) return kpi;
        kpi.put("count", current.stream().filter(AtlasGuardrailUtils::isOpen).count());
        if (previous != null) {
            kpi.put("change", current.size() - previous.size());
        }
        return kpi;
    }

    private static BasicDBObject criticalUnassigned(List<DashboardMaliciousEvent> current) {
        BasicDBObject kpi = new BasicDBObject();
        if (current == null) return kpi;
        List<DashboardMaliciousEvent> matching = current.stream()
                .filter(AtlasGuardrailUtils::isOpenCriticalUnassigned)
                .collect(Collectors.toList());
        kpi.put("count", matching.size());
        matching.stream().mapToLong(DashboardMaliciousEvent::getTimestamp).min()
                .ifPresent(oldest -> kpi.put("oldestAgeSeconds", Math.max(0, Context.now() - oldest)));
        return kpi;
    }

    private static BasicDBObject blocked(List<DashboardMaliciousEvent> current) {
        BasicDBObject kpi = new BasicDBObject();
        if (current == null) return kpi;
        long blocked = current.stream().filter(e -> AtlasGuardrailUtils.BEHAVIOUR_BLOCK.equalsIgnoreCase(e.getBehaviour())).count();
        kpi.put("count", blocked);
        if (!current.isEmpty()) {
            kpi.put("sharePct", Math.round(blocked * 100.0 / current.size()));
        }
        return kpi;
    }

    private static BasicDBObject employeesInvolved(List<DashboardMaliciousEvent> current, InsightDataBundle bundle) {
        BasicDBObject kpi = new BasicDBObject();
        if (current == null) return kpi;
        Set<String> devices = new HashSet<>();
        Set<String> users = new HashSet<>();
        for (DashboardMaliciousEvent e : current) {
            String deviceId = AgenticObserveUtil.extractEndpointId(e.getHost());
            if (deviceId == null) continue;
            devices.add(deviceId);
            String username = bundle.usernameForDevice(deviceId);
            users.add(username != null ? username : deviceId);
        }
        kpi.put("count", users.size());
        kpi.put("devices", devices.size());
        return kpi;
    }

    private static BasicDBObject medianTriage(List<DashboardMaliciousEvent> current) {
        BasicDBObject kpi = new BasicDBObject();
        kpi.put("targetHours", AtlasGuardrailUtils.TRIAGE_TARGET_HOURS);
        if (current == null) return kpi;
        List<Long> waits = new ArrayList<>();
        for (DashboardMaliciousEvent e : current) {
            if (e.getFirstTriagedAt() > 0 && e.getFirstTriagedAt() >= e.getTimestamp()) {
                waits.add(e.getFirstTriagedAt() - e.getTimestamp());
            }
        }
        Long median = AtlasGuardrailUtils.medianOf(waits);
        if (median != null) kpi.put("seconds", median);
        return kpi;
    }
}
