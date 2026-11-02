package com.akto.service.atlas;

import com.akto.dto.ApiCollection;
import com.akto.dto.EndpointShieldSettings;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.PlatformShieldConfig;
import com.akto.action.threat_detection.HostSeverityCount;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightUtil;
import com.akto.util.AgenticObserveUtil;
import com.akto.util.Constants;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    public static final String CAT_IDE = "ide";
    public static final String CAT_MCP = "mcp";
    public static final String CAT_BROWSER = "browser";
    public static final List<String> CATEGORIES = Arrays.asList(CAT_AGENTS, CAT_IDE, CAT_MCP, CAT_BROWSER);

    public static final String OS_MAC = "macos";
    public static final String OS_WINDOWS = "windows";
    public static final String OS_LINUX = "linux";
    public static final String OS_UNKNOWN = "unknown";
    public static final List<String> OS_ORDER = Arrays.asList(OS_MAC, OS_WINDOWS, OS_LINUX, OS_UNKNOWN);

    public static final int VIOLATION_WINDOW_SECONDS = 7 * 24 * 60 * 60;
    public static final int RISK_UNAPPROVED_POINTS = 40;
    public static final int RISK_PERSONAL_POINTS = 30;
    public static final int RISK_VIOLATION_POINTS = 30;
    public static final int RISK_PERSONAL_CAP = 3;
    public static final int RISK_VIOLATION_CAP = 50;
    public static final int RISK_BUCKETS = 10;

    private static final int OFFLINE_SECONDS = 7 * 24 * 60 * 60;
    private static final long CACHE_TTL_MS = 60_000;
    private static final Pattern VERSION = Pattern.compile("^v?(\\d+)\\.(\\d+)\\.(\\d+)");
    private static final Pattern IDE_VENDOR = Pattern.compile("cursor|copilot|windsurf|codeium|vscode|antigravity|kiro|zed|jetbrains|intellij");
    private static final String BROWSER_HOST_MARKER = "chrome";
    private static final String ALERT_ONLY_BEHAVIOUR = "alert";

    private static final Map<String, CachedSnapshot> CACHE = new ConcurrentHashMap<>();

    private static class CachedSnapshot {
        final Snapshot snapshot;
        final long loadedAtMs;

        CachedSnapshot(Snapshot snapshot, long loadedAtMs) {
            this.snapshot = snapshot;
            this.loadedAtMs = loadedAtMs;
        }
    }

    public static class DeviceRow {
        public String name;
        public String moduleId;
        public String os = OS_UNKNOWN;
        public String username = "";
        public String version;
        public int lastHeartbeat;
        public boolean extension;
        public String health = HEALTH_HEALTHY;
        public String guardrailMode = MODE_NONE;
        public final Map<String, Integer> toolsByCategory = new LinkedHashMap<>();
        public int unapprovedTools;
        public int personalAccounts;
        public long violations;

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
            row.put("moduleId", moduleId);
            row.put("os", os);
            row.put("owner", username);
            row.put("agentVersion", version);
            row.put("extension", extension);
            row.put("health", health);
            row.put("guardrailMode", guardrailMode);
            row.put("toolCount", toolCount());
            row.put("toolsByCategory", toolsByCategory);
            row.put("unapprovedTools", unapprovedTools);
            row.put("personalAccounts", personalAccounts);
            row.put("violations", violations);
            row.put("lastHeartbeat", lastHeartbeat);
            return row;
        }
    }

    public static class EmployeeRow {
        public String username;
        public final List<String> deviceNames = new ArrayList<>();
        public int toolCount;
        public int unapprovedTools;
        public int personalAccounts;
        public long violations;
        public int lastActive;
        public int riskUnapprovedPoints;
        public int riskPersonalPoints;
        public int riskViolationPoints;

        public int risk() {
            return riskUnapprovedPoints + riskPersonalPoints + riskViolationPoints;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("username", username);
            row.put("deviceCount", deviceNames.size());
            row.put("devices", deviceNames);
            row.put("toolCount", toolCount);
            row.put("unapprovedTools", unapprovedTools);
            row.put("personalAccounts", personalAccounts);
            row.put("violations", violations);
            row.put("risk", risk());
            row.put("riskUnapprovedPoints", riskUnapprovedPoints);
            row.put("riskPersonalPoints", riskPersonalPoints);
            row.put("riskViolationPoints", riskViolationPoints);
            row.put("lastActive", lastActive);
            return row;
        }
    }

    public static class Snapshot {
        public final List<DeviceRow> devices = new ArrayList<>();
        public final List<EmployeeRow> employees = new ArrayList<>();
        public final Map<String, String> latestVersionByOs = new HashMap<>();
        public boolean toolsAvailable = true;
        public boolean violationsAvailable = true;
    }

    public static class PeopleQuery {
        public String text;
        public List<String> osList;
        public List<String> categories;
        public List<String> healthStates;
        public boolean hasUnapproved;
        public boolean usesPersonal;
        public boolean agentIssues;
        public boolean monitorMode;
        public int riskMin;
        public int riskMax = 100;
        public String sortKey;
        public int sortOrder = -1;
        public int skip;
        public int limit = 50;
    }

    public Snapshot getOrBuild(String key, Supplier<Snapshot> loader) {
        CachedSnapshot cached = CACHE.compute(key, (k, existing) -> {
            if (existing != null && System.currentTimeMillis() - existing.loadedAtMs < CACHE_TTL_MS) return existing;
            return new CachedSnapshot(loader.get(), System.currentTimeMillis());
        });
        return cached.snapshot;
    }

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

    public Snapshot build(List<Document> moduleRows, InsightDataBundle bundle, AtlasDeviceStatsService statsService,
                          Map<String, String> latestVersionByOs, int now, int startTs, int endTs) {
        Snapshot snapshot = new Snapshot();
        snapshot.latestVersionByOs.putAll(latestVersionByOs);
        Map<String, DeviceRow> devicesByLowerName = new LinkedHashMap<>();
        for (Document module : moduleRows) {
            DeviceRow device = toDeviceRow(module, latestVersionByOs, now);
            devicesByLowerName.putIfAbsent(device.name.toLowerCase(Locale.ROOT), device);
        }

        if (bundle == null) {
            snapshot.toolsAvailable = false;
            snapshot.violationsAvailable = false;
        } else {
            try {
                addTools(devicesByLowerName, bundle, statsService, startTs, endTs);
                addGuardrailModes(devicesByLowerName.values(), bundle);
            } catch (Exception e) {
                snapshot.toolsAvailable = false;
            }
            snapshot.violationsAvailable = bundle.threatBackendAvailable;
            if (snapshot.violationsAvailable) addViolations(devicesByLowerName, bundle);
        }

        snapshot.devices.addAll(devicesByLowerName.values());
        snapshot.employees.addAll(buildEmployees(snapshot.devices, snapshot.violationsAvailable));
        return snapshot;
    }

    private static DeviceRow toDeviceRow(Document module, Map<String, String> latestVersionByOs, int now) {
        DeviceRow device = new DeviceRow();
        device.name = String.valueOf(module.get("_id"));
        device.moduleId = String.valueOf(module.get("moduleId"));
        device.version = module.getString("currentVersion");
        String username = module.getString("username");
        device.username = AtlasDeviceStatsService.isRealUsername(username) ? username.trim() : "";
        Number heartbeat = (Number) module.get("lastHeartbeatReceived");
        device.lastHeartbeat = heartbeat == null ? 0 : heartbeat.intValue();
        device.extension = module.getString("browserName") != null
                || (device.version != null && device.version.contains("extension"));
        device.os = normalizeOs(module.getString("os"), module.getString("platform"));

        boolean offline = device.lastHeartbeat > 0 && device.lastHeartbeat < now - OFFLINE_SECONDS;
        String latest = latestVersionByOs.get(device.os);
        int[] deviceVersion = parseVersion(device.version);
        boolean outdated = !device.extension && latest != null && deviceVersion != null
                && compareVersions(deviceVersion, parseVersion(latest)) < 0;
        device.health = offline ? HEALTH_OFFLINE : outdated ? HEALTH_OUTDATED : HEALTH_HEALTHY;
        return device;
    }

    private void addTools(Map<String, DeviceRow> devicesByLowerName, InsightDataBundle bundle,
                          AtlasDeviceStatsService statsService, int startTs, int endTs) {
        boolean ranged = startTs > 0 || endTs > 0;
        Map<String, String> remarksByServiceName = statsService.loadServerRemarks();
        for (ApiCollection c : bundle.collections) {
            if (!InsightUtil.hasTagValue(c, Constants.AKTO_ENDPOINT_SOURCE_TAG, Constants.AKTO_ENDPOINT_SOURCE_VALUE)) continue;
            String hostDevice = InsightUtil.deviceIdOf(c);
            if (hostDevice == null || c.getHostName() == null) continue;
            DeviceRow device = devicesByLowerName.get(hostDevice.toLowerCase(Locale.ROOT));
            if (device == null) continue;
            if (ranged && !AtlasDeviceStatsService.seenInWindow(bundle.collectionLastTrafficSeen.get(c.getId()), startTs, endTs)) continue;
            String category = categoryOf(c);
            device.toolsByCategory.merge(category, 1, Integer::sum);
            if (!AtlasDeviceStatsService.isSanctioned(c, bundle.allowlistNamesLower, remarksByServiceName)) device.unapprovedTools++;
            if (InsightUtil.isPersonalAccount(c)) device.personalAccounts++;
        }
    }

    static String categoryOf(ApiCollection c) {
        String[] parts = c.getHostName().split("\\.");
        String marker = parts.length > 1 ? parts[1] : "";
        if (BROWSER_HOST_MARKER.equals(marker) || InsightUtil.hasTag(c, Constants.AKTO_BROWSER_LLM_TAG)) return CAT_BROWSER;
        if (Constants.AKTO_AI_AGENT_TAG.equals(marker)) {
            String vendor = parts.length > 2 ? parts[2].toLowerCase(Locale.ROOT) : "";
            return IDE_VENDOR.matcher(vendor).find() ? CAT_IDE : CAT_AGENTS;
        }
        if (InsightUtil.hasTag(c, Constants.AKTO_MCP_SERVER_TAG) || InsightUtil.hasTag(c, Constants.AKTO_MCP_CLIENT_TAG)) return CAT_MCP;
        return CAT_AGENTS;
    }

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

    private static void addViolations(Map<String, DeviceRow> devicesByLowerName, InsightDataBundle bundle) {
        for (HostSeverityCount counts : bundle.hostSeverityCounts) {
            String hostDevice = AgenticObserveUtil.extractEndpointId(counts.getHost());
            if (hostDevice == null) continue;
            DeviceRow device = devicesByLowerName.get(hostDevice.toLowerCase(Locale.ROOT));
            if (device != null) {
                device.violations += (long) counts.getCritical() + counts.getHigh() + counts.getMedium() + counts.getLow();
            }
        }
    }

    private static List<EmployeeRow> buildEmployees(List<DeviceRow> devices, boolean violationsAvailable) {
        Map<String, EmployeeRow> byUsername = new LinkedHashMap<>();
        for (DeviceRow device : devices) {
            if (device.username.isEmpty()) continue;
            EmployeeRow employee = byUsername.computeIfAbsent(device.username, name -> {
                EmployeeRow row = new EmployeeRow();
                row.username = name;
                return row;
            });
            employee.deviceNames.add(device.name);
            employee.toolCount += device.toolCount();
            employee.unapprovedTools += device.unapprovedTools;
            employee.personalAccounts += device.personalAccounts;
            employee.violations += device.violations;
            employee.lastActive = Math.max(employee.lastActive, device.lastHeartbeat);
        }
        for (EmployeeRow employee : byUsername.values()) {
            double unapprovedShare = employee.toolCount == 0 ? 0 : (double) employee.unapprovedTools / employee.toolCount;
            employee.riskUnapprovedPoints = (int) Math.round(RISK_UNAPPROVED_POINTS * unapprovedShare);
            employee.riskPersonalPoints = (int) Math.round(
                    RISK_PERSONAL_POINTS * (double) Math.min(employee.personalAccounts, RISK_PERSONAL_CAP) / RISK_PERSONAL_CAP);
            employee.riskViolationPoints = violationsAvailable
                    ? (int) Math.round(RISK_VIOLATION_POINTS * (double) Math.min(employee.violations, RISK_VIOLATION_CAP) / RISK_VIOLATION_CAP)
                    : 0;
        }
        return new ArrayList<>(byUsername.values());
    }

    public Map<String, Object> overview(Snapshot snapshot) {
        Map<String, Object> overview = new LinkedHashMap<>();
        Map<String, Integer> health = new LinkedHashMap<>();
        Map<String, Integer> modes = new LinkedHashMap<>();
        for (String state : Arrays.asList(HEALTH_HEALTHY, HEALTH_OUTDATED, HEALTH_OFFLINE)) health.put(state, 0);
        for (String mode : Arrays.asList(MODE_ENFORCE, MODE_MONITOR, MODE_NONE)) modes.put(mode, 0);
        Map<String, Map<String, Object>> osMatrix = new LinkedHashMap<>();
        for (String os : OS_ORDER) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("os", os);
            row.put("devices", 0);
            row.put("totalTools", 0);
            Map<String, Integer> categoryCounts = new LinkedHashMap<>();
            for (String category : CATEGORIES) categoryCounts.put(category, 0);
            row.put("toolsByCategory", categoryCounts);
            osMatrix.put(os, row);
        }

        List<String> outdatedModuleIds = new ArrayList<>();
        for (DeviceRow device : snapshot.devices) {
            health.merge(device.health, 1, Integer::sum);
            modes.merge(device.guardrailMode, 1, Integer::sum);
            if (HEALTH_OUTDATED.equals(device.health)) outdatedModuleIds.add(device.moduleId);
            Map<String, Object> osRow = osMatrix.get(device.os);
            osRow.put("devices", (Integer) osRow.get("devices") + 1);
            osRow.put("totalTools", (Integer) osRow.get("totalTools") + device.toolCount());
            @SuppressWarnings("unchecked")
            Map<String, Integer> categoryCounts = (Map<String, Integer>) osRow.get("toolsByCategory");
            for (String category : CATEGORIES) categoryCounts.merge(category, device.toolsByCategory.get(category), Integer::sum);
        }

        List<Map<String, Object>> osRows = new ArrayList<>();
        for (Map<String, Object> osRow : osMatrix.values()) {
            int devices = (Integer) osRow.get("devices");
            if (devices == 0) continue;
            osRow.put("avgTools", Math.round(10.0 * (Integer) osRow.get("totalTools") / devices) / 10.0);
            osRows.add(osRow);
        }

        int[] histogram = new int[RISK_BUCKETS];
        int highRisk = 0;
        for (EmployeeRow employee : snapshot.employees) {
            histogram[Math.min(employee.risk() * RISK_BUCKETS / 100, RISK_BUCKETS - 1)]++;
            if (employee.risk() >= 70) highRisk++;
        }
        List<Integer> histogramList = new ArrayList<>();
        for (int count : histogram) histogramList.add(count);

        overview.put("deviceCount", snapshot.devices.size());
        overview.put("employeeCount", snapshot.employees.size());
        overview.put("agentHealth", health);
        overview.put("guardrailModes", modes);
        overview.put("outdatedModuleIds", outdatedModuleIds);
        overview.put("latestVersionByOs", snapshot.latestVersionByOs);
        overview.put("osMatrix", osRows);
        overview.put("riskHistogram", histogramList);
        overview.put("highRiskEmployees", highRisk);
        overview.put("toolsAvailable", snapshot.toolsAvailable);
        overview.put("violationsAvailable", snapshot.violationsAvailable);
        return overview;
    }

    public List<Map<String, Object>> pageDevices(Snapshot snapshot, PeopleQuery query, long[] totalOut) {
        List<DeviceRow> matching = new ArrayList<>();
        for (DeviceRow device : snapshot.devices) {
            if (matchesDevice(device, query)) matching.add(device);
        }
        matching.sort(deviceComparator(query));
        totalOut[0] = matching.size();
        List<Map<String, Object>> page = new ArrayList<>();
        for (DeviceRow device : slice(matching, query)) page.add(device.toMap());
        return page;
    }

    public List<Map<String, Object>> pageEmployees(Snapshot snapshot, PeopleQuery query, long[] totalOut) {
        boolean deviceFiltered = hasDeviceFilters(query);
        Set<String> usernamesWithMatchingDevice = new HashSet<>();
        if (deviceFiltered) {
            for (DeviceRow device : snapshot.devices) {
                if (!device.username.isEmpty() && matchesDeviceFilters(device, query)) usernamesWithMatchingDevice.add(device.username);
            }
        }
        List<EmployeeRow> matching = new ArrayList<>();
        for (EmployeeRow employee : snapshot.employees) {
            if (deviceFiltered && !usernamesWithMatchingDevice.contains(employee.username)) continue;
            if (hasText(query) && !contains(employee.username, query.text) && !anyDeviceContains(employee, query.text)) continue;
            if (query.hasUnapproved && employee.unapprovedTools == 0) continue;
            if (query.usesPersonal && employee.personalAccounts == 0) continue;
            if (employee.risk() < query.riskMin || employee.risk() > query.riskMax) continue;
            matching.add(employee);
        }
        matching.sort(employeeComparator(query));
        totalOut[0] = matching.size();
        List<Map<String, Object>> page = new ArrayList<>();
        for (EmployeeRow employee : slice(matching, query)) page.add(employee.toMap());
        return page;
    }

    private static boolean hasDeviceFilters(PeopleQuery query) {
        return notEmpty(query.osList) || notEmpty(query.categories) || notEmpty(query.healthStates);
    }

    private static boolean matchesDeviceFilters(DeviceRow device, PeopleQuery query) {
        if (notEmpty(query.osList) && !query.osList.contains(device.os)) return false;
        if (notEmpty(query.healthStates) && !query.healthStates.contains(device.health)) return false;
        if (notEmpty(query.categories)) {
            boolean hasCategory = false;
            for (String category : query.categories) {
                if (device.toolsByCategory.getOrDefault(category, 0) > 0) {
                    hasCategory = true;
                    break;
                }
            }
            if (!hasCategory) return false;
        }
        return true;
    }

    private static boolean matchesDevice(DeviceRow device, PeopleQuery query) {
        if (!matchesDeviceFilters(device, query)) return false;
        if (hasText(query) && !contains(device.name, query.text) && !contains(device.username, query.text)
                && !contains(device.os, query.text)) return false;
        if (query.agentIssues && HEALTH_HEALTHY.equals(device.health)) return false;
        if (query.monitorMode && !MODE_MONITOR.equals(device.guardrailMode)) return false;
        return !(query.hasUnapproved && device.unapprovedTools == 0);
    }

    private static boolean anyDeviceContains(EmployeeRow employee, String text) {
        for (String deviceName : employee.deviceNames) {
            if (contains(deviceName, text)) return true;
        }
        return false;
    }

    private static boolean hasText(PeopleQuery query) {
        return query.text != null && !query.text.trim().isEmpty();
    }

    private static boolean contains(String value, String text) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(text.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean notEmpty(List<String> values) {
        return values != null && !values.isEmpty();
    }

    private static <T> List<T> slice(List<T> rows, PeopleQuery query) {
        int from = Math.min(Math.max(query.skip, 0), rows.size());
        int to = Math.min(from + Math.min(Math.max(query.limit, 1), 200), rows.size());
        return rows.subList(from, to);
    }

    private static Comparator<DeviceRow> deviceComparator(PeopleQuery query) {
        Comparator<DeviceRow> comparator;
        switch (query.sortKey == null ? "" : query.sortKey) {
            case "name": comparator = Comparator.comparing(d -> d.name.toLowerCase(Locale.ROOT)); break;
            case "owner": comparator = Comparator.comparing(d -> d.username.toLowerCase(Locale.ROOT)); break;
            case "health": comparator = Comparator.comparing(d -> d.health); break;
            case "guardrailMode": comparator = Comparator.comparing(d -> d.guardrailMode); break;
            case "toolCount": comparator = Comparator.comparingInt(DeviceRow::toolCount); break;
            case "unapprovedTools": comparator = Comparator.comparingInt(d -> d.unapprovedTools); break;
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
            case "violations": comparator = Comparator.comparingLong(e -> e.violations); break;
            case "lastActive": comparator = Comparator.comparingInt(e -> e.lastActive); break;
            default: comparator = Comparator.comparingInt(EmployeeRow::risk);
        }
        return query.sortOrder >= 0 ? comparator : comparator.reversed();
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

    static String normalizeOs(String os, String platform) {
        String value = (os != null ? os : platform != null ? platform : "").toLowerCase(Locale.ROOT);
        if (value.startsWith("mac") || value.contains("darwin")) return OS_MAC;
        if (value.startsWith("win")) return OS_WINDOWS;
        if (value.contains("linux") || value.contains("x11") || value.contains("ubuntu")) return OS_LINUX;
        return OS_UNKNOWN;
    }

    public static List<String> emptyIfNull(List<String> values) {
        return values == null ? Collections.emptyList() : values;
    }
}
