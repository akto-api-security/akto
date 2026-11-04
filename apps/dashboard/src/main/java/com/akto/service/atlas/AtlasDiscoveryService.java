package com.akto.service.atlas;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Builds the Atlas discovery overview from per-tool rows; every list is a fixed top-N. */
public class AtlasDiscoveryService {

    private static final LoggerMaker logger = new LoggerMaker(AtlasDiscoveryService.class, LogDb.DASHBOARD);

    public static final String CATEGORY_ALL = "all";
    public static final String CAT_AGENTS = "agents";
    public static final String CAT_MCP = "mcp";
    public static final String CAT_BROWSER = "browser";
    public static final String CAT_SKILLS = "skills";
    public static final String CAT_LOCAL = "local";
    public static final List<String> CATEGORIES = Arrays.asList(CAT_AGENTS, CAT_MCP, CAT_BROWSER, CAT_SKILLS, CAT_LOCAL);
    // Categories that appear as nodes in the "AI tools" column of the supply chain.
    private static final List<String> CHAIN_TOOL_CATEGORIES = Arrays.asList(CAT_AGENTS, CAT_BROWSER, CAT_LOCAL);

    // Devices whose user has no team tag are grouped under a blank name.
    public static final String UNTAGGED_TEAM = "";
    public static final String UNKNOWN_USERNAME = "not-available";

    private static final Pattern LOCAL_RUNTIME = Pattern.compile("ollama|lm-?studio|llama\\.cpp|llamafile|gpt4all");

    private static final int DECISIONS_LIMIT = 5;
    private static final int ADOPTION_LIMIT = 6;
    private static final int RISK_REACH_LIMIT = 50;
    private static final int MATRIX_AGENTS = 5;
    private static final int MATRIX_SERVERS = 8;
    private static final int MATRIX_TEAMS = 8;
    private static final int CHAIN_COLUMN_LIMIT = 10;
    private static final int TREND_BUCKETS = 12;
    private static final int DAY_SECONDS = 24 * 60 * 60;
    private static final int NEW_TOOL_SECONDS = 7 * 24 * 60 * 60;
    private static final double HIGH_RISK_SCORE = 4.0;

    /** A tool row with its resolved category, status and employees. */
    @Getter
    public static final class Tool {
        private final AtlasToolRow row;
        private final String category;
        private final AtlasAssetStatus status;
        private final Set<String> users;

        Tool(AtlasToolRow row, String category, AtlasAssetStatus status, Set<String> users) {
            this.row = row;
            this.category = category;
            this.status = status;
            this.users = users;
        }

        public String key() {
            return AtlasAssetStatusService.toolKey(row.getRowType(), row.getGroupKey());
        }

        public int devices() {
            return row.getDeviceIds().size();
        }

        public boolean governable() {
            return AtlasAssetStatusService.isGovernable(row.getRowType());
        }

        public boolean needsDecision() {
            return governable() && (status == AtlasAssetStatus.UNAPPROVED || status == AtlasAssetStatus.UNDER_REVIEW);
        }
    }

    private final List<Tool> allTools = new ArrayList<>();
    private final List<Tool> tools = new ArrayList<>();
    private final Map<String, String> usernameByDevice;
    private final Map<String, String> teamByUser;
    private final int managedDevices;
    private final int startTs;
    private final int endTs;
    private final int now;

    public AtlasDiscoveryService(List<AtlasToolRow> rows, Map<String, AtlasAssetStatus> decisions, Set<String> allowlistNamesLower,
                                 Map<String, String> usernameByDevice, Map<String, String> teamByUser, int managedDevices,
                                 int startTs, int endTs, int now) {
        this.usernameByDevice = usernameByDevice;
        this.teamByUser = teamByUser;
        this.managedDevices = managedDevices;
        this.startTs = startTs;
        this.endTs = endTs;
        this.now = now;
        AtlasAssetStatusService statuses = new AtlasAssetStatusService();
        for (AtlasToolRow row : rows) {
            Tool tool = new Tool(row, categoryOf(row), statuses.resolve(row, decisions, allowlistNamesLower), usersOf(row));
            allTools.add(tool);
            if (seenInRange(row)) tools.add(tool);
        }
    }

    public static String categoryOf(AtlasToolRow row) {
        String type = row.getRowType();
        if (AtlasAssetStatusService.ROW_TYPE_SKILL.equals(type) || AtlasAssetStatusService.ROW_TYPE_PLUGIN.equals(type)) return CAT_SKILLS;
        if (AtlasAssetStatusService.ROW_TYPE_LLM.equals(type)) return CAT_BROWSER;
        if (AtlasAssetStatusService.ROW_TYPE_SERVICE.equals(type)) return CAT_MCP;
        String name = (StringUtils.defaultString(row.getGroupKey()) + " " + StringUtils.defaultString(row.getName())).toLowerCase(Locale.ROOT);
        if (LOCAL_RUNTIME.matcher(name).find()) return CAT_LOCAL;
        return CAT_AGENTS;
    }

    public Map<String, Object> buildOverview() {
        Map<String, Object> out = new LinkedHashMap<>();
        List<String> unavailable = new ArrayList<>();
        section(out, unavailable, "tiles", this::tiles);
        section(out, unavailable, "decisions", this::decisions);
        section(out, unavailable, "adoption", this::adoption);
        section(out, unavailable, "riskyConfigs", this::riskyConfigs);
        section(out, unavailable, "statusCounts", this::statusCounts);
        section(out, unavailable, "newToolsTrend", this::newToolsTrend);
        section(out, unavailable, "riskReach", this::riskReach);
        section(out, unavailable, "agentMcpMatrix", this::agentMcpMatrix);
        section(out, unavailable, "teamCategoryMatrix", this::teamCategoryMatrix);
        section(out, unavailable, "supplyChain", this::supplyChain);
        out.put("unavailableSources", unavailable);
        return out;
    }

    /** Filter, sort and page for the tool list (CSV export). */
    @Getter
    @Setter
    public static final class Query {
        private String category;
        private String status;
        private String text;
        private String sortKey;
        // 1 ascending, anything else descending.
        private int sortOrder;
        private int skip;
        private int limit;
    }

    public Map<String, Object> pageTools(Query query) {
        String text = StringUtils.trimToEmpty(query.getText()).toLowerCase(Locale.ROOT);
        List<Tool> matched = tools.stream()
                .filter(t -> StringUtils.isBlank(query.getCategory()) || t.category.equals(query.getCategory()))
                // Skills and plugins have no review status, so a status filter skips them.
                .filter(t -> StringUtils.isBlank(query.getStatus()) || (t.governable() && t.status.name().equals(query.getStatus())))
                .filter(t -> text.isEmpty() || containsText(t.row.getName(), text) || containsText(t.row.getClientType(), text))
                .sorted(toolOrder(query))
                .collect(Collectors.toList());
        int from = Math.min(Math.max(query.getSkip(), 0), matched.size());
        int to = Math.min(from + Math.max(query.getLimit(), 0), matched.size());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Tool tool : matched.subList(from, to)) rows.add(tableRow(tool));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tools", rows);
        out.put("total", matched.size());
        out.put("categoryCounts", categoryCounts());
        return out;
    }

    // Per-category totals for the tool list; they ignore the status and text filters.
    private Map<String, Integer> categoryCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(CATEGORY_ALL, tools.size());
        for (Tool tool : tools) counts.merge(tool.category, 1, Integer::sum);
        return counts;
    }

    private static boolean containsText(String value, String lowerText) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerText);
    }

    private static Comparator<Tool> toolOrder(Query query) {
        Comparator<Tool> base;
        switch (StringUtils.defaultString(query.getSortKey())) {
            case "name": base = Comparator.comparing(t -> StringUtils.defaultString(t.row.getName()).toLowerCase(Locale.ROOT)); break;
            case "employees": base = Comparator.comparingInt(t -> t.users.size()); break;
            case "riskScore": base = Comparator.comparingDouble(t -> t.row.getRiskScore()); break;
            case "firstSeen": base = Comparator.comparingInt(t -> t.row.getFirstSeen()); break;
            case "lastSeen": base = Comparator.comparingInt(t -> t.row.getLastSeen()); break;
            case "status": base = Comparator.comparingInt(t -> t.status.ordinal()); break;
            default: base = Comparator.comparingInt(Tool::devices);
        }
        Comparator<Tool> ordered = query.getSortOrder() == 1 ? base : base.reversed();
        return ordered.thenComparing(Tool::key);
    }

    private Map<String, Object> tableRow(Tool tool) {
        Map<String, Object> row = toolSummary(tool);
        row.put("governable", tool.governable());
        row.put("firstSeen", tool.row.getFirstSeen());
        row.put("lastSeen", tool.row.getLastSeen());
        row.put("personal", tool.row.isPersonal());
        row.put("personalDevices", tool.row.getPersonalDevices());
        row.put("localMcp", tool.row.isLocalMcp());
        row.put("misconfigured", tool.row.isMisconfigured());
        row.put("malicious", tool.row.isMalicious());
        return row;
    }

    private static final int DETAIL_LIST_LIMIT = 10;

    /** One tool for the flyout: its row, top employees and connections; null when the key is unknown. */
    public Map<String, Object> detail(String key) {
        Tool tool = allTools.stream().filter(t -> t.key().equals(key)).findFirst().orElse(null);
        if (tool == null) return null;
        Map<String, Object> out = tableRow(tool);

        Map<String, Integer> devicesByUser = new HashMap<>();
        for (String deviceId : tool.row.getDeviceIds()) {
            String username = usernameByDevice.get(deviceId);
            if (StringUtils.isNotBlank(username) && !UNKNOWN_USERNAME.equals(username)) devicesByUser.merge(username, 1, Integer::sum);
        }
        List<Map<String, Object>> users = new ArrayList<>();
        devicesByUser.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(DETAIL_LIST_LIMIT)
                .forEach(e -> {
                    Map<String, Object> user = new LinkedHashMap<>();
                    user.put("username", e.getKey());
                    user.put("team", teamByUser.get(e.getKey()));
                    user.put("devices", e.getValue());
                    users.add(user);
                });
        out.put("users", users);
        out.put("capabilityCollectionIds", tool.row.getCapabilityCollectionIds());

        boolean isAgent = AtlasAssetStatusService.ROW_TYPE_AGENT.equals(tool.row.getRowType());
        out.put("connectionsKind", isAgent ? "mcpServers" : AtlasAssetStatusService.ROW_TYPE_SERVICE.equals(tool.row.getRowType()) ? "agents" : null);
        out.put("connections", isAgent ? serversOf(tool) : AtlasAssetStatusService.ROW_TYPE_SERVICE.equals(tool.row.getRowType()) ? agentsUsing(tool) : new ArrayList<>());
        return out;
    }

    private List<Map<String, Object>> serversOf(Tool agent) {
        Map<String, AtlasAssetStatus> statuses = serverStatusByName();
        return agent.row.getMcpDevices().entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, Set<String>> e) -> e.getValue().size()).reversed().thenComparing(Map.Entry::getKey))
                .limit(DETAIL_LIST_LIMIT)
                .map(e -> connection(e.getKey(), e.getValue().size(), statuses.get(e.getKey().toLowerCase(Locale.ROOT))))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> agentsUsing(Tool server) {
        List<Map<String, Object>> agents = new ArrayList<>();
        for (Tool candidate : allTools) {
            if (!AtlasAssetStatusService.ROW_TYPE_AGENT.equals(candidate.row.getRowType())) continue;
            for (Map.Entry<String, Set<String>> e : candidate.row.getMcpDevices().entrySet()) {
                if (e.getKey().equalsIgnoreCase(server.row.getGroupKey())) agents.add(connection(candidate.row.getName(), e.getValue().size(), candidate.status));
            }
        }
        agents.sort(Comparator.comparingInt((Map<String, Object> a) -> (Integer) a.get("devices")).reversed());
        return agents.subList(0, Math.min(DETAIL_LIST_LIMIT, agents.size()));
    }

    private Map<String, Object> connection(String name, int devices, AtlasAssetStatus status) {
        Map<String, Object> connection = new LinkedHashMap<>();
        connection.put("name", name);
        connection.put("devices", devices);
        connection.put("status", status == null ? null : status.name());
        return connection;
    }

    // Status of every known MCP server by lower-cased name, whenever it was last seen.
    private Map<String, AtlasAssetStatus> serverStatusByName() {
        Map<String, AtlasAssetStatus> statuses = new HashMap<>();
        for (Tool tool : allTools) {
            if (AtlasAssetStatusService.ROW_TYPE_SERVICE.equals(tool.row.getRowType())) {
                statuses.put(tool.row.getGroupKey().toLowerCase(Locale.ROOT), tool.status);
            }
        }
        return statuses;
    }

    // One failing section blanks only its own card.
    private void section(Map<String, Object> out, List<String> unavailable, String name, Supplier<Object> body) {
        try {
            out.put(name, body.get());
        } catch (Exception e) {
            logger.error("AtlasDiscoveryService: section " + name + " failed: " + e.getMessage());
            unavailable.add(name);
        }
    }

    private Set<String> usersOf(AtlasToolRow row) {
        Set<String> users = new HashSet<>();
        for (String deviceId : row.getDeviceIds()) {
            String username = usernameByDevice.get(deviceId);
            if (StringUtils.isNotBlank(username) && !UNKNOWN_USERNAME.equals(username)) users.add(username);
        }
        return users;
    }

    // Servers last seen outside the range, which the graphs leave out (lastSeen 0 stays visible).
    private Set<String> serversNotSeenInRange() {
        Set<String> stale = new HashSet<>();
        for (Tool tool : allTools) {
            if (AtlasAssetStatusService.ROW_TYPE_SERVICE.equals(tool.row.getRowType()) && !seenInRange(tool.row)) {
                stale.add(tool.row.getGroupKey().toLowerCase(Locale.ROOT));
            }
        }
        return stale;
    }

    private boolean seenInRange(AtlasToolRow row) {
        int lastSeen = row.getLastSeen();
        if (lastSeen == 0) return true;
        return lastSeen >= startTs && (endTs <= 0 || lastSeen <= endTs);
    }

    private Map<String, Object> tiles() {
        List<Map<String, Object>> categories = new ArrayList<>();
        for (String category : CATEGORIES) {
            int count = 0, unapproved = 0, misconfigured = 0;
            Set<String> personalUsers = new HashSet<>();
            for (Tool tool : tools) {
                if (!category.equals(tool.category)) continue;
                count++;
                if (tool.needsDecision()) unapproved++;
                if (tool.row.isMisconfigured()) misconfigured++;
                if (tool.row.isPersonal()) personalUsers.addAll(tool.users);
            }
            Map<String, Object> tile = new LinkedHashMap<>();
            tile.put("id", category);
            tile.put("count", count);
            tile.put("unapproved", unapproved);
            tile.put("misconfigured", misconfigured);
            tile.put("personalAccounts", personalUsers.size());
            categories.add(tile);
        }
        Set<String> devices = new HashSet<>();
        for (Tool tool : tools) devices.addAll(tool.row.getDeviceIds());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("categories", categories);
        out.put("devicesWithAi", devices.size());
        out.put("managedDevices", Math.max(managedDevices, devices.size()));
        return out;
    }

    private Map<String, Object> decisions() {
        return pageDecisions(0, DECISIONS_LIMIT);
    }

    // One page of the tools that still need a decision, riskiest and most widespread first.
    public Map<String, Object> pageDecisions(int skip, int limit) {
        List<Tool> pending = tools.stream().filter(Tool::needsDecision)
                .sorted(Comparator.comparingDouble((Tool t) -> t.row.getRiskScore()).reversed()
                        .thenComparing(Comparator.comparingInt(Tool::devices).reversed()))
                .collect(Collectors.toList());
        int from = Math.min(Math.max(skip, 0), pending.size());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Tool tool : pending.subList(from, Math.min(from + Math.max(limit, 0), pending.size()))) {
            Map<String, Object> row = toolSummary(tool);
            row.put("isNew", tool.row.getFirstSeen() > 0 && tool.row.getFirstSeen() >= now - NEW_TOOL_SECONDS);
            row.put("why", whyLine(tool));
            rows.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", pending.size());
        out.put("rows", rows);
        return out;
    }

    private Map<String, Object> toolSummary(Tool tool) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", tool.key());
        row.put("groupKey", tool.row.getGroupKey());
        row.put("name", tool.row.getName());
        row.put("clientType", tool.row.getClientType());
        row.put("vendorName", tool.row.getVendorName());
        row.put("category", tool.category);
        row.put("status", tool.status.name());
        row.put("riskScore", tool.row.getRiskScore());
        row.put("devices", tool.devices());
        row.put("employees", tool.users.size());
        return row;
    }

    private String whyLine(Tool tool) {
        List<String> parts = new ArrayList<>();
        if (tool.row.isMalicious()) parts.add("Flagged as malicious");
        if (StringUtils.isNotBlank(tool.row.getRiskReason())) parts.add(tool.row.getRiskReason());
        if (tool.row.isPersonal()) parts.add("Personal accounts");
        if (tool.row.isLocalMcp()) parts.add("Local MCP server");
        if (tool.row.isMisconfigured()) parts.add("Misconfigured on " + tool.row.getMisconfiguredDevices() + " devices");
        parts.add(tool.devices() + (tool.devices() == 1 ? " device" : " devices"));
        return String.join(" · ", parts);
    }

    private List<Map<String, Object>> adoption() {
        Set<String> everyone = new HashSet<>();
        for (Tool tool : tools) everyone.addAll(tool.users);
        List<Tool> ranked = tools.stream().filter(t -> !CAT_SKILLS.equals(t.category) && !t.users.isEmpty())
                .sorted(Comparator.comparingInt((Tool t) -> t.users.size()).reversed())
                .collect(Collectors.toList());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Tool tool : ranked.subList(0, Math.min(ADOPTION_LIMIT, ranked.size()))) {
            Map<String, Object> row = toolSummary(tool);
            row.put("percent", Math.round(100.0 * tool.users.size() / Math.max(1, everyone.size())));
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> riskyConfigs() {
        int malicious = 0, misconfigured = 0, localMcp = 0, personal = 0, highRisk = 0;
        for (Tool tool : tools) {
            if (tool.row.isMalicious()) malicious++;
            if (tool.row.isMisconfigured()) misconfigured++;
            if (tool.row.isLocalMcp()) localMcp++;
            if (tool.row.isPersonal()) personal++;
            if (tool.needsDecision() && tool.row.getRiskScore() >= HIGH_RISK_SCORE) highRisk++;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(configRow("malicious", malicious));
        rows.add(configRow("misconfigured", misconfigured));
        rows.add(configRow("localMcp", localMcp));
        rows.add(configRow("personal", personal));
        rows.add(configRow("highRiskUnreviewed", highRisk));
        return rows;
    }

    private Map<String, Object> configRow(String id, int count) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("count", count);
        return row;
    }

    private Map<String, Object> statusCounts() {
        Map<String, Object> counts = new LinkedHashMap<>();
        for (AtlasAssetStatus status : AtlasAssetStatus.values()) counts.put(status.name(), 0);
        int total = 0;
        for (Tool tool : tools) {
            if (!tool.governable()) continue;
            counts.merge(tool.status.name(), 1, (a, b) -> (Integer) a + (Integer) b);
            total++;
        }
        counts.put("total", total);
        counts.put("needsDecision", tools.stream().filter(Tool::needsDecision).count());
        return counts;
    }

    // Tools first seen in the selected range, in up to TREND_BUCKETS equal periods, split by current status (no status history).
    private List<Map<String, Object>> newToolsTrend() {
        int end = endTs > 0 ? endTs : now;
        int start = startTs > 0 ? startTs : earliestFirstSeen(end);
        int period = Math.max(DAY_SECONDS, (int) Math.ceil((end - start) / (double) TREND_BUCKETS));
        int count = Math.max(1, (int) Math.ceil((end - start) / (double) period));
        List<Map<String, Object>> periods = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("periodStart", start + i * period);
            for (AtlasAssetStatus status : AtlasAssetStatus.values()) entry.put(status.name(), 0);
            periods.add(entry);
        }
        for (Tool tool : allTools) {
            int first = tool.row.getFirstSeen();
            if (!tool.governable() || first < start || first > end) continue;
            periods.get(Math.min(count - 1, (first - start) / period)).merge(tool.status.name(), 1, (a, b) -> (Integer) a + (Integer) b);
        }
        return periods;
    }

    // The start of an all-time range: the oldest first-seen time, or a quarter back when no tool has one.
    private int earliestFirstSeen(int end) {
        return allTools.stream().filter(Tool::governable).mapToInt(t -> t.row.getFirstSeen()).filter(first -> first > 0).min()
                .orElse(end - TREND_BUCKETS * 7 * DAY_SECONDS);
    }

    private List<Map<String, Object>> riskReach() {
        return tools.stream().filter(Tool::governable)
                .sorted(Comparator.comparingInt(Tool::devices).reversed())
                .limit(RISK_REACH_LIMIT)
                .map(this::toolSummary)
                .collect(Collectors.toList());
    }

    private Map<String, Object> agentMcpMatrix() {
        List<Tool> agents = tools.stream()
                .filter(t -> AtlasAssetStatusService.ROW_TYPE_AGENT.equals(t.row.getRowType()) && !t.row.getMcpDevices().isEmpty())
                .sorted(Comparator.comparingInt(Tool::devices).reversed())
                .collect(Collectors.toList());
        Set<String> staleServers = serversNotSeenInRange();
        Map<String, Set<String>> devicesByServer = new HashMap<>();
        for (Tool agent : agents) {
            for (Map.Entry<String, Set<String>> e : agent.row.getMcpDevices().entrySet()) {
                String server = e.getKey().toLowerCase(Locale.ROOT);
                if (!staleServers.contains(server)) devicesByServer.computeIfAbsent(server, k -> new HashSet<>()).addAll(e.getValue());
            }
        }
        List<String> servers = devicesByServer.keySet().stream()
                .sorted(Comparator.comparingInt((String s) -> devicesByServer.get(s).size()).reversed().thenComparing(s -> s))
                .limit(MATRIX_SERVERS).collect(Collectors.toList());
        Map<String, AtlasAssetStatus> serverStatus = serverStatusByName();

        List<Map<String, Object>> serverCols = new ArrayList<>();
        for (String server : servers) {
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("name", server);
            AtlasAssetStatus status = serverStatus.get(server);
            col.put("status", status == null ? null : status.name());
            serverCols.add(col);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Tool agent : agents.subList(0, Math.min(MATRIX_AGENTS, agents.size()))) {
            Map<String, Integer> byServer = new HashMap<>();
            for (Map.Entry<String, Set<String>> e : agent.row.getMcpDevices().entrySet()) {
                byServer.merge(e.getKey().toLowerCase(Locale.ROOT), e.getValue().size(), Integer::sum);
            }
            List<Integer> cells = new ArrayList<>();
            for (String server : servers) cells.add(byServer.getOrDefault(server, 0));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", agent.key());
            row.put("name", agent.row.getName());
            row.put("cells", cells);
            rows.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("servers", serverCols);
        out.put("rows", rows);
        return out;
    }

    // Share of each tagged team's employees using each category; empty when no device carries a team tag.
    private Map<String, Object> teamCategoryMatrix() {
        Map<String, Set<String>> members = new HashMap<>();
        for (Map.Entry<String, String> e : teamByUser.entrySet()) {
            members.computeIfAbsent(e.getValue(), k -> new HashSet<>()).add(e.getKey());
        }
        Map<String, Map<String, Set<String>>> adopters = new HashMap<>();
        for (Tool tool : tools) {
            for (String user : tool.users) {
                String team = teamByUser.get(user);
                if (team == null) continue;
                adopters.computeIfAbsent(team, k -> new HashMap<>()).computeIfAbsent(tool.category, k -> new HashSet<>()).add(user);
            }
        }
        List<String> teams = members.keySet().stream()
                .sorted(Comparator.comparingInt((String t) -> members.get(t).size()).reversed().thenComparing(t -> t))
                .limit(MATRIX_TEAMS).collect(Collectors.toList());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String team : teams) {
            int size = members.get(team).size();
            List<Long> percents = new ArrayList<>();
            for (String category : CATEGORIES) {
                Set<String> using = adopters.getOrDefault(team, new HashMap<>()).get(category);
                percents.add(using == null ? 0L : Math.min(100L, Math.round(100.0 * using.size() / size)));
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", team);
            row.put("size", size);
            row.put("percents", percents);
            rows.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("categories", CATEGORIES);
        out.put("rows", rows);
        return out;
    }

    // Teams -> AI tools -> MCP servers; edge weight is devices. Each column keeps its busiest nodes.
    private Map<String, Object> supplyChain() {
        List<Tool> chainTools = tools.stream()
                .filter(t -> CHAIN_TOOL_CATEGORIES.contains(t.category) && t.governable())
                .sorted(Comparator.comparingInt(Tool::devices).reversed())
                .limit(CHAIN_COLUMN_LIMIT).collect(Collectors.toList());

        Set<String> staleServers = serversNotSeenInRange();
        Map<String, Set<String>> devicesByServer = new HashMap<>();
        for (Tool tool : chainTools) {
            for (Map.Entry<String, Set<String>> e : tool.row.getMcpDevices().entrySet()) {
                String server = e.getKey().toLowerCase(Locale.ROOT);
                if (!staleServers.contains(server)) devicesByServer.computeIfAbsent(server, k -> new HashSet<>()).addAll(e.getValue());
            }
        }
        List<String> servers = devicesByServer.keySet().stream()
                .sorted(Comparator.comparingInt((String s) -> devicesByServer.get(s).size()).reversed().thenComparing(s -> s))
                .limit(CHAIN_COLUMN_LIMIT).collect(Collectors.toList());

        Map<String, Map<String, Integer>> teamToolDevices = new HashMap<>();
        // Distinct devices per team: a device that runs several of these tools is counted once.
        Map<String, Set<String>> devicesByTeam = new HashMap<>();
        for (Tool tool : chainTools) {
            Map<String, Integer> byTeam = new HashMap<>();
            for (String deviceId : tool.row.getDeviceIds()) {
                String team = teamOfDevice(deviceId);
                byTeam.merge(team, 1, Integer::sum);
                devicesByTeam.computeIfAbsent(team, k -> new HashSet<>()).add(deviceId);
            }
            teamToolDevices.put(tool.key(), byTeam);
        }
        List<String> teams = devicesByTeam.keySet().stream()
                .sorted(Comparator.comparingInt((String t) -> devicesByTeam.get(t).size()).reversed().thenComparing(t -> t))
                .limit(CHAIN_COLUMN_LIMIT).collect(Collectors.toList());

        Map<String, AtlasAssetStatus> serverStatus = serverStatusByName();

        Map<String, Integer> headcount = new HashMap<>();
        for (String team : teamByUser.values()) headcount.merge(team, 1, Integer::sum);
        List<Map<String, Object>> teamNodes = new ArrayList<>();
        for (String team : teams) {
            Map<String, Object> teamNode = node("team:" + team, team, null, devicesByTeam.get(team).size(), null);
            // Untagged devices have no known headcount.
            if (headcount.containsKey(team)) teamNode.put("employees", headcount.get(team));
            teamNodes.add(teamNode);
        }
        List<Map<String, Object>> toolNodes = new ArrayList<>();
        for (Tool tool : chainTools) {
            Map<String, Object> toolNode = node("tool:" + tool.key(), tool.row.getName(), tool.category, tool.devices(), tool.status);
            toolNode.put("clientType", tool.row.getClientType());
            toolNodes.add(toolNode);
        }
        List<Map<String, Object>> serverNodes = new ArrayList<>();
        for (String server : servers) serverNodes.add(node("mcp:" + server, server, CAT_MCP, devicesByServer.get(server).size(), serverStatus.get(server)));

        List<Map<String, Object>> edges = new ArrayList<>();
        for (Tool tool : chainTools) {
            for (String team : teams) {
                Integer devices = teamToolDevices.get(tool.key()).get(team);
                if (devices != null) edges.add(edge("team:" + team, "tool:" + tool.key(), devices));
            }
            for (Map.Entry<String, Set<String>> e : tool.row.getMcpDevices().entrySet()) {
                String server = e.getKey().toLowerCase(Locale.ROOT);
                if (servers.contains(server) && !e.getValue().isEmpty()) edges.add(edge("tool:" + tool.key(), "mcp:" + server, e.getValue().size()));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("columns", Arrays.asList(teamNodes, toolNodes, serverNodes));
        out.put("edges", edges);
        return out;
    }

    private String teamOfDevice(String deviceId) {
        String team = teamByUser.get(usernameByDevice.get(deviceId));
        return team == null ? UNTAGGED_TEAM : team;
    }

    private Map<String, Object> node(String id, String label, String category, int devices, AtlasAssetStatus status) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("label", label);
        node.put("category", category);
        node.put("devices", devices);
        node.put("status", status == null ? null : status.name());
        return node;
    }

    private Map<String, Object> edge(String source, String target, int weight) {
        Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("source", source);
        edge.put("target", target);
        edge.put("weight", weight);
        return edge;
    }
}
