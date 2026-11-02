package com.akto.service.atlas;

import com.akto.dao.McpAuditInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.DeviceTag;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.McpAuditInfo;
import com.akto.mcp.McpRequestResponseUtils;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightUtil;
import com.akto.service.insights.InsightUtil.GovernanceBucket;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AtlasDeviceStatsService {

    private static final String TEAM_TAG_KEY = "team";
    private static final String ALERT_ONLY_BEHAVIOUR = "alert";
    private static final String UNKNOWN_USERNAME_PLACEHOLDER = "not-available";

    public static class AiToolStats {
        public final long devicesWithAiTools;
        public final long employeesUsingAi;
        public final long totalEmployees;
        public final long devicesWithUnapprovedTools;
        public final long unapprovedTeams;

        public AiToolStats(long devicesWithAiTools, long employeesUsingAi, long totalEmployees,
                           long devicesWithUnapprovedTools, long unapprovedTeams) {
            this.devicesWithAiTools = devicesWithAiTools;
            this.employeesUsingAi = employeesUsingAi;
            this.totalEmployees = totalEmployees;
            this.devicesWithUnapprovedTools = devicesWithUnapprovedTools;
            this.unapprovedTeams = unapprovedTeams;
        }
    }

    public AiToolStats computeAiToolStats(InsightDataBundle bundle, Map<String, String> scopeDeviceUsernames, int startTs, int endTs) {
        boolean ranged = startTs > 0 || endTs > 0;
        Map<String, String> remarksByServiceName = loadServerRemarks();
        Map<String, String> scopeDevicesByLowerName = new HashMap<>();
        for (String scopeDevice : scopeDeviceUsernames.keySet()) {
            scopeDevicesByLowerName.putIfAbsent(scopeDevice.toLowerCase(Locale.ROOT), scopeDevice);
        }
        Set<String> aiDevices = new HashSet<>();
        Set<String> unapprovedDevices = new HashSet<>();

        for (ApiCollection c : bundle.collections) {
            if (!InsightUtil.hasTagValue(c, Constants.AKTO_ENDPOINT_SOURCE_TAG, Constants.AKTO_ENDPOINT_SOURCE_VALUE)) continue;
            String hostDevice = InsightUtil.deviceIdOf(c);
            if (hostDevice == null) continue;
            String device = scopeDevicesByLowerName.get(hostDevice.toLowerCase(Locale.ROOT));
            if (device == null) continue;
            if (ranged && !seenInWindow(bundle.collectionLastTrafficSeen.get(c.getId()), startTs, endTs)) continue;
            aiDevices.add(device);
            if (!isSanctioned(c, bundle.allowlistNamesLower, remarksByServiceName)) unapprovedDevices.add(device);
        }

        Set<String> allEmployees = distinctUsernames(scopeDeviceUsernames, scopeDeviceUsernames.keySet());
        Set<String> aiEmployees = distinctUsernames(scopeDeviceUsernames, aiDevices);
        Set<String> unapprovedEmployees = distinctUsernames(scopeDeviceUsernames, unapprovedDevices);
        return new AiToolStats(aiDevices.size(), aiEmployees.size(), allEmployees.size(),
                unapprovedDevices.size(), distinctTeams(bundle, unapprovedEmployees).size());
    }

    private Map<String, String> loadServerRemarks() {
        Bson filter = Filters.and(
                Filters.eq(McpAuditInfo.TYPE, Constants.AKTO_MCP_SERVER_TAG),
                Filters.in(McpAuditInfo.REMARKS, Arrays.asList(McpAuditInfo.REMARKS_APPROVED, McpAuditInfo.REMARKS_REJECTED)),
                contextSourceFilter(Context.contextSource.get()));
        Map<String, String> remarksByServiceName = new HashMap<>();
        for (McpAuditInfo row : McpAuditInfoDao.instance.findAll(
                filter, Projections.include(McpAuditInfo.RESOURCE_NAME, McpAuditInfo.REMARKS))) {
            String serviceName = McpRequestResponseUtils.extractServiceNameFromHost(row.getResourceName());
            if (serviceName == null) continue;
            String key = serviceName.toLowerCase(Locale.ROOT);
            if (!McpAuditInfo.REMARKS_REJECTED.equals(remarksByServiceName.get(key))) {
                remarksByServiceName.put(key, row.getRemarks());
            }
        }
        return remarksByServiceName;
    }

    private static Bson contextSourceFilter(CONTEXT_SOURCE contextSource) {
        if (contextSource == null) return Filters.eq(McpAuditInfo.CONTEXT_SOURCE, null);
        return Filters.or(
                Filters.eq(McpAuditInfo.CONTEXT_SOURCE, contextSource.name()),
                Filters.exists(McpAuditInfo.CONTEXT_SOURCE, false));
    }

    private static boolean isSanctioned(ApiCollection c, Set<String> allowlistNamesLower, Map<String, String> remarksByServiceName) {
        GovernanceBucket bucket = InsightUtil.governanceBucket(c, allowlistNamesLower, remarksByServiceName);
        if (bucket == GovernanceBucket.SANCTIONED) return true;
        if (bucket != GovernanceBucket.LOCAL) return false;
        String groupingName = InsightUtil.governanceGroupingName(c);
        String serviceName = InsightUtil.serviceNameOf(c);
        return (groupingName != null && allowlistNamesLower.contains(groupingName.toLowerCase(Locale.ROOT)))
                || (serviceName != null && McpAuditInfo.REMARKS_APPROVED.equals(remarksByServiceName.get(serviceName.toLowerCase(Locale.ROOT))));
    }

    private static Set<String> distinctUsernames(Map<String, String> deviceUsernames, Set<String> devices) {
        Set<String> usernames = new HashSet<>();
        for (String device : devices) {
            String username = deviceUsernames.get(device);
            if (username == null || username.trim().isEmpty()) continue;
            if (UNKNOWN_USERNAME_PLACEHOLDER.equalsIgnoreCase(username.trim())) continue;
            usernames.add(username);
        }
        return usernames;
    }

    private static Set<String> distinctTeams(InsightDataBundle bundle, Set<String> usernames) {
        Set<String> teams = new HashSet<>();
        for (String username : usernames) {
            List<DeviceTag> tags = bundle.userTags.get(username);
            if (tags == null) continue;
            for (DeviceTag tag : tags) {
                if (tag != null && TEAM_TAG_KEY.equals(tag.getKey()) && tag.getValue() != null && !tag.getValue().trim().isEmpty()) {
                    teams.add(tag.getValue().trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return teams;
    }

    public long countMonitorOnlyDevices(InsightDataBundle bundle, Set<String> scopeDevices) {
        List<GuardrailPolicies> activePolicies = new ArrayList<>();
        for (GuardrailPolicies policy : bundle.policies) {
            if (policy != null && policy.isActive()) activePolicies.add(policy);
        }
        long monitorOnly = 0;
        for (String device : scopeDevices) {
            boolean covered = false;
            boolean onlyAlerts = true;
            for (GuardrailPolicies policy : activePolicies) {
                if (!InsightUtil.policyCoversDevice(policy.getApplyToDeviceIds(), device)) continue;
                covered = true;
                if (!ALERT_ONLY_BEHAVIOUR.equalsIgnoreCase(policy.getBehaviour())) {
                    onlyAlerts = false;
                    break;
                }
            }
            if (covered && onlyAlerts) monitorOnly++;
        }
        return monitorOnly;
    }

    private static boolean seenInWindow(Integer lastSeen, int startTs, int endTs) {
        if (lastSeen == null) return false;
        if (startTs > 0 && lastSeen < startTs) return false;
        return endTs <= 0 || lastSeen <= endTs;
    }
}
