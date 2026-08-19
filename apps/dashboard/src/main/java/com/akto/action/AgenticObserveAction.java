package com.akto.action;

import com.akto.action.threat_detection.AbstractThreatDetectionAction;
import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.McpAuditInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.ComponentRiskAnalysis;
import com.akto.dto.DeviceTag;
import com.akto.dto.McpAuditInfo;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.mcp.McpRequestResponseUtils;
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
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
    // Matches com.akto.action.observe.Utils.DELTA_PERIOD_VALUE — duplicated locally rather than
    // importing a same-named-but-different-package "Utils" class for one constant.
    private static final int DELTA_PERIOD_VALUE = 60 * 24 * 60 * 60;

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
    @Setter private String queryValue; // free-text search — asset/group name (summary), or username/deviceId (fetchAgenticAssetDevicesPage)
    @Setter private Map<String, Integer> trafficMap;
    @Setter private Map<String, Double> riskScoreMap;
    @Setter private Map<String, List<String>> filters; // AG Grid column filters, keyed by field name (e.g. "tags", "groupName")

    // ---- Server-side pagination for fetchUsersAndDevicesSummary ----
    @Setter private String groupBy; // "user" | "device"
    @Setter private Map<String, List<String>> sensitiveMap; // collection id (string) -> sensitive types
    @Setter private Map<String, String> usernameMap; // Endpoint Shield username resolution map
    @Setter private Map<String, Map<String, String>> userMetadataMap; // username -> {userEmail}
    // username -> that user's generic device tags (AgenticUsers.deviceTags, each {key, value, source}).
    // Device rows inherit their resolved owner's tags (see classifyHostGroupedRows).
    @Setter private Map<String, List<Map<String, String>>> tagsByUsername;

    // ---- Server-side pagination for fetchDeviceEndpointsSummary ----
    @Setter private String parentDeviceId; // blank => paginated top-level device rows; set => that device's (device,service) children
    @Setter private Map<String, Map<String, String>> deviceMetadataMap; // deviceId -> {username, os}
    @Setter private Map<String, Map<String, Integer>> violationsByCollectionId; // collection id (string) -> {critical, high, medium, low}
    // skill name -> {critical, high, medium, low} — skill invocations aren't attributable by
    // collection (a skill's declaring collection is shared with the agent/device that invoked it,
    // so collection-based attribution can't give a skill its own count), so skill rows use this
    // instead of violationsByCollectionId. See ThreatActorService.fetchSkillSeverityCounts.
    @Setter private Map<String, Map<String, Integer>> skillViolationsByName;
    @Setter private Map<String, Integer> userAnalysisFlatMap; // "serviceId|deviceId" -> total AI-interaction tokens (see constants.js's buildUserAnalysisFlatMap)

    // ---- Server-side pagination for fetchAgenticComponentsPage ----
    @Setter private List<String> mcpServerNames; // asset.mcpServers, already known/cheap client-side — passed through rather than re-derived
    @Setter private Map<String, List<Integer>> mcpServerCollectionIds; // asset.mcpServerCollectionIds — needed on each MCP-server row so AgentMcpToolsView's drill-down keeps working
    @Setter private List<String> pluginNames; // asset.pluginNames — same shape/reasoning as mcpServerNames above

    /**
     * ATLAS skill enrichment in a SINGLE query. Skill (/skills/&lt;name&gt;) and agent-config (/config/)
     * endpoints only ever exist on agentic collections, so one regex scan of api_info returns everything
     * the Agentic-assets / Users-and-devices pages need — replacing the old per-collection N+1 that fired
     * one fetchApiInfosForCollection call for each of the account's ~26k collections.
     */
    // Per-account cache of fetchAgenticSkillData's own computation — the frontend fetches this once
    // (its own 2-min PersistStore cache: skillRiskScoreCache) but then has to re-POST the resulting
    // maliciousSkillKeys back into fetchAgenticAssetsSummary on every single paginated request just
    // so that endpoint can check Set membership — measured at 14,218 entries (~500KB+) for Atlas
    // Scale Test, the single largest contributor to that endpoint's ~650KB request payload. Since
    // fetchAgenticSkillData lives on this same class, fetchAgenticAssetsSummary can just read this
    // cache directly instead of requiring the client to round-trip the whole set back to us. Same
    // TTL/eviction shape as getOrBuildClassification (see its own doc for the rationale).
    private static final Map<Integer, SkillDataCacheEntry> skillDataCache = new ConcurrentHashMap<>();

    private static final class SkillDataCacheEntry {
        final Map<String, Float> skillScoreMap;
        final Set<String> maliciousSkills;
        final Set<String> maliciousSkillKeys;
        final Set<String> misconfiguredSkills;
        final Set<Integer> misconfiguredCollectionIds;
        final long builtAt;
        SkillDataCacheEntry(Map<String, Float> skillScoreMap, Set<String> maliciousSkills, Set<String> maliciousSkillKeys,
                Set<String> misconfiguredSkills, Set<Integer> misconfiguredCollectionIds, long builtAt) {
            this.skillScoreMap = skillScoreMap;
            this.maliciousSkills = maliciousSkills;
            this.maliciousSkillKeys = maliciousSkillKeys;
            this.misconfiguredSkills = misconfiguredSkills;
            this.misconfiguredCollectionIds = misconfiguredCollectionIds;
            this.builtAt = builtAt;
        }
    }

    private SkillDataCacheEntry getOrBuildSkillData() {
        int accountId = Context.accountId.get();
        long now = System.currentTimeMillis();
        SkillDataCacheEntry existing = skillDataCache.get(accountId);
        if (existing != null && (now - existing.builtAt) < CLASSIFICATION_CACHE_TTL_MS) {
            return existing;
        }
        if (skillDataCache.size() > CLASSIFICATION_CACHE_SWEEP_THRESHOLD) {
            skillDataCache.entrySet().removeIf(e -> (now - e.getValue().builtAt) > CLASSIFICATION_CACHE_TTL_MS * 10);
        }
        return skillDataCache.compute(accountId, (id, cached) -> {
            if (cached != null && (System.currentTimeMillis() - cached.builtAt) < CLASSIFICATION_CACHE_TTL_MS) {
                return cached;
            }
            long tStart = System.currentTimeMillis();
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
            // skillCollectionKey exactly — so a same-named skill on a different user/collection
            // doesn't inherit the malicious flag (the account-wide maliciousSkills set is name-only).
            Set<String> maliciousSkillKeys = new HashSet<>();
            Set<String> misconfiguredSkills = new HashSet<>();
            Set<Integer> misconfiguredCollectionIds = new HashSet<>();

            for (ApiInfo info : apiInfos) {
                if (info == null || info.getId() == null) continue;
                String url = info.getId().getUrl();
                if (url == null) url = "";
                boolean malicious = hasTrueTag(info.getTagsList(), "malicious-skill-tag");
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
            loggerMaker.warnAndAddToDb("[agenticSkillDataCache] rebuilt: " + (System.currentTimeMillis() - tStart)
                    + "ms, apiInfos=" + apiInfos.size() + ", maliciousSkillKeys=" + maliciousSkillKeys.size());
            return new SkillDataCacheEntry(skillScoreMap, maliciousSkills, maliciousSkillKeys, misconfiguredSkills,
                    misconfiguredCollectionIds, System.currentTimeMillis());
        });
    }

    public String fetchAgenticSkillData() {
        response = new BasicDBObject();
        SkillDataCacheEntry cached = getOrBuildSkillData();
        response.put("skillScoreMap", cached.skillScoreMap);
        response.put("maliciousSkills", new ArrayList<>(cached.maliciousSkills));
        response.put("maliciousSkillKeys", new ArrayList<>(cached.maliciousSkillKeys));
        response.put("misconfiguredSkills", new ArrayList<>(cached.misconfiguredSkills));
        response.put("misconfiguredCollectionIds", new ArrayList<>(cached.misconfiguredCollectionIds));
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

    // Java port of transform.js's splitCollectionNameForEndpointSecurity's "sourceId" segment
    // (parts[1]) — used by fetchAgenticAssetEndpointsPage's child rows for MCP Server/LLM assets,
    // whose "source" column shows which agent/client called into them, not their own service name.
    private static String extractSourceIdForGrouping(String hostName) {
        if (StringUtils.isBlank(hostName)) return "";
        String[] parts = DOT_SPLIT.split(hostName);
        return parts.length >= 2 ? parts[1] : "";
    }

    // ApiInfoDao/SingleTypeInfoDao's scoped lookups key by the raw int collection id; every other
    // consumer in this file (trafficMap/riskScoreMap/sensitiveMap, whether client-posted or
    // computed here) expects String keys, matching how these maps travel over JSON.
    private static <V> Map<String, V> toStringKeyedMap(Map<Integer, V> intKeyed) {
        Map<String, V> result = new HashMap<>();
        if (intKeyed == null) return result;
        for (Map.Entry<Integer, V> e : intKeyed.entrySet()) {
            result.put(String.valueOf(e.getKey()), e.getValue());
        }
        return result;
    }

    // Java port of mcpClientHelper.js's hasPersonalAccountTag/hasLocalMcpServerTag/
    // hasMisconfiguredConfigTag — extracted (unlike GroupSummary.accumulateCheap's own inline copy
    // of this same logic) because fetchAgenticAssetEndpointsPage needs it per-child, not just once
    // per group. {personal, localMcp, misconfigured}.
    private static boolean[] computeAgenticTagFlags(List<CollectionTags> envType) {
        boolean personal = false, localMcp = false, misconfigured = false;
        if (envType != null) {
            for (CollectionTags tag : envType) {
                if (tag == null) continue;
                String key = tag.getKeyName();
                String value = tag.getValue();
                if (("browser-llm-account-type".equals(key) || "login-user-email-type".equals(key)) && "personal".equals(value)) {
                    personal = true;
                }
                if ("local-mcp-server".equals(key)) localMcp = true;
                if ("misconfigured-config".equals(key) && "true".equals(value)) misconfigured = true;
            }
        }
        return new boolean[]{personal, localMcp, misconfigured};
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
        // Agent rows only — the specific tag key that identified this asset (e.g. "mcp-client"),
        // and the raw tag value variants that collapsed into this canonical group (e.g. "cursor",
        // "Cursor", "cursor-ide" all -> group "cursor"). Cheap/bounded (distinct tag-variant count,
        // not collection count) but only used by the legacy Endpoints.jsx page's row-click ->
        // Inventory-filter feature, so exposed via fetchAgenticAssetDetail (lazy, one asset at a
        // time) rather than toSummaryResponse() (every row of every page).
        String tagKey;
        final Set<String> rawTagValues = new HashSet<>();
        final Set<String> hostNames = new HashSet<>();
        final Set<String> endpointIds = new HashSet<>();
        final Set<String> misconfiguredEndpointIds = new HashSet<>();
        final Set<String> skillNames = new HashSet<>();
        // Only the count is serialized per row, same as skillNames — full list comes from the detail call.
        final Set<String> pluginNames = new HashSet<>();
        // Plugin rows only — read off the plugin collection's own tags.
        String pluginVersion;
        String pluginScope;
        String pluginStatus;
        String pluginMarketplace;
        // Plugin rows only — the agent this plugin is installed under (ai-agent/mcp-client tag
        // value), e.g. "claude". A plugin collection carries the SAME owner tag as its parent agent
        // collection, so this is how the UI answers "whose plugin is this" without a second query.
        String pluginParentAgent;
        // Service/LLM/Skill rows — set when a member collection is tagged plugin-name by a plugin
        // that bundles it (AgenticObserveUtil.getOwningPluginName), first-non-null-wins.
        String owningPluginName;
        // Small — bounded by the account's distinct sensitive-data type names (e.g. "Email",
        // "IP Address"), not by member-collection count, so safe to serialize eagerly unlike
        // hostNames/collectionIds/skillNames above. Mirrors constants.js's groupCollectionsByAgent's
        // (etc.) sensitiveTypes accumulation for the legacy Endpoints.jsx page's "Sensitive data"
        // column, which the original server-side rebuild of this class didn't carry over.
        final Set<String> sensitiveTypes = new HashSet<>();
        // Agent rows only — the distinct services (MCP servers) this agent's own collections
        // represent, mirrors constants.js's mcpServers derivation exactly (extractServiceName over
        // non-connector-ingested collections, excluding the agent's own name).
        final Set<String> serviceNames = new HashSet<>();
        // Agent rows only — serviceNames' own collection ids, so the flyout's MCP-server tools
        // drill-down (AgentMcpToolsView) can fetch a specific server's tools without needing the
        // account-wide agenticFlatData this rebuild deliberately doesn't send.
        final Map<String, Set<Integer>> serviceCollectionIds = new HashMap<>();
        final List<Integer> collectionIds = new ArrayList<>();
        double maxRiskScore = 0;
        Double baseRiskScore;
        String baseRiskScoreReason;
        int maxTrafficTimestamp = 0;
        boolean hasPersonalAccount = false;
        boolean hasLocalMcpServer = false;
        boolean hasMisconfiguredConfig = false;

        GroupSummary(String groupKey, String rowType) {
            this.groupKey = groupKey;
            this.rowType = rowType;
        }

        void accumulateCheap(ApiCollection c, String hostName, List<CollectionTags> envType, double collRisk, int collTraffic, String deviceId, List<String> sensitive) {
            collectionIds.add(c.getId());
            hostNames.add(hostName);
            if (deviceId != null) endpointIds.add(deviceId);
            if (sensitive != null) {
                for (String s : sensitive) {
                    if (StringUtils.isNotBlank(s)) sensitiveTypes.add(s);
                }
            }
            if (c.getSkills() != null) {
                for (String s : c.getSkills()) {
                    if (StringUtils.isNotBlank(s)) skillNames.add(s);
                }
            }
            String cPluginName = AgenticObserveUtil.getPluginName(c);
            if (cPluginName != null) pluginNames.add(cPluginName);
            if ("agent".equals(rowType) && !isConnectorIngested(envType)) {
                String serviceName = extractServiceNameForGrouping(hostName);
                if (serviceName != null && !serviceName.equalsIgnoreCase(groupKey)) {
                    serviceNames.add(serviceName);
                    serviceCollectionIds.computeIfAbsent(serviceName, k -> new HashSet<>()).add(c.getId());
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
                    if ("misconfigured-config".equals(key) && "true".equals(value)) {
                        hasMisconfiguredConfig = true;
                        if (deviceId != null) misconfiguredEndpointIds.add(deviceId);
                    }
                }
            }
            if (collRisk > maxRiskScore) {
                maxRiskScore = collRisk;
                baseRiskScore = c.getBaseRiskScore();
                baseRiskScoreReason = c.getBaseRiskScoreReason();
            }
            if (collTraffic > maxTrafficTimestamp) maxTrafficTimestamp = collTraffic;
        }

        // Deliberately excludes hostNames/collectionIds/skillNames/mcpServers/mcpServerCollectionIds
        // (all Set/List/Map fields whose size scales with member-collection count — up to ~25,272 for
        // one agent group on the Atlas Scale Test account) and tagKey/rawTagValues (dead for this
        // page — only a different page/data source reads them). A single page of 50 rows including
        // these measured at 16MB; none of them are read by the main grid's own rendering (confirmed
        // by tracing every consumer), only by the flyout when a user opens ONE specific asset — see
        // fetchAgenticAssetDetail, the lazy per-asset endpoint the flyout now calls on open instead.
        // The two fields the grid's OWN rendering used to derive from collectionIds client-side
        // (violations, isMalicious) are precomputed server-side instead, in fetchAgenticAssetsSummary
        // itself, using the same in-memory collectionIds this method no longer serializes.
        BasicDBObject toSummaryResponse() {
            BasicDBObject g = new BasicDBObject();
            g.put("id", rowType + "-" + groupKey);
            g.put("groupKey", groupKey);
            g.put("name", name);
            g.put("rowType", rowType);
            g.put("clientType", clientType);
            g.put("endpointsCount", endpointIds.size());
            g.put("skillCount", skillNames.size());
            g.put("pluginCount", pluginNames.size());
            if (pluginVersion != null) g.put("pluginVersion", pluginVersion);
            if (pluginScope != null) g.put("pluginScope", pluginScope);
            if (pluginStatus != null) g.put("pluginStatus", pluginStatus);
            if (pluginMarketplace != null) g.put("pluginMarketplace", pluginMarketplace);
            if (pluginParentAgent != null) g.put("pluginParentAgent", pluginParentAgent);
            if (owningPluginName != null) g.put("owningPluginName", owningPluginName);
            g.put("riskScore", maxRiskScore > 0 ? AgenticObserveUtil.roundRiskScore(maxRiskScore) : null);
            if (baseRiskScoreReason != null) {
                g.put("baseRiskScore", baseRiskScore);
                g.put("baseRiskScoreReason", baseRiskScoreReason);
            }
            g.put("lastSeenEpoch", maxTrafficTimestamp);
            g.put("hasPersonalAccount", hasPersonalAccount);
            g.put("hasLocalMcpServer", hasLocalMcpServer);
            g.put("hasMisconfiguredConfig", hasMisconfiguredConfig);
            g.put("misconfiguredDeviceCount", misconfiguredEndpointIds.size());
            if (!sensitiveTypes.isEmpty()) g.put("sensitiveInRespTypes", new ArrayList<>(sensitiveTypes));
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
        int aiInteractions;
        final Set<String> services = new HashSet<>();
        final Set<String> seenAnalysisKeys = new HashSet<>();

        DeviceAcc(String deviceId) { this.deviceId = deviceId; }

        BasicDBObject toResponse() {
            BasicDBObject d = new BasicDBObject();
            d.put("deviceId", deviceId);
            d.put("endpoint", deviceId);
            d.put("riskScore", riskScore > 0 ? AgenticObserveUtil.roundRiskScore(riskScore) : null);
            d.put("lastSeenEpoch", lastSeenEpoch);
            d.put("services", new ArrayList<>(services));
            d.put("aiInteractions", aiInteractions);
            return d;
        }
    }

    // Same hostname-segment candidate derivation as fetchAgenticAssetsStats' "Top Used Applications"
    // ranking (see buildUserAnalysisFlatMap) — UserAnalysisData's serviceId is a readable client name
    // matching the collection's own hostname's second segment, not module_info's id/name.
    private static void accumulateAiInteractions(DeviceAcc d, String hostName, Map<String, Integer> userAnalysisMap) {
        if (userAnalysisMap == null || userAnalysisMap.isEmpty()) return;
        String[] parts = DOT_SPLIT.split(hostName);
        if (parts.length < 2) return;
        String deviceId = parts[0];
        List<String> candidates = new ArrayList<>();
        candidates.add(parts[1]);
        if (parts.length >= 3) candidates.add(String.join(".", Arrays.asList(parts).subList(2, parts.length)));
        for (String serviceId : candidates) {
            String key = serviceId + "|" + deviceId;
            if (!d.seenAnalysisKeys.add(key)) continue;
            d.aiInteractions += userAnalysisMap.getOrDefault(key, 0);
        }
    }

    // Shared by buildDevicesForGroup (bulk summary, scoped to a GroupSummary's own
    // collectionIds) and fetchAgenticAssetDevicesPage (the flyout's paginated Devices tab,
    // scoped to the same collectionIds fetched fresh via apiCollectionIds) — same
    // accumulation, two different callers/scopes.
    private static Map<String, DeviceAcc> accumulateDevices(Collection<Integer> collectionIds, Map<Integer, ApiCollection> byId,
            Map<String, Integer> traffic, Map<String, Double> risk, Map<String, Integer> userAnalysisMap) {
        Map<String, DeviceAcc> devices = new LinkedHashMap<>();
        for (Integer cid : collectionIds) {
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
            accumulateAiInteractions(d, hostName, userAnalysisMap);
        }
        return devices;
    }

    private static List<BasicDBObject> buildDevicesForGroup(GroupSummary g, Map<Integer, ApiCollection> byId,
            Map<String, Integer> traffic, Map<String, Double> risk, Map<String, Integer> userAnalysisMap) {
        Map<String, DeviceAcc> devices = accumulateDevices(g.collectionIds, byId, traffic, risk, userAnalysisMap);
        List<BasicDBObject> out = new ArrayList<>();
        for (DeviceAcc d : devices.values()) out.add(d.toResponse());
        return out;
    }

    private static String resolveUsernameByDeviceId(String deviceId, Map<String, String> usernameMap) {
        if (usernameMap == null || usernameMap.isEmpty() || deviceId == null) return "-";
        String v = usernameMap.get("__deviceId__" + deviceId.toLowerCase(Locale.ROOT));
        return StringUtils.isNotBlank(v) ? v : "-";
    }

    private static Comparator<BasicDBObject> buildDeviceComparator(String key) {
        if ("username".equals(key)) {
            return Comparator.comparing((BasicDBObject r) -> String.valueOf(r.get("username")), String.CASE_INSENSITIVE_ORDER);
        } else if ("lastSeenEpoch".equals(key) || "lastSeen".equals(key)) {
            return Comparator.comparingInt(r -> ((Number) r.get("lastSeenEpoch")).intValue());
        }
        return Comparator.comparingDouble(r -> {
            Object v = r.get("riskScore");
            return v == null ? 0.0 : ((Number) v).doubleValue();
        });
    }

    /**
     * Server-side paginated device list for ONE asset's flyout Devices tab — scoped to just
     * this asset's own collectionIds (sent as apiCollectionIds, already known client-side),
     * cheap unlike classifyAllGroups which walks every collection in the account. Resolves
     * username via the same Endpoint Shield usernameMap fetchUsersAndDevicesSummary already
     * accepts, so search/sort can work against the person's name, not just the raw deviceId —
     * matches constants.js's enrichDevicesWithUsername lookup key format exactly
     * ("__deviceId__" + lowercased deviceId).
     */
    public String fetchAgenticAssetDevicesPage() {
        response = new BasicDBObject();
        try {
            List<Integer> ids = apiCollectionIds != null ? apiCollectionIds : Collections.emptyList();
            if (ids.isEmpty()) {
                response.put("devices", new ArrayList<>());
                response.put("total", 0);
                return SUCCESS.toUpperCase();
            }

            List<ApiCollection> collections = ApiCollectionsDao.instance.findAll(
                    Filters.in(Constants.ID, ids),
                    Projections.include(ApiCollection.ID, ApiCollection.HOST_NAME)
            );
            Map<Integer, ApiCollection> byId = new HashMap<>();
            for (ApiCollection c : collections) byId.put(c.getId(), c);

            Map<String, Integer> traffic = trafficMap != null ? trafficMap : Collections.emptyMap();
            Map<String, Double> risk = riskScoreMap != null ? riskScoreMap : Collections.emptyMap();
            Map<String, Integer> userAnalysis = userAnalysisFlatMap != null ? userAnalysisFlatMap : Collections.emptyMap();

            Map<String, DeviceAcc> devices = accumulateDevices(ids, byId, traffic, risk, userAnalysis);

            List<BasicDBObject> rows = new ArrayList<>();
            for (DeviceAcc d : devices.values()) {
                BasicDBObject row = d.toResponse();
                row.put("username", resolveUsernameByDeviceId(d.deviceId, usernameMap));
                rows.add(row);
            }

            if (StringUtils.isNotBlank(queryValue)) {
                String q = queryValue.toLowerCase(Locale.ROOT);
                rows.removeIf(r -> {
                    String u = String.valueOf(r.get("username")).toLowerCase(Locale.ROOT);
                    String did = String.valueOf(r.get("deviceId")).toLowerCase(Locale.ROOT);
                    return !u.contains(q) && !did.contains(q);
                });
            }

            Comparator<BasicDBObject> cmp = buildDeviceComparator(sortKey);
            if (sortOrder < 0) cmp = cmp.reversed();
            rows.sort(cmp);

            int total = rows.size();
            int effectiveLimit = limit > 0 ? Math.min(limit, 500) : 20;
            int from = Math.max(0, skip);
            int to = Math.min(rows.size(), from + effectiveLimit);
            List<BasicDBObject> page = from < to ? rows.subList(from, to) : Collections.emptyList();

            response.put("devices", page);
            response.put("total", total);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching agentic asset devices page: " + e.getMessage());
            addActionError("Error fetching agentic asset devices page: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    // Richer per-device accumulator for fetchAgenticAssetEndpointsPage — unlike DeviceAcc (which
    // only keeps merged service NAMES for the flat devices tab), this keeps every member
    // collection's own row (risk/sensitive/tags/skills) so the frontend can render an expandable
    // per-device breakdown — the Java equivalent of AgentEndpointTreeTable.jsx's groupByEndpointId,
    // scoped to one asset's own collectionIds instead of the whole account.
    private static final class EndpointGroup {
        final String deviceId;
        String username = "-";
        double riskScore = 0;
        int lastSeenEpoch = 0;
        int startTs = 0; // 0 == "no discovered time seen yet"; matches JS's Infinity->0 fallback
        final Set<String> sensitiveTypes = new LinkedHashSet<>();
        boolean hasPersonalAccount = false;
        boolean hasLocalMcpServer = false;
        boolean hasMisconfiguredConfig = false;
        boolean hasMaliciousSkill = false;
        final List<BasicDBObject> children = new ArrayList<>();

        EndpointGroup(String deviceId) { this.deviceId = deviceId; }
    }

    // Java port of AgentEndpointTreeTable.jsx's groupByEndpointId — scoped to a single asset's own
    // collectionIds (never account-wide). rowType decides each child's display name field, matching
    // getChildColumnConfig: agent/skill rows show their own service name, service(mcp)/llm rows show
    // the calling source's id (transform.js's splitCollectionNameForEndpointSecurity's sourceId).
    private static Map<String, EndpointGroup> groupCollectionsByEndpointId(List<ApiCollection> collections,
            Map<String, Integer> traffic, Map<String, Double> risk, Map<String, List<String>> sensitive,
            Set<String> maliciousSkillKeys, Map<String, String> usernameMap, String rowType) {
        boolean useServiceName = "agent".equals(rowType) || "skill".equals(rowType) || "plugin".equals(rowType);
        Map<String, EndpointGroup> groups = new LinkedHashMap<>();
        for (ApiCollection c : collections) {
            String hostName = c.getHostName();
            if (StringUtils.isBlank(hostName)) continue;
            String deviceId = AgenticObserveUtil.extractEndpointId(hostName);
            if (deviceId == null) continue;

            String idStr = String.valueOf(c.getId());
            Integer t = traffic.get(idStr);
            int collTraffic = t != null ? t : 0;
            Double r = risk.get(idStr);
            double collRisk = r != null ? r : 0.0;
            List<String> collSensitive = sensitive != null ? sensitive.get(idStr) : null;
            boolean[] flags = computeAgenticTagFlags(c.getEnvType());
            int childStartTs = c.getStartTs();

            int skillCount = 0;
            boolean childMalicious = false;
            boolean skipSkillsForPluginCollapsedService = "service".equals(rowType)
                    && AgenticObserveUtil.getOwningPluginName(c) != null;
            if (c.getSkills() != null && !skipSkillsForPluginCollapsedService) {
                skillCount = c.getSkills().size();
                for (String s : c.getSkills()) {
                    if (StringUtils.isNotBlank(s) && maliciousSkillKeys.contains(idStr + "|" + s.toLowerCase(Locale.ROOT))) {
                        childMalicious = true;
                        break;
                    }
                }
            }

            String displayName = useServiceName ? extractServiceNameForGrouping(hostName) : extractSourceIdForGrouping(hostName);

            BasicDBObject child = new BasicDBObject();
            child.put("id", c.getId());
            child.put("name", StringUtils.isNotBlank(displayName) ? displayName : hostName);
            child.put("riskScore", collRisk > 0 ? AgenticObserveUtil.roundRiskScore(collRisk) : 0);
            child.put("sensitiveInRespTypes", collSensitive != null ? collSensitive : Collections.emptyList());
            child.put("lastSeenEpoch", collTraffic);
            child.put("startTs", childStartTs);
            child.put("hasPersonalAccount", flags[0]);
            child.put("hasLocalMcpServer", flags[1]);
            child.put("hasMisconfiguredConfig", flags[2]);
            child.put("hasMaliciousSkill", childMalicious);
            child.put("skillCount", skillCount);
            child.put("baseRiskScore", c.getBaseRiskScore());
            child.put("baseRiskScoreReason", c.getBaseRiskScoreReason());

            // Plugin collections carry the agent's mcp-client/ai-agent tags (naming where the plugin is
            // installed), so label the child by its own type rather than letting those tags speak.
            if ("plugin".equals(rowType)) {
                child.put("type", AgenticObserveUtil.CLIENT_TYPE_PLUGIN);
            } else {
                // MCP Server/LLM asset's own device tree only — each child IS that server's own
                // collection, so its plugin-name tag (if any) is unambiguous here (see
                // AgenticObserveUtil.getOwningPluginName). Excluded for "plugin" rows: there, the
                // child IS the plugin's own collection, so this would show the plugin as "bundled
                // by" itself.
                String owningPluginName = AgenticObserveUtil.getOwningPluginName(c);
                if (owningPluginName != null) child.put("owningPluginName", owningPluginName);
            }

            EndpointGroup g = groups.computeIfAbsent(deviceId, id -> {
                EndpointGroup ng = new EndpointGroup(id);
                ng.username = resolveUsernameByDeviceId(id, usernameMap);
                return ng;
            });
            g.children.add(child);
            if (collTraffic > g.lastSeenEpoch) g.lastSeenEpoch = collTraffic;
            if (childStartTs > 0 && (g.startTs == 0 || childStartTs < g.startTs)) g.startTs = childStartTs;
            if (collRisk > g.riskScore) g.riskScore = collRisk;
            if (collSensitive != null) g.sensitiveTypes.addAll(collSensitive);
            if (flags[0]) g.hasPersonalAccount = true;
            if (flags[1]) g.hasLocalMcpServer = true;
            if (flags[2]) g.hasMisconfiguredConfig = true;
            if (childMalicious) g.hasMaliciousSkill = true;
        }
        return groups;
    }

    private static Comparator<BasicDBObject> buildEndpointGroupComparator(String key) {
        if ("username".equals(key)) {
            return Comparator.comparing((BasicDBObject r) -> String.valueOf(r.get("username")), String.CASE_INSENSITIVE_ORDER);
        } else if ("endpointId".equals(key)) {
            return Comparator.comparing((BasicDBObject r) -> String.valueOf(r.get("endpointId")), String.CASE_INSENSITIVE_ORDER);
        } else if ("lastSeenEpoch".equals(key) || "detectedTimestamp".equals(key)) {
            return Comparator.comparingInt(r -> ((Number) r.get("lastSeenEpoch")).intValue());
        } else if ("startTs".equals(key) || "discovered".equals(key)) {
            return Comparator.comparingInt(r -> ((Number) r.get("startTs")).intValue());
        }
        return Comparator.comparingDouble(r -> {
            Object v = r.get("riskScore");
            return v == null ? 0.0 : ((Number) v).doubleValue();
        });
    }

    /**
     * Server-side paginated, per-device TREE list for ONE asset's legacy-layout row-click page —
     * the richer sibling of fetchAgenticAssetDevicesPage, matching AgentEndpointTreeTable.jsx's
     * expandable device -> children UI, but scoped to just this asset's own collectionIds and
     * paginated at the device-group level instead of loading the whole account's collections
     * (getAllCollectionsBasic) into the browser to group/filter client-side.
     */
    public String fetchAgenticAssetEndpointsPage() {
        response = new BasicDBObject();
        try {
            List<Integer> ids = apiCollectionIds != null ? apiCollectionIds : Collections.emptyList();
            if (ids.isEmpty()) {
                response.put("endpoints", new ArrayList<>());
                response.put("total", 0);
                return SUCCESS.toUpperCase();
            }

            List<ApiCollection> collections = ApiCollectionsDao.instance.findAll(
                    Filters.in(Constants.ID, ids),
                    Projections.include(ApiCollection.ID, ApiCollection.HOST_NAME, ApiCollection.TAGS_STRING,
                            ApiCollection.SKILLS, ApiCollection.START_TS, ApiCollection.BASE_RISK_SCORE,
                            ApiCollection.BASE_RISK_SCORE_REASON)
            );

            // Computed here, scoped to just this asset's own `ids` — unlike every other agentic
            // endpoint on this page, which reads trafficMap/riskScoreMap/sensitiveMap off the
            // request because they're genuinely account-wide maps a LIST page already has to fetch
            // once for all its rows. A single-asset page has no such list to amortize across, so
            // requiring the client to fetch the whole account's map just to read a handful of
            // entries back out of it was pure waste — these three DAO calls are real Mongo queries
            // filtered by `ids`, not a repost of client-supplied data.
            Map<String, Integer> traffic = toStringKeyedMap(ApiInfoDao.instance.getLastTrafficSeenForCollections(ids));
            Map<String, Double> risk = toStringKeyedMap(ApiInfoDao.instance.getRiskScoreForCollections(ids));
            List<String> sensitiveSubtypes = SingleTypeInfoDao.instance.sensitiveSubTypeInResponseNames();
            sensitiveSubtypes.addAll(SingleTypeInfoDao.instance.sensitiveSubTypeNames());
            sensitiveSubtypes.addAll(SingleTypeInfoDao.instance.sensitiveSubTypeInRequestNames());
            Map<String, List<String>> sensitive = toStringKeyedMap(
                    SingleTypeInfoDao.instance.getSensitiveSubtypesDetectedForCollections(sensitiveSubtypes, ids));

            Set<String> maliciousSkillKeys = getOrBuildSkillData().maliciousSkillKeys;
            String effectiveRowType = StringUtils.isNotBlank(rowType) ? rowType : "agent";

            Map<String, EndpointGroup> groups = groupCollectionsByEndpointId(
                    collections, traffic, risk, sensitive, maliciousSkillKeys, usernameMap, effectiveRowType);

            List<BasicDBObject> rows = new ArrayList<>();
            for (EndpointGroup g : groups.values()) {
                BasicDBObject row = new BasicDBObject();
                row.put("endpointId", g.deviceId);
                row.put("username", g.username);
                row.put("riskScore", g.riskScore > 0 ? AgenticObserveUtil.roundRiskScore(g.riskScore) : 0);
                row.put("sensitiveInRespTypes", new ArrayList<>(g.sensitiveTypes));
                row.put("lastSeenEpoch", g.lastSeenEpoch);
                row.put("startTs", g.startTs);
                row.put("hasPersonalAccount", g.hasPersonalAccount);
                row.put("hasLocalMcpServer", g.hasLocalMcpServer);
                row.put("hasMisconfiguredConfig", g.hasMisconfiguredConfig);
                row.put("hasMaliciousSkill", g.hasMaliciousSkill);
                row.put("childCount", g.children.size());
                row.put("children", g.children);
                rows.add(row);
            }

            // Filter-chip choices — matches AgentEndpointTreeTable.jsx's own Endpoint ID/Username
            // facets, which enumerate every value present rather than a free-text field, since this
            // asset's own device count is small enough to list directly (bounded by ids.size(), not
            // account-wide). Computed from the full unfiltered/unsearched set so choices stay stable
            // regardless of what's currently searched/filtered — same as prod's own behavior.
            Set<String> distinctEndpointIds = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            Set<String> distinctUsernames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (BasicDBObject r : rows) {
                distinctEndpointIds.add(String.valueOf(r.get("endpointId")));
                String u = String.valueOf(r.get("username"));
                if (StringUtils.isNotBlank(u) && !"-".equals(u)) distinctUsernames.add(u);
            }

            if (StringUtils.isNotBlank(queryValue)) {
                String q = queryValue.toLowerCase(Locale.ROOT);
                rows.removeIf(r -> {
                    String u = String.valueOf(r.get("username")).toLowerCase(Locale.ROOT);
                    String eid = String.valueOf(r.get("endpointId")).toLowerCase(Locale.ROOT);
                    return !u.contains(q) && !eid.contains(q);
                });
            }

            List<String> endpointTagFilters = filters != null ? filters.get("endpointTags") : null;
            if (endpointTagFilters != null && !endpointTagFilters.isEmpty()) {
                Set<String> wanted = new HashSet<>(endpointTagFilters);
                rows.removeIf(r -> {
                    if (wanted.contains("Contains personal account") && Boolean.TRUE.equals(r.getBoolean("hasPersonalAccount", false))) return false;
                    if (wanted.contains("Local MCP Server") && Boolean.TRUE.equals(r.getBoolean("hasLocalMcpServer", false))) return false;
                    if (wanted.contains("Misconfigured") && Boolean.TRUE.equals(r.getBoolean("hasMisconfiguredConfig", false))) return false;
                    if (wanted.contains("Malicious Skills") && Boolean.TRUE.equals(r.getBoolean("hasMaliciousSkill", false))) return false;
                    return true;
                });
            }

            List<String> endpointIdFilters = filters != null ? filters.get("endpointId") : null;
            if (endpointIdFilters != null && !endpointIdFilters.isEmpty()) {
                Set<String> wanted = new HashSet<>(endpointIdFilters);
                rows.removeIf(r -> !wanted.contains(String.valueOf(r.get("endpointId"))));
            }

            List<String> usernameFilters = filters != null ? filters.get("username") : null;
            if (usernameFilters != null && !usernameFilters.isEmpty()) {
                Set<String> wanted = new HashSet<>(usernameFilters);
                rows.removeIf(r -> !wanted.contains(String.valueOf(r.get("username"))));
            }

            Comparator<BasicDBObject> cmp = buildEndpointGroupComparator(sortKey);
            if (sortOrder < 0) cmp = cmp.reversed();
            rows.sort(cmp);

            int total = rows.size();
            int effectiveLimit = limit > 0 ? Math.min(limit, 500) : 20;
            int from = Math.max(0, skip);
            int to = Math.min(rows.size(), from + effectiveLimit);
            List<BasicDBObject> page = from < to ? rows.subList(from, to) : Collections.emptyList();

            response.put("endpoints", page);
            response.put("total", total);
            response.put("distinctEndpointIds", new ArrayList<>(distinctEndpointIds));
            response.put("distinctUsernames", new ArrayList<>(distinctUsernames));
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching agentic asset endpoints page: " + e.getMessage());
            addActionError("Error fetching agentic asset endpoints page: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    // ---- fetchAgenticComponentsPage helpers — Java port of agenticPageBuilders.js's
    // buildSkillsFlyoutData/buildMcpComponentsFromStis/buildAgentBuiltinToolsFromStis, batched
    // across an asset's whole collectionIds set instead of one collection at a time. ----

    private static String skillNameFromUrl(String url) {
        if (StringUtils.isBlank(url)) return null;
        int idx = url.indexOf("skills/");
        if (idx < 0) return null;
        String name = url.substring(idx + "skills/".length());
        return StringUtils.isNotBlank(name) ? name : null;
    }

    // Plugin-bundled agent definitions (bundle.go's tagPluginAgent) live at "/agents/<name>" on the
    // plugin's own collection — same URL-based discovery as skills, just a different path segment.
    private static String agentNameFromUrl(String url) {
        if (StringUtils.isBlank(url)) return null;
        int idx = url.indexOf("agents/");
        if (idx < 0) return null;
        String name = url.substring(idx + "agents/".length());
        return StringUtils.isNotBlank(name) ? name : null;
    }

    // Last non-empty path segment — mirrors agenticPageBuilders.js's mcpDisplayName.
    private static String mcpDisplayName(String url) {
        if (url == null) return null;
        String trimmed = url.replaceAll("/+$", "");
        String[] parts = trimmed.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (StringUtils.isNotBlank(parts[i])) return parts[i];
        }
        return StringUtils.isNotBlank(trimmed) ? trimmed : url;
    }

    // Authoritative classification from McpAuditInfo's own `type` — mirrors bucketFromAuditType.
    private static String bucketFromAuditType(String type) {
        if (type == null) return null;
        String t = type.toLowerCase(Locale.ROOT);
        if (t.contains("skill")) return "Skill";
        if (t.contains("resource")) return "Resource";
        if (t.contains("prompt")) return "Prompt";
        if (t.contains("server")) return "Server";
        if (t.contains("tool")) return "Tool";
        return null;
    }

    // Fallback when no audit record matches — mirrors bucketFromUrl.
    private static String bucketFromUrl(String url) {
        String u = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (u.contains("skill")) return "Skill";
        if (u.contains("resource")) return "Resource";
        if (u.contains("prompt")) return "Prompt";
        if (u.contains("server")) return "Server";
        if (u.matches("^/mcp\\b.*") || "initialize".equals(u) || "ping".equals(u)) return "Server";
        return "Tool";
    }

    private static boolean isAgentBuiltinToolUrl(String url) {
        return url != null && url.startsWith("/tool/") && url.length() > "/tool/".length();
    }

    // LLM traffic on the agent host — mirrors agenticPageBuilders.js's isAgentLlmMessagesUrl.
    private static boolean isAgentLlmMessagesUrl(String url) {
        return "/v1/messages".equals(url) || (url != null && url.startsWith("/v1/messages/"));
    }

    // Split-and-capitalize fallback only — mirrors mcpClientHelper.js's formatDisplayName minus its
    // MCP-client-keyword branch (that branch matches known client/service names like "cursor", not
    // skill/tool names, so it never fires for this endpoint's inputs in practice).
    private static String formatComponentDisplayName(String raw) {
        if (StringUtils.isBlank(raw)) return "Unknown";
        if (raw.contains(".")) return raw;
        StringBuilder sb = new StringBuilder();
        for (String w : raw.split("[-_\\s]+")) {
            if (StringUtils.isBlank(w)) continue;
            if (sb.length() > 0) sb.append(' ');
            String lower = w.toLowerCase(Locale.ROOT);
            if ("cli".equals(lower) || "mcp".equals(lower)) sb.append(w.toUpperCase(Locale.ROOT));
            else sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.length() > 0 ? sb.toString() : raw;
    }

    private static int sumViolations(Map<String, Integer> violations) {
        if (violations == null) return 0;
        int total = 0;
        for (Integer v : violations.values()) total += v != null ? v : 0;
        return total;
    }

    private static final class SkillAcc {
        final String name;
        String url;
        String method;
        String description = "";
        double riskScore = 0;
        int violations = 0;
        double threatScore = 0;
        boolean hasThreatTag = false;
        Integer apiCollectionId;
        SkillAcc(String name) { this.name = name; }
    }

    private static Comparator<BasicDBObject> buildComponentComparator(String key) {
        if ("violations".equals(key)) {
            return Comparator.comparingInt(r -> ((Number) r.getOrDefault("violations", 0)).intValue());
        } else if ("riskScore".equals(key)) {
            return Comparator.comparingDouble(r -> {
                Object v = r.get("riskScore");
                return v == null ? 0.0 : ((Number) v).doubleValue();
            });
        } else if ("_type".equals(key) || "type".equals(key)) {
            return Comparator.comparing((BasicDBObject r) -> String.valueOf(r.get("_type")), String.CASE_INSENSITIVE_ORDER);
        }
        return Comparator.comparing((BasicDBObject r) -> String.valueOf(r.get("name")), String.CASE_INSENSITIVE_ORDER);
    }

    /**
     * Server-side paginated Components list for ONE AI-Agent asset's flyout — merges skills
     * (collection.skills ∪ /skills/&lt;name&gt; apiInfo/STI matches, mirrors buildSkillsFlyoutData),
     * built-in tools (STI /tool/* endpoints, audit-type/url-bucket classified, mirrors
     * buildAgentBuiltinToolsFromStis), and connected MCP servers (passed through from the
     * already-known mcpServerNames — cheap, no re-derivation) into one sorted, paginated list.
     * Batches what the old per-collection-id JS pipeline fired as 3N+N round trips
     * (fetchApisFromStis/fetchApiInfosForCollection/fetchMcpAuditInfoByCollection × N collections,
     * plus a full-account getAllCollectionsBasic() scan per collection just to look up one
     * ApiCollection by id) into 3 total queries. The synthetic "Config" row stays client-side
     * (AgentComponentsView.jsx pins it above this list) — it's derived from violation rows this
     * endpoint has no reason to touch.
     */
    public String fetchAgenticComponentsPage() {
        response = new BasicDBObject();
        try {
            List<Integer> ids = apiCollectionIds != null ? apiCollectionIds : Collections.emptyList();
            if (ids.isEmpty()) {
                response.put("components", new ArrayList<>());
                response.put("total", 0);
                return SUCCESS.toUpperCase();
            }

            List<ApiCollection> collections = ApiCollectionsDao.instance.findAll(
                    Filters.in(Constants.ID, ids),
                    Projections.include(ApiCollection.ID, ApiCollection.HOST_NAME, ApiCollection.SKILLS, ApiCollection.TAGS_STRING)
            );

            Set<String> skillNamesFromCollections = new LinkedHashSet<>();
            Set<String> mcpHostNames = new HashSet<>();
            Map<Integer, ApiCollection> collectionById = new HashMap<>();
            for (ApiCollection c : collections) {
                collectionById.put(c.getId(), c);
                if (c.getSkills() != null) {
                    for (String s : c.getSkills()) if (StringUtils.isNotBlank(s)) skillNamesFromCollections.add(s);
                }
                String mcpHost = McpRequestResponseUtils.extractServiceNameFromHost(c.getHostName());
                if (StringUtils.isNotBlank(mcpHost)) mcpHostNames.add(mcpHost);
            }

            List<ApiInfo> apiInfos = ApiInfoDao.instance.findAll(Filters.in(SingleTypeInfo._COLLECTION_IDS, ids));

            List<BasicDBObject> stiRows = ApiCollectionsDao.fetchEndpointsInCollection(
                    Filters.in(SingleTypeInfo._COLLECTION_IDS, ids), 0, -1, DELTA_PERIOD_VALUE);

            List<McpAuditInfo> auditRows = mcpHostNames.isEmpty() ? Collections.emptyList() :
                    McpAuditInfoDao.instance.findAll(
                            Filters.and(Filters.in(McpAuditInfo.MCP_HOST, mcpHostNames), Filters.ne(McpAuditInfo.TYPE, McpAuditInfo.TYPE_AGENT_SKILL)),
                            0, 1000, null);

            // "method url" -> [riskScore, violationCount], shared by both skills and tools.
            Map<String, double[]> infoByKey = new HashMap<>();
            for (ApiInfo info : apiInfos) {
                if (info.getId() == null || info.getId().getMethod() == null || info.getId().getUrl() == null) continue;
                infoByKey.put(info.getId().getMethod().name() + " " + info.getId().getUrl(),
                        new double[]{ info.getRiskScore(), sumViolations(info.getViolations()) });
            }

            // resourceName -> type/malicious/privileged/evidence from McpAuditInfo (server-type rows excluded).
            Map<String, String> typeByName = new HashMap<>();
            Map<String, Boolean> maliciousByName = new HashMap<>();
            Map<String, Boolean> privilegedByName = new HashMap<>();
            Map<String, String> riskDescByName = new HashMap<>();
            for (McpAuditInfo row : auditRows) {
                if (StringUtils.isBlank(row.getType()) || StringUtils.isBlank(row.getResourceName())) continue;
                if (row.getType().toLowerCase(Locale.ROOT).contains("server")) continue;
                typeByName.put(row.getResourceName(), row.getType());
                ComponentRiskAnalysis cra = row.getComponentRiskAnalysis();
                if (cra != null) {
                    if (cra.getIsComponentMalicious()) maliciousByName.put(row.getResourceName(), true);
                    if (cra.getHasPrivilegedAccess()) privilegedByName.put(row.getResourceName(), true);
                    if (StringUtils.isNotBlank(cra.getEvidence())) riskDescByName.put(row.getResourceName(), cra.getEvidence());
                    else if (StringUtils.isNotBlank(row.getRemarks())) riskDescByName.put(row.getResourceName(), row.getRemarks());
                } else if (StringUtils.isNotBlank(row.getRemarks())) {
                    riskDescByName.put(row.getResourceName(), row.getRemarks());
                }
            }

            // ---- Skills: collection.skills ∪ apiInfo "/skills/<name>" matches ∪ STI fallback ----
            Map<String, SkillAcc> skillAcc = new LinkedHashMap<>();
            for (String s : skillNamesFromCollections) skillAcc.computeIfAbsent(s, SkillAcc::new);
            for (ApiInfo info : apiInfos) {
                if (info.getId() == null) continue;
                String skillName = skillNameFromUrl(info.getId().getUrl());
                if (skillName == null) continue;
                SkillAcc acc = skillAcc.computeIfAbsent(skillName, SkillAcc::new);
                acc.url = info.getId().getUrl();
                if (info.getId().getMethod() != null) acc.method = info.getId().getMethod().name();
                // A skill can exist on both a shared agent collection and a plugin's own collection
                // (plugin-bundled skills are ingested onto both) — prefer the plugin-tagged one so
                // owningPluginName resolves, instead of whichever collection's ApiInfo iterates last.
                Integer candidateId = info.getId().getApiCollectionId();
                boolean currentIsPlugin = acc.apiCollectionId != null && AgenticObserveUtil.getOwningPluginName(collectionById.get(acc.apiCollectionId)) != null;
                if (acc.apiCollectionId == null || !currentIsPlugin) {
                    acc.apiCollectionId = candidateId;
                }
                if (StringUtils.isNotBlank(info.getDescription())) acc.description = info.getDescription();
                acc.riskScore = Math.max(acc.riskScore, info.getRiskScore());
                int vCount = sumViolations(info.getViolations());
                if (vCount > 0) acc.violations = vCount;
                acc.threatScore = Math.max(acc.threatScore, info.getThreatScore());
                if (info.getTagsList() != null) {
                    for (CollectionTags t : info.getTagsList()) {
                        if (t == null) continue;
                        String key = t.getKeyName();
                        String value = t.getValue();
                        if ("skill-tags".equals(key) && StringUtils.isNotBlank(value) && !value.toLowerCase(Locale.ROOT).startsWith("version=")) acc.hasThreatTag = true;
                        if ("malicious-skill-tag".equals(key) && "true".equals(value)) acc.hasThreatTag = true;
                    }
                }
            }
            for (BasicDBObject sti : stiRows) {
                Object idObj = sti.get("_id");
                if (!(idObj instanceof BasicDBObject)) continue;
                BasicDBObject stiId = (BasicDBObject) idObj;
                String skillName = skillNameFromUrl(stiId.getString("url"));
                if (skillName == null) continue;
                SkillAcc acc = skillAcc.computeIfAbsent(skillName, SkillAcc::new);
                if (acc.url == null) acc.url = stiId.getString("url");
                if (acc.method == null) acc.method = stiId.getString("method");
                if (acc.apiCollectionId == null) acc.apiCollectionId = stiId.getInt("apiCollectionId", -1);
            }

            // ---- Agents: plugin-bundled agent definitions, apiInfo "/agents/<name>" matches only —
            // no collection.skills-equivalent source, no STI fallback needed (a plugin's own
            // collection always has real traffic from tagPluginAgent's ingest). ----
            Map<String, Integer> agentNameToCollectionId = new LinkedHashMap<>();
            for (ApiInfo info : apiInfos) {
                if (info.getId() == null) continue;
                String agentName = agentNameFromUrl(info.getId().getUrl());
                if (agentName == null) continue;
                agentNameToCollectionId.putIfAbsent(agentName, info.getId().getApiCollectionId());
            }

            List<BasicDBObject> rows = new ArrayList<>();
            for (SkillAcc acc : skillAcc.values()) {
                BasicDBObject row = new BasicDBObject();
                row.put("_type", "Skill");
                row.put("name", formatComponentDisplayName(acc.name));
                row.put("rawName", acc.name);
                row.put("isNew", false);
                row.put("violations", acc.violations);
                row.put("blocked", false);
                row.put("riskScore", acc.riskScore);
                row.put("url", acc.url != null ? acc.url : "/skills/" + acc.name);
                row.put("method", acc.method != null ? acc.method : "SKILL");
                row.put("apiCollectionId", acc.apiCollectionId);
                row.put("description", acc.description);
                row.put("threatScore", acc.threatScore);
                row.put("isThreatEnabled", acc.threatScore > 0 || acc.hasThreatTag);
                // Plugin-bundled skills live directly in the plugin's own collection (bundle.go's
                // tagPluginSkill), so the skill's own apiCollectionId's plugin-name tag is the answer.
                ApiCollection skillCollection = acc.apiCollectionId != null ? collectionById.get(acc.apiCollectionId) : null;
                String owningPluginName = AgenticObserveUtil.getOwningPluginName(skillCollection);
                if (owningPluginName != null) row.put("owningPluginName", owningPluginName);
                rows.add(row);
            }

            for (Map.Entry<String, Integer> e : agentNameToCollectionId.entrySet()) {
                String agentName = e.getKey();
                Integer apiCollectionId = e.getValue();
                BasicDBObject row = new BasicDBObject();
                row.put("_type", "Agent");
                row.put("name", formatComponentDisplayName(agentName));
                row.put("rawName", agentName);
                row.put("url", "/agents/" + agentName);
                row.put("method", "AGENT");
                row.put("apiCollectionId", apiCollectionId);
                ApiCollection agentCollection = apiCollectionId != null ? collectionById.get(apiCollectionId) : null;
                String owningPluginNameForAgent = AgenticObserveUtil.getOwningPluginName(agentCollection);
                if (owningPluginNameForAgent != null) row.put("owningPluginName", owningPluginNameForAgent);
                rows.add(row);
            }

            // ---- Built-in tools: STI /tool/* endpoints only, Tool/Server buckets only (matches
            // buildAgentBuiltinToolsFromStis returning just .tools) ----
            for (BasicDBObject sti : stiRows) {
                Object idObj = sti.get("_id");
                if (!(idObj instanceof BasicDBObject)) continue;
                BasicDBObject stiId = (BasicDBObject) idObj;
                String method = stiId.getString("method");
                String url = stiId.getString("url");
                if (method == null || url == null || !isAgentBuiltinToolUrl(url)) continue;
                String name = mcpDisplayName(url);
                String bucketType = bucketFromAuditType(typeByName.get(name));
                if (bucketType == null) bucketType = bucketFromUrl(url);
                if (!"Tool".equals(bucketType) && !"Server".equals(bucketType)) continue;
                double[] info = infoByKey.getOrDefault(method + " " + url, new double[]{0, 0});
                boolean isMalicious = maliciousByName.getOrDefault(name, false);
                BasicDBObject row = new BasicDBObject();
                row.put("_type", "Tool");
                row.put("name", name);
                row.put("url", url);
                row.put("method", method);
                row.put("apiCollectionId", stiId.getInt("apiCollectionId", -1));
                row.put("description", "");
                row.put("riskScore", info[0]);
                row.put("riskLevel", isMalicious ? "critical" : null);
                row.put("isMalicious", isMalicious);
                row.put("hasPrivilegedAccess", privilegedByName.getOrDefault(name, false));
                row.put("riskDescription", riskDescByName.getOrDefault(name, ""));
                row.put("params", new ArrayList<>());
                row.put("violations", (int) info[1]);
                row.put("_serverType", "Server".equals(bucketType));
                rows.add(row);
            }

            // ---- Connected MCP servers — already known/cheap, no re-derivation ----
            if (mcpServerNames != null) {
                Set<String> seen = new HashSet<>();
                for (String mcpName : mcpServerNames) {
                    if (StringUtils.isBlank(mcpName) || !seen.add(mcpName.toLowerCase(Locale.ROOT))) continue;
                    BasicDBObject row = new BasicDBObject();
                    row.put("_type", "MCP Server");
                    row.put("name", mcpName);
                    row.put("endpoint", mcpName);
                    row.put("toolCount", 0);
                    List<Integer> mcpCollectionIds = mcpServerCollectionIds != null ? mcpServerCollectionIds.get(mcpName) : null;
                    row.put("collectionIds", mcpCollectionIds != null ? mcpCollectionIds : Collections.emptyList());
                    rows.add(row);
                }
            }

            // ---- Installed plugins — already known/cheap (GroupSummary.pluginNames' reverse link
            // from classifyAllGroups' plugin fan-out), no re-derivation. Same shape as MCP servers
            // above: this list has no per-plugin tools/traffic of its own, so clicking one just opens
            // that Plugin asset directly (AgentComponentsView's handleListRowClick _type dispatch)
            // rather than a drill-down view like AgentMcpToolsView.
            if (pluginNames != null) {
                Set<String> seenPlugins = new HashSet<>();
                for (String pluginKey : pluginNames) {
                    // pluginKey is "<pluginName>|<ownerKeyOrCollectionId>" — the compound identity
                    // classifyAllGroups now keys plugin groups by (see the "figma on claude AND
                    // copilot" case this disambiguates); split off just the display name here.
                    if (StringUtils.isBlank(pluginKey) || !seenPlugins.add(pluginKey.toLowerCase(Locale.ROOT))) continue;
                    int sep = pluginKey.indexOf('|');
                    String displayName = sep > 0 ? pluginKey.substring(0, sep) : pluginKey;
                    BasicDBObject row = new BasicDBObject();
                    row.put("_type", "Plugin");
                    row.put("name", displayName);
                    row.put("rawName", pluginKey);
                    rows.add(row);
                }
            }

            if (StringUtils.isNotBlank(queryValue)) {
                String q = queryValue.toLowerCase(Locale.ROOT);
                rows.removeIf(r -> {
                    Object n = r.get("name");
                    return n == null || !n.toString().toLowerCase(Locale.ROOT).contains(q);
                });
            }

            Comparator<BasicDBObject> cmp = buildComponentComparator(sortKey);
            if (sortOrder < 0) cmp = cmp.reversed();
            rows.sort(cmp);

            int total = rows.size();
            int effectiveLimit = limit > 0 ? Math.min(limit, 500) : 20;
            int from = Math.max(0, skip);
            int to = Math.min(rows.size(), from + effectiveLimit);
            List<BasicDBObject> page = from < to ? rows.subList(from, to) : Collections.emptyList();

            response.put("components", page);
            response.put("total", total);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching agentic components page: " + e.getMessage());
            addActionError("Error fetching agentic components page: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    /**
     * One pass over every collection classifying it into agent/service/llm/skill groups — mirrors
     * groupCollectionsByAgent/Service/LLM/Skill in constants.js combined, including the agent-type
     * KNOWN_CLIENTS/alias resolution (McpClientRegistry) that the reverted first Stage 3 attempt
     * deliberately left client-side (see atlas-scale-test/DASHBOARD_OPTIMIZATION.md — leaving it out
     * is what made that attempt not a win, since agent groups cover ~98% of this account's
     * collections). Cheap: no per-device breakdown here, see buildDevicesForGroup.
     */
    // Per-account cache of (findAll + classifyAllGroups) — shared by fetchAgenticAssetsSummary,
    // fetchAgenticAssetsStats and fetchAgenticAssetDetail, the 3 callers that each independently
    // re-ran this same O(N) pass over EVERY collection in the account on EVERY request (every sort
    // click, filter change, tab switch, page turn, or asset open). Measured live on Atlas Scale Test
    // (25,890 collections): findAll ~1-3s, but classifyAllGroups alone took 30s+ under concurrent
    // load (multiple tabs/requests all recomputing this at once, contending for CPU) — this is what
    // actually made fetchAgenticAssetsSummary take 13-17s for a ~5KB response.
    // ConcurrentHashMap.compute() holds its per-key lock for the whole remapping call, so concurrent
    // requests for the SAME account serialize onto one recompute instead of each redoing the full
    // pass (the "thundering herd" that made the live numbers above so much worse than a single
    // isolated call) — different accounts don't block each other.
    // TTL is deliberately short (not the 45-minute style of ApiCollectionsAction's one-hardcoded-
    // account cache): traffic/risk/sensitive inputs are themselves already frontend-cached for ~2
    // minutes, and newly-discovered assets only need to show up within a similarly short window, not
    // instantly — this just eliminates the redundant recompute across the handful of calls one
    // page-load/interactive session naturally makes within that window.
    private static final long CLASSIFICATION_CACHE_TTL_MS = 60_000L;
    private static final Map<Integer, ClassificationCacheEntry> classificationCache = new ConcurrentHashMap<>();

    private static final class ClassificationCacheEntry {
        final List<ApiCollection> collections;
        final Map<String, GroupSummary> groups;
        final long builtAt;
        ClassificationCacheEntry(List<ApiCollection> collections, Map<String, GroupSummary> groups, long builtAt) {
            this.collections = collections;
            this.groups = groups;
            this.builtAt = builtAt;
        }
    }

    // Entries are only ever overwritten on that same account's next miss, never proactively evicted
    // — over a long server uptime with many distinct accounts, stale entries for accounts nobody
    // revisits would otherwise sit in memory forever. Piggybacks a cheap sweep onto the (already
    // slow-path) rebuild rather than running a separate background thread for it.
    private static final int CLASSIFICATION_CACHE_SWEEP_THRESHOLD = 50;

    // Fallback for fetchAgenticAssetsSummary/Stats only: use the client-supplied map if present, else compute it here via ApiCollectionsAction so the client no longer has to fetch+re-POST it.
    private static final class ScopedMapCacheEntry<V> {
        final Map<String, V> map;
        final long builtAt;
        ScopedMapCacheEntry(Map<String, V> map, long builtAt) {
            this.map = map;
            this.builtAt = builtAt;
        }
    }
    private static final Map<String, ScopedMapCacheEntry<Integer>> trafficMapFallbackCache = new ConcurrentHashMap<>();
    private static final Map<String, ScopedMapCacheEntry<Double>> riskScoreMapFallbackCache = new ConcurrentHashMap<>();

    // Both underlying DAO calls RBAC-scope by (userId, accountId) — keyed on accountId alone, one user's restricted view would leak into another's.
    private static String scopedCacheKey() {
        return Context.accountId.get() + "_" + Context.userId.get();
    }

    private Map<String, Integer> getOrComputeTrafficMap() {
        if (trafficMap != null && !trafficMap.isEmpty()) return trafficMap;
        String key = scopedCacheKey();
        long now = System.currentTimeMillis();
        ScopedMapCacheEntry<Integer> existing = trafficMapFallbackCache.get(key);
        if (existing != null && (now - existing.builtAt) < CLASSIFICATION_CACHE_TTL_MS) {
            return existing.map;
        }
        if (trafficMapFallbackCache.size() > CLASSIFICATION_CACHE_SWEEP_THRESHOLD) {
            trafficMapFallbackCache.entrySet().removeIf(e -> (now - e.getValue().builtAt) > CLASSIFICATION_CACHE_TTL_MS * 10);
        }
        // compute() serializes concurrent misses for the same key instead of each racing to Mongo.
        return trafficMapFallbackCache.compute(key, (k, cached) -> {
            long recheckNow = System.currentTimeMillis();
            if (cached != null && (recheckNow - cached.builtAt) < CLASSIFICATION_CACHE_TTL_MS) {
                return cached;
            }
            try {
                ApiCollectionsAction apiCollectionsAction = new ApiCollectionsAction();
                apiCollectionsAction.fetchLastSeenInfoInCollections();
                Map<Integer, Integer> raw = apiCollectionsAction.getLastTrafficSeenMap();
                Map<String, Integer> result = new HashMap<>();
                if (raw != null) raw.forEach((id, ts) -> result.put(String.valueOf(id), ts));
                return new ScopedMapCacheEntry<>(result, recheckNow);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Failed computing traffic map server-side for agentic assets", LogDb.DASHBOARD);
                return cached != null ? cached : new ScopedMapCacheEntry<>(Collections.emptyMap(), recheckNow);
            }
        }).map;
    }

    private Map<String, Double> getOrComputeRiskScoreMap() {
        if (riskScoreMap != null && !riskScoreMap.isEmpty()) return riskScoreMap;
        String key = scopedCacheKey();
        long now = System.currentTimeMillis();
        ScopedMapCacheEntry<Double> existing = riskScoreMapFallbackCache.get(key);
        if (existing != null && (now - existing.builtAt) < CLASSIFICATION_CACHE_TTL_MS) {
            return existing.map;
        }
        if (riskScoreMapFallbackCache.size() > CLASSIFICATION_CACHE_SWEEP_THRESHOLD) {
            riskScoreMapFallbackCache.entrySet().removeIf(e -> (now - e.getValue().builtAt) > CLASSIFICATION_CACHE_TTL_MS * 10);
        }
        return riskScoreMapFallbackCache.compute(key, (k, cached) -> {
            long recheckNow = System.currentTimeMillis();
            if (cached != null && (recheckNow - cached.builtAt) < CLASSIFICATION_CACHE_TTL_MS) {
                return cached;
            }
            try {
                ApiCollectionsAction apiCollectionsAction = new ApiCollectionsAction();
                apiCollectionsAction.fetchRiskScoreInfo();
                Map<Integer, Double> raw = apiCollectionsAction.getRiskScoreOfCollectionsMap();
                Map<String, Double> result = new HashMap<>();
                if (raw != null) raw.forEach((id, score) -> result.put(String.valueOf(id), score));
                return new ScopedMapCacheEntry<>(result, recheckNow);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Failed computing risk score map server-side for agentic assets", LogDb.DASHBOARD);
                return cached != null ? cached : new ScopedMapCacheEntry<>(Collections.emptyMap(), recheckNow);
            }
        }).map;
    }

    private ClassificationCacheEntry getOrBuildClassification(Map<String, Integer> traffic,
            Map<String, Double> risk, Map<String, List<String>> sensitive) {
        int accountId = Context.accountId.get();
        long now = System.currentTimeMillis();
        ClassificationCacheEntry existing = classificationCache.get(accountId);
        if (existing != null && (now - existing.builtAt) < CLASSIFICATION_CACHE_TTL_MS) {
            return existing;
        }
        if (classificationCache.size() > CLASSIFICATION_CACHE_SWEEP_THRESHOLD) {
            classificationCache.entrySet().removeIf(e -> (now - e.getValue().builtAt) > CLASSIFICATION_CACHE_TTL_MS * 10);
        }
        return classificationCache.compute(accountId, (id, cached) -> {
            long recheckNow = System.currentTimeMillis();
            if (cached != null && (recheckNow - cached.builtAt) < CLASSIFICATION_CACHE_TTL_MS) {
                return cached;
            }
            long tStart = System.currentTimeMillis();
            List<ApiCollection> collections = ApiCollectionsDao.instance.findAll(
                    Filters.empty(),
                    Projections.include(ApiCollection.ID, ApiCollection.HOST_NAME, ApiCollection.TAGS_STRING, ApiCollection.SKILLS, ApiCollection.START_TS,
                            ApiCollection.BASE_RISK_SCORE, ApiCollection.BASE_RISK_SCORE_REASON)
            );
            long tFindAll = System.currentTimeMillis();
            Map<String, GroupSummary> groups = classifyAllGroups(collections, traffic, risk, sensitive);
            long tClassify = System.currentTimeMillis();
            loggerMaker.warnAndAddToDb("[agenticClassificationCache] rebuilt: findAll=" + (tFindAll - tStart)
                    + "ms, classify=" + (tClassify - tFindAll) + "ms, collections=" + collections.size()
                    + ", groups=" + groups.size());
            return new ClassificationCacheEntry(collections, groups, tClassify);
        });
    }

    private Map<String, GroupSummary> classifyAllGroups(List<ApiCollection> collections,
            Map<String, Integer> traffic, Map<String, Double> risk, Map<String, List<String>> sensitiveMap) {
        Map<String, GroupSummary> groups = new LinkedHashMap<>();

        // A plugin-bundled skill's own collection (tagPluginSkill's isolation fix) carries the skill's
        // name on its "skill" tag, not in ApiCollection.skills (that array only gets populated by the
        // legacy per-agent skill-detector collection, a DIFFERENT collection than the plugin's own —
        // so the skill fan-out loop below, which only reads .skills, never sees the plugin-tagged
        // collection at all). Built as its own pre-pass so the fallback below is available regardless
        // of which collection the fan-out loop is currently on.
        Map<String, String> skillNameToOwningPlugin = new HashMap<>();
        for (ApiCollection c : collections) {
            String pluginName = AgenticObserveUtil.getOwningPluginName(c);
            if (pluginName == null) continue;
            String skillName = AgenticObserveUtil.getPluginTagValue(c, Constants.AKTO_SKILL_TAG);
            if (StringUtils.isNotBlank(skillName)) skillNameToOwningPlugin.put(skillName, pluginName);
        }

        for (ApiCollection c : collections) {
            if (c.isDeactivated()) continue;
            String hostName = c.getHostName();
            if (StringUtils.isBlank(hostName)) continue;

            List<CollectionTags> envType = c.getEnvType();
            String idStr = String.valueOf(c.getId());
            Integer trafficVal = traffic.get(idStr);
            // Falls back to the collection's own creation time when it has no directly-attributed
            // traffic entry (e.g. an MCP server/LLM collection only ever discovered via a tag/config
            // scan) — otherwise maxTrafficTimestamp stays 0 and the group gets silently dropped by
            // any date-range filter narrower than "All time" in fetchAgenticAssetsStats/Summary, even
            // though the asset genuinely exists. Mirrors constants.js's lastSeenOrStartTs (dead code
            // for this rebuilt page, but the same real bug it was written to fix).
            int collTraffic = trafficVal != null ? trafficVal : c.getStartTs();
            Double riskVal = risk.get(idStr);
            double collRisk = riskVal != null ? riskVal : 0.0;
            List<String> sensitive = sensitiveMap != null ? sensitiveMap.get(idStr) : null;
            String deviceId = AgenticObserveUtil.extractEndpointId(hostName);

            // Skill fan-out — a collection with multiple skills is a member of multiple skill
            // groups, independent of its agent/service/llm classification below. Counted even for a
            // "not-attached" hostname (an orphan skill bucket, not a real agent/service/llm) —
            // matches constants.js's groupCollectionsBySkill, which never checks
            // isNotAttachedHostName, unlike its Agent/Service/LLM siblings below.
            if (c.getSkills() != null) {
                for (String skill : c.getSkills()) {
                    if (StringUtils.isBlank(skill)) continue;
                    GroupSummary g = groups.computeIfAbsent("skill|" + skill, k -> {
                        GroupSummary gs = new GroupSummary(skill, "skill");
                        gs.name = skill;
                        gs.clientType = AgenticObserveUtil.CLIENT_TYPE_SKILL;
                        return gs;
                    });
                    g.accumulateCheap(c, hostName, envType, collRisk, collTraffic, deviceId, sensitive);
                    // Same skill name can fan out from both a plugin's own collection and the shared
                    // agent collection (which never carries plugin-name) — first non-null wins, so
                    // whichever member collection is actually plugin-tagged sets it, unambiguous per
                    // collection even though the group itself spans several. Falls back to the
                    // skillNameToOwningPlugin pre-pass since a plugin-bundled skill's own collection
                    // (tagPluginSkill's isolation fix) never has a populated .skills array itself — only
                    // its "skill" tag names it, so this collection (c) is never the one contributing it.
                    if (g.owningPluginName == null) g.owningPluginName = AgenticObserveUtil.getOwningPluginName(c);
                    if (g.owningPluginName == null) g.owningPluginName = skillNameToOwningPlugin.get(skill);
                }
            }

            // Plugins get their own collection (<device>.claude-plugin.<name>), like MCP servers — so
            // this is one group per collection, and the collection belongs to no other group. The
            // `continue` stops it also becoming an agent/service row off its own mcp-client/ai-agent
            // tags, which name the agent the plugin is installed on.
            String pluginName = AgenticObserveUtil.getPluginName(c);
            if (pluginName != null) {
                final ApiCollection pc = c;
                CollectionTags ownerTag = AgenticObserveUtil.findAssetTag(pc);
                // Canonicalized to match the agent group's own key below (raw tag values like
                // "claude"/"claudecli"/"claude-cli-user" all collapse to the same canonical agent).
                String ownerKey = ownerTag != null ? McpClientRegistry.resolveClientKey(ownerTag.getValue()) : null;
                // Compound identity: the SAME plugin name installed under two different agents (e.g.
                // "figma" on both claude and copilot) are genuinely different installs — bare
                // pluginName alone used to collapse them into one merged/wrong asset. ingestPlugin
                // always stamps ai-agent/mcp-client on a plugin's own collection, so ownerKey should
                // never actually be null in practice; the collection id fallback (always real, never
                // a placeholder string) only matters for malformed/legacy data missing that tag.
                String pluginKey = pluginName + "|" + (ownerKey != null ? ownerKey : String.valueOf(c.getId()));
                GroupSummary g = groups.computeIfAbsent("plugin|" + pluginKey, k -> {
                    GroupSummary gs = new GroupSummary(pluginKey, "plugin");
                    gs.name = pluginName;
                    gs.clientType = AgenticObserveUtil.CLIENT_TYPE_PLUGIN;
                    gs.pluginVersion = AgenticObserveUtil.getPluginTagValue(pc, Constants.AKTO_PLUGIN_VERSION_TAG);
                    gs.pluginScope = AgenticObserveUtil.getPluginTagValue(pc, Constants.AKTO_PLUGIN_SCOPE_TAG);
                    gs.pluginStatus = AgenticObserveUtil.getPluginTagValue(pc, Constants.AKTO_PLUGIN_STATUS_TAG);
                    gs.pluginMarketplace = AgenticObserveUtil.getPluginTagValue(pc, Constants.AKTO_PLUGIN_MARKETPLACE_TAG);
                    gs.pluginParentAgent = ownerKey != null ? McpClientRegistry.formatDisplayName(ownerKey) : null;
                    return gs;
                });
                g.accumulateCheap(c, hostName, envType, collRisk, collTraffic, deviceId, sensitive);
                // Reverse link: the agent's own group learns it has this plugin too (mirrors how an
                // agent group already learns its own serviceNames in accumulateCheap above) — this is
                // what lets the flyout's Components tab list a Plugin row under its parent AI Agent,
                // the same way it already lists that agent's MCP servers. Stores the compound key
                // (not bare pluginName) since fetchAgenticComponentsPage's "Installed plugins" row
                // round-trips this straight into fetchAgenticAssetDetail's groupKey lookup.
                if (ownerKey != null) {
                    GroupSummary agentGroup = groups.computeIfAbsent("agent|" + ownerKey, k -> {
                        GroupSummary gs = new GroupSummary(ownerKey, "agent");
                        gs.name = McpClientRegistry.formatDisplayName(ownerKey);
                        gs.clientType = McpClientRegistry.getAgentTypeFromValue(ownerKey);
                        return gs;
                    });
                    agentGroup.pluginNames.add(pluginKey);
                }

                if (hasTagKey(envType, Constants.AKTO_MCP_SERVER_TAG)) {
                    String serverGroupName = extractServiceNameForGrouping(hostName);
                    if (StringUtils.isNotBlank(serverGroupName)) {
                        GroupSummary sg = groups.computeIfAbsent("service|" + serverGroupName, k -> {
                            GroupSummary gs = new GroupSummary(serverGroupName, "service");
                            gs.name = serverGroupName;
                            gs.clientType = AgenticObserveUtil.CLIENT_TYPE_MCP_SERVER;
                            return gs;
                        });
                        sg.accumulateCheap(c, hostName, envType, collRisk, collTraffic, deviceId, sensitive);
                        if (sg.owningPluginName == null) sg.owningPluginName = pluginName;
                    }
                }
                continue;
            }

            if (hostName.contains(AgenticObserveUtil.NOT_ATTACHED_VALUE)) continue; // orphan bucket — not a real agent/service/llm

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
                    g.accumulateCheap(c, hostName, envType, collRisk, collTraffic, deviceId, sensitive);
                    if (g.owningPluginName == null) g.owningPluginName = AgenticObserveUtil.getOwningPluginName(c);
                }
                continue; // matches groupCollectionsByAgent/Service's browser-llm skip
            }

            CollectionTags assetTag = AgenticObserveUtil.findAssetTag(c);
            boolean addedToAgentGroup = false;
            if (assetTag != null && StringUtils.isNotBlank(assetTag.getValue())
                    && !Constants.AKTO_BROWSER_LLM_AGENT_TAG.equals(assetTag.getKeyName())) {
                String key = McpClientRegistry.resolveClientKey(assetTag.getValue());
                GroupSummary g = groups.computeIfAbsent("agent|" + key, k -> {
                    GroupSummary gs = new GroupSummary(key, "agent");
                    gs.name = McpClientRegistry.formatDisplayName(key);
                    gs.clientType = McpClientRegistry.getAgentTypeFromValue(key);
                    return gs;
                });
                g.tagKey = assetTag.getKeyName();
                g.rawTagValues.add(assetTag.getValue());
                g.accumulateCheap(c, hostName, envType, collRisk, collTraffic, deviceId, sensitive);
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
            g.accumulateCheap(c, hostName, envType, collRisk, collTraffic, deviceId, sensitive);
            if (g.owningPluginName == null) g.owningPluginName = AgenticObserveUtil.getOwningPluginName(c);
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
        } else if ("pluginCount".equals(key)) {
            cmp = Comparator.comparingInt(g -> g.pluginNames.size());
        } else {
            cmp = Comparator.comparingDouble(g -> g.maxRiskScore);
        }
        return cmp;
    }

    @Setter
    private Map<String, Map<String, Integer>> hostCounts;

    @Getter
    private Map<String, Map<String, Integer>> collectionViolationCounts;

    // Mirrors agenticObserveApi.js's deviceServiceKey exactly — device+service loose-match key for
    // 2-segment vs 3-segment host attribution (see resolveHostToCollectionIds's javadoc below).
    private static String deviceServiceKey(String hostName) {
        if (StringUtils.isBlank(hostName)) return null;
        String[] parts = hostName.split("\\.");
        if (parts.length < 2) return null;
        return parts[0] + " " + parts[parts.length - 1];
    }

    // Mirrors agenticObserveApi.js's isClaudeConfigHost exactly.
    private static boolean isClaudeConfigHost(String hostName) {
        if (StringUtils.isBlank(hostName)) return false;
        String[] parts = hostName.split("\\.");
        if (parts.length != 2) return false;
        String service = parts[1].toLowerCase(Locale.ROOT);
        return "claude-settings".equals(service) || "claude".equals(service);
    }

    /**
     * Attributes server-aggregated per-host violation severity counts (from
     * ThreatApiAction.fetchHostSeverityCounts, already fetched client-side and passed in as
     * hostCounts) to collection ids — the same exact/loose/claude-config three-tier join
     * agenticObserveApi.js's buildHostAttributionMaps/resolveHostToCollectionIds used to do
     * client-side, which required fetching every ApiCollection in the account (getAllCollectionsBasic,
     * ~19 fields/doc, several MB on large accounts) just to read two fields (id, hostName) off each
     * one. Ported here instead: loads only {id, hostName} for the join, runs entirely server-side, and
     * returns counts already keyed by collection id — the Agentic Assets page no longer needs the
     * account's collection list for this at all.
     */
    public String attributeViolationCountsToCollections() {
        collectionViolationCounts = new HashMap<>();
        try {
            if (hostCounts == null || hostCounts.isEmpty()) {
                return SUCCESS.toUpperCase();
            }

            List<ApiCollection> collections = ApiCollectionsDao.instance.findAll(
                    Filters.empty(),
                    Projections.include(Constants.ID, ApiCollection.HOST_NAME)
            );

            Map<String, List<Integer>> hostToIds = new HashMap<>();
            Map<String, List<Integer>> looseToIds = new HashMap<>();
            Map<String, List<Integer>> claudeDeviceToIds = new HashMap<>();
            List<Integer> allClaudeIds = new ArrayList<>();

            for (ApiCollection c : collections) {
                String hostName = c.getHostName();
                if (StringUtils.isBlank(hostName)) continue;

                hostToIds.computeIfAbsent(hostName, k -> new ArrayList<>()).add(c.getId());

                String lk = deviceServiceKey(hostName);
                if (lk != null) {
                    looseToIds.computeIfAbsent(lk, k -> new ArrayList<>()).add(c.getId());
                }

                String[] parts = hostName.split("\\.");
                String deviceId = parts.length > 0 ? parts[0] : null;
                String service = parts.length > 0 ? parts[parts.length - 1].toLowerCase(Locale.ROOT) : null;
                if (StringUtils.isNotBlank(deviceId) && "claude".equals(service)) {
                    claudeDeviceToIds.computeIfAbsent(deviceId, k -> new ArrayList<>()).add(c.getId());
                    allClaudeIds.add(c.getId());
                }
            }

            for (Map.Entry<String, Map<String, Integer>> entry : hostCounts.entrySet()) {
                String host = entry.getKey();
                Map<String, Integer> counts = entry.getValue();
                if (host == null || counts == null) continue;

                List<Integer> ids = hostToIds.get(host);
                if (ids == null || ids.isEmpty()) {
                    ids = looseToIds.get(deviceServiceKey(host));
                }
                if ((ids == null || ids.isEmpty()) && isClaudeConfigHost(host)) {
                    String deviceId = host.split("\\.")[0];
                    List<Integer> pool = claudeDeviceToIds.get(deviceId);
                    if (pool == null || pool.isEmpty()) pool = allClaudeIds;
                    ids = pool.isEmpty() ? null : Collections.singletonList(pool.get(0));
                }
                if (ids == null || ids.isEmpty()) continue;

                for (Integer id : ids) {
                    Map<String, Integer> agg = collectionViolationCounts.computeIfAbsent(String.valueOf(id), k -> {
                        Map<String, Integer> m = new HashMap<>();
                        m.put("critical", 0);
                        m.put("high", 0);
                        m.put("medium", 0);
                        m.put("low", 0);
                        return m;
                    });
                    agg.merge("critical", counts.getOrDefault("critical", 0), Integer::sum);
                    agg.merge("high", counts.getOrDefault("high", 0), Integer::sum);
                    agg.merge("medium", counts.getOrDefault("medium", 0), Integer::sum);
                    agg.merge("low", counts.getOrDefault("low", 0), Integer::sum);
                }
            }

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error attributing violation counts to collections: " + e.getMessage());
            addActionError(e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    // Sums an already-computed collectionId -> {critical,high,medium,low} map (from
    // attributeViolationCountsToCollections, fetched once account-wide and passed in as
    // violationsByCollectionId) over one row's own collectionIds. Returns null (not a zeroed object)
    // when the total is 0, matching the old client-side violationsForCollections' contract exactly.
    private static BasicDBObject sumViolationsForCollections(List<Integer> collectionIds, Map<String, Map<String, Integer>> violationsByCollectionId) {
        if (violationsByCollectionId == null || violationsByCollectionId.isEmpty()) return null;
        int critical = 0, high = 0, medium = 0, low = 0;
        for (Integer cid : collectionIds) {
            Map<String, Integer> v = violationsByCollectionId.get(String.valueOf(cid));
            if (v == null) continue;
            critical += v.getOrDefault("critical", 0);
            high += v.getOrDefault("high", 0);
            medium += v.getOrDefault("medium", 0);
            low += v.getOrDefault("low", 0);
        }
        if (critical + high + medium + low == 0) return null;
        BasicDBObject out = new BasicDBObject();
        out.put("critical", critical);
        out.put("high", high);
        out.put("medium", medium);
        out.put("low", low);
        return out;
    }

    // Skill-name equivalent of sumViolationsForCollections above — skill rows use this instead,
    // since a skill's declaring collection is shared with the agent/device that invoked it, so
    // collection-based attribution can't give a skill its own count (see skillViolationsByName's
    // own field comment). Same "null when zero" contract.
    private static BasicDBObject violationsForSkillName(String skillName, Map<String, Map<String, Integer>> skillViolationsByName) {
        if (StringUtils.isBlank(skillName) || skillViolationsByName == null) return null;
        Map<String, Integer> v = skillViolationsByName.get(skillName);
        if (v == null) return null;
        int critical = v.getOrDefault("critical", 0);
        int high = v.getOrDefault("high", 0);
        int medium = v.getOrDefault("medium", 0);
        int low = v.getOrDefault("low", 0);
        if (critical + high + medium + low == 0) return null;
        BasicDBObject out = new BasicDBObject();
        out.put("critical", critical);
        out.put("high", high);
        out.put("medium", medium);
        out.put("low", low);
        return out;
    }

    // Total violation count for one group, used to make "violations" a real server-side sort key
    // (see fetchAgenticAssetsSummary's sortKey handling) instead of only a per-page display value.
    // Skill rows always sort as 0 here, matching the per-row "violations" field above which no
    // longer surfaces skill-name-keyed violations on this grid at all.
    private static int violationsTotalForGroup(GroupSummary g, Map<String, Map<String, Integer>> violationsByCollectionId, Map<String, Map<String, Integer>> skillViolationsByName) {
        if ("skill".equals(g.rowType)) return 0;
        BasicDBObject v = sumViolationsForCollections(g.collectionIds, violationsByCollectionId);
        if (v == null) return 0;
        return v.getInt("critical", 0) + v.getInt("high", 0) + v.getInt("medium", 0) + v.getInt("low", 0);
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
            long tStart = System.currentTimeMillis();
            Map<String, Integer> traffic = getOrComputeTrafficMap();
            Map<String, Double> risk = getOrComputeRiskScoreMap();
            Map<String, List<String>> sensitive = sensitiveMap != null ? sensitiveMap : Collections.emptyMap();

            ClassificationCacheEntry cached = getOrBuildClassification(traffic, risk, sensitive);
            List<ApiCollection> collections = cached.collections;
            List<GroupSummary> all = new ArrayList<>(cached.groups.values());

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

            // AG Grid column filters. "type" is a small fixed enum (AgenticObserveUtil.CLIENT_TYPE_*),
            // matched directly. "tags" mirrors AgenticAssetsPage.jsx's shapeRow tag derivation exactly
            // (an array-valued field — Set Filter semantics: match if ANY selected tag is present),
            // including the "Malicious Skill" branch, which reads maliciousSkillKeys from this same
            // class's own skill-data cache (getOrBuildSkillData) instead of requiring the client to
            // re-POST the whole account-wide set (measured 14,218 entries / ~500KB+ on Atlas Scale
            // Test — the single largest contributor to this endpoint's request payload).
            if (filters != null) {
                if (filters.containsKey("type")) {
                    Set<String> allowed = new HashSet<>(filters.get("type"));
                    all.removeIf(g -> !allowed.contains(g.clientType));
                }
                if (filters.containsKey("pluginStatus")) {
                    Set<String> allowed = new HashSet<>(filters.get("pluginStatus"));
                    all.removeIf(g -> {
                        String status = StringUtils.isBlank(g.pluginStatus) ? null
                                : "enabled".equalsIgnoreCase(g.pluginStatus) ? "enabled" : "disabled";
                        return status == null || !allowed.contains(status);
                    });
                }
                if (filters.containsKey("tags")) {
                    Set<String> allowedTags = new HashSet<>(filters.get("tags"));
                    Set<String> maliciousKeys = getOrBuildSkillData().maliciousSkillKeys;
                    all.removeIf(g -> {
                        boolean isSkill = "skill".equals(g.rowType);
                        // Fan-out rows borrow their parent agent collection's flags, so suppress both.
                        boolean isFanout = isSkill || "plugin".equals(g.rowType);
                        Set<String> groupTags = new HashSet<>();
                        if (g.hasPersonalAccount && !isFanout) groupTags.add("Contains personal account");
                        if (g.hasLocalMcpServer && !isFanout) groupTags.add("Local MCP Server");
                        if (g.hasMisconfiguredConfig && !isFanout) groupTags.add("Misconfigured");
                        if (isSkill && StringUtils.isNotBlank(g.name)) {
                            String lname = g.name.toLowerCase(Locale.ROOT);
                            boolean malicious = g.collectionIds.stream()
                                    .anyMatch(cid -> maliciousKeys.contains(cid + "|" + lname));
                            if (malicious) groupTags.add("Malicious Skill");
                        }
                        return groupTags.stream().noneMatch(allowedTags::contains);
                    });
                }
                // "username" — a group matches if ANY of its member devices resolves (via the client's
                // Endpoint Shield usernameMap) to one of the selected usernames. Mirrors
                // resolveUsernameByDeviceId's use elsewhere in this class (e.g. fetchAgenticAssetDetail).
                if (filters.containsKey("username")) {
                    Set<String> allowedUsernames = new HashSet<>(filters.get("username"));
                    Map<String, String> resolveMap = usernameMap != null ? usernameMap : Collections.emptyMap();
                    all.removeIf(g -> g.endpointIds.stream()
                            .noneMatch(id -> allowedUsernames.contains(resolveUsernameByDeviceId(id, resolveMap))));
                }
                // "severity" — drives the Violations card's Critical/High/Medium/Low chips. Skill rows
                // never carry violations on this grid (see the row-loop's own comment below), so they
                // never match. Same "null when zero" contract as sumViolationsForCollections itself.
                if (filters.containsKey("severity")) {
                    Set<String> allowedSeverities = new HashSet<>(filters.get("severity"));
                    all.removeIf(g -> {
                        if ("skill".equals(g.rowType)) return true;
                        BasicDBObject v = sumViolationsForCollections(g.collectionIds, violationsByCollectionId);
                        if (v == null) return true;
                        return allowedSeverities.stream().noneMatch(s -> v.getInt(s, 0) > 0);
                    });
                }
            }

            long total = all.size();

            // "violations" isn't a stored/indexed field on GroupSummary — it's derived from the
            // violationsByCollectionId/skillViolationsByName maps the client already fetched once at
            // mount (see fetchAgenticAssetsStats' identical routing) — so it needs its own branch here
            // rather than going through buildSummaryComparator, which only knows about GroupSummary's
            // own fields.
            Comparator<GroupSummary> cmp = "violations".equals(sortKey)
                    ? Comparator.comparingInt(g -> violationsTotalForGroup(g, violationsByCollectionId, skillViolationsByName))
                    : buildSummaryComparator(sortKey);
            if (sortOrder < 0) cmp = cmp.reversed();
            all.sort(cmp);

            int effectiveLimit = limit > 0 ? Math.min(limit, 500) : 50;
            int from = Math.max(0, skip);
            int to = Math.min(all.size(), from + effectiveLimit);
            List<GroupSummary> page = from < to ? all.subList(from, to) : Collections.emptyList();

            Map<Integer, ApiCollection> byId = new HashMap<>();
            for (ApiCollection c : collections) byId.put(c.getId(), c);

            Map<String, Integer> userAnalysis = userAnalysisFlatMap != null ? userAnalysisFlatMap : Collections.emptyMap();
            SkillDataCacheEntry skillData = getOrBuildSkillData();
            Set<String> maliciousKeys = skillData.maliciousSkillKeys;
            List<BasicDBObject> rowsOut = new ArrayList<>();
            for (GroupSummary g : page) {
                BasicDBObject row = g.toSummaryResponse();
                // devices itself is NOT sent (dropped in favor of the two small values actually
                // derived from it below) — a group with hundreds of member devices turned this into
                // the single biggest contributor to a 16MB response once hostNames/collectionIds were
                // fixed (see fetchAgenticAssetDetail, the lazy per-asset endpoint the Overview tab's
                // full device list — topology graph, etc. — now comes from instead).
                List<BasicDBObject> devices = buildDevicesForGroup(g, byId, traffic, risk, userAnalysis);
                int aiInteractionsTotal = 0;
                for (BasicDBObject d : devices) {
                    Object v = d.get("aiInteractions");
                    if (v instanceof Number) aiInteractionsTotal += ((Number) v).intValue();
                }
                if (aiInteractionsTotal > 0) row.put("aiInteractions", aiInteractionsTotal);
                // Precomputed here (server already has g.collectionIds in memory) instead of sending
                // the raw collectionIds list to the browser just so it can do this same sum — see
                // toSummaryResponse()'s comment for why collectionIds/hostNames/etc. no longer appear
                // in this response at all.
                // Skill rows deliberately carry no "violations" here — same reasoning as the
                // "Top Assets with Violations" ranking above: skill-name-keyed violation counts
                // are a different signal (which skill got invoked during an attack) from this
                // grid's per-asset "how much trouble is this asset in" column, and mixing them in
                // made ~770 near-identical skill rows dominate a column meant for agents/devices/LLMs.
                BasicDBObject violations = "skill".equals(g.rowType)
                        ? null
                        : sumViolationsForCollections(g.collectionIds, violationsByCollectionId);
                if (violations != null) row.put("violations", violations);
                // Misconfigured is an Agent/MCP-server-only concept (a bad tool/config setup on that
                // asset) — Skill rows never show it, regardless of any tag collision. isMalicious is
                // the only skill-specific risk flag.
                if ("skill".equals(g.rowType) && StringUtils.isNotBlank(g.name)) {
                    String lname = g.name.toLowerCase(Locale.ROOT);
                    boolean malicious = g.collectionIds.stream().anyMatch(cid -> maliciousKeys.contains(cid + "|" + lname));
                    if (malicious) row.put("isMalicious", true);
                }
                // Plugin discovery carries no risk of its own, and g.maxRiskScore is the AGENT's —
                // showing it would mark every plugin on a risky agent as risky.
                if ("plugin".equals(g.rowType)) {
                    row.put("riskScore", null);
                    row.remove("baseRiskScore");
                    row.remove("baseRiskScoreReason");
                }
                if ("plugin".equals(g.rowType)) row.put("aiInteractions", null);
                rowsOut.add(row);
            }
            loggerMaker.warnAndAddToDb("[fetchAgenticAssetsSummary-timing] TOTAL=" + (System.currentTimeMillis() - tStart)
                    + "ms, rows=" + rowsOut.size() + ", accountCollections=" + collections.size());

            // Current-page-only, same as fetchAgenticAssetEndpointsPage's distinctUsernames — the
            // client accumulates these across page turns to progressively populate the Username filter's
            // choices (see Endpoints.jsx's updateFilterChoicesIfChanged).
            Map<String, String> resolveMapForChoices = usernameMap != null ? usernameMap : Collections.emptyMap();
            Set<String> distinctUsernames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (GroupSummary g : page) {
                for (String id : g.endpointIds) {
                    String u = resolveUsernameByDeviceId(id, resolveMapForChoices);
                    if (StringUtils.isNotBlank(u) && !"-".equals(u)) distinctUsernames.add(u);
                }
            }
            response.put("distinctUsernames", new ArrayList<>(distinctUsernames));

            response.put("rows", rowsOut);
            response.put("total", total);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching agentic assets summary: " + e.getMessage());
            addActionError("Error fetching agentic assets summary: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    @Setter private String groupKey;
    @Setter private String rowType;

    @Getter private List<String> assetHostNames;
    @Getter private List<Integer> assetCollectionIds;
    // Count only, not the full name list — the only consumer is the Overview tab's topology graph,
    // which just needs a "N Skills" summary node (see AssetTopologyGraph.jsx). The actual skill
    // *names* are never read off this response: the Components tab's fetchAgenticComponentsPage
    // independently re-derives its own full skill listing straight from apiCollectionIds/STIs when
    // opened (see its own comment), so shipping g.skillNames here too was pure duplicate payload —
    // unlike assetMcpServers/assetHostNames below, which ARE consumed as real inputs downstream
    // (AgentComponentsView's Components-tab fetch and ViolationsTab's own query, respectively).
    @Getter private int assetSkillCount;
    @Getter private List<String> assetPluginNames;
    @Getter private String assetPluginVersion;
    @Getter private String assetPluginScope;
    @Getter private String assetPluginStatus;
    @Getter private String assetPluginMarketplace;
    @Getter private String assetPluginParentAgent;
    // Service/LLM/Skill rows — which plugin bundled this component (AgenticObserveUtil.getOwningPluginName).
    @Getter private String assetOwningPluginName;
    // Plugin rows only — the MCP servers/skills this plugin bundles (reverse of assetOwningPluginName
    // above), so the plugin's own flyout can list what it brought in.
    @Getter private List<String> assetPluginMcpServers;
    @Getter private Map<String, List<Integer>> assetPluginMcpServerCollectionIds;
    @Getter private List<String> assetPluginSkills;
    @Getter private List<String> assetPluginAgents;
    @Getter private List<String> assetMcpServers;
    @Getter private Map<String, List<Integer>> assetMcpServerCollectionIds;
    // Agent rows only — collectionIds of every plugin this agent owns (assetPluginNames' groups),
    // so the Components tab can search plugin-bundled skill content, not just the agent's own collection.
    @Getter private List<Integer> assetPluginCollectionIds;
    // Count + a small capped sample only, not the full per-device list — mirrors assetSkillCount's
    // reasoning above. The flyout's Devices tab never reads this at all (it independently calls the
    // server-paginated fetchAgenticAssetDevicesPage, scoped by assetCollectionIds); the Overview tab
    // only needs the count (a "Devices: N" stat) and one sample deviceId (a risk-factor deep link);
    // and the topology graph only ever rendered a handful of device nodes usefully anyway — one
    // ReactFlow node per device stopped making sense once a group had hundreds/thousands of members.
    // A group with 1000 devices turned assetDevices into ~260KB of a ~630KB response on its own.
    @Getter private int assetDeviceCount;
    @Getter private List<BasicDBObject> assetDeviceSample;
    @Getter private String assetTagKey;
    @Getter private List<String> assetRawTagValues;
    // Inline-topology summary for the flyout's Overview tab — computed here (scoped to just this
    // asset's own collectionIds, via the same ApiCollectionsDao.fetchEndpointsInCollection STI
    // aggregation fetchAgenticComponentsPage already uses) instead of the browser fetching raw STI
    // data itself (fetchApiInfosFromSTIs) and re-deriving these same three values client-side. Only
    // one of the two is ever populated: hasInlineLlm/inlineToolNames for AI Agent rows,
    // mcpComponentCount for MCP Server/LLM rows (Skill rows need neither).
    @Getter private boolean assetHasInlineLlm;
    @Getter private List<String> assetInlineToolNames;
    @Getter private int assetMcpComponentCount;

    /**
     * Lazy per-asset detail — hostNames/collectionIds/skillCount/mcpServers/mcpServerCollectionIds/
     * devices for exactly ONE group (identified by groupKey+rowType, both already on every row
     * fetchAgenticAssetsSummary returns), fetched only when a user actually opens that asset's
     * flyout instead of being shipped for all 50 rows of every page (see toSummaryResponse()'s
     * comment, and fetchAgenticAssetsSummary's row-loop comment for the devices/groups/aiInteractions
     * split — together these are what used to make that response 16MB, mostly from raw per-device
     * breakdowns on rows with hundreds of member devices). Pays the same classifyAllGroups
     * full-account-scan cost fetchAgenticAssetsSummary already pays on every page turn (documented
     * tech debt, not made worse by this endpoint), just on-demand instead of on every list load.
     * trafficMap/riskScoreMap/userAnalysisFlatMap are the same account-wide maps already sent to
     * fetchAgenticAssetsSummary/fetchAgenticAssetDevicesPage — reused here for the same per-device
     * enrichment (risk score, last seen, AI interactions) the Overview tab's topology graph needs.
     * Ships skillCount rather than the full skill-name list (see assetSkillCount's own comment) —
     * the individual names aren't read by anything downstream of this response. Likewise ships
     * deviceCount + a small capped deviceSample rather than the full per-device list (see
     * assetDeviceCount/assetDeviceSample's own comment) — the Devices tab re-fetches its own
     * paginated device list and never reads this response's devices at all.
     */
    public String fetchAgenticAssetDetail() {
        response = new BasicDBObject();
        try {
            if (StringUtils.isBlank(groupKey) || StringUtils.isBlank(rowType)) {
                addActionError("groupKey and rowType are required");
                return ERROR.toUpperCase();
            }

            // Uses the request's own trafficMap/riskScoreMap (not empty maps) even though this
            // endpoint itself never reads GroupSummary's traffic/risk-derived fields — it shares the
            // classification cache with fetchAgenticAssetsSummary/fetchAgenticAssetsStats (which DO
            // read them), so building with empty maps here could win the rebuild race and leave every
            // other caller reading zeroed-out traffic/risk for the whole TTL window.
            Map<String, Integer> traffic = trafficMap != null ? trafficMap : Collections.emptyMap();
            Map<String, Double> risk = riskScoreMap != null ? riskScoreMap : Collections.emptyMap();
            ClassificationCacheEntry cached = getOrBuildClassification(traffic, risk, Collections.emptyMap());
            List<ApiCollection> collections = cached.collections;
            GroupSummary g = cached.groups.get(rowType + "|" + groupKey);

            if (g == null) {
                assetHostNames = Collections.emptyList();
                assetCollectionIds = Collections.emptyList();
                assetSkillCount = 0;
                assetPluginNames = Collections.emptyList();
                assetPluginVersion = null;
                assetPluginScope = null;
                assetPluginStatus = null;
                assetPluginMarketplace = null;
                assetPluginParentAgent = null;
                assetOwningPluginName = null;
                assetPluginMcpServers = Collections.emptyList();
                assetPluginMcpServerCollectionIds = Collections.emptyMap();
                assetPluginSkills = Collections.emptyList();
                assetPluginAgents = Collections.emptyList();
                assetMcpServers = Collections.emptyList();
                assetMcpServerCollectionIds = Collections.emptyMap();
                assetPluginCollectionIds = Collections.emptyList();
                assetDeviceCount = 0;
                assetDeviceSample = Collections.emptyList();
                assetTagKey = null;
                assetRawTagValues = Collections.emptyList();
                assetHasInlineLlm = false;
                assetInlineToolNames = Collections.emptyList();
                assetMcpComponentCount = 0;
                return SUCCESS.toUpperCase();
            }

            assetHostNames = new ArrayList<>(g.hostNames);
            assetCollectionIds = g.collectionIds;
            assetSkillCount = g.skillNames.size();
            assetPluginNames = new ArrayList<>(g.pluginNames);
            assetPluginVersion = g.pluginVersion;
            assetPluginScope = g.pluginScope;
            assetPluginStatus = g.pluginStatus;
            assetPluginMarketplace = g.pluginMarketplace;
            assetPluginParentAgent = g.pluginParentAgent;
            assetMcpServers = new ArrayList<>(g.serviceNames);
            assetMcpServerCollectionIds = new HashMap<>();
            for (Map.Entry<String, Set<Integer>> e : g.serviceCollectionIds.entrySet()) {
                assetMcpServerCollectionIds.put(e.getKey(), new ArrayList<>(e.getValue()));
            }
            assetTagKey = g.tagKey;
            assetRawTagValues = new ArrayList<>(g.rawTagValues);
            assetOwningPluginName = g.owningPluginName;

            Set<Integer> pluginCollectionIdsOut = new LinkedHashSet<>();
            for (String pluginKey : g.pluginNames) {
                GroupSummary pluginGroup = cached.groups.get("plugin|" + pluginKey);
                if (pluginGroup != null) pluginCollectionIdsOut.addAll(pluginGroup.collectionIds);
            }
            assetPluginCollectionIds = new ArrayList<>(pluginCollectionIdsOut);

            // Plugin rows only — reverse of assetOwningPluginName: which MCP servers/skills does
            // THIS plugin bundle. Servers are found by scanning already-classified "service"/"llm"
            // GroupSummary rows (never raw collections) for one whose own collection carries this
            // plugin's plugin-name tag — reusing classifyAllGroups' classification instead of
            // re-deriving it here is what keeps this from matching a plugin-name tag that landed on
            // an unrelated collection (e.g. a shared agent collection carrying stale plugin-name
            // tags from an older, since-fixed ingestion bug — see plugin-tag-attribution-and-flyout.md).
            // Excluded by id (own collectionIds), not by isPluginCollection — a plugin can bundle a
            // server sharing its own name (tagPluginMCPServers then re-tags the plugin's own
            // collection), and plugin-name is still the honest owner there too.
            assetPluginMcpServers = Collections.emptyList();
            assetPluginMcpServerCollectionIds = Collections.emptyMap();
            assetPluginSkills = Collections.emptyList();
            assetPluginAgents = Collections.emptyList();

            Map<Integer, ApiCollection> byId = new HashMap<>();
            for (ApiCollection c : collections) byId.put(c.getId(), c);

            if ("plugin".equals(rowType)) {
                Set<Integer> ownCollectionIds = new HashSet<>(g.collectionIds);
                List<String> mcpServers = new ArrayList<>();
                Map<String, List<Integer>> mcpServerCollectionIdsOut = new HashMap<>();
                for (GroupSummary other : cached.groups.values()) {
                    if (!"service".equals(other.rowType) && !"llm".equals(other.rowType)) continue;
                    boolean ownedByThisPlugin = false;
                    for (Integer id : other.collectionIds) {

                        ApiCollection c = byId.get(id);
                        // Match by g.name (bare plugin name — a server's plugin-name tag never
                        // carries the owner-agent suffix), NOT groupKey, which is the compound
                        // "pluginName|ownerAgent" key since the same plugin name can be installed
                        // under multiple agents.
                        if (c != null && g.name.equals(AgenticObserveUtil.getOwningPluginName(c))) {
                            ownedByThisPlugin = true;
                            break;
                        }
                    }
                    if (ownedByThisPlugin) {
                        mcpServers.add(other.name);
                        mcpServerCollectionIdsOut.put(other.name, new ArrayList<>(other.collectionIds));
                    }
                }
                assetPluginMcpServers = mcpServers;
                assetPluginMcpServerCollectionIds = mcpServerCollectionIdsOut;
                if (!g.collectionIds.isEmpty()) {
                    List<ApiInfo> pluginApiInfos = ApiInfoDao.instance.findAll(Filters.in(SingleTypeInfo._COLLECTION_IDS, g.collectionIds));
                    Set<String> skillNames = new LinkedHashSet<>();
                    Set<String> agentNames = new LinkedHashSet<>();
                    for (ApiInfo info : pluginApiInfos) {
                        if (info.getId() == null) continue;
                        String skillName = skillNameFromUrl(info.getId().getUrl());
                        if (skillName != null) skillNames.add(skillName);
                        String agentName = agentNameFromUrl(info.getId().getUrl());
                        if (agentName != null) agentNames.add(agentName);
                    }
                    assetPluginSkills = new ArrayList<>(skillNames);
                    assetPluginAgents = new ArrayList<>(agentNames);
                }
            }

            Map<String, Integer> userAnalysis = userAnalysisFlatMap != null ? userAnalysisFlatMap : Collections.emptyMap();
            // Count comes from the same accumulateDevices() map buildDevicesForGroup uses internally —
            // that per-collection accumulation pass can't be skipped for the count either way, but we
            // avoid the more costly per-device toResponse()/serialization step for anything past the
            // small sample cap below (that's what actually bloated the payload, not the accumulation).
            Map<String, DeviceAcc> deviceAccs = accumulateDevices(g.collectionIds, byId, traffic, risk, userAnalysis);
            assetDeviceCount = deviceAccs.size();
            List<BasicDBObject> deviceSample = new ArrayList<>();
            int deviceSampleCap = 6;
            for (DeviceAcc d : deviceAccs.values()) {
                if (deviceSample.size() >= deviceSampleCap) break;
                deviceSample.add(d.toResponse());
            }
            assetDeviceSample = deviceSample;

            // Scoped to just THIS asset's own collectionIds (never account-wide) — same STI
            // aggregation fetchAgenticComponentsPage already runs when the Components tab opens, just
            // a cheap boolean/name-set pass over it instead of the full skill/tool/resource/prompt
            // breakdown. "agent" rows want hasInlineLlm/inlineToolNames; "service"/"llm" rows want
            // mcpComponentCount; "skill" rows need neither, so skip the query entirely for those.
            assetHasInlineLlm = false;
            assetInlineToolNames = Collections.emptyList();
            assetMcpComponentCount = 0;
            // "skill" and "plugin" rows want neither, so they skip the expensive STI query below.
            boolean wantsInlineTopology = "agent".equals(g.rowType);
            boolean wantsComponentCount = "service".equals(g.rowType) || "llm".equals(g.rowType);
            if ((wantsInlineTopology || wantsComponentCount) && !g.collectionIds.isEmpty()) {
                List<BasicDBObject> stiRows = ApiCollectionsDao.fetchEndpointsInCollection(
                        Filters.in(SingleTypeInfo._COLLECTION_IDS, g.collectionIds), 0, -1, DELTA_PERIOD_VALUE);
                if (wantsInlineTopology) {
                    boolean hasLlm = false;
                    Set<String> toolNames = new LinkedHashSet<>();
                    for (BasicDBObject sti : stiRows) {
                        Object idObj = sti.get("_id");
                        if (!(idObj instanceof BasicDBObject)) continue;
                        String url = ((BasicDBObject) idObj).getString("url");
                        if (isAgentLlmMessagesUrl(url)) hasLlm = true;
                        if (isAgentBuiltinToolUrl(url)) toolNames.add(mcpDisplayName(url));
                    }
                    assetHasInlineLlm = hasLlm;
                    assetInlineToolNames = new ArrayList<>(toolNames);
                } else {
                    // Distinct Tool/Resource/Prompt names (Skill/Server buckets excluded) — mirrors
                    // buildMcpComponentsFromStis' tools+resources+prompts count, url-based fallback
                    // bucketing only (no apiInfo/audit — this is a count, not a detailed listing).
                    Set<String> componentKeys = new HashSet<>();
                    for (BasicDBObject sti : stiRows) {
                        Object idObj = sti.get("_id");
                        if (!(idObj instanceof BasicDBObject)) continue;
                        BasicDBObject stiId = (BasicDBObject) idObj;
                        String method = stiId.getString("method");
                        String url = stiId.getString("url");
                        if (method == null || url == null) continue;
                        String bucket = bucketFromUrl(url);
                        if ("Tool".equals(bucket) || "Resource".equals(bucket) || "Prompt".equals(bucket)) {
                            componentKeys.add(bucket + ":" + mcpDisplayName(url));
                        }
                    }
                    assetMcpComponentCount = componentKeys.size();
                }
            }

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching agentic asset detail: " + e.getMessage());
            addActionError("Error fetching agentic asset detail: " + e.getMessage());
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
            long tStatsStart = System.currentTimeMillis();
            Map<String, Integer> traffic = getOrComputeTrafficMap();
            Map<String, Double> risk = getOrComputeRiskScoreMap();
            Map<String, Map<String, Integer>> violations = violationsByCollectionId != null ? violationsByCollectionId : Collections.emptyMap();

            // This endpoint doesn't send/read sensitiveMap (no "Sensitive data" breakdown on the
            // stats cards) — shares the classification cache with fetchAgenticAssetsSummary anyway,
            // so if THIS call happens to win the rebuild race, sensitiveTypes will be empty for every
            // group until the cache's short TTL naturally expires. Accepted as a minor, self-healing
            // edge case (see AgenticObserveAction.getOrBuildClassification's own doc) rather than
            // plumbing sensitiveMap through a card that never uses it.
            ClassificationCacheEntry cached = getOrBuildClassification(traffic, risk, Collections.emptyMap());
            List<ApiCollection> collections = cached.collections;
            Map<String, GroupSummary> groups = cached.groups;
            Collection<GroupSummary> counted = groups.values();
            if (startTimestamp > 0) {
                int end = endTimestamp > 0 ? endTimestamp : (int) (System.currentTimeMillis() / 1000);
                List<GroupSummary> filtered = new ArrayList<>(counted);
                filtered.removeIf(g -> g.maxTrafficTimestamp < startTimestamp || g.maxTrafficTimestamp > end);
                counted = filtered;
            }

            Map<String, Integer> countsByType = new LinkedHashMap<>();
            List<Integer> assetTs = new ArrayList<>();
            for (GroupSummary g : counted) {
                countsByType.merge(g.clientType, 1, Integer::sum);
                assetTs.add(g.maxTrafficTimestamp);
            }
            response.put("totalAssets", counted.size());
            response.put("countsByType", countsByType);

            // Legacy Endpoints.jsx's "Total endpoints" summary card — unique device count across the
            // whole account (not deduped per-group, since one device can belong to several groups).
            // collections is already in memory for classification above; one more cheap pass.
            Set<String> allEndpointIds = new HashSet<>();
            for (ApiCollection c : collections) {
                if (c.isDeactivated()) continue;
                String endpointId = AgenticObserveUtil.extractEndpointId(c.getHostName());
                if (endpointId != null) allEndpointIds.add(endpointId);
            }
            response.put("totalEndpoints", allEndpointIds.size());

            // Asset-count trend for the "Agentic Assets" card — same buildWindowSlots/cumulativeCounts
            // pattern already used for Endpoints/Users-and-Devices; bucketing ~800 group timestamps is
            // cheap, no extra query. Mirrors the pre-rebuild client-side cumulativeByMonth(..., r =>
            // r.lastSeenEpoch, ...) exactly — bucketed by each asset's own last-traffic timestamp.
            int nowSec = (int) (System.currentTimeMillis() / 1000);
            int end = endTimestamp > 0 ? endTimestamp : nowSec;
            List<MonthSlot> slots = buildWindowSlots(startTimestamp, endTimestamp, earliestTs(assetTs));
            List<String> monthLabels = new ArrayList<>();
            for (MonthSlot s : slots) monthLabels.add(s.label);
            int[] assetCounts = cumulativeCounts(assetTs, slots, end);
            response.put("monthLabels", monthLabels);
            response.put("assetSparkline", assetCounts);
            response.put("assetDelta", windowDelta(assetCounts));

            // "Top Assets with Violations" ranking — per-group totals from the violationsByCollectionId
            // map the frontend already fetched once at mount (aggregateViolationCountsByCollectionId),
            // summed over each group's own collectionIds. No new data source; top-N of what's already
            // available. Computed before the trend fetches below so we know which 5 assets need one.
            // Skill rows are deliberately excluded from this specific ranking — it's meant to surface
            // which agent/device/LLM is attracting the most attacks (an actionable "which asset to
            // triage" leaderboard), and with ~770 skills per account, mixing in skill-name entries
            // (which have their own accurate count elsewhere — the grid row, the flyout) would drown
            // out the agent/device signal this widget exists to show.
            List<Map.Entry<GroupSummary, Integer>> violRanked = new ArrayList<>();
            for (GroupSummary g : groups.values()) {
                if ("skill".equals(g.rowType)) continue;
                int groupTotal = 0;
                for (Integer cid : g.collectionIds) {
                    Map<String, Integer> v = violations.get(String.valueOf(cid));
                    if (v == null) continue;
                    groupTotal += v.getOrDefault("critical", 0) + v.getOrDefault("high", 0)
                            + v.getOrDefault("medium", 0) + v.getOrDefault("low", 0);
                }
                if (groupTotal > 0) violRanked.add(new AbstractMap.SimpleEntry<>(g, groupTotal));
            }
            violRanked.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
            List<Map.Entry<GroupSummary, Integer>> topViolPage = violRanked.subList(0, Math.min(5, violRanked.size()));

            // Violations trend for the "Violations" card (overall) + one per top-5 asset, scoped via
            // host_filter — each is its own HTTP round-trip to the threat-detection-backend
            // (fetchViolationsMonthlyTotals), and these 6 calls were previously issued one after
            // another. That sequential chain — NOT classifyAllGroups, already cached above — is what
            // actually dominated this endpoint's latency (measured 8s+ on Atlas Scale Test even with a
            // warm classification cache). They're independent of each other, so fire them concurrently
            // and only pay the slowest one instead of the sum of all six.
            List<Integer> monthBoundaries = new ArrayList<>();
            for (MonthSlot s : slots) monthBoundaries.add(s.boundary);
            CompletableFuture<List<Integer>> overallTrendFuture = CompletableFuture.supplyAsync(
                    () -> fetchViolationsMonthlyTotals(startTimestamp, endTimestamp, monthBoundaries, null));
            List<CompletableFuture<List<Integer>>> topViolTrendFutures = new ArrayList<>();
            for (Map.Entry<GroupSummary, Integer> entry : topViolPage) {
                GroupSummary g = entry.getKey();
                topViolTrendFutures.add(CompletableFuture.supplyAsync(() -> fetchViolationsMonthlyTotals(
                        startTimestamp, endTimestamp, monthBoundaries, new ArrayList<>(g.hostNames))));
            }

            List<Integer> monthlyViolations = overallTrendFuture.join();
            int[] violationsCounts = cumulativeFromMonthlyCounts(monthlyViolations, slots.size());
            response.put("violationsSparkline", violationsCounts);
            response.put("violationsDelta", windowDelta(violationsCounts));

            List<BasicDBObject> topViol = new ArrayList<>();
            for (int i = 0; i < topViolPage.size(); i++) {
                Map.Entry<GroupSummary, Integer> entry = topViolPage.get(i);
                GroupSummary g = entry.getKey();
                BasicDBObject row = new BasicDBObject();
                row.put("id", g.rowType + "-" + g.groupKey);
                row.put("name", g.name);
                row.put("type", g.clientType);
                row.put("violations", entry.getValue());
                List<Integer> assetMonthly = topViolTrendFutures.get(i).join();
                row.put("sparkline", cumulativeFromMonthlyCounts(assetMonthly, slots.size()));
                topViol.add(row);
            }
            response.put("topAssetsWithViolations", topViol);

            // "Top Used Applications" — per-group AI-interaction totals from userAnalysisFlatMap (see
            // constants.js's buildUserAnalysisFlatMap). UserAnalysisData's serviceId is a readable
            // client name ("claudecli", "codexcli", ...) matching the SECOND segment of the
            // collection's own hostname (<deviceId>.<serviceId>.<host>) — mirrors
            // analysisKeysForCollection's hostname-segment candidates, not module_info's own id/name
            // (which never actually matches UserAnalysisData's key scheme).
            Map<String, Integer> userAnalysis = userAnalysisFlatMap != null ? userAnalysisFlatMap : Collections.emptyMap();
            List<BasicDBObject> topApps = new ArrayList<>();
            for (GroupSummary g : groups.values()) {
                int groupTotal = 0;
                Set<String> seenKeys = new HashSet<>();
                for (String hostName : g.hostNames) {
                    String[] parts = DOT_SPLIT.split(hostName);
                    if (parts.length < 2) continue;
                    String deviceId = parts[0];
                    List<String> candidates = new ArrayList<>();
                    candidates.add(parts[1]);
                    if (parts.length >= 3) candidates.add(String.join(".", Arrays.asList(parts).subList(2, parts.length)));
                    for (String serviceId : candidates) {
                        String key = serviceId + "|" + deviceId;
                        if (!seenKeys.add(key)) continue;
                        groupTotal += userAnalysis.getOrDefault(key, 0);
                    }
                }
                if (groupTotal > 0) {
                    BasicDBObject row = new BasicDBObject();
                    row.put("id", g.rowType + "-" + g.groupKey);
                    row.put("name", g.name);
                    row.put("type", g.clientType);
                    row.put("aiInteractions", groupTotal);
                    topApps.add(row);
                }
            }
            topApps.sort((a, b) -> Integer.compare(b.getInt("aiInteractions"), a.getInt("aiInteractions")));
            response.put("topUsedApplications", topApps.subList(0, Math.min(5, topApps.size())));

            loggerMaker.warnAndAddToDb("[fetchAgenticAssetsStats-timing] TOTAL=" + (System.currentTimeMillis() - tStatsStart) + "ms");
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
        // Generic device tags (AgenticUsers.deviceTags), resolved via this group's owner username.
        List<Map<String, String>> tags = Collections.emptyList();
        int nonSkillCollectionsCount = 0;
        final Set<String> uniqueSkillNames = new HashSet<>();
        final Set<String> uniquePluginNames = new HashSet<>();
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
            // Plugins have their own collection, so nonSkillCollectionsCount above already counted it —
            // this set is for the "N plugins" badge only, never added to the asset total.
            String pluginName = AgenticObserveUtil.getPluginName(c);
            if (pluginName != null) uniquePluginNames.add(pluginName.toLowerCase(Locale.ROOT));
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
            // Keep in sync with fetchUsersAndDevicesStats' own copy of this sum, or the header tile and
            // the rows below it disagree.
            row.put("endpointsCount", isUserGroup
                    ? nonSkillCollectionsCount + uniqueSkillNames.size()
                    : hostNames.size());
            row.put("sensitiveInRespTypes", new ArrayList<>(sensitiveTypes));
            row.put("riskScore", maxRiskScore);
            row.put("lastSeenEpoch", maxTrafficTimestamp);
            row.put("tags", tags);
            // Bare tag keys only — matches constants.js's buildTagFilterValues ("filtering is by
            // tag key only... regardless of value") so the Tags column's Set Filter/dropdown options
            // and this row's own filter-membership value stay derived the same way client- and
            // server-side.
            Set<String> tagKeys = new LinkedHashSet<>();
            for (Map<String, String> t : tags) {
                String k = t != null ? t.get(DeviceTag.KEY) : null;
                if (StringUtils.isNotBlank(k)) tagKeys.add(k);
            }
            row.put("tagFilterValues", new ArrayList<>(tagKeys));
            row.put("hasPersonalAccount", hasPersonalAccount);
            row.put("hasLocalMcpServer", hasLocalMcpServer);
            row.put("hasMisconfiguredConfig", hasMisconfiguredConfig);
            row.put("uniqueSkillNames", new ArrayList<>(uniqueSkillNames));
            row.put("uniquePluginNames", new ArrayList<>(uniquePluginNames));
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
        return classifyHostGroupedRows(collections, groupBy, traffic, risk, sensitive, usernames, userMeta,
                deviceMeta, violationsByCollectionId, excludeConnectorIngested, Collections.emptyMap());
    }

    private Map<String, HostGroupSummary> classifyHostGroupedRows(List<ApiCollection> collections, String groupBy,
            Map<String, Integer> traffic, Map<String, Double> risk, Map<String, List<String>> sensitive,
            Map<String, String> usernames, Map<String, Map<String, String>> userMeta,
            Map<String, Map<String, String>> deviceMeta, Map<String, Map<String, Integer>> violationsByCollectionId,
            boolean excludeConnectorIngested, Map<String, List<Map<String, String>>> tagsByUser) {
        boolean isUserGroup = "user".equals(groupBy);
        Map<String, List<Map<String, String>>> tags = tagsByUser != null ? tagsByUser : Collections.emptyMap();
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
                if (isUserGroup) {
                    gs.tags = tags.getOrDefault(fKey, Collections.emptyList());
                }
                if (!isUserGroup && deviceMeta != null) {
                    Map<String, String> meta = deviceMeta.get(fKey);
                    if (meta != null) {
                        gs.os = meta.get("os");
                        gs.username = StringUtils.isNotBlank(meta.get("username")) ? meta.get("username") : "-";
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
            // Device tags are the resolved owner's device tags (AgenticUsers.deviceTags is keyed by
            // username, not deviceId) — re-derive whenever the username above may have just changed.
            if (!isUserGroup && !"-".equals(g.username)) {
                g.tags = tags.getOrDefault(g.username, Collections.emptyList());
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
            Map<String, List<Map<String, String>>> tagsByUser = tagsByUsername != null ? tagsByUsername : Collections.emptyMap();

            List<ApiCollection> collections = ApiCollectionsDao.instance.findAll(
                    Filters.empty(),
                    Projections.include(ApiCollection.ID, ApiCollection.HOST_NAME, ApiCollection.TAGS_STRING, ApiCollection.SKILLS, ApiCollection.START_TS)
            );

            Map<String, HostGroupSummary> groups = classifyHostGroupedRows(collections, groupBy, traffic, risk, sensitive, usernames, userMeta, null, null, false, tagsByUser);
            List<HostGroupSummary> all = new ArrayList<>(groups.values());

            if (StringUtils.isNotBlank(queryValue)) {
                String q = queryValue.toLowerCase(Locale.ROOT);
                all.removeIf(g -> g.groupName == null || !g.groupName.toLowerCase(Locale.ROOT).contains(q));
            }

            // AG Grid column filters (agSetColumnFilter on "tags", agTextColumnFilter on "groupName")
            // — extractFilterModel (AgGridTable.jsx) keys these by colDef field name.
            if (filters != null) {
                List<String> nameFilter = filters.get("groupName");
                if (nameFilter != null && !nameFilter.isEmpty() && StringUtils.isNotBlank(nameFilter.get(0))) {
                    String q = nameFilter.get(0).toLowerCase(Locale.ROOT);
                    all.removeIf(g -> g.groupName == null || !g.groupName.toLowerCase(Locale.ROOT).contains(q));
                }
                // Devices tab's "User" filter (choices sourced from fetchUsersAndDevicesStats'
                // usernames list) — matches each device row's already-resolved owner username.
                List<String> usernameFilter = filters.get("username");
                if (usernameFilter != null && !usernameFilter.isEmpty()) {
                    Set<String> allowed = new HashSet<>(usernameFilter);
                    all.removeIf(g -> !allowed.contains(g.username));
                }
                // Generic device-tag filter — matches by tag KEY only (buildTagFilterValues'
                // semantics: "group" matches any row with a "group" tag, regardless of value).
                List<String> tagsFilter = filters.get("tags");
                if (tagsFilter != null && !tagsFilter.isEmpty()) {
                    Set<String> allowedKeys = new HashSet<>(tagsFilter);
                    all.removeIf(g -> g.tags.stream().noneMatch(t -> t != null && allowedKeys.contains(t.get(DeviceTag.KEY))));
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
     * Tab-header counts ("Users (1234)" / "Devices (567)"), distinct device-tag keys for the Tags
     * filter/"Edit device tags" modal, and each tab's "Agentic assets" total (sum of endpointsCount
     * across every row — needs the full per-group classification, not just distinct-key counting, so
     * this calls classifyHostGroupedRows for both groupBy modes; same order of cost as
     * fetchUsersAndDevicesSummary itself, just without the pagination slice).
     */
    public String fetchUsersAndDevicesStats() {
        response = new BasicDBObject();
        try {
            Map<String, Integer> traffic = trafficMap != null ? trafficMap : Collections.emptyMap();
            Map<String, Double> risk = riskScoreMap != null ? riskScoreMap : Collections.emptyMap();
            Map<String, String> usernames = usernameMap != null ? usernameMap : Collections.emptyMap();
            Map<String, Map<String, String>> userMeta = userMetadataMap != null ? userMetadataMap : Collections.emptyMap();
            Map<String, List<Map<String, String>>> tagsByUser = tagsByUsername != null ? tagsByUsername : Collections.emptyMap();
            List<ApiCollection> collections = ApiCollectionsDao.instance.findAll(
                    Filters.empty(),
                    Projections.include(ApiCollection.ID, ApiCollection.HOST_NAME, ApiCollection.TAGS_STRING, ApiCollection.SKILLS, ApiCollection.START_TS)
            );

            Map<String, HostGroupSummary> userGroups = classifyHostGroupedRows(collections, "user", traffic, risk,
                    Collections.emptyMap(), usernames, userMeta, null, null, false, tagsByUser);
            Map<String, HostGroupSummary> deviceGroups = classifyHostGroupedRows(collections, "device", traffic, risk,
                    Collections.emptyMap(), usernames, Collections.emptyMap(), null, null, false, tagsByUser);

            long usersAgenticAssetsTotal = 0;
            for (HostGroupSummary g : userGroups.values()) {
                // Mirrors HostGroupSummary.toRow's endpointsCount — change both together.
                usersAgenticAssetsTotal += g.nonSkillCollectionsCount + g.uniqueSkillNames.size();
            }
            long devicesAgenticAssetsTotal = 0;
            for (HostGroupSummary g : deviceGroups.values()) {
                devicesAgenticAssetsTotal += g.hostNames.size();
            }

            // Distinct device-owner usernames — feeds the Devices tab's "User" filter (GithubServerTable
            // choices, UsersAndDevices.jsx). deviceGroups already resolved each device's username
            // (Endpoint Shield first, device-module fallback), so this is free/complete, not a guess.
            Set<String> deviceUsernames = new HashSet<>();
            for (HostGroupSummary g : deviceGroups.values()) {
                if (StringUtils.isNotBlank(g.username) && !"-".equals(g.username)) deviceUsernames.add(g.username);
            }
            List<String> deviceUsernameList = new ArrayList<>(deviceUsernames);
            Collections.sort(deviceUsernameList);

            // Distinct device-tag keys across every known user/device — feeds the Tags column's Set
            // Filter dropdown (both tabs) and the "Edit device tags" modal's key autocomplete.
            Set<String> tagKeys = new TreeSet<>();
            for (HostGroupSummary g : userGroups.values()) {
                for (Map<String, String> t : g.tags) {
                    if (t != null && StringUtils.isNotBlank(t.get(DeviceTag.KEY))) tagKeys.add(t.get(DeviceTag.KEY));
                }
            }
            for (HostGroupSummary g : deviceGroups.values()) {
                for (Map<String, String> t : g.tags) {
                    if (t != null && StringUtils.isNotBlank(t.get(DeviceTag.KEY))) tagKeys.add(t.get(DeviceTag.KEY));
                }
            }

            response.put("usersCount", userGroups.size());
            response.put("devicesCount", deviceGroups.size());
            response.put("usersAgenticAssetsTotal", usersAgenticAssetsTotal);
            response.put("devicesAgenticAssetsTotal", devicesAgenticAssetsTotal);
            response.put("usernames", deviceUsernameList);
            response.put("tagKeys", new ArrayList<>(tagKeys));
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

    // Sorts the flyout's own "Agentic Assets" tab / the main grid's tree-expand children rows —
    // reads riskScore/violations/skillCount straight off the row (buildDeviceChildren already
    // computed them), no separate lookup map needed.
    private static Comparator<BasicDBObject> buildDeviceChildComparator(String key) {
        if ("riskScore".equals(key)) {
            return Comparator.comparingDouble(r -> {
                Object v = r.get("riskScore");
                return v == null ? 0.0 : ((Number) v).doubleValue();
            });
        } else if ("violations".equals(key)) {
            return Comparator.comparingInt(r -> {
                Object v = r.get("violations");
                if (!(v instanceof BasicDBObject)) return 0;
                BasicDBObject vo = (BasicDBObject) v;
                return vo.getInt("critical", 0) + vo.getInt("high", 0) + vo.getInt("medium", 0) + vo.getInt("low", 0);
            });
        } else if ("skillCount".equals(key)) {
            return Comparator.comparingInt(r -> ((Number) r.getOrDefault("skillCount", 0)).intValue());
        } else if ("pluginCount".equals(key)) {
            return Comparator.comparingInt(r -> ((Number) r.getOrDefault("pluginCount", 0)).intValue());
        }
        return Comparator.comparing((BasicDBObject r) -> String.valueOf(r.get("endpoint")), String.CASE_INSENSITIVE_ORDER);
    }

    // Mirrors agenticPageBuilders.js's child row shape within one device, grouped by service name
    // (toChildPathKey(extractServiceName(hostName))) rather than across the whole account.
    private BasicDBObject buildDeviceChildren(String deviceId, List<ApiCollection> collections,
            Map<String, Integer> traffic, Map<String, Double> risk, Map<String, Map<String, Integer>> violationsByCollection) {
        Map<String, BasicDBObject> children = new LinkedHashMap<>();
        Map<String, double[]> childRisk = new HashMap<>(); // pathKey -> {riskScore, lastTraffic}
        Map<String, int[]> childViolations = new HashMap<>();
        Map<String, Integer> childSkillCount = new HashMap<>();
        // Collection ids per row, so the frontend can deep-link to a row's own component page.
        Map<String, List<Integer>> childCollectionIds = new HashMap<>();
        // Plugins live in their own child row (own collection), not embedded in the agent's — so
        // "how many plugins does this agent have" is answered by matching sibling plugin children
        // under the same device to this agent's own owner tag (mcp-client/ai-agent value).
        Map<String, Integer> pluginCountByOwnerTag = new HashMap<>();
        Map<String, String> ownerTagByPathKey = new HashMap<>();

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
            String childType = AgenticObserveUtil.getTypeFromCollection(c);
            BasicDBObject child = children.computeIfAbsent(pathKey, k -> {
                BasicDBObject row = new BasicDBObject();
                row.put("path", Arrays.asList(deviceId, k));
                row.put("id", "device-" + deviceId + "-" + k);
                row.put("endpoint", McpClientRegistry.formatDisplayName(fServiceName));
                row.put("rawServiceName", fServiceName);
                row.put("type", childType);
                return row;
            });
            double[] rt = childRisk.computeIfAbsent(pathKey, k -> new double[2]);
            if (collRisk > rt[0]) rt[0] = collRisk;
            if (collTraffic > rt[1]) rt[1] = collTraffic;
            childSkillCount.merge(pathKey, skillCount, Math::max);
            childCollectionIds.computeIfAbsent(pathKey, k -> new ArrayList<>()).add(c.getId());
            if (AgenticObserveUtil.CLIENT_TYPE_AI_AGENT.equals(childType)) {
                CollectionTags ownerTag = AgenticObserveUtil.findAssetTag(c);
                if (ownerTag != null) ownerTagByPathKey.put(pathKey, ownerTag.getValue());
            }
            String pluginOwnerValue = AgenticObserveUtil.CLIENT_TYPE_PLUGIN.equals(childType)
                    ? (AgenticObserveUtil.findAssetTag(c) != null ? AgenticObserveUtil.findAssetTag(c).getValue() : null)
                    : null;
            if (pluginOwnerValue != null) {
                pluginCountByOwnerTag.merge(pluginOwnerValue, 1, Integer::sum);
            }
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
            row.put("collectionIds", childCollectionIds.getOrDefault(pathKey, Collections.emptyList()));
            String ownerTagValue = ownerTagByPathKey.get(pathKey);
            int pluginCount = ownerTagValue != null ? pluginCountByOwnerTag.getOrDefault(ownerTagValue, 0) : 0;
            if (pluginCount > 0) row.put("pluginCount", pluginCount);
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
            Map<String, List<Map<String, String>>> tagsByUser = tagsByUsername != null ? tagsByUsername : Collections.emptyMap();

            List<ApiCollection> collections = ApiCollectionsDao.instance.findAll(
                    Filters.empty(),
                    Projections.include(ApiCollection.ID, ApiCollection.HOST_NAME, ApiCollection.TAGS_STRING, ApiCollection.SKILLS, ApiCollection.START_TS)
            );

            if (StringUtils.isNotBlank(parentDeviceId)) {
                BasicDBObject childrenResp = buildDeviceChildren(parentDeviceId, collections, traffic, risk, violations);
                @SuppressWarnings("unchecked")
                List<BasicDBObject> childRows = (List<BasicDBObject>) childrenResp.get("rows");

                if (StringUtils.isNotBlank(queryValue)) {
                    String q = queryValue.toLowerCase(Locale.ROOT);
                    childRows.removeIf(r -> {
                        Object name = r.get("endpoint");
                        return name == null || !name.toString().toLowerCase(Locale.ROOT).contains(q);
                    });
                }

                Comparator<BasicDBObject> cmp = buildDeviceChildComparator(sortKey);
                if (sortOrder < 0) cmp = cmp.reversed();
                childRows.sort(cmp);

                int childTotal = childRows.size();
                int effectiveLimit = limit > 0 ? Math.min(limit, 500) : 20;
                int from = Math.max(0, skip);
                int to = Math.min(childRows.size(), from + effectiveLimit);
                List<BasicDBObject> page = from < to ? childRows.subList(from, to) : Collections.emptyList();

                response.put("rows", page);
                response.put("total", childTotal);
                return SUCCESS.toUpperCase();
            }

            Map<String, HostGroupSummary> groups = classifyHostGroupedRows(collections, "device", traffic, risk,
                    Collections.emptyMap(), usernames, Collections.emptyMap(), deviceMeta, violations, true, tagsByUser);
            List<HostGroupSummary> all = new ArrayList<>(groups.values());

            if (StringUtils.isNotBlank(queryValue)) {
                String q = queryValue.toLowerCase(Locale.ROOT);
                all.removeIf(g -> {
                    String name = StringUtils.isNotBlank(g.username) && !"-".equals(g.username) ? g.username : g.groupName;
                    return name == null || !name.toLowerCase(Locale.ROOT).contains(q);
                });
            }

            // AG Grid column filters (agSetColumnFilter on "os"/"tags") — extractFilterModel
            // (AgGridTable.jsx) keys these by colDef field name, matching DEVICE_COL_DEFS in
            // DeviceEndpoints.jsx. A key present with an empty list means the user unchecked every
            // value, which must match zero rows (real Set Filter semantics) — containsKey (not
            // null-and-nonempty) is what distinguishes that from "filter untouched".
            if (filters != null) {
                if (filters.containsKey("deviceId")) {
                    Set<String> allowed = new HashSet<>(filters.get("deviceId"));
                    all.removeIf(g -> !allowed.contains(g.groupKey));
                }
                if (filters.containsKey("os")) {
                    Set<String> allowed = new HashSet<>(filters.get("os"));
                    all.removeIf(g -> !allowed.contains(g.os));
                }
                if (filters.containsKey("tags")) {
                    Set<String> allowedKeys = new HashSet<>(filters.get("tags"));
                    all.removeIf(g -> g.tags.stream().noneMatch(t -> t != null && allowedKeys.contains(t.get(DeviceTag.KEY))));
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
    // chunk of the cost this whole rebuild moved off the client. Violations delta/sparkline (below,
    // deltaViolations/sparklines.violations) comes from fetchViolationsMonthlyTotals's cheap $bucket
    // aggregation on threat-detection-backend, not the raw up-to-100k-row event fetch — no full
    // violation history is pulled here.

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

    // Running total from already-bucketed per-month counts (as opposed to cumulativeCounts, which
    // buckets raw per-item timestamps) — the threat-detection-backend does the bucketing, this just
    // accumulates it into the same cumulative-trend shape the other sparklines use.
    private static int[] cumulativeFromMonthlyCounts(List<Integer> monthlyCounts, int n) {
        int[] counts = new int[n];
        int acc = 0;
        for (int i = 0; i < n; i++) {
            acc += (monthlyCounts != null && i < monthlyCounts.size() && monthlyCounts.get(i) != null) ? monthlyCounts.get(i) : 0;
            counts[i] = acc;
        }
        return counts;
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
            // Server-side $bucket aggregation in threat-detection-backend — one more cheap call, no
            // raw violation events fetched — mirrors the endpoint/browser/user trends above.
            List<Integer> monthBoundaries = new ArrayList<>();
            for (MonthSlot s : slots) monthBoundaries.add(s.boundary);
            List<Integer> monthlyViolations = fetchViolationsMonthlyTotals(startTimestamp, endTimestamp, monthBoundaries, null);
            int[] violationsCounts = cumulativeFromMonthlyCounts(monthlyViolations, slots.size());

            BasicDBObject sparklines = new BasicDBObject();
            sparklines.put("endpoints", endpointCounts); sparklines.put("browsers", browserCounts); sparklines.put("users", userCounts);
            sparklines.put("violations", violationsCounts);

            // Distinct device ids for the "Endpoint" column's Set Filter on DeviceEndpoints.jsx —
            // deviceFirstSeen.keySet() is the same extractEndpointId(hostName) grouping key the grid's
            // own rows use, so this is guaranteed complete/consistent (not an approximation), and free
            // since it's already computed above for the trend charts.
            List<String> deviceIds = new ArrayList<>(deviceFirstSeen.keySet());
            Collections.sort(deviceIds);

            response.put("deviceCount", endpointDeviceIds.size());
            response.put("browserDeviceCount", browserDeviceIds.size());
            response.put("totalUsers", userFirstSeen.size());
            response.put("deviceIds", deviceIds);
            response.put("monthLabels", monthLabels);
            response.put("osTrend", osTrend);
            response.put("browserTrend", browserTrend);
            response.put("sparklines", sparklines);
            response.put("deltaEndpoints", windowDelta(endpointCounts));
            response.put("deltaBrowsers", windowDelta(browserCounts));
            response.put("deltaUsers", windowDelta(userCounts));
            response.put("deltaViolations", windowDelta(violationsCounts));
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching device endpoints stats: " + e.getMessage());
            addActionError("Error fetching device endpoints stats: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }
}
