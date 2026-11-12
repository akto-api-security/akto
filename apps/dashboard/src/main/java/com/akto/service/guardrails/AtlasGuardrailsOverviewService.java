package com.akto.service.guardrails;

import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.dao.context.Context;
import com.akto.dto.threat_detection_backend.MaliciousEventDto.Status;
import com.akto.dto.ApiCollection;
import com.akto.service.atlas.AtlasPeopleService;
import com.akto.service.atlas.AtlasToolRow;
import com.akto.service.atlas.AtlasAssetStatusService;
import com.akto.service.atlas.AtlasAssetStatus;
import com.akto.service.insights.HostCollectionResolver;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightUtil;
import com.akto.service.insights.providers.SkillEvaluationConcentrationProvider;
import com.akto.util.AgenticObserveUtil;
import com.akto.util.Constants;
import com.akto.util.Pair;
import com.mongodb.BasicDBObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AtlasGuardrailsOverviewService {

    public BasicDBObject buildTriage(InsightDataBundle bundle) {
        BasicDBObject triage = new BasicDBObject();
        triage.put("periodDays", AtlasGuardrailUtils.periodDays(bundle));
        triage.put("staleAfterHours", AtlasGuardrailUtils.TRIAGE_TARGET_HOURS);
        List<DashboardMaliciousEvent> events = AtlasGuardrailUtils.currentPeriodEvents(bundle);
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

    public BasicDBObject buildDecisions(InsightDataBundle bundle) {
        BasicDBObject response = new BasicDBObject();
        List<BasicDBObject> days = new ArrayList<>();
        response.put("days", days);
        List<DashboardMaliciousEvent> events = AtlasGuardrailUtils.currentPeriodEvents(bundle);
        if (events == null) return response;
        List<DashboardMaliciousEvent> current = AtlasGuardrailUtils.collapseConfigFindings(events);

        long endTs = Math.min(bundle.ctx.getEndTs(), Context.now());
        long startTs = bundle.ctx.getStartTs();
        if (startTs <= 0) {
            if (current.isEmpty()) return response;
            startTs = current.stream().mapToLong(DashboardMaliciousEvent::getTimestamp).min().getAsLong();
        }

        Map<Long, BasicDBObject> byDay = new HashMap<>();
        for (long day = startTs - startTs % Constants.ONE_DAY_TIMESTAMP; day <= endTs; day += Constants.ONE_DAY_TIMESTAMP) {
            BasicDBObject point = new BasicDBObject("ts", day).append("blocked", 0L).append("approval", 0L).append("alerted", 0L);
            byDay.put(day, point);
            days.add(point);
        }
        for (DashboardMaliciousEvent e : current) {
            BasicDBObject point = byDay.get(e.getTimestamp() - e.getTimestamp() % Constants.ONE_DAY_TIMESTAMP);
            if (point == null) continue;
            String decision = AtlasGuardrailUtils.BEHAVIOUR_BLOCK.equalsIgnoreCase(e.getBehaviour()) ? "blocked"
                    : AtlasGuardrailUtils.BEHAVIOUR_APPROVAL.equalsIgnoreCase(e.getBehaviour()) ? "approval" : "alerted";
            point.put(decision, point.getLong(decision) + 1);
        }
        return response;
    }

    public BasicDBObject buildPolicies(InsightDataBundle bundle) {
        BasicDBObject response = new BasicDBObject();
        List<DashboardMaliciousEvent> events = AtlasGuardrailUtils.currentPeriodEvents(bundle);
        if (events == null) return response;
        Map<String, BasicDBObject> byPolicy = new LinkedHashMap<>();
        for (DashboardMaliciousEvent e : AtlasGuardrailUtils.collapseConfigFindings(events)) {
            boolean misconfigured = AtlasGuardrailUtils.isConfigFinding(e);
            boolean skill = !misconfigured && AtlasGuardrailUtils.isSkillFinding(e);
            String policy = misconfigured ? "" : (e.getFilterId() == null ? "" : e.getFilterId());
            BasicDBObject row = byPolicy.computeIfAbsent(misconfigured + "|" + skill + "|" + policy, k -> new BasicDBObject("policy", policy)
                    .append("misconfigured", misconfigured).append("skill", skill).append("count", 0L).append("ignored", 0L));
            row.put("count", row.getLong("count") + 1);
            if (Status.IGNORED.name().equalsIgnoreCase(e.getStatus())) row.put("ignored", row.getLong("ignored") + 1);
        }
        response.put("policies", new ArrayList<>(byPolicy.values()));
        return response;
    }

    public BasicDBObject buildKpis(InsightDataBundle bundle) {
        Pair<List<DashboardMaliciousEvent>, List<DashboardMaliciousEvent>> split = AtlasGuardrailUtils.splitByPeriod(bundle);
        List<DashboardMaliciousEvent> current = split == null ? null : split.getFirst();
        List<DashboardMaliciousEvent> previous = split == null ? null : split.getSecond();

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

    public BasicDBObject buildViolationsSources(InsightDataBundle bundle) {
        BasicDBObject response = new BasicDBObject();
        response.put("periodDays", AtlasGuardrailUtils.periodDays(bundle));
        Pair<List<DashboardMaliciousEvent>, List<DashboardMaliciousEvent>> split = AtlasGuardrailUtils.splitByPeriod(bundle);
        if (split == null) return response;
        int startTs = bundle.ctx.getStartTs();
        boolean hasPreviousPeriod = split.getSecond() != null;
        List<DashboardMaliciousEvent> events = new ArrayList<>(split.getFirst());
        if (hasPreviousPeriod) events.addAll(split.getSecond());

        Map<String, BasicDBObject> byType = new LinkedHashMap<>();
        for (String type : AtlasGuardrailUtils.ASSET_TYPES) byType.put(type, AtlasGuardrailUtils.assetTypeRow(type));

        Map<Integer, ApiCollection> collectionsById = new HashMap<>();
        for (ApiCollection c : bundle.collections) collectionsById.put(c.getId(), c);
        Map<String, String> remarksByServiceName = InsightUtil.remarksByServiceName(bundle.auditRows);
        Set<Integer> mcpServers = new HashSet<>();
        Set<String> mcpDevices = new HashSet<>();
        Set<Integer> unapprovedMcpServers = new HashSet<>();

        HostCollectionResolver resolver = bundle.hostResolver();
        Map<Integer, Map<String, Integer>> byCollection = resolver.countByCollection(events,
                e -> AtlasGuardrailUtils.isSkillFinding(e) ? null : AtlasGuardrailUtils.periodKey(e, startTs));
        for (Map.Entry<Integer, Map<String, Integer>> entry : byCollection.entrySet()) {
            ApiCollection c = collectionsById.get(entry.getKey());
            String type = c == null ? AtlasGuardrailUtils.ASSET_TYPE_OTHER : AgenticObserveUtil.getTypeFromCollection(c);
            String label = c == null ? null : AgenticObserveUtil.getPluginName(c);
            String host = c == null ? null : c.getHostName();
            int current = entry.getValue().getOrDefault(AtlasGuardrailUtils.PERIOD_CURRENT, 0);
            AtlasGuardrailUtils.addToAssetType(byType, type, label, host, current, entry.getValue().getOrDefault(AtlasGuardrailUtils.PERIOD_PREVIOUS, 0));
            if (AgenticObserveUtil.CLIENT_TYPE_MCP_SERVER.equals(type) && current > 0) {
                mcpServers.add(c.getId());
                String deviceId = InsightUtil.deviceIdOf(c);
                if (deviceId != null) mcpDevices.add(deviceId);
                if (!AtlasPeopleService.isSanctioned(c, bundle.allowlistNamesLower, remarksByServiceName)) unapprovedMcpServers.add(c.getId());
            }
        }
        for (DashboardMaliciousEvent e : events) {
            String period = AtlasGuardrailUtils.periodKey(e, startTs);
            boolean current = AtlasGuardrailUtils.PERIOD_CURRENT.equals(period);
            if (AtlasGuardrailUtils.isSkillFinding(e)) {
                AtlasGuardrailUtils.addToAssetType(byType, AgenticObserveUtil.CLIENT_TYPE_SKILL,
                        SkillEvaluationConcentrationProvider.extractSkillName(e.getUrl()), null, current ? 1 : 0, current ? 0 : 1);
            } else if (resolver.resolveEvent(e.getHost(), e.getActor()).isEmpty()) {
                AtlasGuardrailUtils.addToAssetType(byType, AtlasGuardrailUtils.ASSET_TYPE_OTHER, null, e.getHost(), current ? 1 : 0, current ? 0 : 1);
            }
        }

        BasicDBObject mcp = byType.get(AgenticObserveUtil.CLIENT_TYPE_MCP_SERVER);
        mcp.put("servers", mcpServers.size());
        mcp.put("devices", mcpDevices.size());
        mcp.put("unapproved", unapprovedMcpServers.size());
        List<BasicDBObject> assetTypes = new ArrayList<>();
        for (BasicDBObject row : byType.values()) {
            if (AtlasGuardrailUtils.ASSET_TYPE_OTHER.equals(row.getString("type")) && row.getLong("count") == 0) continue;
            assetTypes.add(AtlasGuardrailUtils.finishAssetTypeRow(row, hasPreviousPeriod));
        }
        response.put("assetTypes", assetTypes);
        return response;
    }

    public BasicDBObject buildTopAgenticAssets(InsightDataBundle bundle) {
        BasicDBObject response = new BasicDBObject();
        List<BasicDBObject> assets = new ArrayList<>();
        response.put("assets", assets);
        List<DashboardMaliciousEvent> events = AtlasGuardrailUtils.currentPeriodEvents(bundle);
        if (events == null) return response;
        events = AtlasGuardrailUtils.collapseConfigFindings(events);

        Map<String, TopAgenticAssetRow> skills = new LinkedHashMap<>();
        for (DashboardMaliciousEvent e : events) {
            String skill = SkillEvaluationConcentrationProvider.extractSkillName(e.getUrl());
            if (skill == null) continue;
            TopAgenticAssetRow row = skills.computeIfAbsent(skill, k -> new TopAgenticAssetRow(
                    AtlasAssetStatusService.toolKey(AtlasAssetStatusService.ROW_TYPE_SKILL, k), k, AgenticObserveUtil.CLIENT_TYPE_SKILL, null));
            row.addCount(1);
            row.addDevice(AgenticObserveUtil.extractEndpointId(e.getHost()), bundle);
        }

        AtlasAssetStatusService statusService = new AtlasAssetStatusService();
        Map<String, AtlasAssetStatus> decisions = new HashMap<>(statusService.loadDecisions());
        decisions.putAll(statusService.loadSkillDecisionsByName(skills.keySet()));

        Map<Integer, ApiCollection> collectionsById = new HashMap<>();
        for (ApiCollection c : bundle.collections) collectionsById.put(c.getId(), c);
        Map<Integer, Map<String, Integer>> byCollection = bundle.hostResolver().countByCollection(events,
                e -> AtlasGuardrailUtils.isSkillFinding(e) ? null : AtlasGuardrailUtils.PERIOD_CURRENT);

        Map<String, TopAgenticAssetRow> byAsset = new LinkedHashMap<>();
        for (Map.Entry<Integer, Map<String, Integer>> entry : byCollection.entrySet()) {
            ApiCollection c = collectionsById.get(entry.getKey());
            if (c == null) continue;
            String type = AgenticObserveUtil.getTypeFromCollection(c);
            if (!AtlasGuardrailUtils.AGENTIC_ASSET_TYPES.contains(type)) continue;
            AtlasToolRow asset = AtlasGuardrailUtils.collectionAsset(c, type);
            String key = asset == null ? AtlasAssetStatusService.toolKey(type, c.getHostName())
                    : AtlasAssetStatusService.toolKey(asset.getRowType(), asset.getGroupKey());
            TopAgenticAssetRow row = byAsset.computeIfAbsent(key, k -> new TopAgenticAssetRow(k, asset == null ? c.getHostName() : asset.getName(), type, asset == null ? c.getHostName() : null));
            row.addCount(entry.getValue().getOrDefault(AtlasGuardrailUtils.PERIOD_CURRENT, 0));
            if (InsightUtil.isPersonalAccount(c)) row.markPersonal();
            row.addStatus(asset == null ? AtlasAssetStatus.UNAPPROVED : statusService.resolve(asset, decisions, bundle.allowlistNamesLower));
            row.addDevice(InsightUtil.deviceIdOf(c), bundle);
        }
        for (Map.Entry<String, TopAgenticAssetRow> entry : skills.entrySet()) {
            TopAgenticAssetRow row = entry.getValue();
            row.addStatus(statusService.resolve(AtlasGuardrailUtils.skillAsset(entry.getKey()), decisions, bundle.allowlistNamesLower));
            byAsset.put(row.key, row);
        }

        byAsset.values().stream()
                .sorted(Comparator.comparingInt((TopAgenticAssetRow row) -> AtlasGuardrailUtils.assetStatusPriority(row.status))
                        .thenComparing(Comparator.comparingLong((TopAgenticAssetRow row) -> row.count).reversed()))
                .forEach(row -> assets.add(row.toResponse()));
        return response;
    }

    public BasicDBObject buildTopEmployees(InsightDataBundle bundle) {
        BasicDBObject response = new BasicDBObject();
        List<DashboardMaliciousEvent> events = AtlasGuardrailUtils.currentPeriodEvents(bundle);
        if (events == null) return response;

        Map<String, TopEmployeeRow> byEmployee = new HashMap<>();
        for (DashboardMaliciousEvent e : AtlasGuardrailUtils.collapseConfigFindings(events)) {
            String deviceId = AgenticObserveUtil.extractEndpointId(e.getHost());
            if (deviceId == null) continue;
            String username = bundle.usernameForDevice(deviceId);
            TopEmployeeRow row = byEmployee.computeIfAbsent(username != null ? username : deviceId,
                    k -> new TopEmployeeRow(k, AtlasGuardrailUtils.employeeTags(bundle, username)));
            row.addViolation(deviceId, e.getHost(), AtlasGuardrailUtils.isCritical(e));
        }

        response.put("employees", byEmployee.values().stream()
                .sorted(Comparator.comparingLong((TopEmployeeRow row) -> row.critical).thenComparingLong(row -> row.count).reversed())
                .map(TopEmployeeRow::toResponse)
                .collect(Collectors.toList()));
        return response;
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

    private static class TopAgenticAssetRow {
        private final String key;
        private final String name;
        private final String type;
        private final String host;
        private final Set<String> devices = new HashSet<>();
        private final Set<String> people = new HashSet<>();
        private long count;
        private boolean personal;
        private String status;

        private TopAgenticAssetRow(String key, String name, String type, String host) {
            this.key = key;
            this.name = name;
            this.type = type;
            this.host = host;
        }

        private void addCount(long violations) {
            count += violations;
        }

        private void markPersonal() {
            personal = true;
        }

        private void addStatus(AtlasAssetStatus candidate) {
            String candidateName = AtlasAssetStatus.nameOf(candidate);
            if (status == null || AtlasGuardrailUtils.assetStatusPriority(candidateName) < AtlasGuardrailUtils.assetStatusPriority(status)) {
                status = candidateName;
            }
        }

        private void addDevice(String deviceId, InsightDataBundle bundle) {
            if (deviceId == null) return;
            devices.add(deviceId);
            String username = bundle.usernameForDevice(deviceId);
            people.add(username != null ? username : deviceId);
        }

        private BasicDBObject toResponse() {
            return new BasicDBObject("key", key).append("name", name).append("type", type).append("count", count)
                    .append("personal", personal).append("status", status)
                    .append("devices", devices.size()).append("people", people.size()).append("host", host);
        }
    }

    private static class TopEmployeeRow {
        private final String name;
        private final List<String> tags;
        private final Set<String> devices = new HashSet<>();
        private final Set<String> hosts = new HashSet<>();
        private long count;
        private long critical;

        private TopEmployeeRow(String name, List<String> tags) {
            this.name = name;
            this.tags = tags;
        }

        private void addViolation(String deviceId, String host, boolean isCritical) {
            count++;
            if (isCritical) critical++;
            devices.add(deviceId);
            hosts.add(host);
        }

        private BasicDBObject toResponse() {
            return new BasicDBObject("name", name).append("tags", tags).append("count", count)
                    .append("critical", critical).append("devices", devices.size()).append("hosts", hosts);
        }
    }
}
