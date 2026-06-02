package com.akto.action;

import com.akto.action.threat_detection.AbstractThreatDetectionAction;
import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.McpAuditInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.McpAuditInfo;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.traffic.CollectionTags;
import com.akto.mcp.McpRequestResponseUtils;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.AgenticObserveUtil;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Violations for agentic observe flyouts and assets table.
 * Inventory grouping uses existing collection APIs on the frontend.
 */
public class AgenticObserveAction extends AbstractThreatDetectionAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(AgenticObserveAction.class, LogDb.DASHBOARD);
    private static final int MAX_THREAT_FETCH_LIMIT = 100_000;

    @Getter
    private BasicDBObject response = new BasicDBObject();

    @Setter
    private String deviceId;

    @Setter
    private String assetId;

    @Setter
    private List<Integer> apiCollectionIds;

    @Setter
    private String assetType;

    /** When true, fetchAgenticAssetDetails also returns the raw endpoint list (opt-in, it is large). */
    @Setter
    private boolean includeEndpoints;

    /** Optional cap on the raw endpoint list when includeEndpoints is true. */
    @Setter
    private int endpointLimit;

    @Setter
    private int startTimestamp;

    @Setter
    private int endTimestamp;

    /** Guardrail: keep the tool-fetched context small so MCP /chat stays well under gateway limits. */
    private static final int DEFAULT_ENDPOINT_LIMIT = 50;
    private static final int MAX_ENDPOINTS = 200;
    private static final int MAX_MCP_COMPONENTS = 300;

    public String fetchAgenticViolations() {
        response = new BasicDBObject();
        try {
            if (endTimestamp == 0) {
                endTimestamp = Context.now();
            }
            if (startTimestamp <= 0) {
                startTimestamp = endTimestamp - 90 * Constants.ONE_DAY_TIMESTAMP;
            }
            Set<Integer> demoCollections = getDemoCollections();
            Set<Integer> collectionIds = resolveCollectionIdsForViolations();
            List<BasicDBObject> rows = new ArrayList<>();

            List<DashboardMaliciousEvent> threats = super.fetchAllMaliciousEvents(
                    startTimestamp, endTimestamp, MAX_THREAT_FETCH_LIMIT, null);
            int id = 0;
            for (DashboardMaliciousEvent event : threats) {
                if (demoCollections.contains(event.getApiCollectionId())) {
                    continue;
                }
                if (!collectionIds.isEmpty() && !collectionIds.contains(event.getApiCollectionId())) {
                    continue;
                }
                String sev = AgenticObserveUtil.normalizeSeverityKey(event.getSeverity());
                if (sev == null) {
                    sev = "medium";
                }
                BasicDBObject row = new BasicDBObject();
                row.put("id", id++);
                row.put("severity", sev);
                row.put("title", StringUtils.defaultIfBlank(event.getFilterId(), "Policy violation"));
                row.put("description", StringUtils.defaultIfBlank(event.getUrl(), event.getHost()));
                row.put("agent", StringUtils.defaultIfBlank(event.getHost(), "Unknown"));
                row.put("agentType", AgenticObserveUtil.CLIENT_TYPE_MCP_SERVER);
                row.put("apiCollectionId", event.getApiCollectionId());
                row.put("time", event.getTimestamp());
                rows.add(row);
            }

            Bson issuesFilter = Filters.and(
                    Filters.gte(TestingRunIssues.CREATION_TIME, startTimestamp),
                    Filters.lte(TestingRunIssues.CREATION_TIME, endTimestamp),
                    Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, GlobalEnums.TestRunIssueStatus.OPEN),
                    Filters.nin(TestingRunIssues.ID_API_COLLECTION_ID, demoCollections)
            );
            if (!collectionIds.isEmpty()) {
                issuesFilter = Filters.and(issuesFilter, Filters.in(TestingRunIssues.ID_API_COLLECTION_ID, collectionIds));
            }
            List<TestingRunIssues> issues = TestingRunIssuesDao.instance.findAll(issuesFilter);
            Map<String, String> testNames = fetchTestNames(issues);

            for (TestingRunIssues issue : issues) {
                if (issue.getId() == null) {
                    continue;
                }
                GlobalEnums.Severity severity = issue.getSeverity();
                String sev = severity != null
                        ? AgenticObserveUtil.normalizeSeverityKey(severity.name())
                        : "medium";
                if (sev == null) {
                    sev = "medium";
                }
                String subCategory = issue.getId().getTestSubCategory();
                BasicDBObject row = new BasicDBObject();
                row.put("id", id++);
                row.put("severity", sev);
                row.put("title", testNames.getOrDefault(subCategory, StringUtils.defaultString(subCategory, "Security issue")));
                row.put("description", subCategory);
                row.put("agent", issue.getId().getApiInfoKey() != null ? issue.getId().getApiInfoKey().getUrl() : "Unknown");
                row.put("agentType", AgenticObserveUtil.CLIENT_TYPE_AI_AGENT);
                if (issue.getId().getApiInfoKey() != null) {
                    row.put("apiCollectionId", issue.getId().getApiInfoKey().getApiCollectionId());
                }
                row.put("time", issue.getCreationTime());
                rows.add(row);
            }

            rows.sort((a, b) -> Integer.compare(b.getInt("time", 0), a.getInt("time", 0)));
            response.put("violations", rows);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching agentic violations: " + e.getMessage());
            addActionError("Error fetching agentic violations: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    /**
     * Tool-driven context for agentic_observe chat. Given a minimal key — either an asset's
     * apiCollectionIds or a deviceId — resolves the relevant collections and returns a compact
     * summary of the asset's endpoints, (for MCP servers) tools/resources/prompts, and a violation
     * count breakdown. Lets the MCP agent fetch an asset's data on demand instead of the dashboard
     * pre-stuffing every field into the chat metadata.
     */
    public String fetchAgenticAssetDetails() {
        response = new BasicDBObject();
        try {
            Set<Integer> collectionIds = resolveCollectionIdsForViolations();
            if (collectionIds.isEmpty()) {
                response.put("mcpServers", new ArrayList<>());
                response.put("deviceMcpMap", new BasicDBObject());
                response.put("devices", new ArrayList<>());
                response.put("deviceCount", 0);
                response.put("endpointCount", 0);
                response.put("skillCount", 0);
                response.put("maliciousSkillCount", 0);
                response.put("violationCounts", new BasicDBObject());
                return SUCCESS.toUpperCase();
            }
            Set<Integer> demoCollections = getDemoCollections();
            collectionIds.removeAll(demoCollections);

            // ── 1. Collections (hostName encodes <deviceId>.<sourceId>.<serviceName>) ───────
            List<ApiCollection> collections = ApiCollectionsDao.instance.findAll(
                    Filters.in(Constants.ID, collectionIds),
                    Projections.include(Constants.ID, ApiCollection.HOST_NAME, ApiCollection.SKILLS));

            // MCP service names deduped case-insensitively (matches UI getAgentLinkedComponents)
            Map<String, String> canonicalMcpName = new java.util.LinkedHashMap<>(); // lcKey -> display
            Map<String, Set<String>> deviceMcpKeys = new java.util.LinkedHashMap<>(); // deviceId -> lcKeys
            Set<String> deviceIds = new java.util.LinkedHashSet<>();
            // Skill names come from ApiCollection.skills[] — unique across all collections for this asset.
            Set<String> allSkillNames = new java.util.LinkedHashSet<>();

            for (ApiCollection col : collections) {
                String hostName = col.getHostName();
                if (StringUtils.isBlank(hostName)) continue;
                String[] parts = hostName.split("\\.");
                String devId = parts[0];
                if (StringUtils.isBlank(devId)) continue;
                deviceIds.add(devId);
                String mcpName = McpRequestResponseUtils.extractServiceNameFromHost(hostName);
                if (StringUtils.isBlank(mcpName)) continue;
                String lcKey = mcpName.toLowerCase();
                canonicalMcpName.putIfAbsent(lcKey, mcpName);
                deviceMcpKeys.computeIfAbsent(devId, k -> new java.util.LinkedHashSet<>()).add(lcKey);
                // Collect skill names from this collection
                if (col.getSkills() != null) {
                    for (String skill : col.getSkills()) {
                        if (StringUtils.isNotBlank(skill)) allSkillNames.add(skill);
                    }
                }
            }

            // ── 2. Device username/os from Endpoint Shield ModuleInfo ─────────────────────
            // module.name is the human-readable device name; the hex deviceId lives in
            // additionalData.deviceId / additionalData.endpointId. Fetch all shield modules for
            // this account and match by ad.deviceId (lowercase) against our hex deviceIds —
            // same approach as the frontend's registerDeviceKeys / getUsernameForCollection.
            Map<String, String> deviceUsernameMap = new java.util.LinkedHashMap<>();
            Map<String, String> deviceOsMap = new java.util.LinkedHashMap<>();
            if (!deviceIds.isEmpty()) {
                Set<String> lcDeviceIds = new HashSet<>();
                for (String d : deviceIds) lcDeviceIds.add(d.toLowerCase());

                List<ModuleInfo> modules = ModuleInfoDao.instance.findAll(
                        Filters.eq(ModuleInfo.MODULE_TYPE, ModuleInfo.ModuleType.MCP_ENDPOINT_SHIELD.name()),
                        0, 2000, null);
                for (ModuleInfo mod : modules) {
                    Map<String, Object> ad = mod.getAdditionalData();
                    if (ad == null) continue;
                    String uname = resolveUsername(ad);
                    String os = resolveStringField(ad, "os", "platform");
                    // Register under every possible device key the frontend uses
                    for (String candidate : new String[]{
                            resolveStringField(ad, "deviceId", "endpointId"),
                            mod.getName()}) {
                        if (StringUtils.isBlank(candidate)) continue;
                        String lcCandidate = candidate.toLowerCase();
                        if (!lcDeviceIds.contains(lcCandidate)) continue;
                        // Match — store under the original-case deviceId
                        String matchedDevId = null;
                        for (String d : deviceIds) {
                            if (d.toLowerCase().equals(lcCandidate)) { matchedDevId = d; break; }
                        }
                        if (matchedDevId == null) continue;
                        if (StringUtils.isNotBlank(uname)) deviceUsernameMap.putIfAbsent(matchedDevId, uname);
                        if (StringUtils.isNotBlank(os))    deviceOsMap.putIfAbsent(matchedDevId, os);
                    }
                }
            }

            // ── 3. MCP audit data: component counts + malicious/blocked flags ─────────────
            Map<String, BasicDBObject> mcpAuditByKey = new java.util.LinkedHashMap<>();
            if (!canonicalMcpName.isEmpty()) {
                List<McpAuditInfo> auditRows = McpAuditInfoDao.instance.findAll(
                        Filters.and(
                                Filters.in(McpAuditInfo.MCP_HOST, canonicalMcpName.values()),
                                Filters.ne(McpAuditInfo.TYPE, McpAuditInfo.TYPE_AGENT_SKILL)),
                        0, MAX_MCP_COMPONENTS, null);
                for (McpAuditInfo row : auditRows) {
                    if (StringUtils.isBlank(row.getMcpHost()) || StringUtils.isBlank(row.getType())) continue;
                    String lcKey = row.getMcpHost().toLowerCase();
                    if (!canonicalMcpName.containsKey(lcKey)) continue;
                    BasicDBObject agg = mcpAuditByKey.computeIfAbsent(lcKey, k -> {
                        BasicDBObject c = new BasicDBObject();
                        c.put("tools", 0); c.put("resources", 0); c.put("prompts", 0); c.put("total", 0);
                        c.put("isRejected", false); c.put("isBlocked", false); c.put("lastSeen", 0);
                        return c;
                    });
                    // component counts
                    if (StringUtils.isNotBlank(row.getResourceName())) {
                        String t = row.getType().toLowerCase();
                        String bucket = t.contains("tool") ? "tools" : t.contains("resource") ? "resources" : t.contains("prompt") ? "prompts" : null;
                        if (bucket != null) agg.put(bucket, agg.getInt(bucket) + 1);
                        agg.put("total", agg.getInt("total") + 1);
                    }
                    // malicious/blocked signals
                    if (McpAuditInfo.REMARKS_REJECTED.equalsIgnoreCase(row.getRemarks())) agg.put("isRejected", true);
                    if (row.isBlockAll()) agg.put("isBlocked", true);
                    // last seen
                    if (row.getLastDetected() > agg.getInt("lastSeen")) agg.put("lastSeen", row.getLastDetected());
                }
            }

            // ── 4. Build mcpServers list ──────────────────────────────────────────────────
            Map<String, Set<String>> devicesByMcpKey = new java.util.LinkedHashMap<>();
            deviceMcpKeys.forEach((dev, keys) ->
                    keys.forEach(k -> devicesByMcpKey.computeIfAbsent(k, x -> new java.util.LinkedHashSet<>()).add(dev)));

            List<BasicDBObject> mcpServers = new ArrayList<>();
            for (Map.Entry<String, String> e : canonicalMcpName.entrySet()) {
                String lcKey = e.getKey();
                BasicDBObject agg = mcpAuditByKey.getOrDefault(lcKey, new BasicDBObject()
                        .append("tools", 0).append("resources", 0).append("prompts", 0).append("total", 0)
                        .append("isRejected", false).append("isBlocked", false).append("lastSeen", 0));
                BasicDBObject row = new BasicDBObject();
                row.put("name", e.getValue());
                row.put("toolCount",      agg.getInt("tools"));
                row.put("resourceCount",  agg.getInt("resources"));
                row.put("promptCount",    agg.getInt("prompts"));
                row.put("componentCount", agg.getInt("total"));
                row.put("isRejected",     agg.getBoolean("isRejected", false));
                row.put("isBlocked",      agg.getBoolean("isBlocked", false));
                row.put("lastSeen",       agg.getInt("lastSeen"));
                row.put("devices", new ArrayList<>(devicesByMcpKey.getOrDefault(lcKey, Collections.emptySet())));
                mcpServers.add(row);
            }
            response.put("mcpServers", mcpServers);
            response.put("mcpServerCount", mcpServers.size());
            long rejectedCount = mcpServers.stream().filter(r -> r.getBoolean("isRejected", false)).count();
            long blockedCount  = mcpServers.stream().filter(r -> r.getBoolean("isBlocked", false)).count();
            response.put("maliciousMcpCount", rejectedCount);
            response.put("blockedMcpCount",   blockedCount);

            // ── 5. deviceMcpMap and device summary list ───────────────────────────────────
            BasicDBObject deviceMcp = new BasicDBObject();
            deviceMcpKeys.forEach((dev, keys) -> {
                List<String> names = new ArrayList<>();
                keys.forEach(k -> names.add(canonicalMcpName.get(k)));
                deviceMcp.put(dev, names);
            });
            response.put("deviceMcpMap", deviceMcp);
            response.put("deviceCount", deviceIds.size());

            // Per-device summary rows
            List<BasicDBObject> deviceList = new ArrayList<>();
            for (String devId : deviceIds) {
                BasicDBObject devRow = new BasicDBObject();
                devRow.put("deviceId", devId);
                String uname = deviceUsernameMap.get(devId);
                if (uname != null) devRow.put("username", uname);
                String os = deviceOsMap.get(devId);
                if (os != null) devRow.put("os", os);
                Set<String> devMcpKeys = deviceMcpKeys.getOrDefault(devId, Collections.emptySet());
                devRow.put("mcpServers", devMcpKeys.stream().map(canonicalMcpName::get).collect(java.util.stream.Collectors.toList()));
                devRow.put("mcpServerCount", devMcpKeys.size());
                deviceList.add(devRow);
            }
            response.put("devices", deviceList);

            // ── 6. Skills: count from ApiCollection.skills[], malicious/blocked from ApiInfo ──
            // Skill names are stored in ApiCollection.skills[] — unique names across all collections.
            // Malicious/blocked state lives in ApiInfo.tagsList (key=malicious-skill, value=true)
            // and ApiInfo.isSkillBlocked, matched by URL suffix after "skills/".
            response.put("skillCount", allSkillNames.size());

            List<String> maliciousSkills = new ArrayList<>();
            int blockedSkillCount = 0;
            int maxRiskScore = 0;

            // Only fetch ApiInfo when there's something to look up (skills or endpoints needed)
            List<ApiInfo> allApiInfos = Collections.emptyList();
            if (!allSkillNames.isEmpty() || includeEndpoints) {
                allApiInfos = ApiInfoDao.instance.findAll(
                        Filters.in(ApiInfo.ID_API_COLLECTION_ID, collectionIds),
                        0, MAX_ENDPOINTS, null,
                        Projections.include(Constants.ID, ApiInfo.RISK_SCORE, ApiInfo.TAGS_STRING, ApiInfo.IS_SKILL_BLOCKED));
            } else {
                // Still need riskScore — lightweight fetch
                allApiInfos = ApiInfoDao.instance.findAll(
                        Filters.in(ApiInfo.ID_API_COLLECTION_ID, collectionIds),
                        0, MAX_ENDPOINTS, null,
                        Projections.include(Constants.ID, ApiInfo.RISK_SCORE));
            }

            for (ApiInfo apiInfo : allApiInfos) {
                if (apiInfo.getRiskScore() > maxRiskScore) maxRiskScore = (int) apiInfo.getRiskScore();
                if (allSkillNames.isEmpty()) continue;
                String url = apiInfo.getId() != null ? apiInfo.getId().getUrl() : null;
                if (url == null) continue;
                int slashIdx = url.lastIndexOf("skills/");
                if (slashIdx < 0) continue;
                String skillName = url.substring(slashIdx + "skills/".length());
                if (StringUtils.isBlank(skillName) || !allSkillNames.contains(skillName)) continue;
                if (apiInfo.getIsSkillBlocked()) blockedSkillCount++;
                if (apiInfo.getTagsList() != null) {
                    for (CollectionTags tag : apiInfo.getTagsList()) {
                        if ("malicious-skill".equals(tag.getKeyName()) && "true".equals(tag.getValue())) {
                            maliciousSkills.add(skillName);
                            break;
                        }
                    }
                }
            }
            response.put("riskScore",          maxRiskScore);
            response.put("maliciousSkillCount", maliciousSkills.size());
            response.put("blockedSkillCount",   blockedSkillCount);
            if (!maliciousSkills.isEmpty()) response.put("maliciousSkills", maliciousSkills);

            // ── 7. Endpoint count (cheap countDocuments) + optional list ─────────────────
            long totalEndpoints = ApiInfoDao.instance.getMCollection().countDocuments(
                    Filters.in(ApiInfo.ID_API_COLLECTION_ID, collectionIds));
            response.put("endpointCount", totalEndpoints);

            if (includeEndpoints) {
                int limit = endpointLimit > 0 ? Math.min(endpointLimit, MAX_ENDPOINTS) : DEFAULT_ENDPOINT_LIMIT;
                List<BasicDBObject> endpoints = new ArrayList<>();
                for (ApiInfo apiInfo : allApiInfos.subList(0, Math.min(limit, allApiInfos.size()))) {
                    if (apiInfo.getId() == null) continue;
                    BasicDBObject ep = new BasicDBObject();
                    ep.put("apiCollectionId", apiInfo.getId().getApiCollectionId());
                    ep.put("url", apiInfo.getId().getUrl());
                    ep.put("method", apiInfo.getId().getMethod() != null ? apiInfo.getId().getMethod().name() : null);
                    ep.put("riskScore", apiInfo.getRiskScore());
                    endpoints.add(ep);
                }
                response.put("endpoints", endpoints);
                response.put("endpointsReturned", endpoints.size());
                response.put("endpointsTruncated", totalEndpoints > endpoints.size());
            }

            // ── 8. Violation counts ───────────────────────────────────────────────────────
            response.put("violationCounts", aggregateViolationCounts(collectionIds, demoCollections));
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching agentic asset details: " + e.getMessage());
            addActionError("Error fetching agentic asset details: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    private static String resolveUsername(Map<String, Object> ad) {
        for (String key : new String[]{"username", "userName", "user", "email"}) {
            Object v = ad.get(key);
            if (v instanceof String && StringUtils.isNotBlank((String) v)) return ((String) v).trim();
        }
        return null;
    }

    private static String resolveStringField(Map<String, Object> ad, String... keys) {
        for (String key : keys) {
            Object v = ad.get(key);
            if (v instanceof String && StringUtils.isNotBlank((String) v)) return ((String) v).trim();
        }
        return null;
    }

    private BasicDBObject aggregateViolationCounts(Set<Integer> collectionIds, Set<Integer> demoCollections) {
        int end = Context.now();
        int start = end - 90 * Constants.ONE_DAY_TIMESTAMP;
        Bson issuesFilter = Filters.and(
                Filters.gte(TestingRunIssues.CREATION_TIME, start),
                Filters.lte(TestingRunIssues.CREATION_TIME, end),
                Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, GlobalEnums.TestRunIssueStatus.OPEN),
                Filters.nin(TestingRunIssues.ID_API_COLLECTION_ID, demoCollections),
                Filters.in(TestingRunIssues.ID_API_COLLECTION_ID, collectionIds));
        List<TestingRunIssues> issues = TestingRunIssuesDao.instance.findAll(
                issuesFilter, Projections.include(TestingRunIssues.KEY_SEVERITY));
        int critical = 0, high = 0, medium = 0, low = 0;
        for (TestingRunIssues issue : issues) {
            String sev = issue.getSeverity() != null
                    ? AgenticObserveUtil.normalizeSeverityKey(issue.getSeverity().name())
                    : "medium";
            if ("critical".equals(sev)) critical++;
            else if ("high".equals(sev)) high++;
            else if ("low".equals(sev)) low++;
            else medium++;
        }
        BasicDBObject counts = new BasicDBObject();
        counts.put("critical", critical);
        counts.put("high", high);
        counts.put("medium", medium);
        counts.put("low", low);
        return counts;
    }

    private Set<Integer> resolveCollectionIdsForViolations() {
        if (apiCollectionIds != null && !apiCollectionIds.isEmpty()) {
            return new HashSet<>(apiCollectionIds);
        }
        if (StringUtils.isNotBlank(assetId)) {
            return Collections.emptySet();
        }
        return resolveCollectionIdsForDevice();
    }

    private Set<Integer> resolveCollectionIdsForDevice() {
        if (StringUtils.isBlank(deviceId)) {
            return Collections.emptySet();
        }
        return com.akto.dao.ApiCollectionsDao.instance.findAll(
                Filters.regex(com.akto.dto.ApiCollection.HOST_NAME, "^" + Pattern.quote(deviceId) + "\\."),
                Projections.include(Constants.ID)
        ).stream().map(com.akto.dto.ApiCollection::getId).collect(java.util.stream.Collectors.toSet());
    }

    private Map<String, String> fetchTestNames(List<TestingRunIssues> issues) {
        Set<String> ids = new HashSet<>();
        for (TestingRunIssues issue : issues) {
            if (issue.getId() != null && issue.getId().getTestSubCategory() != null
                    && !issue.getId().getTestSubCategory().startsWith("http")) {
                ids.add(issue.getId().getTestSubCategory());
            }
        }
        Map<String, String> names = new java.util.HashMap<>();
        if (ids.isEmpty()) {
            return names;
        }
        List<YamlTemplate> templates = YamlTemplateDao.instance.findAll(
                Filters.in("_id", ids),
                Projections.include(YamlTemplate.INFO)
        );
        for (YamlTemplate t : templates) {
            if (t != null && t.getInfo() != null) {
                Info info = t.getInfo();
                if (StringUtils.isNotBlank(info.getName())) {
                    names.put(t.getId(), info.getName());
                }
            }
        }
        return names;
    }

    private Set<Integer> getDemoCollections() {
        Set<Integer> demoCollections = new HashSet<>(UsageMetricCalculator.getDeactivated());
        com.akto.dto.ApiCollection juiceshop = com.akto.dao.ApiCollectionsDao.instance.findByName("juice_shop_demo");
        if (juiceshop != null) {
            demoCollections.add(juiceshop.getId());
        }
        return demoCollections;
    }
}
