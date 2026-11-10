package com.akto.service.atlas;

import com.akto.action.AgenticObserveAction;
import com.akto.action.threat_detection.HostSeverityCount;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.McpAuditInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.DeviceTag;
import com.akto.dto.EndpointShieldSettings;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.McpAuditInfo;
import com.akto.dto.PlatformShieldConfig;
import com.akto.dto.traffic.CollectionTags;
import com.akto.mcp.McpRequestResponseUtils;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightUtil;
import com.akto.service.insights.InsightUtil.GovernanceBucket;
import com.akto.util.AgenticObserveUtil;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AtlasPeopleService {

    public static final String HEALTH_HEALTHY = "healthy";
    public static final String HEALTH_OUTDATED = "outdated";
    public static final String HEALTH_OFFLINE = "offline";

    public static final String MODE_ENFORCE = "enforce";
    public static final String MODE_MONITOR = "monitor";
    public static final String MODE_NONE = "none";

    public static final String CAT_AGENTS = "agents";
    public static final String CAT_MCP = "mcp";
    public static final String CAT_BROWSER = "browser";
    public static final String CAT_PLUGIN = "plugin";
    public static final String CAT_SKILL = "skill";
    public static final int MAX_SKILLS_PER_TOOL = 1000;
    public static final List<String> CATEGORIES = Arrays.asList(CAT_AGENTS, CAT_MCP, CAT_BROWSER);

    public static final String OS_MAC = "macos";
    public static final String OS_WINDOWS = "windows";
    public static final String OS_LINUX = "linux";
    public static final String OS_UNKNOWN = "unknown";
    public static final List<String> OS_ORDER = Arrays.asList(OS_MAC, OS_WINDOWS, OS_LINUX, OS_UNKNOWN);

    public static final List<String> SEVERITIES = Arrays.asList("critical", "high", "medium", "low");
    // Violation counts on devices and employees cover this many seconds (7 days).
    public static final int VIOLATION_WINDOW_SECONDS = 7 * 24 * 60 * 60;
    // A device with no heartbeat for this long is shown as Offline.
    public static final int OFFLINE_SECONDS = 7 * 24 * 60 * 60;
    public static final double RISK_MAX = 5.0;
    public static final double HIGH_RISK_SCORE = 4.0;
    public static final int RISK_BUCKETS = 5;

    private static final String STATUS_ACTIVE = "active";
    private static final String ALERT_ONLY_BEHAVIOUR = "alert";
    private static final String TEAM_TAG_KEY = "team";
    private static final String MISCONFIGURED_TAG_KEY = "misconfigured-config";
    private static final String UNKNOWN_USERNAME_PLACEHOLDER = "not-available";
    private static final String BROWSER_HOST_MARKER = "chrome";
    private static final String BROWSER_USER_EMAIL_TAG = "browser-user-email";
    private static final Pattern ACCOUNT_SLOT_SUFFIX = Pattern.compile("-u\\d+$");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]");
    // How long a built snapshot is reused before the next request rebuilds it.
    private static final long CACHE_TTL_MS = 60_000;
    private static final Pattern VERSION = Pattern.compile("^v?(\\d+)\\.(\\d+)\\.(\\d+)");

    private static final Map<String, CachedSnapshot> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<Snapshot>> IN_FLIGHT = new ConcurrentHashMap<>();

    private static class CachedSnapshot {
        final Snapshot snapshot;
        final long loadedAtMs;

        CachedSnapshot(Snapshot snapshot, long loadedAtMs) {
            this.snapshot = snapshot;
            this.loadedAtMs = loadedAtMs;
        }
    }

    // One AI tool (agent, MCP server, browser LLM, plugin or skill owner) found in an api_collection on a device.
    public static class ToolRow {
        public int collectionId;
        public String hostName;
        public String name;
        public String client;
        public String category;
        public boolean approved;
        public boolean personal;
        public boolean misconfigured;
        public double riskScore;
        public String plugin;
        public boolean orphan;
        public int skillCount;
        public List<String> skills = new ArrayList<>();

        public Map<String, Object> toMap() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("plugin", plugin);
            row.put("orphan", orphan);
            row.put("skillCount", skillCount);
            row.put("skills", skills);
            row.put("collectionId", collectionId);
            row.put("hostName", hostName);
            row.put("name", name);
            row.put("client", client);
            row.put("category", category);
            row.put("approved", approved);
            row.put("personal", personal);
            row.put("misconfigured", misconfigured);
            row.put("riskScore", riskScore);
            return row;
        }
    }

    // One heartbeat row (desktop shield or browser extension) from module_info plus its joined tools, risk, violations and guardrail mode.
    public static class DeviceRow {
        public String name;
        public String browser = "";
        public String key = "";
        public String moduleId;
        public String status;
        public String os = OS_UNKNOWN;
        public String username = "";
        public String version;
        public int lastHeartbeat;
        public int firstSeen;
        public String primaryKey;
        public boolean extension;
        public String health = HEALTH_HEALTHY;
        public String guardrailMode = MODE_NONE;
        public final Map<String, Integer> toolsByCategory = new LinkedHashMap<>();
        public final Map<String, Integer> violationsBySeverity = emptySeverityCounts();
        public final List<ToolRow> tools = new ArrayList<>();
        public final Set<String> skillNames = new LinkedHashSet<>();
        public final Set<Set<String>> skillSets = new HashSet<>();
        public int unapprovedTools;
        public int unapprovedSkills;
        public int personalAccounts;
        public boolean misconfigured;
        public final List<Map<String, String>> tags = new ArrayList<>();
        public double riskScore;

        public DeviceRow() {
            for (String category : CATEGORIES) toolsByCategory.put(category, 0);
        }

        public int toolCount() {
            int total = 0;
            for (int count : toolsByCategory.values()) total += count;
            return total;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", name);
            row.put("label", extension ? (username.isEmpty() ? (name.isEmpty() ? "Unidentified browser" : name) : username) : name);
            row.put("moduleId", moduleId);
            row.put("browser", browser);
            row.put("key", key);
            row.put("os", os);
            row.put("owner", username);
            row.put("agentVersion", version);
            row.put("extension", extension);
            row.put("health", health);
            row.put("guardrailMode", guardrailMode);
            row.put("toolCount", toolCount());
            row.put("toolsByCategory", toolsByCategory);
            row.put("skillCount", skillNames.size());
            row.put("unapprovedTools", unapprovedTools);
            row.put("unapprovedSkills", unapprovedSkills);
            row.put("personalAccounts", personalAccounts);
            row.put("hasPersonalAccount", personalAccounts > 0);
            row.put("hasMisconfiguredConfig", misconfigured);
            row.put("tags", tags);
            row.put("violations", violationsBySeverity);
            row.put("violationCount", sum(violationsBySeverity));
            row.put("riskScore", riskScore);
            row.put("lastHeartbeat", lastHeartbeat);
            return row;
        }

        public Map<String, Object> toDetailMap() {
            Map<String, Object> row = toMap();
            List<Map<String, Object>> toolMaps = new ArrayList<>();
            for (ToolRow tool : tools) toolMaps.add(tool.toMap());
            row.put("tools", toolMaps);
            return row;
        }
    }

    // An employee is a distinct username across devices; counts are summed over their devices and risk is the maximum.
    public static class EmployeeRow {
        public String username;
        public final List<String> deviceNames = new ArrayList<>();
        public final List<String> deviceKeys = new ArrayList<>();
        public final Set<String> teams = new LinkedHashSet<>();
        public final Set<String> skillNames = new LinkedHashSet<>();
        public final Map<String, Integer> violationsBySeverity = emptySeverityCounts();
        public int toolCount;
        public int unapprovedTools;
        public int personalAccounts;
        public int lastActive;
        public double riskScore;

        public Map<String, Object> toMap() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("username", username);
            row.put("deviceCount", deviceNames.size());
            row.put("devices", deviceNames);
            row.put("teams", new ArrayList<>(teams));
            row.put("toolCount", toolCount);
            row.put("skillCount", skillNames.size());
            row.put("unapprovedTools", unapprovedTools);
            row.put("personalAccounts", personalAccounts);
            row.put("violations", violationsBySeverity);
            row.put("violationCount", sum(violationsBySeverity));
            row.put("riskScore", riskScore);
            row.put("lastActive", lastActive);
            return row;
        }
    }

    // Everything the Atlas page needs for one account, built once and cached for CACHE_TTL_MS.
    public static class Snapshot {
        public final List<DeviceRow> devices = new ArrayList<>();
        public final Map<String, DeviceRow> devicesByLowerName = new LinkedHashMap<>();
        public final Map<String, DeviceRow> devicesByHost = new HashMap<>();
        public final List<EmployeeRow> employees = new ArrayList<>();
        public final Map<String, String> latestVersionByOs = new HashMap<>();
        public boolean toolsAvailable = true;
        public boolean violationsAvailable = true;
        public boolean riskAvailable = true;
        public int unapprovedTeams;
        public int unapprovedNoTeam;
        public final Set<String> unmatchedBrowserIdentities = new HashSet<>();
    }

    // Filters, search, sort and paging sent by the frontend for the Devices and Employees tables.
    public static class PeopleQuery {
        public String text;
        public List<String> osList;
        public List<String> categories;
        public List<String> healthStates;
        public List<String> guardrailModes;
        public List<String> teams;
        public List<String> tagPairs;
        public List<String> violationSeverities;
        public boolean hasUnapproved;
        public boolean usesPersonal;
        public boolean agentIssues;
        public boolean agentActive;
        public boolean usesAi;
        public List<String> browserList;
        public double riskMin;
        public double riskMax = RISK_MAX;
        public String sortKey;
        public int sortOrder = -1;
        public int skip;
        public int limit = 50;
    }

    // Returns the cached snapshot for the key; on a miss one caller builds it (outside any map lock) and concurrent callers wait for that build.
    public Snapshot getOrBuild(String key, Supplier<Snapshot> loader) {
        CACHE.values().removeIf(entry -> System.currentTimeMillis() - entry.loadedAtMs >= CACHE_TTL_MS);
        CachedSnapshot cached = CACHE.get(key);
        if (cached != null) return cached.snapshot;
        CompletableFuture<Snapshot> mine = new CompletableFuture<>();
        CompletableFuture<Snapshot> running = IN_FLIGHT.putIfAbsent(key, mine);
        if (running != null) return running.join();
        try {
            cached = CACHE.get(key);
            Snapshot built = cached != null ? cached.snapshot : loader.get();
            if (cached == null) CACHE.put(key, new CachedSnapshot(built, System.currentTimeMillis()));
            mine.complete(built);
            return built;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            IN_FLIGHT.remove(key, mine);
        }
    }

    // Latest released shield version per OS from the account's endpoint shield settings; used to flag outdated agents.
    public static Map<String, String> latestVersionByOs(EndpointShieldSettings settings) {
        Map<String, String> latest = new HashMap<>();
        if (settings == null || settings.getPlatforms() == null) return latest;
        for (Map.Entry<String, PlatformShieldConfig> entry : settings.getPlatforms().entrySet()) {
            String os = entry.getKey().startsWith("macos") ? OS_MAC : entry.getKey().startsWith("windows") ? OS_WINDOWS : null;
            PlatformShieldConfig config = entry.getValue();
            if (os == null || config == null || parseVersion(config.getLatestVersion()) == null) continue;
            String current = latest.get(os);
            if (current == null || compareVersions(parseVersion(config.getLatestVersion()), parseVersion(current)) > 0) {
                latest.put(os, config.getLatestVersion());
            }
        }
        return latest;
    }

    // Joins module_info rows with collections (tools), risk, guardrail modes and violations into one snapshot.
    public Snapshot build(List<Document> moduleRows, InsightDataBundle bundle, Map<String, String> latestVersionByOs,
                          int now, int startTs, int endTs) {
        Snapshot snapshot = new Snapshot();
        snapshot.latestVersionByOs.putAll(latestVersionByOs);
        List<Document> newestFirst = new ArrayList<>(moduleRows);
        newestFirst.sort(Comparator.comparingInt((Document module) -> module.get("lastHeartbeatReceived") instanceof Number ? ((Number) module.get("lastHeartbeatReceived")).intValue() : 0).reversed());
        for (Document module : newestFirst) {
            DeviceRow device = toDeviceRow(module, latestVersionByOs, now);
            if (snapshot.devicesByLowerName.containsKey(device.key)) {
                device.primaryKey = device.key;
                device.key = device.key + "|" + device.moduleId;
            }
            snapshot.devicesByLowerName.put(device.key, device);
        }

        if (bundle != null && bundle.userTags != null) {
            for (DeviceRow device : snapshot.devicesByLowerName.values()) {
                List<DeviceTag> tags = bundle.userTags.get(device.username);
                if (tags == null) continue;
                for (DeviceTag tag : tags) {
                    if (tag == null || tag.getKey() == null || tag.getValue() == null) continue;
                    Map<String, String> entry = new LinkedHashMap<>();
                    entry.put("key", tag.getKey());
                    entry.put("value", tag.getValue());
                    entry.put("source", tag.getSource());
                    device.tags.add(entry);
                }
            }
        }

        if (bundle == null) {
            snapshot.toolsAvailable = false;
            snapshot.violationsAvailable = false;
        } else {
            try {
                addTools(snapshot.devicesByLowerName, snapshot.devicesByHost, snapshot.unmatchedBrowserIdentities, bundle, startTs, endTs);
                addGuardrailModes(snapshot.devicesByLowerName.values(), bundle);
            } catch (Exception e) {
                snapshot.toolsAvailable = false;
            }
            try {
                addRiskScores(snapshot.devicesByLowerName.values());
            } catch (Exception e) {
                snapshot.riskAvailable = false;
            }
            snapshot.violationsAvailable = bundle.threatBackendAvailable;
            if (snapshot.violationsAvailable) addViolations(snapshot.devicesByLowerName, snapshot.devicesByHost, bundle);
        }

        shareDataAcrossSameNameDevices(snapshot.devicesByLowerName);
        snapshot.devices.addAll(snapshot.devicesByLowerName.values());
        snapshot.employees.addAll(buildEmployees(snapshot.devices, bundle));
        Set<String> unapprovedTeams = new HashSet<>();
        for (EmployeeRow employee : snapshot.employees) {
            if (employee.unapprovedTools == 0) continue;
            unapprovedTeams.addAll(employee.teams);
            if (employee.teams.isEmpty()) snapshot.unapprovedNoTeam++;
        }
        snapshot.unapprovedTeams = unapprovedTeams.size();
        return snapshot;
    }

    // Same-name devices (e.g. one host reporting two OSes) cannot be told apart in collections, so they show the same tools, risk and violations as the most recently seen one.
    private static void shareDataAcrossSameNameDevices(Map<String, DeviceRow> devicesByLowerName) {
        for (DeviceRow device : devicesByLowerName.values()) {
            DeviceRow primary = device.primaryKey == null ? null : devicesByLowerName.get(device.primaryKey);
            if (primary == null) continue;
            device.tools.addAll(primary.tools);
            device.toolsByCategory.putAll(primary.toolsByCategory);
            device.violationsBySeverity.putAll(primary.violationsBySeverity);
            device.skillNames.addAll(primary.skillNames);
            device.unapprovedTools = primary.unapprovedTools;
            device.unapprovedSkills = primary.unapprovedSkills;
            device.personalAccounts = primary.personalAccounts;
            device.misconfigured = primary.misconfigured;
            device.riskScore = primary.riskScore;
        }
    }

    // Turns one grouped module_info row into a DeviceRow and derives extension, OS and health (offline after OFFLINE_SECONDS).
    private static DeviceRow toDeviceRow(Document module, Map<String, String> latestVersionByOs, int now) {
        DeviceRow device = new DeviceRow();
        device.name = module.getString("deviceName") == null ? "" : module.getString("deviceName");
        device.browser = module.getString("browserName") == null ? "" : module.getString("browserName").toLowerCase(Locale.ROOT);
        device.moduleId = String.valueOf(module.get("moduleId"));
        device.status = module.getString("status");
        device.version = module.getString("currentVersion");
        String username = module.getString("username");
        device.username = isRealUsername(username) ? username.trim() : "";
        Number heartbeat = (Number) module.get("lastHeartbeatReceived");
        device.lastHeartbeat = heartbeat == null ? 0 : heartbeat.intValue();
        Number firstSeen = (Number) module.get("firstSeenTs");
        device.firstSeen = firstSeen == null ? 0 : firstSeen.intValue();
        device.extension = module.getString("browserName") != null
                || (device.version != null && device.version.contains("extension"));
        device.key = device.extension ? "ext|" + device.name : device.name.toLowerCase(Locale.ROOT);
        device.os = device.extension ? normalizeOs(module.getString("os"), null) : normalizeOs(module.getString("os"), module.getString("platform"));

        boolean offline = device.lastHeartbeat <= 0 || device.lastHeartbeat < now - OFFLINE_SECONDS;
        String latest = latestVersionByOs.get(device.os);
        int[] deviceVersion = parseVersion(device.version);
        boolean outdated = !device.extension && latest != null && deviceVersion != null
                && compareVersions(deviceVersion, parseVersion(latest)) < 0;
        device.health = offline ? HEALTH_OFFLINE : outdated ? HEALTH_OUTDATED : HEALTH_HEALTHY;
        return device;
    }

    // Attaches api_collections tagged source=ENDPOINT to devices: desktop by host prefix, browser by identity, email tag or id.
    private static void addTools(Map<String, DeviceRow> devicesByLowerName, Map<String, DeviceRow> devicesByHost, Set<String> unmatchedBrowserIdentities, InsightDataBundle bundle, int startTs, int endTs) {
        boolean ranged = startTs > 0 || endTs > 0;
        Map<String, DeviceRow> browserRowByName = new HashMap<>();
        Map<String, DeviceRow> browserRowByEmail = new HashMap<>();
        Map<String, DeviceRow> browserRowById = new HashMap<>();
        for (DeviceRow row : devicesByLowerName.values()) {
            if (!row.extension) continue;
            String nameKey = identityKey(row.name);
            if (!nameKey.isEmpty()) browserRowByName.putIfAbsent(nameKey, row);
            String emailKey = identityKey(localPart(row.username));
            if (!emailKey.isEmpty()) browserRowByEmail.putIfAbsent(emailKey, row);
            String idKey = identityKey(row.moduleId);
            if (!idKey.isEmpty()) browserRowById.putIfAbsent(idKey, row);
        }
        Map<String, String> remarksByServiceName = loadServerRemarks();
        for (ApiCollection c : bundle.collections) {
            if (!InsightUtil.hasTagValue(c, Constants.AKTO_ENDPOINT_SOURCE_TAG, Constants.AKTO_ENDPOINT_SOURCE_VALUE)) continue;
            String hostDevice = InsightUtil.deviceIdOf(c);
            if (hostDevice == null || c.getHostName() == null) continue;
            boolean browser = InsightUtil.hasTag(c, Constants.AKTO_BROWSER_LLM_TAG);
            DeviceRow device = browser ? null : devicesByLowerName.get(hostDevice.toLowerCase(Locale.ROOT));
            if (!browser && device == null) continue;
            if (ranged && !seenInWindow(bundle.collectionLastTrafficSeen.get(c.getId()), startTs, endTs)) continue;

            String[] parts = c.getHostName().split("\\.");
            ToolRow tool = new ToolRow();
            tool.collectionId = c.getId();
            tool.hostName = c.getHostName();
            tool.client = parts.length > 1 ? parts[1] : "";
            tool.name = parts.length > 2 ? String.join(".", Arrays.copyOfRange(parts, 2, parts.length)) : tool.client;
            tool.category = categoryOf(c, parts);
            tool.approved = isSanctioned(c, bundle.allowlistNamesLower, remarksByServiceName);
            tool.personal = InsightUtil.isPersonalAccount(c);
            tool.misconfigured = InsightUtil.hasTagValue(c, MISCONFIGURED_TAG_KEY, "true");
            tool.plugin = AgenticObserveUtil.getPluginName(c);
            tool.orphan = c.getHostName().contains(AgenticObserveUtil.NOT_ATTACHED_VALUE);
            if (browser) {
                String identity = identityKey(ACCOUNT_SLOT_SUFFIX.matcher(parts[0].toLowerCase(Locale.ROOT)).replaceAll(""));
                device = browserRowByName.get(identity);
                String email = browserEmailOf(c);
                if (device == null && email != null) device = browserRowByEmail.get(identityKey(localPart(email)));
                if (device == null) device = browserRowById.get(identity);
                if (device == null) {
                    unmatchedBrowserIdentities.add(identity);
                    continue;
                }
            }
            Set<String> skills = skillNamesOf(c);
            if (!skills.isEmpty()) {
                device.skillNames.addAll(skills);
                tool.skillCount = device.skillSets.add(skills) ? skills.size() : 0;
                if (tool.skillCount > 0) tool.skills = new ArrayList<>(skills).subList(0, Math.min(skills.size(), MAX_SKILLS_PER_TOOL));
            }

            device.tools.add(tool);
            if (tool.misconfigured) device.misconfigured = true;
            devicesByHost.put(c.getHostName().toLowerCase(Locale.ROOT), device);
            if (tool.plugin != null) {
                tool.category = CAT_PLUGIN;
                continue;
            }
            if (tool.orphan) {
                tool.category = CAT_SKILL;
                continue;
            }
            device.toolsByCategory.merge(tool.category, 1, Integer::sum);
            if (!tool.approved) device.unapprovedTools++;
            if (tool.personal) device.personalAccounts++;
        }
        Set<String> approvedSkills = loadApprovedSkillNames();
        for (DeviceRow device : devicesByLowerName.values()) {
            for (String skill : device.skillNames) {
                if (approvedSkills.contains(skill.toLowerCase(Locale.ROOT))) continue;
                device.unapprovedSkills++;
                device.unapprovedTools++;
            }
        }
    }

    private static Set<String> skillNamesOf(ApiCollection c) {
        Set<String> names = new LinkedHashSet<>();
        if (c.getSkills() != null) {
            for (String skill : c.getSkills()) {
                if (skill != null && !skill.trim().isEmpty()) names.add(skill);
            }
        }
        if (c.getEnvType() != null) {
            for (CollectionTags tag : c.getEnvType()) {
                if (tag != null && Constants.AKTO_SKILL_TAG.equals(tag.getKeyName()) && tag.getValue() != null && !tag.getValue().trim().isEmpty()) {
                    names.add(tag.getValue());
                }
            }
        }
        return names;
    }

    private static String localPart(String value) {
        if (value == null) return "";
        int at = value.indexOf('@');
        return at < 0 ? value : value.substring(0, at);
    }

    // Lowercases and strips non-alphanumerics so browser collection prefixes can match extension names and emails.
    static String identityKey(String value) {
        return value == null ? "" : NON_ALPHANUMERIC.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    private static String browserEmailOf(ApiCollection c) {
        if (c.getEnvType() == null) return null;
        for (CollectionTags tag : c.getEnvType()) {
            if (tag != null && BROWSER_USER_EMAIL_TAG.equals(tag.getKeyName()) && tag.getValue() != null && !tag.getValue().trim().isEmpty()) {
                return tag.getValue().trim().toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    private static String categoryOf(ApiCollection c, String[] parts) {
        String marker = parts.length > 1 ? parts[1] : "";
        if (BROWSER_HOST_MARKER.equals(marker) || InsightUtil.hasTag(c, Constants.AKTO_BROWSER_LLM_TAG)) return CAT_BROWSER;
        if (Constants.AKTO_AI_AGENT_TAG.equals(marker)) return CAT_AGENTS;
        if (InsightUtil.hasTag(c, Constants.AKTO_MCP_SERVER_TAG) || InsightUtil.hasTag(c, Constants.AKTO_MCP_CLIENT_TAG)) return CAT_MCP;
        return CAT_AGENTS;
    }

    // A tool is approved when its governance bucket is sanctioned or its name is on the allowlist.
    public static boolean isSanctioned(ApiCollection c, Set<String> allowlistNamesLower, Map<String, String> remarksByServiceName) {
        GovernanceBucket bucket = InsightUtil.governanceBucket(c, allowlistNamesLower, remarksByServiceName);
        if (bucket == GovernanceBucket.SANCTIONED) return true;
        if (bucket != GovernanceBucket.LOCAL) return false;
        String groupingName = InsightUtil.governanceGroupingName(c);
        String serviceName = InsightUtil.serviceNameOf(c);
        return (groupingName != null && allowlistNamesLower.contains(groupingName.toLowerCase(Locale.ROOT)))
                || (serviceName != null && McpAuditInfo.REMARKS_APPROVED.equals(remarksByServiceName.get(serviceName.toLowerCase(Locale.ROOT))));
    }

    // Names of skills marked approved in mcp_audit_info.
    private static Set<String> loadApprovedSkillNames() {
        Bson filter = Filters.and(
                Filters.eq(McpAuditInfo.TYPE, McpAuditInfo.TYPE_AGENT_SKILL),
                Filters.eq(McpAuditInfo.REMARKS, McpAuditInfo.REMARKS_APPROVED),
                contextSourceFilter(Context.contextSource.get()));
        Set<String> approved = new HashSet<>();
        for (McpAuditInfo row : McpAuditInfoDao.instance.findAll(filter, Projections.include(McpAuditInfo.RESOURCE_NAME))) {
            if (row.getResourceName() != null) approved.add(row.getResourceName().toLowerCase(Locale.ROOT));
        }
        return approved;
    }

    // Service-name to remarks map from mcp_audit_info, used when deciding approval.
    private static Map<String, String> loadServerRemarks() {
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

    // Device risk is the max stored api_info.riskScore over its tools' collections; violations are not an input here.
    private static void addRiskScores(Iterable<DeviceRow> devices) {
        List<Integer> collectionIds = new ArrayList<>();
        for (DeviceRow device : devices) {
            for (ToolRow tool : device.tools) collectionIds.add(tool.collectionId);
        }
        Map<Integer, Double> riskByCollection = ApiInfoDao.instance.getRiskScoreForCollections(collectionIds);
        for (DeviceRow device : devices) {
            for (ToolRow tool : device.tools) {
                tool.riskScore = AgenticObserveUtil.roundRiskScore(riskByCollection.getOrDefault(tool.collectionId, 0.0));
                device.riskScore = Math.max(device.riskScore, tool.riskScore);
            }
        }
    }

    // Enforce if any active covering policy blocks, Monitor if every covering policy only alerts, None if no policy covers the device.
    private static void addGuardrailModes(Iterable<DeviceRow> devices, InsightDataBundle bundle) {
        List<GuardrailPolicies> activePolicies = new ArrayList<>();
        for (GuardrailPolicies policy : bundle.policies) {
            if (policy != null && policy.isActive()) activePolicies.add(policy);
        }
        for (DeviceRow device : devices) {
            boolean covered = false;
            boolean onlyAlerts = true;
            for (GuardrailPolicies policy : activePolicies) {
                if (!InsightUtil.policyCoversDevice(policy.getApplyToDeviceIds(), device.name)) continue;
                covered = true;
                if (!ALERT_ONLY_BEHAVIOUR.equalsIgnoreCase(policy.getBehaviour())) {
                    onlyAlerts = false;
                    break;
                }
            }
            device.guardrailMode = !covered ? MODE_NONE : onlyAlerts ? MODE_MONITOR : MODE_ENFORCE;
        }
    }

    // Adds threat-backend severity counts per host (last VIOLATION_WINDOW_SECONDS) to the matching device.
    private static void addViolations(Map<String, DeviceRow> devicesByLowerName, Map<String, DeviceRow> devicesByHost, InsightDataBundle bundle) {
        for (HostSeverityCount counts : bundle.hostSeverityCounts) {
            if (counts.getHost() == null) continue;
            DeviceRow device = devicesByHost.get(counts.getHost().toLowerCase(Locale.ROOT));
            if (device == null) {
                String hostDevice = AgenticObserveUtil.extractEndpointId(counts.getHost());
                if (hostDevice != null) device = devicesByLowerName.get(hostDevice.toLowerCase(Locale.ROOT));
            }
            if (device == null) continue;
            device.violationsBySeverity.merge("critical", counts.getCritical(), Integer::sum);
            device.violationsBySeverity.merge("high", counts.getHigh(), Integer::sum);
            device.violationsBySeverity.merge("medium", counts.getMedium(), Integer::sum);
            device.violationsBySeverity.merge("low", counts.getLow(), Integer::sum);
        }
    }

    // Groups devices by lowercase username into employees and attaches team tags from agent_users.
    private static List<EmployeeRow> buildEmployees(List<DeviceRow> devices, InsightDataBundle bundle) {
        Map<String, EmployeeRow> byUsername = new LinkedHashMap<>();
        for (DeviceRow device : devices) {
            if (device.username.isEmpty()) continue;
            EmployeeRow employee = byUsername.computeIfAbsent(device.username.toLowerCase(Locale.ROOT), key -> {
                EmployeeRow row = new EmployeeRow();
                row.username = device.username;
                return row;
            });
            employee.deviceNames.add(device.name);
            employee.deviceKeys.add(device.key);
            employee.skillNames.addAll(device.skillNames);
            employee.toolCount += device.toolCount();
            employee.unapprovedTools += device.unapprovedTools;
            employee.personalAccounts += device.personalAccounts;
            employee.lastActive = Math.max(employee.lastActive, device.lastHeartbeat);
            employee.riskScore = Math.max(employee.riskScore, device.riskScore);
            for (String severity : SEVERITIES) {
                employee.violationsBySeverity.merge(severity, device.violationsBySeverity.get(severity), Integer::sum);
            }
        }
        if (bundle != null && bundle.userTags != null) {
            for (EmployeeRow employee : byUsername.values()) {
                List<DeviceTag> tags = bundle.userTags.get(employee.username);
                if (tags == null) continue;
                for (DeviceTag tag : tags) {
                    if (tag != null && TEAM_TAG_KEY.equals(tag.getKey()) && tag.getValue() != null && !tag.getValue().trim().isEmpty()) {
                        employee.teams.add(tag.getValue().trim().toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
        return new ArrayList<>(byUsername.values());
    }

    // Totals for the six stat cards (managed, active, unapproved, monitor-only and so on) from the snapshot.
    public Map<String, Object> stats(Snapshot snapshot) {
        long desktop = 0, browsers = 0, desktopActive = 0, browserActive = 0, offline = 0;
        long unapproved = 0, monitorOnly = 0;
        for (DeviceRow device : snapshot.devices) {
            boolean active = STATUS_ACTIVE.equals(device.status);
            if (device.extension) {
                browsers++;
                if (active) browserActive++;
            } else {
                desktop++;
                if (active) desktopActive++;
            }
            if (HEALTH_OFFLINE.equals(device.health)) offline++;
            if (device.unapprovedTools > 0) unapproved++;
            if (MODE_MONITOR.equals(device.guardrailMode)) monitorOnly++;
        }
        long managed = desktop + browsers;
        boolean tools = snapshot.toolsAvailable;
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("managedDevices", managed);
        stats.put("desktopDevices", desktop);
        stats.put("browserDevices", browsers);
        stats.put("agentActive", desktopActive + browserActive);
        stats.put("desktopActive", desktopActive);
        stats.put("browserActive", browserActive);
        stats.put("offlineDevices", offline);
        stats.put("totalEmployees", (long) snapshot.employees.size());
        stats.put("unapprovedTeams", tools ? snapshot.unapprovedTeams : null);
        stats.put("unapprovedNoTeam", tools ? snapshot.unapprovedNoTeam : null);
        stats.put("devicesWithUnapprovedTools", tools ? unapproved : null);
        stats.put("monitorOnlyDevices", tools ? monitorOnly : null);
        stats.put("unattributedBrowserUsers", (long) snapshot.unmatchedBrowserIdentities.size());
        List<String> unavailableSources = new ArrayList<>();
        if (!tools) unavailableSources.add("insightsBundle");
        stats.put("unavailableSources", unavailableSources);
        return stats;
    }

    private static Map<String, Object> matrixRow(String keyField, String key) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(keyField, key);
        row.put("devices", 0);
        row.put("totalTools", 0);
        row.put("devicesWithTools", 0);
        Map<String, Integer> categoryCounts = new LinkedHashMap<>();
        for (String category : CATEGORIES) categoryCounts.put(category, 0);
        row.put("toolsByCategory", categoryCounts);
        return row;
    }

    private static List<Map<String, Object>> finalizedMatrixRows(Map<String, Map<String, Object>> matrix) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : matrix.values()) {
            int devices = (Integer) row.get("devices");
            if (devices == 0) continue;
            row.put("avgTools", Math.round(10.0 * (Integer) row.get("totalTools") / devices) / 10.0);
            rows.add(row);
        }
        return rows;
    }

    // Chart data: agent health, guardrail modes, AI tools by OS and browser, risk histogram and filter options.
    public Map<String, Object> overview(Snapshot snapshot) {
        Map<String, Object> overview = new LinkedHashMap<>();
        Map<String, Integer> health = new LinkedHashMap<>();
        Map<String, Integer> modes = new LinkedHashMap<>();
        for (String state : Arrays.asList(HEALTH_HEALTHY, HEALTH_OUTDATED, HEALTH_OFFLINE)) health.put(state, 0);
        for (String mode : Arrays.asList(MODE_ENFORCE, MODE_MONITOR, MODE_NONE)) modes.put(mode, 0);
        Map<String, Integer> categoryTotals = new LinkedHashMap<>();
        for (String category : CATEGORIES) categoryTotals.put(category, 0);
        Map<String, Map<String, Object>> osMatrix = new LinkedHashMap<>();
        for (String os : OS_ORDER) osMatrix.put(os, matrixRow("os", os));
        Map<String, Map<String, Object>> browserMatrix = new LinkedHashMap<>();

        List<String> outdatedModuleIds = new ArrayList<>();
        for (DeviceRow device : snapshot.devices) {
            health.merge(device.health, 1, Integer::sum);
            modes.merge(device.guardrailMode, 1, Integer::sum);
            if (HEALTH_OUTDATED.equals(device.health)) outdatedModuleIds.add(device.moduleId);
            Map<String, Object> osRow = device.extension
                    ? browserMatrix.computeIfAbsent(device.browser.isEmpty() ? "other" : device.browser, key -> matrixRow("browser", key))
                    : osMatrix.get(device.os);
            osRow.put("devices", (Integer) osRow.get("devices") + 1);
            osRow.put("totalTools", (Integer) osRow.get("totalTools") + device.toolCount());
            if (device.toolCount() > 0) osRow.put("devicesWithTools", (Integer) osRow.get("devicesWithTools") + 1);
            @SuppressWarnings("unchecked")
            Map<String, Integer> categoryCounts = (Map<String, Integer>) osRow.get("toolsByCategory");
            for (String category : CATEGORIES) {
                int hasCategory = device.toolsByCategory.get(category) > 0 ? 1 : 0;
                categoryCounts.merge(category, hasCategory, Integer::sum);
                if (device.extension || !OS_UNKNOWN.equals(device.os)) categoryTotals.merge(category, hasCategory, Integer::sum);
            }
        }

        List<Map<String, Object>> osRows = finalizedMatrixRows(osMatrix);
        List<Map<String, Object>> browserRows = finalizedMatrixRows(browserMatrix);

        int[] histogram = new int[RISK_BUCKETS];
        int highRisk = 0;
        for (EmployeeRow employee : snapshot.employees) {
            histogram[Math.min((int) (employee.riskScore * RISK_BUCKETS / RISK_MAX), RISK_BUCKETS - 1)]++;
            if (employee.riskScore >= HIGH_RISK_SCORE) highRisk++;
        }
        List<Integer> histogramList = new ArrayList<>();
        for (int count : histogram) histogramList.add(count);

        Set<String> teamOptions = new TreeSet<>();
        for (EmployeeRow employee : snapshot.employees) teamOptions.addAll(employee.teams);
        overview.put("teamOptions", new ArrayList<>(teamOptions));
        Set<String> tagOptions = new TreeSet<>();
        for (DeviceRow device : snapshot.devices) for (Map<String, String> tag : device.tags) tagOptions.add(tagPair(tag));
        overview.put("tagOptions", new ArrayList<>(tagOptions));
        overview.put("deviceCount", snapshot.devices.size());
        overview.put("employeeCount", snapshot.employees.size());
        overview.put("agentHealth", health);
        overview.put("guardrailModes", modes);
        overview.put("outdatedModuleIds", outdatedModuleIds);
        overview.put("latestVersionByOs", snapshot.latestVersionByOs);
        overview.put("osMatrix", osRows);
        overview.put("browserMatrix", browserRows);
        overview.put("categoryTotals", categoryTotals);
        overview.put("riskHistogram", histogramList);
        overview.put("highRiskEmployees", highRisk);
        overview.put("toolsAvailable", snapshot.toolsAvailable);
        overview.put("violationsAvailable", snapshot.violationsAvailable);
        overview.put("riskAvailable", snapshot.riskAvailable);
        return overview;
    }

    // Full detail including tools for the flyout; devices resolve by key, name or host prefix, employees by username.
    public Map<String, Object> detail(Snapshot snapshot, String kind, String id) {
        if (id == null) return null;
        if ("device".equals(kind)) {
            DeviceRow device = snapshot.devicesByLowerName.get(id);
            if (device == null) device = snapshot.devicesByLowerName.get(id.toLowerCase(Locale.ROOT));
            if (device == null) device = findDeviceByAlias(snapshot, id);
            return device == null ? null : device.toDetailMap();
        }
        for (EmployeeRow employee : snapshot.employees) {
            if (!employee.username.equalsIgnoreCase(id)) continue;
            Map<String, Object> detail = employee.toMap();
            List<Map<String, Object>> devices = new ArrayList<>();
            for (DeviceRow device : devicesOf(snapshot, employee)) devices.add(device.toDetailMap());
            detail.put("deviceDetails", devices);
            return detail;
        }
        return null;
    }

    private static final List<String> TREND_OS_KEYS = Arrays.asList("mac", "windows", "linux", "unknown");
    private static final List<String> TREND_BROWSER_KEYS = Arrays.asList("chrome", "firefox", "edge", "safari", "other");

    // Cumulative device counts by OS and browser, bucketed by each group's earliest startedTs (first seen).
    public Map<String, Object> trend(Snapshot snapshot, int startTs, int endTs, int now) {
        Map<String, List<Integer>> osFirstSeen = new HashMap<>();
        Map<String, List<Integer>> browserFirstSeen = new HashMap<>();
        List<Integer> all = new ArrayList<>();
        for (DeviceRow device : snapshot.devices) {
            int ts = device.firstSeen;
            all.add(ts);
            if (device.extension) {
                String browser = TREND_BROWSER_KEYS.contains(device.browser) ? device.browser : "other";
                browserFirstSeen.computeIfAbsent(browser, key -> new ArrayList<>()).add(ts);
            } else {
                String os = OS_MAC.equals(device.os) ? "mac" : OS_WINDOWS.equals(device.os) ? "windows" : OS_LINUX.equals(device.os) ? "linux" : "unknown";
                osFirstSeen.computeIfAbsent(os, key -> new ArrayList<>()).add(ts);
            }
        }
        int end = endTs > 0 ? endTs : now;
        List<AgenticObserveAction.MonthSlot> slots = AgenticObserveAction.buildWindowSlots(startTs, endTs, AgenticObserveAction.earliestTs(all));
        List<String> labels = new ArrayList<>();
        for (AgenticObserveAction.MonthSlot slot : slots) labels.add(slot.label);
        Map<String, Object> osTrend = new LinkedHashMap<>();
        for (String key : TREND_OS_KEYS) osTrend.put(key, AgenticObserveAction.cumulativeCounts(osFirstSeen.getOrDefault(key, new ArrayList<>()), slots, end));
        Map<String, Object> browserTrend = new LinkedHashMap<>();
        for (String key : TREND_BROWSER_KEYS) browserTrend.put(key, AgenticObserveAction.cumulativeCounts(browserFirstSeen.getOrDefault(key, new ArrayList<>()), slots, end));
        Map<String, Object> trend = new LinkedHashMap<>();
        trend.put("monthLabels", labels);
        trend.put("osTrend", osTrend);
        trend.put("browserTrend", browserTrend);
        return trend;
    }

    // Fallback lookup for deep links: device name, browser identity, or a collection host prefix.
    private static DeviceRow findDeviceByAlias(Snapshot snapshot, String id) {
        String idKey = identityKey(id);
        for (DeviceRow device : snapshot.devices) {
            if (device.name.equalsIgnoreCase(id)) return device;
            if (device.extension && !idKey.isEmpty() && identityKey(device.name).equals(idKey)) return device;
            for (ToolRow tool : device.tools) {
                if (tool.hostName != null && tool.hostName.split("\\.")[0].equalsIgnoreCase(id)) return device;
            }
        }
        return null;
    }

    // Filters, sorts and slices the snapshot's device list; this is the Devices tab's data source.
    public List<Map<String, Object>> pageDevices(Snapshot snapshot, PeopleQuery query, long[] totalOut) {
        List<String[]> terms = parseTerms(query.text);
        List<DeviceRow> matching = new ArrayList<>();
        for (DeviceRow device : snapshot.devices) {
            if (!matchesDeviceFilters(device, query)) continue;
            if (!deviceMatchesAllTerms(device, terms)) continue;
            if (query.agentIssues && HEALTH_HEALTHY.equals(device.health)) continue;
            if (query.agentActive && !STATUS_ACTIVE.equals(device.status)) continue;
            if (query.hasUnapproved && device.unapprovedTools == 0) continue;
            if (query.usesPersonal && device.personalAccounts == 0) continue;
            if (query.usesAi && device.toolCount() == 0) continue;
            if (query.riskMin > 0 && device.riskScore < query.riskMin) continue;
            if (query.riskMax < RISK_MAX && device.riskScore >= query.riskMax) continue;
            if (notEmpty(query.teams) && deviceTeams(device).stream().noneMatch(query.teams::contains)) continue;
            matching.add(device);
        }
        matching.sort(deviceComparator(query).thenComparing(device -> device.key));
        totalOut[0] = matching.size();
        List<Map<String, Object>> page = new ArrayList<>();
        for (DeviceRow device : slice(matching, query)) page.add(device.toMap());
        return page;
    }

    // Filters, sorts and slices the employee list; device filters match if any of the employee's devices match.
    public List<Map<String, Object>> pageEmployees(Snapshot snapshot, PeopleQuery query, long[] totalOut) {
        List<String[]> terms = parseTerms(query.text);
        boolean deviceFiltered = notEmpty(query.osList) || notEmpty(query.browserList) || notEmpty(query.categories)
                || notEmpty(query.healthStates) || notEmpty(query.guardrailModes);
        List<EmployeeRow> matching = new ArrayList<>();
        for (EmployeeRow employee : snapshot.employees) {
            List<DeviceRow> devices = devicesOf(snapshot, employee);
            if (deviceFiltered && devices.stream().noneMatch(device -> matchesDeviceFilters(device, query))) continue;
            if (!employeeMatchesAllTerms(employee, devices, terms)) continue;
            if (notEmpty(query.teams) && employee.teams.stream().noneMatch(query.teams::contains)) continue;
            if (notEmpty(query.violationSeverities) && query.violationSeverities.stream().noneMatch(severity -> employee.violationsBySeverity.getOrDefault(severity, 0) > 0)) continue;
            if (query.usesAi && employee.toolCount == 0) continue;
            if (query.hasUnapproved && employee.unapprovedTools == 0) continue;
            if (query.usesPersonal && employee.personalAccounts == 0) continue;
            if (employee.riskScore < query.riskMin) continue;
            if (query.riskMax < RISK_MAX && employee.riskScore >= query.riskMax) continue;
            matching.add(employee);
        }
        matching.sort(employeeComparator(query));
        totalOut[0] = matching.size();
        List<Map<String, Object>> page = new ArrayList<>();
        for (EmployeeRow employee : slice(matching, query)) page.add(employee.toMap());
        return page;
    }

    private static List<DeviceRow> devicesOf(Snapshot snapshot, EmployeeRow employee) {
        List<DeviceRow> devices = new ArrayList<>();
        for (String key : employee.deviceKeys) {
            DeviceRow device = snapshot.devicesByLowerName.get(key);
            if (device != null) devices.add(device);
        }
        return devices;
    }

    // Applies the structured filters (OS or browser, health, mode, category, tags, violation severity) to one device.
    private static boolean matchesDeviceFilters(DeviceRow device, PeopleQuery query) {
        if (notEmpty(query.osList) || notEmpty(query.browserList)) {
            boolean osMatch = !device.extension && notEmpty(query.osList) && query.osList.contains(device.os);
            boolean browserMatch = device.extension && notEmpty(query.browserList) && query.browserList.contains(device.browser.isEmpty() ? "other" : device.browser);
            if (!osMatch && !browserMatch) return false;
        }
        if (notEmpty(query.healthStates) && !query.healthStates.contains(device.health)) return false;
        if (notEmpty(query.guardrailModes) && !query.guardrailModes.contains(device.guardrailMode)) return false;
        if (notEmpty(query.violationSeverities) && query.violationSeverities.stream().noneMatch(severity -> device.violationsBySeverity.getOrDefault(severity, 0) > 0)) return false;
        if (notEmpty(query.tagPairs) && device.tags.stream().noneMatch(tag -> query.tagPairs.contains(tagPair(tag)))) return false;
        if (notEmpty(query.categories)) {
            for (String category : query.categories) {
                if (device.toolsByCategory.getOrDefault(category, 0) > 0) return true;
            }
            return false;
        }
        return true;
    }

    // Splits search text into key:value terms (owner:, os:, tag: and so on) and plain terms.
    static List<String[]> parseTerms(String text) {
        List<String[]> terms = new ArrayList<>();
        if (text == null) return terms;
        for (String token : text.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            if (token.isEmpty()) continue;
            int colon = token.indexOf(':');
            if (colon > 0 && colon < token.length() - 1) {
                terms.add(new String[]{token.substring(0, colon), token.substring(colon + 1)});
            } else {
                terms.add(new String[]{null, token});
            }
        }
        return terms;
    }

    private static List<String> deviceTeams(DeviceRow device) {
        List<String> teams = new ArrayList<>();
        for (Map<String, String> tag : device.tags) {
            if (TEAM_TAG_KEY.equals(tag.get("key")) && tag.get("value") != null) teams.add(tag.get("value").trim().toLowerCase(Locale.ROOT));
        }
        return teams;
    }

    private static String tagPair(Map<String, String> tag) {
        return tag.get("key") + "=" + tag.get("value");
    }

    private static boolean anyTagContains(DeviceRow device, String value) {
        for (Map<String, String> tag : device.tags) {
            if (contains(tagPair(tag), value)) return true;
        }
        return false;
    }

    private static boolean deviceMatchesAllTerms(DeviceRow device, List<String[]> terms) {
        for (String[] term : terms) {
            if (!deviceMatchesTerm(device, term[0], term[1])) return false;
        }
        return true;
    }

    private static boolean deviceMatchesTerm(DeviceRow device, String key, String value) {
        if (key == null) {
            return contains(device.name, value) || contains(device.username, value) || contains(device.os, value)
                    || contains(device.version, value) || anyToolContains(device, value) || anyTagContains(device, value);
        }
        switch (key) {
            case "device": return contains(device.name, value);
            case "owner":
            case "user": return contains(device.username, value);
            case "os": return contains(device.os, value);
            case "health": return contains(device.health, value);
            case "mode": return contains(device.guardrailMode, value);
            case "version": return contains(device.version, value);
            case "tool": return anyToolContains(device, value);
            case "tag": return anyTagContains(device, value);
            case "category": return hasCategory(device, value);
            default: return false;
        }
    }

    private static boolean employeeMatchesAllTerms(EmployeeRow employee, List<DeviceRow> devices, List<String[]> terms) {
        for (String[] term : terms) {
            String key = term[0];
            String value = term[1];
            boolean matched;
            if (key == null) {
                matched = contains(employee.username, value) || anyContains(employee.teams, value)
                        || devices.stream().anyMatch(device -> deviceMatchesTerm(device, null, value));
            } else if ("user".equals(key) || "owner".equals(key)) {
                matched = contains(employee.username, value);
            } else if ("team".equals(key)) {
                matched = anyContains(employee.teams, value);
            } else {
                matched = devices.stream().anyMatch(device -> deviceMatchesTerm(device, key, value));
            }
            if (!matched) return false;
        }
        return true;
    }

    private static boolean anyToolContains(DeviceRow device, String value) {
        for (ToolRow tool : device.tools) {
            if (contains(tool.name, value) || contains(tool.client, value)) return true;
        }
        return false;
    }

    private static boolean hasCategory(DeviceRow device, String value) {
        for (Map.Entry<String, Integer> entry : device.toolsByCategory.entrySet()) {
            if (entry.getValue() > 0 && contains(entry.getKey(), value)) return true;
        }
        return false;
    }

    private static boolean anyContains(Iterable<String> values, String text) {
        for (String value : values) {
            if (contains(value, text)) return true;
        }
        return false;
    }

    private static boolean contains(String value, String lowerText) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerText);
    }

    private static boolean notEmpty(List<String> values) {
        return values != null && !values.isEmpty();
    }

    // Applies skip and limit from the query to an already filtered and sorted list.
    private static <T> List<T> slice(List<T> rows, PeopleQuery query) {
        int from = Math.min(Math.max(query.skip, 0), rows.size());
        int to = from + Math.min(Math.max(query.limit, 1), rows.size() - from);
        return rows.subList(from, to);
    }

    private static Comparator<DeviceRow> deviceComparator(PeopleQuery query) {
        Comparator<DeviceRow> comparator;
        switch (query.sortKey == null ? "" : query.sortKey) {
            case "name": comparator = Comparator.comparing(d -> d.name.toLowerCase(Locale.ROOT)); break;
            case "owner": comparator = Comparator.comparing(d -> d.username.toLowerCase(Locale.ROOT)); break;
            case "os": comparator = Comparator.comparing(d -> d.os); break;
            case "health": comparator = Comparator.comparing(d -> d.health); break;
            case "guardrailMode": comparator = Comparator.comparing(d -> d.guardrailMode); break;
            case "toolCount": comparator = Comparator.comparingInt(DeviceRow::toolCount); break;
            case "unapprovedTools": comparator = Comparator.comparingInt(d -> d.unapprovedTools); break;
            case "riskScore": comparator = Comparator.comparingDouble(d -> d.riskScore); break;
            case "violations": comparator = Comparator.comparingInt(d -> sum(d.violationsBySeverity)); break;
            default: comparator = Comparator.comparingInt(d -> d.lastHeartbeat);
        }
        return query.sortOrder >= 0 ? comparator : comparator.reversed();
    }

    private static Comparator<EmployeeRow> employeeComparator(PeopleQuery query) {
        Comparator<EmployeeRow> comparator;
        switch (query.sortKey == null ? "" : query.sortKey) {
            case "username": comparator = Comparator.comparing(e -> e.username.toLowerCase(Locale.ROOT)); break;
            case "deviceCount": comparator = Comparator.comparingInt(e -> e.deviceNames.size()); break;
            case "toolCount": comparator = Comparator.comparingInt(e -> e.toolCount); break;
            case "unapprovedTools": comparator = Comparator.comparingInt(e -> e.unapprovedTools); break;
            case "personalAccounts": comparator = Comparator.comparingInt(e -> e.personalAccounts); break;
            case "violations": comparator = Comparator.comparingInt(e -> sum(e.violationsBySeverity)); break;
            case "lastActive": comparator = Comparator.comparingInt(e -> e.lastActive); break;
            default: comparator = Comparator.comparingDouble(e -> e.riskScore);
        }
        return query.sortOrder >= 0 ? comparator : comparator.reversed();
    }

    private static Map<String, Integer> emptySeverityCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String severity : SEVERITIES) counts.put(severity, 0);
        return counts;
    }

    private static int sum(Map<String, Integer> counts) {
        int total = 0;
        for (int count : counts.values()) total += count;
        return total;
    }

    // Filters out empty and placeholder usernames so they don't become employees.
    public static boolean isRealUsername(String username) {
        return username != null && !username.trim().isEmpty()
                && !UNKNOWN_USERNAME_PLACEHOLDER.equalsIgnoreCase(username.trim());
    }

    private static boolean seenInWindow(Integer lastSeen, int startTs, int endTs) {
        if (lastSeen == null) return false;
        if (startTs > 0 && lastSeen < startTs) return false;
        return endTs <= 0 || lastSeen <= endTs;
    }

    static int[] parseVersion(String version) {
        if (version == null) return null;
        Matcher matcher = VERSION.matcher(version.trim());
        if (!matcher.find()) return null;
        return new int[]{Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3))};
    }

    static int compareVersions(int[] left, int[] right) {
        for (int i = 0; i < 3; i++) {
            if (left[i] != right[i]) return Integer.compare(left[i], right[i]);
        }
        return 0;
    }

    // Maps module_info os and platform strings to macos, windows, linux or unknown.
    static String normalizeOs(String os, String platform) {
        String value = (os != null ? os : platform != null ? platform : "").toLowerCase(Locale.ROOT);
        if (value.startsWith("mac") || value.contains("darwin")) return OS_MAC;
        if (value.startsWith("win")) return OS_WINDOWS;
        if (value.contains("linux") || value.contains("x11") || value.contains("ubuntu")) return OS_LINUX;
        return OS_UNKNOWN;
    }
}
