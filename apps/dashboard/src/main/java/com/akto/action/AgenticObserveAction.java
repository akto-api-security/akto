package com.akto.action;

import com.akto.action.threat_detection.AbstractThreatDetectionAction;
import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.AgenticObserveUtil;
import com.akto.util.Constants;
import com.akto.util.McpClientRegistry;
import com.akto.util.enums.GlobalEnums;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Violations for agentic observe flyouts and assets table.
 * Also: server-side pagination for the Agentic Assets "New Layout" table (see
 * fetchAgenticAssetsSummary/fetchAgenticAssetsStats) — see this action's per-method docs and
 * atlas-scale-test/DASHBOARD_OPTIMIZATION.md's "paginated server-side aggregation rebuild" entry for
 * why this exists (computing all ~795 grouped rows client-side on every load was the root latency
 * cause at real account scale).
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
    private int startTimestamp;

    @Setter
    private int endTimestamp;

    // ---- Server-side pagination for fetchAgenticAssetsSummary ----
    @Setter private int skip;
    @Setter private int limit;
    @Setter private String sortKey;
    @Setter private int sortOrder; // 1 asc, -1 desc (Mongo convention, matches NhiGovernanceViolationsAction)
    @Setter private String queryValue; // search on asset/group name
    @Setter private Map<String, Integer> trafficMap;
    @Setter private Map<String, Double> riskScoreMap;
    @Setter private Map<String, List<String>> filters; // AG Grid column filters, keyed by field name (e.g. "team", "userRole")

    // ---- Server-side pagination for fetchUsersAndDevicesSummary ----
    @Setter private String groupBy; // "user" | "device"
    @Setter private Map<String, List<String>> sensitiveMap; // collection id (string) -> sensitive types
    @Setter private Map<String, String> usernameMap; // Endpoint Shield username resolution map
    @Setter private Map<String, Map<String, String>> userMetadataMap; // username -> {team, userRole, teamSource, roleSource}

    // ---- Server-side pagination for fetchDeviceEndpointsSummary ----
    @Setter private String parentDeviceId; // blank => paginated top-level device rows; set => that device's (device,service) children
    @Setter private Map<String, Map<String, String>> deviceMetadataMap; // deviceId -> {username, team, role, os}
    @Setter private Map<String, Map<String, Integer>> violationsByCollectionId; // collection id (string) -> {critical, high, medium, low}

    /**
     * ATLAS skill enrichment in a SINGLE query. Skill (/skills/&lt;name&gt;) and agent-config (/config/)
     * endpoints only ever exist on agentic collections, so one regex scan of api_info returns everything
     * the Agentic-assets / Users-and-devices pages need — replacing the old per-collection N+1 that fired
     * one fetchApiInfosForCollection call for each of the account's ~26k collections.
     */
    public String fetchAgenticSkillData() {
        response = new BasicDBObject();
        Set<Integer> deactivated = UsageMetricCalculator.getDeactivated();
        List<ApiInfo> apiInfos = ApiInfoDao.instance.findAll(
                Filters.and(
                        Filters.or(
                                Filters.regex(ApiInfo.ID_URL, "skills/"),
                                Filters.regex(ApiInfo.ID_URL, "/config/")
                        ),
                        Filters.nin(ApiInfo.ID_API_COLLECTION_ID, deactivated)
                ));

        Map<String, Float> skillScoreMap = new HashMap<>();
        Set<String> maliciousSkills = new HashSet<>();
        // Collection-scoped "<collectionId>|<skillNameLowercase>" keys — matches constants.js's
        // skillCollectionKey exactly — so a same-named skill on a different user/collection doesn't
        // inherit the malicious flag (the account-wide maliciousSkills set above is name-only).
        Set<String> maliciousSkillKeys = new HashSet<>();
        Set<String> misconfiguredSkills = new HashSet<>();
        Set<Integer> misconfiguredCollectionIds = new HashSet<>();

        for (ApiInfo info : apiInfos) {
            if (info == null || info.getId() == null) continue;
            String url = info.getId().getUrl();
            if (url == null) url = "";
            boolean malicious = hasTrueTag(info.getTagsList(), "malicious-skill");
            boolean misconfigured = hasTrueTag(info.getTagsList(), "misconfigured-config");

            int idx = url.indexOf("skills/");
            if (idx >= 0) {
                String skillName = url.substring(idx + "skills/".length());
                if (!skillName.isEmpty()) {
                    skillScoreMap.put(skillName, info.getRiskScore());
                    if (malicious) {
                        maliciousSkills.add(skillName);
                        maliciousSkillKeys.add(info.getId().getApiCollectionId() + "|" + skillName.toLowerCase(Locale.ROOT));
                    }
                    if (misconfigured) misconfiguredSkills.add(skillName);
                }
            }
            if (url.contains("/config/") && misconfigured) {
                misconfiguredCollectionIds.add(info.getId().getApiCollectionId());
            }
        }

        response.put("skillScoreMap", skillScoreMap);
        response.put("maliciousSkills", new ArrayList<>(maliciousSkills));
        response.put("maliciousSkillKeys", new ArrayList<>(maliciousSkillKeys));
        response.put("misconfiguredSkills", new ArrayList<>(misconfiguredSkills));
        response.put("misconfiguredCollectionIds", new ArrayList<>(misconfiguredCollectionIds));
        return SUCCESS.toUpperCase();
    }

    private static boolean hasTrueTag(List<CollectionTags> tags, String keyName) {
        if (tags == null) return false;
        for (CollectionTags t : tags) {
            if (t != null && keyName.equals(t.getKeyName()) && "true".equals(t.getValue())) return true;
        }
        return false;
    }

    public String fetchAgenticViolations() {
        response = new BasicDBObject();
        try {
            if (endTimestamp == 0) {
                endTimestamp = Context.now();
            }
            // startTimestamp <= 0 means "all time" (no lower bound) — do NOT clamp to a default window.
            // fetchAllMaliciousEvents already omits the start bound when startTimestamp <= 0, and the
            // issues filter below applies the lower bound only when startTimestamp > 0.
            boolean hasStartBound = startTimestamp > 0;
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
                GlobalEnums.Severity sev = AgenticObserveUtil.parseSeverity(event.getSeverity());
                if (sev == null) {
                    sev = GlobalEnums.Severity.MEDIUM;
                }
                BasicDBObject row = new BasicDBObject();
                row.put("id", id++);
                row.put("severity", sev.name().toLowerCase());
                row.put("title", StringUtils.defaultIfBlank(event.getFilterId(), "Policy violation"));
                row.put("description", StringUtils.defaultIfBlank(event.getUrl(), event.getHost()));
                row.put("agent", StringUtils.defaultIfBlank(event.getHost(), "Unknown"));
                row.put("agentType", AgenticObserveUtil.CLIENT_TYPE_MCP_SERVER);
                row.put("apiCollectionId", event.getApiCollectionId());
                row.put("time", event.getTimestamp());
                rows.add(row);
            }

            List<Bson> issuesFilters = new ArrayList<>();
            if (hasStartBound) {
                issuesFilters.add(Filters.gte(TestingRunIssues.CREATION_TIME, startTimestamp));
            }
            issuesFilters.add(Filters.lte(TestingRunIssues.CREATION_TIME, endTimestamp));
            issuesFilters.add(Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, GlobalEnums.TestRunIssueStatus.OPEN));
            issuesFilters.add(Filters.nin(TestingRunIssues.ID_API_COLLECTION_ID, demoCollections));
            Bson issuesFilter = Filters.and(issuesFilters);
            if (!collectionIds.isEmpty()) {
                issuesFilter = Filters.and(issuesFilter, Filters.in(TestingRunIssues.ID_API_COLLECTION_ID, collectionIds));
            }
            List<TestingRunIssues> issues = TestingRunIssuesDao.instance.findAll(issuesFilter);
            Map<String, String> testNames = fetchTestNames(issues);

            for (TestingRunIssues issue : issues) {
                if (issue.getId() == null) {
                    continue;
                }
                GlobalEnums.Severity sev = issue.getSeverity();
                if (sev == null) {
                    sev = GlobalEnums.Severity.MEDIUM;
                }
                String subCategory = issue.getId().getTestSubCategory();
                BasicDBObject row = new BasicDBObject();
                row.put("id", id++);
                row.put("severity", sev.name().toLowerCase());
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

    // ==================== Server-side pagination for Agentic Assets ====================
    // See the class-level doc comment and atlas-scale-test/DASHBOARD_OPTIMIZATION.md for why this
    // exists. Same suffix list as constants.js's SUFFIX_TLDS — hostnames like
    // "vulnerable-mcp-kong.akto.io" aren't in device.agent.service format, so grouping by a bare TLD
    // would incorrectly collapse unrelated collections together; fall back to the full hostname.
    private static final Set<String> SERVICE_NAME_SUFFIX_TLDS = new HashSet<>(Arrays.asList(
            "io", "com", "net", "org", "cloud", "dev", "app", "ai", "co"
    ));
    // Mirrors mcpClientHelper.js's AGENT_OWNER_TAG_KEYS.
    private static final Set<String> AGENT_OWNER_TAG_KEYS = new HashSet<>(Arrays.asList(
            Constants.AKTO_MCP_CLIENT_TAG, Constants.AKTO_AI_AGENT_TAG
    ));
    // String.split(String) compiles a new Pattern on every call unless the regex is a single
    // non-metacharacter literal — precompile once, this runs once per (collection, group) pair.
    private static final Pattern DOT_SPLIT = Pattern.compile("\\.");

    private static boolean hasTagKey(List<CollectionTags> envType, String keyName) {
        if (envType == null) return false;
        for (CollectionTags tag : envType) {
            if (tag != null && keyName.equals(tag.getKeyName())) return true;
        }
        return false;
    }

    private static boolean hasAgentOwnerTag(List<CollectionTags> envType) {
        if (envType == null) return false;
        for (CollectionTags tag : envType) {
            if (tag == null) continue;
            if (AGENT_OWNER_TAG_KEYS.contains(tag.getKeyName()) || "agent-name".equals(tag.getKeyName())) return true;
        }
        return false;
    }

    private static String extractServiceNameForGrouping(String hostName) {
        if (StringUtils.isBlank(hostName)) return null;
        String[] parts = DOT_SPLIT.split(hostName);
        if (parts.length < 3) return hostName;
        if (SERVICE_NAME_SUFFIX_TLDS.contains(parts[2].toLowerCase(Locale.ROOT))) return hostName;
        StringBuilder sb = new StringBuilder(parts[2]);
        for (int i = 3; i < parts.length; i++) sb.append(".").append(parts[i]);
        return sb.toString();
    }

    // Cheap per-group accumulator, built for EVERY group (agent/service/llm/skill — ~800 at real
    // scale) in one pass over all collections. Deliberately does NOT build a per-device breakdown —
    // that's the expensive part (needs a Map<deviceId, ...> with its own Set<String> per device),
    // and only happens for the page actually being returned, in buildDevicesForGroup below.
    private static final class GroupSummary {
        final String groupKey;
        final String rowType; // "agent" | "service" | "llm" | "skill"
        String name;
        String clientType;
        String tagKey; // agent rows only
        final Set<String> rawTagValues = new HashSet<>(); // agent rows only — variant tag values collapsed into this canonical group
        final Set<String> hostNames = new HashSet<>();
        final Set<String> endpointIds = new HashSet<>();
        final Set<String> skillNames = new HashSet<>();
        final List<Integer> collectionIds = new ArrayList<>();
        double maxRiskScore = 0;
        int maxTrafficTimestamp = 0;
        boolean hasPersonalAccount = false;
        boolean hasLocalMcpServer = false;
        boolean hasMisconfiguredConfig = false;

        GroupSummary(String groupKey, String rowType) {
            this.groupKey = groupKey;
            this.rowType = rowType;
        }

        void accumulateCheap(ApiCollection c, String hostName, List<CollectionTags> envType, double collRisk, int collTraffic, String deviceId) {
            collectionIds.add(c.getId());
            hostNames.add(hostName);
            if (deviceId != null) endpointIds.add(deviceId);
            if (c.getSkills() != null) {
                for (String s : c.getSkills()) {
                    if (StringUtils.isNotBlank(s)) skillNames.add(s);
                }
            }
            if (envType != null) {
                for (CollectionTags tag : envType) {
                    if (tag == null) continue;
                    String key = tag.getKeyName();
                    String value = tag.getValue();
                    if (("browser-llm-account-type".equals(key) || "login-user-email-type".equals(key)) && "personal".equals(value)) {
                        hasPersonalAccount = true;
                    }
                    if ("local-mcp-server".equals(key)) hasLocalMcpServer = true;
                    if ("misconfigured-config".equals(key) && "true".equals(value)) hasMisconfiguredConfig = true;
                }
            }
            if (collRisk > maxRiskScore) maxRiskScore = collRisk;
            if (collTraffic > maxTrafficTimestamp) maxTrafficTimestamp = collTraffic;
        }

        BasicDBObject toSummaryResponse() {
            BasicDBObject g = new BasicDBObject();
            g.put("id", rowType + "-" + groupKey);
            g.put("groupKey", groupKey);
            g.put("name", name);
            g.put("rowType", rowType);
            g.put("clientType", clientType);
            g.put("tagKey", tagKey);
            g.put("rawTagValues", new ArrayList<>(rawTagValues));
            g.put("hostNames", new ArrayList<>(hostNames));
            g.put("collectionIds", collectionIds);
            g.put("endpointsCount", endpointIds.size());
            g.put("skillNames", new ArrayList<>(skillNames));
            g.put("skillCount", skillNames.size());
            g.put("riskScore", maxRiskScore > 0 ? AgenticObserveUtil.roundRiskScore(maxRiskScore) : null);
            g.put("lastSeenEpoch", maxTrafficTimestamp);
            g.put("hasPersonalAccount", hasPersonalAccount);
            g.put("hasLocalMcpServer", hasLocalMcpServer);
            g.put("hasMisconfiguredConfig", hasMisconfiguredConfig);
            return g;
        }
    }

    // Expensive per-device accumulator — only built for groups on the page actually being returned
    // (see fetchAgenticAssetsSummary), not all ~800 groups. This is what made the old client-side
    // pipeline (and the reverted first Stage 3 attempt, which built this for every group) slow.
    private static final class DeviceAcc {
        final String deviceId;
        double riskScore;
        int lastSeenEpoch;
        final Set<String> services = new HashSet<>();

        DeviceAcc(String deviceId) { this.deviceId = deviceId; }

        BasicDBObject toResponse() {
            BasicDBObject d = new BasicDBObject();
            d.put("deviceId", deviceId);
            d.put("endpoint", deviceId);
            d.put("riskScore", riskScore > 0 ? AgenticObserveUtil.roundRiskScore(riskScore) : null);
            d.put("lastSeenEpoch", lastSeenEpoch);
            d.put("services", new ArrayList<>(services));
            return d;
        }
    }

    private static List<BasicDBObject> buildDevicesForGroup(GroupSummary g, Map<Integer, ApiCollection> byId,
            Map<String, Integer> traffic, Map<String, Double> risk) {
        Map<String, DeviceAcc> devices = new LinkedHashMap<>();
        for (Integer cid : g.collectionIds) {
            ApiCollection c = byId.get(cid);
            if (c == null) continue;
            String hostName = c.getHostName();
            String deviceId = AgenticObserveUtil.extractEndpointId(hostName);
            if (deviceId == null) continue;
            String serviceName = extractServiceNameForGrouping(hostName);
            String idStr = String.valueOf(cid);
            Integer t = traffic.get(idStr);
            int collTraffic = t != null ? t : 0;
            Double r = risk.get(idStr);
            double collRisk = r != null ? r : 0.0;
            DeviceAcc d = devices.computeIfAbsent(deviceId, DeviceAcc::new);
            if (collRisk > d.riskScore) d.riskScore = collRisk;
            if (collTraffic > d.lastSeenEpoch) d.lastSeenEpoch = collTraffic;
            if (serviceName != null) d.services.add(serviceName);
        }
        List<BasicDBObject> out = new ArrayList<>();
        for (DeviceAcc d : devices.values()) out.add(d.toResponse());
        return out;
    }

    /**
     * One pass over every collection classifying it into agent/service/llm/skill groups — mirrors
     * groupCollectionsByAgent/Service/LLM/Skill in constants.js combined, including the agent-type
     * KNOWN_CLIENTS/alias resolution (McpClientRegistry) that the reverted first Stage 3 attempt
     * deliberately left client-side (see atlas-scale-test/DASHBOARD_OPTIMIZATION.md — leaving it out
     * is what made that attempt not a win, since agent groups cover ~98% of this account's
     * collections). Cheap: no per-device breakdown here, see buildDevicesForGroup.
     */
    private Map<String, GroupSummary> classifyAllGroups(List<ApiCollection> collections,
            Map<String, Integer> traffic, Map<String, Double> risk) {
        Map<String, GroupSummary> groups = new LinkedHashMap<>();

        for (ApiCollection c : collections) {
            if (c.isDeactivated()) continue;
            String hostName = c.getHostName();
            if (StringUtils.isBlank(hostName) || hostName.contains(AgenticObserveUtil.NOT_ATTACHED_VALUE)) continue;

            List<CollectionTags> envType = c.getEnvType();
            String idStr = String.valueOf(c.getId());
            Integer trafficVal = traffic.get(idStr);
            int collTraffic = trafficVal != null ? trafficVal : 0;
            Double riskVal = risk.get(idStr);
            double collRisk = riskVal != null ? riskVal : 0.0;
            String deviceId = AgenticObserveUtil.extractEndpointId(hostName);

            // Skill fan-out — a collection with multiple skills is a member of multiple skill
            // groups, independent of its agent/service/llm classification below.
            if (c.getSkills() != null) {
                for (String skill : c.getSkills()) {
                    if (StringUtils.isBlank(skill)) continue;
                    GroupSummary g = groups.computeIfAbsent("skill|" + skill, k -> {
                        GroupSummary gs = new GroupSummary(skill, "skill");
                        gs.name = skill;
                        gs.clientType = AgenticObserveUtil.CLIENT_TYPE_SKILL;
                        return gs;
                    });
                    g.accumulateCheap(c, hostName, envType, collRisk, collTraffic, deviceId);
                }
            }

            boolean hasBrowserLlm = hasTagKey(envType, Constants.AKTO_BROWSER_LLM_TAG);
            if (hasBrowserLlm) {
                String serviceName = extractServiceNameForGrouping(hostName);
                if (StringUtils.isNotBlank(serviceName)) {
                    GroupSummary g = groups.computeIfAbsent("llm|" + serviceName, k -> {
                        GroupSummary gs = new GroupSummary(serviceName, "llm");
                        gs.name = serviceName;
                        gs.clientType = AgenticObserveUtil.CLIENT_TYPE_LLM;
                        return gs;
                    });
                    g.accumulateCheap(c, hostName, envType, collRisk, collTraffic, deviceId);
                }
                continue; // matches groupCollectionsByAgent/Service's browser-llm skip
            }

            CollectionTags assetTag = AgenticObserveUtil.findAssetTag(c);
            boolean addedToAgentGroup = false;
            if (assetTag != null && StringUtils.isNotBlank(assetTag.getValue())
                    && !Constants.AKTO_BROWSER_LLM_AGENT_TAG.equals(assetTag.getKeyName())) {
                String key = McpClientRegistry.resolveClientKey(assetTag.getValue());
                final CollectionTags fAssetTag = assetTag;
                GroupSummary g = groups.computeIfAbsent("agent|" + key, k -> {
                    GroupSummary gs = new GroupSummary(key, "agent");
                    gs.name = McpClientRegistry.formatDisplayName(key);
                    gs.clientType = McpClientRegistry.getAgentTypeFromValue(key);
                    gs.tagKey = fAssetTag.getKeyName();
                    return gs;
                });
                g.rawTagValues.add(assetTag.getValue());
                g.accumulateCheap(c, hostName, envType, collRisk, collTraffic, deviceId);
                addedToAgentGroup = true;
            }

            CollectionTags typeTag = AgenticObserveUtil.findTypeTag(c);
            if (typeTag == null) continue;

            // constants.js's groupCollectionsByService only excludes an agent-owned collection from
            // ALSO getting its own service row when the type tag is gen-ai — an agent-owned
            // mcp-server collection (e.g. mcp-client=cursor + mcp-server) still gets a service row
            // too, deliberately double-counted across the agent and service groups. Mirror that
            // exact scoping rather than unconditionally skipping the service branch whenever a bound
            // or orphaned (unbound) owner tag was found.
            if (Constants.AKTO_GEN_AI_TAG.equals(typeTag.getKeyName())) {
                if (addedToAgentGroup || hasAgentOwnerTag(envType)) continue;
            }

            String serviceName = extractServiceNameForGrouping(hostName);
            if (StringUtils.isBlank(serviceName)) continue;
            final ApiCollection fc = c;
            GroupSummary g = groups.computeIfAbsent("service|" + serviceName, k -> {
                GroupSummary gs = new GroupSummary(serviceName, "service");
                gs.name = serviceName;
                gs.clientType = AgenticObserveUtil.getTypeFromCollection(fc);
                return gs;
            });
            g.accumulateCheap(c, hostName, envType, collRisk, collTraffic, deviceId);
        }

        // Matches constants.js's agentGroupKeys/servicesToShow filter — ONLY service groups (not
        // llm/skill) are excluded when their groupKey collides with a canonical agent group's key.
        Set<String> agentGroupKeys = new HashSet<>();
        for (GroupSummary g : groups.values()) {
            if ("agent".equals(g.rowType)) agentGroupKeys.add(g.groupKey);
        }
        groups.values().removeIf(g -> "service".equals(g.rowType) && agentGroupKeys.contains(g.groupKey));

        return groups;
    }

    private static Comparator<GroupSummary> buildSummaryComparator(String key) {
        Comparator<GroupSummary> cmp;
        if ("name".equals(key)) {
            cmp = Comparator.comparing((GroupSummary g) -> g.name == null ? "" : g.name, String.CASE_INSENSITIVE_ORDER);
        } else if ("lastSeenEpoch".equals(key) || "lastSeen".equals(key)) {
            cmp = Comparator.comparingInt(g -> g.maxTrafficTimestamp);
        } else if ("endpointsCount".equals(key) || "deviceCount".equals(key)) {
            cmp = Comparator.comparingInt(g -> g.endpointIds.size());
        } else if ("skillCount".equals(key)) {
            cmp = Comparator.comparingInt(g -> g.skillNames.size());
        } else {
            cmp = Comparator.comparingDouble(g -> g.maxRiskScore);
        }
        return cmp;
    }

    /**
     * Paginated, sorted, searchable grouped-asset rows for the Agentic Assets "New Layout" table.
     * Builds lightweight summaries for every group (classifyAllGroups — one pass, no per-device
     * breakdown) so sort/search/pagination can run over the full ~800-group set cheaply, then does
     * the expensive per-device breakdown ONLY for the page being returned. Team breakdown and
     * AI-interaction totals stay client-side (need Endpoint Shield / user-analysis data this
     * endpoint doesn't touch), scoped to just this page's devices — see AgenticAssetsPage.jsx.
     */
    public String fetchAgenticAssetsSummary() {
        response = new BasicDBObject();
        try {
            Map<String, Integer> traffic = trafficMap != null ? trafficMap : Collections.emptyMap();
            Map<String, Double> risk = riskScoreMap != null ? riskScoreMap : Collections.emptyMap();

            List<ApiCollection> collections = ApiCollectionsDao.instance.findAll(
                    Filters.empty(),
                    Projections.include(ApiCollection.ID, ApiCollection.HOST_NAME, ApiCollection.TAGS_STRING, ApiCollection.SKILLS)
            );

            Map<String, GroupSummary> groups = classifyAllGroups(collections, traffic, risk);
            List<GroupSummary> all = new ArrayList<>(groups.values());

            // Matches agenticPageBuilders.js's filterAssetsByLastSeen exactly — startTimestamp <= 0
            // means "all time" (no filter).
            if (startTimestamp > 0) {
                int end = endTimestamp > 0 ? endTimestamp : (int) (System.currentTimeMillis() / 1000);
                all.removeIf(g -> g.maxTrafficTimestamp < startTimestamp || g.maxTrafficTimestamp > end);
            }

            if (StringUtils.isNotBlank(queryValue)) {
                String q = queryValue.toLowerCase(Locale.ROOT);
                all.removeIf(g -> g.name == null || !g.name.toLowerCase(Locale.ROOT).contains(q));
            }

            long total = all.size();

            Comparator<GroupSummary> cmp = buildSummaryComparator(sortKey);
            if (sortOrder < 0) cmp = cmp.reversed();
            all.sort(cmp);

            int effectiveLimit = limit > 0 ? Math.min(limit, 500) : 50;
            int from = Math.max(0, skip);
            int to = Math.min(all.size(), from + effectiveLimit);
            List<GroupSummary> page = from < to ? all.subList(from, to) : Collections.emptyList();

            Map<Integer, ApiCollection> byId = new HashMap<>();
            for (ApiCollection c : collections) byId.put(c.getId(), c);

            List<BasicDBObject> rowsOut = new ArrayList<>();
            for (GroupSummary g : page) {
                BasicDBObject row = g.toSummaryResponse();
                row.put("devices", buildDevicesForGroup(g, byId, traffic, risk));
                rowsOut.add(row);
            }

            response.put("rows", rowsOut);
            response.put("total", total);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching agentic assets summary: " + e.getMessage());
            addActionError("Error fetching agentic assets summary: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    /**
     * Header-tile stats (asset counts by type) for the Agentic Assets page — same classification
     * pass as fetchAgenticAssetsSummary, aggregated rather than paginated. Violation totals stay
     * client-side (already cheap — see aggregateViolationCountsByCollectionId in constants.js,
     * proportional to the server-aggregated host-severity-count list, not to collection count).
     */
    public String fetchAgenticAssetsStats() {
        response = new BasicDBObject();
        try {
            Map<String, Integer> traffic = trafficMap != null ? trafficMap : Collections.emptyMap();
            Map<String, Double> risk = riskScoreMap != null ? riskScoreMap : Collections.emptyMap();

            List<ApiCollection> collections = ApiCollectionsDao.instance.findAll(
                    Filters.empty(),
                    Projections.include(ApiCollection.ID, ApiCollection.HOST_NAME, ApiCollection.TAGS_STRING, ApiCollection.SKILLS)
            );
            Map<String, GroupSummary> groups = classifyAllGroups(collections, traffic, risk);
            Collection<GroupSummary> counted = groups.values();
            if (startTimestamp > 0) {
                int end = endTimestamp > 0 ? endTimestamp : (int) (System.currentTimeMillis() / 1000);
                List<GroupSummary> filtered = new ArrayList<>(counted);
                filtered.removeIf(g -> g.maxTrafficTimestamp < startTimestamp || g.maxTrafficTimestamp > end);
                counted = filtered;
            }

            Map<String, Integer> countsByType = new LinkedHashMap<>();
            for (GroupSummary g : counted) {
                countsByType.merge(g.clientType, 1, Integer::sum);
            }
            response.put("totalAssets", counted.size());
            response.put("countsByType", countsByType);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching agentic assets stats: " + e.getMessage());
            addActionError("Error fetching agentic assets stats: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    // ==================== Server-side pagination for Users-and-Devices / Endpoints ====================
    // Mirrors constants.js's groupCollectionsByUser/groupCollectionsByDevice — same shared
    // accumulate/finalize shape as those two share (accumulateHostGroupedCollection/
    // finalizeHostGroupedRow), just one grouping key resolver (username vs deviceId) branching on
    // `groupBy`. Unlike Agentic Assets, sensitive-data (`sensitiveMap`) is load-bearing here — it's
    // an actual rendered column on this page, not dropped — so it's threaded through as an input map
    // the same way trafficMap/riskScoreMap already are (frontend keeps it cached, no new server-side
    // fetch needed).

    private static final Set<String> USERNAME_TAG_KEYS = new HashSet<>(Arrays.asList(
            "username", "user", "useremail", "employee", "employeeemail", "employeemail"
    ));

    // Mirrors endpointShieldHelper.js's findUsernameFromEnvTypeTags.
    private static String findUsernameFromEnvTypeTags(List<CollectionTags> envType) {
        if (envType == null) return null;
        for (CollectionTags tag : envType) {
            if (tag == null || StringUtils.isBlank(tag.getKeyName()) || StringUtils.isBlank(tag.getValue())) continue;
            String normalized = tag.getKeyName().toLowerCase(Locale.ROOT).replace(" ", "");
            if (USERNAME_TAG_KEYS.contains(normalized)) {
                String v = tag.getValue().trim();
                if (StringUtils.isNotBlank(v)) return v;
            }
        }
        return null;
    }

    // Mirrors endpointShieldHelper.js's getUsernameForCollection + getResolvedUsernameForCollection.
    // JS also tries collection.displayName/collection.name before falling back to hostname-derived
    // deviceId parsing — Java's ApiCollection has no displayName field for agentic collections, so
    // hostName (already the primary identifier used throughout this file) covers that tier too.
    private static String resolveUsername(String hostName, Map<String, String> usernameMap, List<CollectionTags> envType) {
        if (usernameMap != null && !usernameMap.isEmpty() && StringUtils.isNotBlank(hostName)) {
            String lower = hostName.toLowerCase(Locale.ROOT);
            String direct = usernameMap.get(lower);
            if (direct != null) return direct;

            String[] parts = DOT_SPLIT.split(lower);
            if (parts.length >= 1) {
                String deviceId = parts[0];
                String deviceIdKey = "__deviceId__" + deviceId;
                String viaDeviceId = usernameMap.get(deviceIdKey);
                if (viaDeviceId != null) return viaDeviceId;

                if (parts.length >= 3) {
                    StringBuilder serviceName = new StringBuilder(parts[2]);
                    for (int i = 3; i < parts.length; i++) serviceName.append('.').append(parts[i]);
                    String endpointShieldKey = deviceId + "." + serviceName;
                    String viaEndpointShieldKey = usernameMap.get(endpointShieldKey);
                    if (viaEndpointShieldKey != null) return viaEndpointShieldKey;
                }
            }
        }
        String fromTags = findUsernameFromEnvTypeTags(envType);
        return fromTags != null ? fromTags : "-";
    }

    // Cheap per-group accumulator for user/device rows — mirrors constants.js's
    // accumulateHostGroupedCollection + finalizeHostGroupedRow shape (shared by both grouping modes).
    private static final class HostGroupSummary {
        final String groupKey;
        String groupName;
        final Set<String> hostNames = new HashSet<>();
        final Set<String> clientTypes = new HashSet<>();
        final Set<String> sensitiveTypes = new HashSet<>();
        double maxRiskScore = 0;
        int maxTrafficTimestamp = 0;
        boolean hasPersonalAccount = false;
        boolean hasLocalMcpServer = false;
        boolean hasMisconfiguredConfig = false;
        String team = "";
        String userRole = "";
        String teamSource = "sso";
        String roleSource = "sso";
        int nonSkillCollectionsCount = 0;
        final Set<String> uniqueSkillNames = new HashSet<>();
        // Device-row extras (DeviceEndpoints only — null/zero when not populated, e.g. Users-and-Devices).
        String os = null;
        String username = "-";
        final int[] violations = new int[4]; // critical, high, medium, low

        HostGroupSummary(String groupKey) {
            this.groupKey = groupKey;
            this.groupName = groupKey;
        }

        void accumulate(ApiCollection c, String hostName, List<CollectionTags> envType, double collRisk,
                int collTraffic, List<String> sensitive, int[] collViolations) {
            hostNames.add(hostName);
            String category = AgenticObserveUtil.getTypeFromCollection(c);
            clientTypes.add(category);
            if (!AgenticObserveUtil.CLIENT_TYPE_SKILL.equals(category)) nonSkillCollectionsCount++;
            if (c.getSkills() != null && !c.getSkills().isEmpty()) {
                clientTypes.add(AgenticObserveUtil.CLIENT_TYPE_SKILL);
                for (String s : c.getSkills()) {
                    if (StringUtils.isNotBlank(s)) uniqueSkillNames.add(s.toLowerCase(Locale.ROOT));
                }
            }
            if (sensitive != null) sensitiveTypes.addAll(sensitive);
            if (collRisk > maxRiskScore) maxRiskScore = collRisk;
            if (collTraffic > maxTrafficTimestamp) maxTrafficTimestamp = collTraffic;
            if (collViolations != null) {
                for (int i = 0; i < 4; i++) violations[i] += collViolations[i];
            }
            if (envType != null) {
                for (CollectionTags tag : envType) {
                    if (tag == null) continue;
                    String key = tag.getKeyName();
                    String value = tag.getValue();
                    if (("browser-llm-account-type".equals(key) || "login-user-email-type".equals(key)) && "personal".equals(value)) {
                        hasPersonalAccount = true;
                    }
                    if ("local-mcp-server".equals(key)) hasLocalMcpServer = true;
                    if ("misconfigured-config".equals(key) && "true".equals(value)) hasMisconfiguredConfig = true;
                }
            }
        }

        BasicDBObject toRow(boolean isUserGroup) {
            BasicDBObject row = new BasicDBObject();
            List<String> sortedTypes = new ArrayList<>(clientTypes);
            Collections.sort(sortedTypes);
            row.put("groupKey", groupKey);
            row.put("id", (isUserGroup ? "user-" : "device-") + groupKey.replaceAll("[^a-zA-Z0-9_.-]", "_"));
            row.put("groupName", groupName);
            row.put("rowType", "service"); // matches mcpClientHelper.js's ROW_TYPES.SERVICE
            row.put("inventoryScopeLabel", groupName);
            row.put("clientType", sortedTypes.isEmpty() ? "-" : String.join(", ", sortedTypes));
            row.put("hostNames", new ArrayList<>(hostNames));
            row.put("endpointsCount", isUserGroup ? nonSkillCollectionsCount + uniqueSkillNames.size() : hostNames.size());
            row.put("sensitiveInRespTypes", new ArrayList<>(sensitiveTypes));
            row.put("riskScore", maxRiskScore);
            row.put("lastSeenEpoch", maxTrafficTimestamp);
            row.put("team", team);
            row.put("userRole", userRole);
            row.put("teamSource", teamSource);
            row.put("roleSource", roleSource);
            row.put("hasPersonalAccount", hasPersonalAccount);
            row.put("hasLocalMcpServer", hasLocalMcpServer);
            row.put("hasMisconfiguredConfig", hasMisconfiguredConfig);
            row.put("uniqueSkillNames", new ArrayList<>(uniqueSkillNames));
            row.put("os", os);
            row.put("username", username);
            BasicDBObject violationsObj = new BasicDBObject();
            violationsObj.put("critical", violations[0]);
            violationsObj.put("high", violations[1]);
            violationsObj.put("medium", violations[2]);
            violationsObj.put("low", violations[3]);
            row.put("violations", violationsObj);
            return row;
        }
    }

    // Matches agenticPageBuilders.js's buildDeviceEndpointsPageData's connector-ingested exclusion —
    // these represent the AI agent as seen through an external connector (e.g. MICROSOFT_DEFENDER),
    // not a real service child row.
    private static boolean isConnectorIngested(List<CollectionTags> envType) {
        if (envType == null) return false;
        for (CollectionTags tag : envType) {
            if (tag == null) continue;
            if ("connector".equals(tag.getKeyName())) return true;
            if ("source".equals(tag.getKeyName()) && "DEFENDER".equals(tag.getValue())) return true;
        }
        return false;
    }

    // Mirrors agenticPageBuilders.js's mergeViolations — collViolations is {critical,high,medium,low}.
    private static int[] violationsArrayFor(String idStr, Map<String, Map<String, Integer>> violationsByCollectionId) {
        if (violationsByCollectionId == null) return null;
        Map<String, Integer> v = violationsByCollectionId.get(idStr);
        if (v == null) return null;
        return new int[]{
                v.getOrDefault("critical", 0), v.getOrDefault("high", 0),
                v.getOrDefault("medium", 0), v.getOrDefault("low", 0),
        };
    }

    private Map<String, HostGroupSummary> classifyHostGroupedRows(List<ApiCollection> collections, String groupBy,
            Map<String, Integer> traffic, Map<String, Double> risk, Map<String, List<String>> sensitive,
            Map<String, String> usernames, Map<String, Map<String, String>> userMeta,
            Map<String, Map<String, String>> deviceMeta, Map<String, Map<String, Integer>> violationsByCollectionId,
            boolean excludeConnectorIngested) {
        boolean isUserGroup = "user".equals(groupBy);
        Map<String, HostGroupSummary> groups = new LinkedHashMap<>();

        for (ApiCollection c : collections) {
            if (c == null || c.isDeactivated()) continue;
            String hostName = c.getHostName();
            if (StringUtils.isBlank(hostName)) continue;
            List<CollectionTags> envType = c.getEnvType();
            if (excludeConnectorIngested && isConnectorIngested(envType)) continue;

            String key;
            if (isUserGroup) {
                key = resolveUsername(hostName, usernames, envType);
                if (StringUtils.isBlank(key) || "-".equals(key)) continue;
            } else {
                key = AgenticObserveUtil.extractEndpointId(hostName);
                if (StringUtils.isBlank(key)) continue;
            }

            String idStr = String.valueOf(c.getId());
            Integer trafficVal = traffic.get(idStr);
            int collTraffic = trafficVal != null ? trafficVal : 0;
            Double riskVal = risk.get(idStr);
            double collRisk = riskVal != null ? riskVal : 0.0;
            List<String> sensitiveTypes = sensitive != null ? sensitive.get(idStr) : null;
            int[] collViolations = violationsArrayFor(idStr, violationsByCollectionId);

            final String fKey = key;
            HostGroupSummary g = groups.computeIfAbsent(key, k -> {
                HostGroupSummary gs = new HostGroupSummary(fKey);
                if (isUserGroup && userMeta != null) {
                    Map<String, String> meta = userMeta.get(fKey);
                    if (meta != null) {
                        gs.team = meta.getOrDefault("team", "");
                        gs.userRole = meta.getOrDefault("userRole", "");
                        gs.teamSource = meta.getOrDefault("teamSource", "sso");
                        gs.roleSource = meta.getOrDefault("roleSource", "sso");
                    }
                }
                if (!isUserGroup && deviceMeta != null) {
                    Map<String, String> meta = deviceMeta.get(fKey);
                    if (meta != null) {
                        gs.os = meta.get("os");
                        gs.username = StringUtils.isNotBlank(meta.get("username")) ? meta.get("username") : "-";
                        gs.team = meta.getOrDefault("team", "");
                        gs.userRole = meta.getOrDefault("role", "");
                    }
                }
                return gs;
            });
            // Endpoint Shield username resolution takes priority over the device-module fallback,
            // matching agenticPageBuilders.js's `resolvedUsername !== DEFAULT_VALUE ? resolvedUsername : mod.username`.
            if (!isUserGroup && "-".equals(g.username)) {
                String resolved = resolveUsername(hostName, usernames, envType);
                if (StringUtils.isNotBlank(resolved) && !"-".equals(resolved)) g.username = resolved;
            }
            g.accumulate(c, hostName, envType, collRisk, collTraffic, sensitiveTypes, collViolations);
        }

        return groups;
    }

    private static Comparator<HostGroupSummary> buildHostGroupComparator(String key) {
        if ("name".equals(key) || "groupName".equals(key)) {
            return Comparator.comparing((HostGroupSummary g) -> g.groupName == null ? "" : g.groupName, String.CASE_INSENSITIVE_ORDER);
        } else if ("lastSeenEpoch".equals(key) || "lastSeen".equals(key)) {
            return Comparator.comparingInt(g -> g.maxTrafficTimestamp);
        } else if ("endpointsCount".equals(key)) {
            return Comparator.comparingInt(g -> g.hostNames.size());
        }
        return Comparator.comparingDouble(g -> g.maxRiskScore);
    }

    /**
     * Paginated, sorted, searchable user/device rows for Users-and-Devices ("old layout") and
     * Endpoints' device rows ("new layout"). Same lightweight-summary-first-then-slice shape as
     * fetchAgenticAssetsSummary. `groupBy` selects "user" or "device" grouping.
     */
    public String fetchUsersAndDevicesSummary() {
        response = new BasicDBObject();
        try {
            Map<String, Integer> traffic = trafficMap != null ? trafficMap : Collections.emptyMap();
            Map<String, Double> risk = riskScoreMap != null ? riskScoreMap : Collections.emptyMap();
            Map<String, List<String>> sensitive = sensitiveMap != null ? sensitiveMap : Collections.emptyMap();
            Map<String, String> usernames = usernameMap != null ? usernameMap : Collections.emptyMap();
            Map<String, Map<String, String>> userMeta = userMetadataMap != null ? userMetadataMap : Collections.emptyMap();

            List<ApiCollection> collections = ApiCollectionsDao.instance.findAll(
                    Filters.empty(),
                    Projections.include(ApiCollection.ID, ApiCollection.HOST_NAME, ApiCollection.TAGS_STRING, ApiCollection.SKILLS)
            );

            Map<String, HostGroupSummary> groups = classifyHostGroupedRows(collections, groupBy, traffic, risk, sensitive, usernames, userMeta, null, null, false);
            List<HostGroupSummary> all = new ArrayList<>(groups.values());

            if (StringUtils.isNotBlank(queryValue)) {
                String q = queryValue.toLowerCase(Locale.ROOT);
                all.removeIf(g -> g.groupName == null || !g.groupName.toLowerCase(Locale.ROOT).contains(q));
            }

            // AG Grid column filters (agSetColumnFilter on "team"/"userRole", agTextColumnFilter on
            // "groupName") — extractFilterModel (AgGridTable.jsx) keys these by colDef field name.
            if (filters != null) {
                List<String> teamFilter = filters.get("team");
                if (teamFilter != null && !teamFilter.isEmpty()) {
                    Set<String> allowed = new HashSet<>(teamFilter);
                    all.removeIf(g -> !allowed.contains(g.team));
                }
                List<String> roleFilter = filters.get("userRole");
                if (roleFilter != null && !roleFilter.isEmpty()) {
                    Set<String> allowed = new HashSet<>(roleFilter);
                    all.removeIf(g -> !allowed.contains(g.userRole));
                }
                List<String> nameFilter = filters.get("groupName");
                if (nameFilter != null && !nameFilter.isEmpty() && StringUtils.isNotBlank(nameFilter.get(0))) {
                    String q = nameFilter.get(0).toLowerCase(Locale.ROOT);
                    all.removeIf(g -> g.groupName == null || !g.groupName.toLowerCase(Locale.ROOT).contains(q));
                }
            }

            long total = all.size();

            Comparator<HostGroupSummary> cmp = buildHostGroupComparator(sortKey);
            if (sortOrder < 0) cmp = cmp.reversed();
            all.sort(cmp);

            int effectiveLimit = limit > 0 ? Math.min(limit, 500) : 50;
            int from = Math.max(0, skip);
            int to = Math.min(all.size(), from + effectiveLimit);
            List<HostGroupSummary> page = from < to ? all.subList(from, to) : Collections.emptyList();

            boolean isUserGroup = "user".equals(groupBy);
            List<BasicDBObject> rowsOut = new ArrayList<>();
            for (HostGroupSummary g : page) rowsOut.add(g.toRow(isUserGroup));

            response.put("rows", rowsOut);
            response.put("total", total);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching users and devices summary: " + e.getMessage());
            addActionError("Error fetching users and devices summary: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    /**
     * Tab-header counts ("Users (1234)" / "Devices (567)"), distinct team/role names for the edit
     * modal's autocomplete, and each tab's "Agentic assets" total (sum of endpointsCount across every
     * row — needs the full per-group classification, not just distinct-key counting, so this calls
     * classifyHostGroupedRows for both groupBy modes; same order of cost as fetchUsersAndDevicesSummary
     * itself, just without the pagination slice).
     */
    public String fetchUsersAndDevicesStats() {
        response = new BasicDBObject();
        try {
            Map<String, Integer> traffic = trafficMap != null ? trafficMap : Collections.emptyMap();
            Map<String, Double> risk = riskScoreMap != null ? riskScoreMap : Collections.emptyMap();
            Map<String, String> usernames = usernameMap != null ? usernameMap : Collections.emptyMap();
            Map<String, Map<String, String>> userMeta = userMetadataMap != null ? userMetadataMap : Collections.emptyMap();
            List<ApiCollection> collections = ApiCollectionsDao.instance.findAll(
                    Filters.empty(),
                    Projections.include(ApiCollection.ID, ApiCollection.HOST_NAME, ApiCollection.TAGS_STRING, ApiCollection.SKILLS)
            );

            Map<String, HostGroupSummary> userGroups = classifyHostGroupedRows(collections, "user", traffic, risk,
                    Collections.emptyMap(), usernames, userMeta, null, null, false);
            Map<String, HostGroupSummary> deviceGroups = classifyHostGroupedRows(collections, "device", traffic, risk,
                    Collections.emptyMap(), usernames, Collections.emptyMap(), null, null, false);

            long usersAgenticAssetsTotal = 0;
            for (HostGroupSummary g : userGroups.values()) {
                usersAgenticAssetsTotal += g.nonSkillCollectionsCount + g.uniqueSkillNames.size();
            }
            long devicesAgenticAssetsTotal = 0;
            for (HostGroupSummary g : deviceGroups.values()) {
                devicesAgenticAssetsTotal += g.hostNames.size();
            }

            // Distinct team/role names across all known users — feeds the "Edit team & role" modal's
            // autocomplete suggestions, which previously read straight off the full in-memory user
            // array; cheap to compute here since userMetadataMap is already keyed by username.
            Set<String> teams = new HashSet<>();
            Set<String> roles = new HashSet<>();
            for (String username : userGroups.keySet()) {
                Map<String, String> meta = userMeta.get(username);
                if (meta == null) continue;
                String team = meta.get("team");
                String role = meta.get("userRole");
                if (StringUtils.isNotBlank(team)) teams.add(team);
                if (StringUtils.isNotBlank(role)) roles.add(role);
            }
            List<String> teamList = new ArrayList<>(teams);
            List<String> roleList = new ArrayList<>(roles);
            Collections.sort(teamList);
            Collections.sort(roleList);

            response.put("usersCount", userGroups.size());
            response.put("devicesCount", deviceGroups.size());
            response.put("usersAgenticAssetsTotal", usersAgenticAssetsTotal);
            response.put("devicesAgenticAssetsTotal", devicesAgenticAssetsTotal);
            response.put("teams", teamList);
            response.put("roles", roleList);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching users and devices stats: " + e.getMessage());
            addActionError("Error fetching users and devices stats: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    // ==================== Server-side tree pagination for Endpoints (DeviceEndpoints.jsx) ====================
    // Mirrors agenticPageBuilders.js's buildDeviceEndpointsPageData's two-level shape (device rows,
    // each with a small (device,service) child list) as an AG-Grid SSRM tree: top-level device rows
    // are paginated/sorted like classifyHostGroupedRows already does; a device's children are only
    // computed when that row is expanded (parentDeviceId set), scoped to just that device's
    // collections — never a full second pass over the whole account.

    // Mirrors agenticPageBuilders.js's child row shape within one device, grouped by service name
    // (toChildPathKey(extractServiceName(hostName))) rather than across the whole account.
    private BasicDBObject buildDeviceChildren(String deviceId, List<ApiCollection> collections,
            Map<String, Integer> traffic, Map<String, Double> risk, Map<String, Map<String, Integer>> violationsByCollection) {
        Map<String, BasicDBObject> children = new LinkedHashMap<>();
        Map<String, double[]> childRisk = new HashMap<>(); // pathKey -> {riskScore, lastTraffic}
        Map<String, int[]> childViolations = new HashMap<>();
        Map<String, Integer> childSkillCount = new HashMap<>();

        for (ApiCollection c : collections) {
            if (c == null || c.isDeactivated()) continue;
            String hostName = c.getHostName();
            if (StringUtils.isBlank(hostName)) continue;
            List<CollectionTags> envType = c.getEnvType();
            if (isConnectorIngested(envType)) continue;
            if (!deviceId.equals(AgenticObserveUtil.extractEndpointId(hostName))) continue;

            String serviceName = extractServiceNameForGrouping(hostName);
            if (StringUtils.isBlank(serviceName)) serviceName = hostName;
            String pathKey = serviceName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
            if (StringUtils.isBlank(pathKey)) pathKey = "unknown";

            String idStr = String.valueOf(c.getId());
            Integer trafficVal = traffic.get(idStr);
            int collTraffic = trafficVal != null ? trafficVal : 0;
            Double riskVal = risk.get(idStr);
            double collRisk = riskVal != null ? riskVal : 0.0;
            int skillCount = c.getSkills() != null ? c.getSkills().size() : 0;

            final String fServiceName = serviceName;
            BasicDBObject child = children.computeIfAbsent(pathKey, k -> {
                BasicDBObject row = new BasicDBObject();
                row.put("path", Arrays.asList(deviceId, k));
                row.put("id", "device-" + deviceId + "-" + k);
                row.put("endpoint", McpClientRegistry.formatDisplayName(fServiceName));
                row.put("rawServiceName", fServiceName);
                row.put("type", AgenticObserveUtil.getTypeFromCollection(c));
                return row;
            });
            double[] rt = childRisk.computeIfAbsent(pathKey, k -> new double[2]);
            if (collRisk > rt[0]) rt[0] = collRisk;
            if (collTraffic > rt[1]) rt[1] = collTraffic;
            childSkillCount.merge(pathKey, skillCount, Math::max);
            int[] cv = violationsArrayFor(idStr, violationsByCollection);
            if (cv != null) {
                int[] acc = childViolations.computeIfAbsent(pathKey, k -> new int[4]);
                for (int i = 0; i < 4; i++) acc[i] += cv[i];
            }
        }

        List<BasicDBObject> rows = new ArrayList<>();
        for (Map.Entry<String, BasicDBObject> e : children.entrySet()) {
            String pathKey = e.getKey();
            BasicDBObject row = e.getValue();
            double[] rt = childRisk.get(pathKey);
            row.put("riskScore", Math.round(rt[0] * 10) / 10.0);
            row.put("lastTrafficEpoch", (int) rt[1]);
            int skillCount = childSkillCount.getOrDefault(pathKey, 0);
            if (skillCount > 0) row.put("skillCount", skillCount);
            int[] cv = childViolations.get(pathKey);
            BasicDBObject violationsObj = new BasicDBObject();
            violationsObj.put("critical", cv != null ? cv[0] : 0);
            violationsObj.put("high", cv != null ? cv[1] : 0);
            violationsObj.put("medium", cv != null ? cv[2] : 0);
            violationsObj.put("low", cv != null ? cv[3] : 0);
            row.put("violations", violationsObj);
            rows.add(row);
        }

        BasicDBObject out = new BasicDBObject();
        out.put("rows", rows);
        out.put("total", rows.size());
        return out;
    }

    /**
     * Paginated, sorted, searchable device rows (top level) for Endpoints' tree grid, or — when
     * `parentDeviceId` is set — that one device's (device,service) children. Mirrors
     * agenticPageBuilders.js's buildDeviceEndpointsPageData, split into a cheap top-level pass (like
     * classifyHostGroupedRows) plus an on-demand per-device children pass, matching how
     * fetchAgenticAssetsSummary only builds device breakdowns for the page actually returned.
     */
    public String fetchDeviceEndpointsSummary() {
        response = new BasicDBObject();
        try {
            Map<String, Integer> traffic = trafficMap != null ? trafficMap : Collections.emptyMap();
            Map<String, Double> risk = riskScoreMap != null ? riskScoreMap : Collections.emptyMap();
            Map<String, Map<String, String>> deviceMeta = deviceMetadataMap != null ? deviceMetadataMap : Collections.emptyMap();
            Map<String, String> usernames = usernameMap != null ? usernameMap : Collections.emptyMap();
            Map<String, Map<String, Integer>> violations = violationsByCollectionId != null ? violationsByCollectionId : Collections.emptyMap();

            List<ApiCollection> collections = ApiCollectionsDao.instance.findAll(
                    Filters.empty(),
                    Projections.include(ApiCollection.ID, ApiCollection.HOST_NAME, ApiCollection.TAGS_STRING, ApiCollection.SKILLS)
            );

            if (StringUtils.isNotBlank(parentDeviceId)) {
                response = buildDeviceChildren(parentDeviceId, collections, traffic, risk, violations);
                return SUCCESS.toUpperCase();
            }

            Map<String, HostGroupSummary> groups = classifyHostGroupedRows(collections, "device", traffic, risk,
                    Collections.emptyMap(), usernames, Collections.emptyMap(), deviceMeta, violations, true);
            List<HostGroupSummary> all = new ArrayList<>(groups.values());

            if (StringUtils.isNotBlank(queryValue)) {
                String q = queryValue.toLowerCase(Locale.ROOT);
                all.removeIf(g -> {
                    String name = StringUtils.isNotBlank(g.username) && !"-".equals(g.username) ? g.username : g.groupName;
                    return name == null || !name.toLowerCase(Locale.ROOT).contains(q);
                });
            }

            long total = all.size();

            Comparator<HostGroupSummary> cmp = buildHostGroupComparator(sortKey);
            if (sortOrder < 0) cmp = cmp.reversed();
            all.sort(cmp);

            int effectiveLimit = limit > 0 ? Math.min(limit, 500) : 50;
            int from = Math.max(0, skip);
            int to = Math.min(all.size(), from + effectiveLimit);
            List<HostGroupSummary> page = from < to ? all.subList(from, to) : Collections.emptyList();

            List<BasicDBObject> rowsOut = new ArrayList<>();
            for (HostGroupSummary g : page) {
                BasicDBObject row = g.toRow(false);
                row.put("path", Collections.singletonList(g.groupKey));
                row.put("deviceId", g.groupKey);
                rowsOut.add(row);
            }

            response.put("rows", rowsOut);
            response.put("total", total);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching device endpoints summary: " + e.getMessage());
            addActionError("Error fetching device endpoints summary: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    // ==================== Endpoints header stats: trend charts, deltas, Browsers/Endpoints/Users ====================
    // Mirrors agenticPageBuilders.js's buildDeviceEndpointsPageData's summary section
    // (buildWindowSlots/cumulativeCounts/cumulativeByMonth/cumulativeSeriesByMonth) exactly, computed
    // server-side rather than client-side: bucketing ~26k collections' first-seen timestamps by month
    // is real O(n) work, and redoing it in the browser on every load would reintroduce a meaningful
    // chunk of the cost this whole rebuild moved off the client. Violations delta/sparkline is the one
    // piece NOT restored here — it needs the full raw violation-event history (up to 100k rows), which
    // is exactly the fetch Stage 4/5 eliminated from the blocking path; the "Total Violations" tile
    // shows the current aggregate total only (already available from the host-severity-count call the
    // page already makes), not a historical trend line.

    private static final String[] MONTH_ABBR = {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};

    private static final class MonthSlot {
        final int boundary;
        final String label;
        MonthSlot(int boundary, String label) { this.boundary = boundary; this.label = label; }
    }

    private static int monthStartEpoch(int year, int month0) {
        return (int) YearMonth.of(year, month0 + 1).atDay(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
    }

    // Mirrors buildWindowSlots exactly: windowStart>0 spans [windowStart, windowEnd] months;
    // windowStart<=0 (all-time) anchors at the earliest data point (or 365 days back if none).
    private static List<MonthSlot> buildWindowSlots(int windowStart, int windowEnd, int dataMinTs) {
        int nowSec = (int) (System.currentTimeMillis() / 1000);
        int end = windowEnd > 0 ? windowEnd : nowSec;
        int startSec = windowStart > 0 ? windowStart : (dataMinTs > 0 ? dataMinTs : end - 365 * 86400);

        java.time.LocalDate startDate = Instant.ofEpochSecond(startSec).atZone(ZoneId.systemDefault()).toLocalDate();
        java.time.LocalDate endDate = Instant.ofEpochSecond(end).atZone(ZoneId.systemDefault()).toLocalDate();
        YearMonth startYm = YearMonth.from(startDate);
        YearMonth endYm = YearMonth.from(endDate);
        int numMonths = Math.max((int) startYm.until(endYm, java.time.temporal.ChronoUnit.MONTHS) + 1, 1);

        List<MonthSlot> slots = new ArrayList<>();
        for (int i = 0; i < numMonths; i++) {
            YearMonth ym = startYm.plusMonths(i);
            int boundary = monthStartEpoch(ym.getYear(), ym.getMonthValue() - 1);
            String label = MONTH_ABBR[ym.getMonthValue() - 1] + " '" + String.valueOf(ym.getYear()).substring(2);
            slots.add(new MonthSlot(boundary, label));
        }
        return slots;
    }

    // Mirrors cumulativeCounts: a true cumulative running total — items before the first slot seed
    // the baseline so the line ends at the true grand total, not just in-window additions.
    private static int[] cumulativeCounts(List<Integer> timestamps, List<MonthSlot> slots, int end) {
        int n = slots.size();
        int firstBoundary = slots.get(0).boundary;
        int baseline = 0;
        int[] perMonth = new int[n];
        for (Integer ts : timestamps) {
            if (ts == null || ts <= 0 || ts < firstBoundary) { baseline++; continue; }
            if (ts > end) continue;
            int idx = -1;
            for (int i = 0; i < n; i++) {
                if (ts >= slots.get(i).boundary) idx = i;
            }
            if (idx >= 0) perMonth[idx]++;
        }
        int[] counts = new int[n];
        int acc = baseline;
        for (int i = 0; i < n; i++) { acc += perMonth[i]; counts[i] = acc; }
        return counts;
    }

    private static int earliestTs(List<Integer> timestamps) {
        int min = Integer.MAX_VALUE;
        for (Integer ts : timestamps) if (ts != null && ts > 0 && ts < min) min = ts;
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    private static int windowDelta(int[] counts) {
        if (counts == null || counts.length < 2) return 0;
        return Math.max(0, counts[counts.length - 1] - counts[0]);
    }

    public String fetchDeviceEndpointsStats() {
        response = new BasicDBObject();
        try {
            Map<String, String> usernames = usernameMap != null ? usernameMap : Collections.emptyMap();
            Map<String, Map<String, String>> deviceMeta = deviceMetadataMap != null ? deviceMetadataMap : Collections.emptyMap();

            List<ApiCollection> collections = ApiCollectionsDao.instance.findAll(
                    Filters.empty(),
                    Projections.include(ApiCollection.ID, ApiCollection.HOST_NAME, ApiCollection.TAGS_STRING, ApiCollection.START_TS)
            );

            // First-seen (earliest startTs) per device, and derived os/browser-only/username.
            Map<String, Integer> deviceFirstSeen = new HashMap<>();
            Map<String, String> deviceOs = new HashMap<>();
            Map<String, Boolean> deviceIsBrowserOnly = new HashMap<>();
            Map<String, String> deviceBrowser = new HashMap<>();
            Map<String, Integer> userFirstSeen = new HashMap<>();

            for (ApiCollection c : collections) {
                if (c == null || c.isDeactivated()) continue;
                String hostName = c.getHostName();
                if (StringUtils.isBlank(hostName)) continue;
                String deviceId = AgenticObserveUtil.extractEndpointId(hostName);
                if (StringUtils.isBlank(deviceId)) continue;
                int ts = c.getStartTs();

                Integer existing = deviceFirstSeen.get(deviceId);
                if (existing == null || ts < existing) {
                    deviceFirstSeen.put(deviceId, ts);
                    Map<String, String> meta = deviceMeta.get(deviceId);
                    String os = meta != null ? meta.get("os") : null;
                    String browserName = meta != null ? meta.get("browserName") : null;
                    deviceOs.put(deviceId, os);
                    boolean isBrowserOnly = StringUtils.isNotBlank(browserName);
                    deviceIsBrowserOnly.put(deviceId, isBrowserOnly);
                    if (isBrowserOnly) deviceBrowser.put(deviceId, browserName.toLowerCase(Locale.ROOT));
                }

                List<CollectionTags> envType = c.getEnvType();
                String username = resolveUsername(hostName, usernames, envType);
                if (StringUtils.isBlank(username) || "-".equals(username)) {
                    Map<String, String> meta = deviceMeta.get(deviceId);
                    username = meta != null ? meta.get("username") : null;
                }
                if (StringUtils.isNotBlank(username) && !"-".equals(username)) {
                    Integer existingUser = userFirstSeen.get(username);
                    if (existingUser == null || ts < existingUser) userFirstSeen.put(username, ts);
                }
            }

            List<String> endpointDeviceIds = new ArrayList<>();
            List<String> browserDeviceIds = new ArrayList<>();
            for (String devId : deviceFirstSeen.keySet()) {
                if (Boolean.TRUE.equals(deviceIsBrowserOnly.get(devId))) browserDeviceIds.add(devId);
                else endpointDeviceIds.add(devId);
            }

            List<Integer> endpointTs = new ArrayList<>();
            for (String d : endpointDeviceIds) endpointTs.add(deviceFirstSeen.get(d));
            List<Integer> browserTs = new ArrayList<>();
            for (String d : browserDeviceIds) browserTs.add(deviceFirstSeen.get(d));
            List<Integer> userTs = new ArrayList<>(userFirstSeen.values());

            List<Integer> macTs = new ArrayList<>(), winTs = new ArrayList<>(), linuxTs = new ArrayList<>(), unknownTs = new ArrayList<>();
            for (String d : endpointDeviceIds) {
                String os = deviceOs.get(d);
                int ts = deviceFirstSeen.get(d);
                if ("mac".equals(os)) macTs.add(ts);
                else if ("windows".equals(os)) winTs.add(ts);
                else if ("linux".equals(os)) linuxTs.add(ts);
                else unknownTs.add(ts);
            }
            List<Integer> chromeTs = new ArrayList<>(), firefoxTs = new ArrayList<>(), edgeTs = new ArrayList<>(), safariTs = new ArrayList<>();
            for (String d : browserDeviceIds) {
                String b = deviceBrowser.get(d);
                int ts = deviceFirstSeen.get(d);
                if ("chrome".equals(b)) chromeTs.add(ts);
                else if ("firefox".equals(b)) firefoxTs.add(ts);
                else if ("edge".equals(b)) edgeTs.add(ts);
                else if ("safari".equals(b)) safariTs.add(ts);
            }

            int nowSec = (int) (System.currentTimeMillis() / 1000);
            int end = endTimestamp > 0 ? endTimestamp : nowSec;
            List<Integer> allForMin = new ArrayList<>();
            allForMin.addAll(endpointTs); allForMin.addAll(browserTs); allForMin.addAll(userTs);
            int dataMin = earliestTs(allForMin);
            List<MonthSlot> slots = buildWindowSlots(startTimestamp, endTimestamp, dataMin);
            List<String> monthLabels = new ArrayList<>();
            for (MonthSlot s : slots) monthLabels.add(s.label);

            int[] macCounts = cumulativeCounts(macTs, slots, end);
            int[] winCounts = cumulativeCounts(winTs, slots, end);
            int[] linuxCounts = cumulativeCounts(linuxTs, slots, end);
            int[] unknownCounts = cumulativeCounts(unknownTs, slots, end);
            int[] chromeCounts = cumulativeCounts(chromeTs, slots, end);
            int[] firefoxCounts = cumulativeCounts(firefoxTs, slots, end);
            int[] edgeCounts = cumulativeCounts(edgeTs, slots, end);
            int[] safariCounts = cumulativeCounts(safariTs, slots, end);
            int[] endpointCounts = cumulativeCounts(endpointTs, slots, end);
            int[] browserCounts = cumulativeCounts(browserTs, slots, end);
            int[] userCounts = cumulativeCounts(userTs, slots, end);

            BasicDBObject osTrend = new BasicDBObject();
            osTrend.put("mac", macCounts); osTrend.put("windows", winCounts);
            osTrend.put("linux", linuxCounts); osTrend.put("unknown", unknownCounts);
            BasicDBObject browserTrend = new BasicDBObject();
            browserTrend.put("chrome", chromeCounts); browserTrend.put("firefox", firefoxCounts);
            browserTrend.put("edge", edgeCounts); browserTrend.put("safari", safariCounts);
            BasicDBObject sparklines = new BasicDBObject();
            sparklines.put("endpoints", endpointCounts); sparklines.put("browsers", browserCounts); sparklines.put("users", userCounts);

            response.put("deviceCount", endpointDeviceIds.size());
            response.put("browserDeviceCount", browserDeviceIds.size());
            response.put("totalUsers", userFirstSeen.size());
            response.put("monthLabels", monthLabels);
            response.put("osTrend", osTrend);
            response.put("browserTrend", browserTrend);
            response.put("sparklines", sparklines);
            response.put("deltaEndpoints", windowDelta(endpointCounts));
            response.put("deltaBrowsers", windowDelta(browserCounts));
            response.put("deltaUsers", windowDelta(userCounts));
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching device endpoints stats: " + e.getMessage());
            addActionError("Error fetching device endpoints stats: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }
}
