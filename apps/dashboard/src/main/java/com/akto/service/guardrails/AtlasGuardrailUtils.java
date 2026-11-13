package com.akto.service.guardrails;

import com.akto.action.AgenticObserveAction;
import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.dto.ApiCollection;
import com.akto.dto.DeviceTag;
import com.akto.dto.threat_detection_backend.MaliciousEventDto.Status;
import com.akto.service.atlas.AtlasAssetStatus;
import com.akto.service.atlas.AtlasAssetStatusService;
import com.akto.service.atlas.AtlasToolRow;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightUtil;
import com.akto.service.insights.providers.SkillEvaluationConcentrationProvider;
import com.akto.util.AgenticObserveUtil;
import com.akto.util.McpClientRegistry;
import com.akto.util.Pair;
import com.akto.util.ThreatDetectionConstants;
import com.akto.util.enums.GlobalEnums.Severity;
import com.mongodb.BasicDBObject;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AtlasGuardrailUtils {

    private AtlasGuardrailUtils() {}

    static final int TRIAGE_TARGET_HOURS = 4;
    static final String BEHAVIOUR_BLOCK = "block";
    static final String BEHAVIOUR_APPROVAL = "approval";
    private static final String UNASSIGNED_OWNER = "__unassigned__";
    private static final String CONFIG_ENDPOINT_SEGMENT = "/config/";
    static final String ASSET_TYPE_OTHER = "Other";
    static final List<String> ASSET_TYPES = Arrays.asList(AgenticObserveUtil.CLIENT_TYPE_AI_AGENT,
            AgenticObserveUtil.CLIENT_TYPE_SAAS_AGENT, AgenticObserveUtil.CLIENT_TYPE_MCP_SERVER, AgenticObserveUtil.CLIENT_TYPE_LLM,
            AgenticObserveUtil.CLIENT_TYPE_SKILL, AgenticObserveUtil.CLIENT_TYPE_PLUGIN, ASSET_TYPE_OTHER);
    static final String PERIOD_CURRENT = "current";
    static final String PERIOD_PREVIOUS = "previous";
    private static final int ASSET_TYPE_EXAMPLES = 5;
    private static final String EXAMPLE_COUNTS = "exampleCounts";
    static final List<String> AGENTIC_ASSET_TYPES = Arrays.asList(AgenticObserveUtil.CLIENT_TYPE_AI_AGENT,
            AgenticObserveUtil.CLIENT_TYPE_SAAS_AGENT, AgenticObserveUtil.CLIENT_TYPE_LLM, AgenticObserveUtil.CLIENT_TYPE_MCP_SERVER);
    private static final List<String> EMPLOYEE_TAG_KEYS = Arrays.asList("team", "group", "role");
    private static final List<String> ASSET_STATUS_PRIORITY = Arrays.asList(AtlasAssetStatus.UNAPPROVED.name(),
            AtlasAssetStatus.UNDER_REVIEW.name(), AtlasAssetStatus.BLOCKED.name(), AtlasAssetStatus.APPROVED.name());

    static List<DashboardMaliciousEvent> collapseConfigFindings(List<DashboardMaliciousEvent> events) {
        Map<String, DashboardMaliciousEvent> latestConfigFinding = new LinkedHashMap<>();
        List<DashboardMaliciousEvent> out = new ArrayList<>();
        for (DashboardMaliciousEvent e : events) {
            if (e == null) continue;
            if (!isConfigFinding(e)) {
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

    static boolean isConfigFinding(DashboardMaliciousEvent e) {
        return e.getUrl() != null && e.getUrl().contains(CONFIG_ENDPOINT_SEGMENT);
    }

    static Long periodDays(InsightDataBundle bundle) {
        if (bundle.ctx.getStartTs() <= 0) return null;
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
        return isOpen(e) && isCritical(e) && isUnassigned(e.getCaseOwner());
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

    static String periodKey(DashboardMaliciousEvent e, int startTs) {
        return e.getTimestamp() >= startTs ? PERIOD_CURRENT : PERIOD_PREVIOUS;
    }

    static boolean isSkillFinding(DashboardMaliciousEvent e) {
        return SkillEvaluationConcentrationProvider.extractSkillName(e.getUrl()) != null;
    }

    static BasicDBObject assetTypeRow(String type) {
        return new BasicDBObject("type", type).append("count", 0L).append("previousCount", 0L)
                .append(EXAMPLE_COUNTS, new LinkedHashMap<String, BasicDBObject>());
    }

    @SuppressWarnings("unchecked")
    static void addToAssetType(Map<String, BasicDBObject> byType, String type, String name, String host, long current, long previous) {
        BasicDBObject row = byType.get(type);
        if (row == null) return;
        row.put("count", row.getLong("count") + current);
        row.put("previousCount", row.getLong("previousCount") + previous);
        if (current == 0 || (name == null && host == null)) return;
        Map<String, BasicDBObject> examples = (Map<String, BasicDBObject>) row.get(EXAMPLE_COUNTS);
        BasicDBObject example = examples.computeIfAbsent(name != null ? "name|" + name : "host|" + host,
                k -> new BasicDBObject("name", name).append("host", host).append("count", 0L));
        example.put("count", example.getLong("count") + current);
    }

    @SuppressWarnings("unchecked")
    static BasicDBObject finishAssetTypeRow(BasicDBObject row, boolean hasPreviousPeriod) {
        Map<String, BasicDBObject> examples = (Map<String, BasicDBObject>) row.remove(EXAMPLE_COUNTS);
        List<BasicDBObject> top = new ArrayList<>(examples.values());
        top.sort((a, b) -> Long.compare(b.getLong("count"), a.getLong("count")));
        row.put("examples", new ArrayList<>(top.subList(0, Math.min(ASSET_TYPE_EXAMPLES, top.size()))));
        long previous = row.getLong("previousCount");
        if (hasPreviousPeriod && previous > 0) {
            row.put("changePct", Math.round((row.getLong("count") - previous) * 100.0 / previous));
        }
        return row;
    }

    static boolean hasPreviousPeriod(InsightDataBundle bundle) {
        int startTs = bundle.ctx.getStartTs();
        int length = bundle.ctx.getEndTs() - startTs;
        return startTs > 0 && length > 0 && startTs - length > 0;
    }

    static List<DashboardMaliciousEvent> periodEvents(InsightDataBundle bundle) {
        if (!hasPreviousPeriod(bundle))
            return bundle.windowEvents();
        int startTs = bundle.ctx.getStartTs();
        int endTs = bundle.ctx.getEndTs();
        return bundle.eventsBetween(startTs - (endTs - startTs), endTs);
    }

    static Pair<List<DashboardMaliciousEvent>, List<DashboardMaliciousEvent>> splitByPeriod(InsightDataBundle bundle) {
        List<DashboardMaliciousEvent> fetched = periodEvents(bundle);
        if (fetched == null) return null;
        int startTs = bundle.ctx.getStartTs();
        List<DashboardMaliciousEvent> current = collapseConfigFindings(fetched.stream()
                .filter(e -> e != null && e.getTimestamp() >= startTs).collect(Collectors.toList()));
        List<DashboardMaliciousEvent> previous = hasPreviousPeriod(bundle)
                ? collapseConfigFindings(fetched.stream()
                        .filter(e -> e != null && e.getTimestamp() < startTs).collect(Collectors.toList()))
                : null;
        return new Pair<>(current, previous);
    }

    static List<DashboardMaliciousEvent> currentPeriodEvents(InsightDataBundle bundle) {
        List<DashboardMaliciousEvent> events = periodEvents(bundle);
        if (events == null) return null;
        int startTs = bundle.ctx.getStartTs();
        return events.stream().filter(e -> e != null && e.getTimestamp() >= startTs).collect(Collectors.toList());
    }

    static AtlasToolRow collectionAsset(ApiCollection c, String type) {
        String rowType;
        String groupKey;
        String name;
        if (AgenticObserveUtil.CLIENT_TYPE_LLM.equals(type)) {
            rowType = AtlasAssetStatusService.ROW_TYPE_LLM;
            groupKey = AgenticObserveAction.llmGroupKey(c);
            name = groupKey;
        } else if (AgenticObserveUtil.CLIENT_TYPE_MCP_SERVER.equals(type)) {
            rowType = AtlasAssetStatusService.ROW_TYPE_SERVICE;
            groupKey = AgenticObserveAction.serviceGroupKey(c, AgenticObserveAction.agentGroupKey(c) != null);
            name = groupKey;
        } else {
            rowType = AtlasAssetStatusService.ROW_TYPE_AGENT;
            groupKey = AgenticObserveAction.agentGroupKey(c);
            name = groupKey == null ? null : McpClientRegistry.formatDisplayName(groupKey);
        }
        if (groupKey == null) return null;
        return AtlasToolRow.builder().rowType(rowType).groupKey(groupKey).name(name)
                .vendorName(InsightUtil.endpointVendorNameOfHost(c.getHostName()))
                .malicious(InsightUtil.isMaliciousMcpServer(c)).build();
    }

    static AtlasToolRow skillAsset(String skillName) {
        return AtlasToolRow.builder().rowType(AtlasAssetStatusService.ROW_TYPE_SKILL)
                .groupKey(skillName).name(skillName).build();
    }

    static boolean isCritical(DashboardMaliciousEvent e) {
        return Severity.CRITICAL.name().equalsIgnoreCase(e.getSeverity());
    }

    static List<String> employeeTags(InsightDataBundle bundle, String username) {
        List<DeviceTag> tags = username == null || bundle.userTags == null ? null : bundle.userTags.get(username);
        if (tags == null) return Collections.emptyList();
        Set<String> values = new LinkedHashSet<>();
        for (String key : EMPLOYEE_TAG_KEYS) {
            for (DeviceTag tag : tags) {
                if (tag != null && key.equalsIgnoreCase(tag.getKey()) && StringUtils.isNotBlank(tag.getValue())) values.add(tag.getValue().trim());
            }
        }
        for (DeviceTag tag : tags) {
            if (tag != null && StringUtils.isNotBlank(tag.getValue())) values.add(tag.getValue().trim());
        }
        return new ArrayList<>(values);
    }

    static int assetStatusPriority(String status) {
        int index = ASSET_STATUS_PRIORITY.indexOf(status);
        return index < 0 ? ASSET_STATUS_PRIORITY.size() : index;
    }
}
