package com.akto.service.atlas;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Builds the Atlas discovery overview from per-tool rows; every list is a fixed top-N.
 * Counts are all-time; adoption looks at the last 30 days and the new-tools trend at the last 12 weeks.
 */
public class AtlasDiscoveryService {

    private static final LoggerMaker logger = new LoggerMaker(AtlasDiscoveryService.class, LogDb.DASHBOARD);

    private static final int DECISIONS_LIMIT = 5;
    private static final int ADOPTION_LIMIT = 6;
    private static final int RISK_REACH_LIMIT = 50;
    private static final int DETAIL_LIST_LIMIT = 10;
    private static final int DAY_SECONDS = 24 * 60 * 60;
    private static final int WEEK_SECONDS = 7 * DAY_SECONDS;
    private static final int TREND_WEEKS = 12;
    private static final int ADOPTION_WINDOW_SECONDS = 30 * DAY_SECONDS;
    private static final int NEW_TOOL_SECONDS = WEEK_SECONDS;
    private static final double HIGH_RISK_SCORE = 4.0;

    private final List<AtlasTool> tools = new ArrayList<>();
    private final AtlasPeople people;
    private final int now;
    private final Map<String, AtlasAssetStatus> serverStatuses = new HashMap<>();
    private final AtlasGraphSections graphs;

    public AtlasDiscoveryService(List<AtlasToolRow> rows, Map<String, AtlasAssetStatus> decisions, Set<String> allowlistNamesLower,
                                 AtlasPeople people, int now) {
        this.people = people;
        this.now = now;
        AtlasAssetStatusService statuses = new AtlasAssetStatusService();
        for (AtlasToolRow row : rows) {
            AtlasAssetStatus status = statuses.resolve(row, decisions, allowlistNamesLower);
            tools.add(new AtlasTool(row, AtlasCategory.of(row), status, people.usersOf(row.getDeviceIds()), recentUsersOf(row)));
            if (AtlasAssetStatusService.ROW_TYPE_SERVICE.equals(row.getRowType())) serverStatuses.put(lower(row.getGroupKey()), status);
        }
        this.graphs = new AtlasGraphSections(tools, people, serverStatuses);
    }

    // Employees on devices that used the tool in the adoption window; a tool with no per-device times falls back to its own last-seen.
    private Set<String> recentUsersOf(AtlasToolRow row) {
        int since = now - ADOPTION_WINDOW_SECONDS;
        if (row.getDeviceLastSeen().isEmpty()) {
            boolean recent = row.getLastSeen() == 0 || row.getLastSeen() >= since;
            return recent ? people.usersOf(row.getDeviceIds()) : Collections.<String>emptySet();
        }
        Set<String> devices = new HashSet<>();
        for (Map.Entry<String, Integer> e : row.getDeviceLastSeen().entrySet()) {
            if (e.getValue() >= since) devices.add(e.getKey());
        }
        return people.usersOf(devices);
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
        section(out, unavailable, "agentMcpMatrix", graphs::agentMcpMatrix);
        section(out, unavailable, "teamCategoryMatrix", graphs::teamCategoryMatrix);
        section(out, unavailable, "supplyChain", graphs::supplyChain);
        out.put("unavailableSources", unavailable);
        return out;
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
        List<AtlasTool> matched = tools.stream()
                .filter(t -> StringUtils.isBlank(query.getCategory()) || t.getCategory().equals(query.getCategory()))
                // Tools that cannot be approved or blocked have no review status, so a status filter skips them.
                .filter(t -> StringUtils.isBlank(query.getStatus()) || (t.canDecide() && t.getStatus().name().equals(query.getStatus())))
                .filter(t -> text.isEmpty() || containsText(t.getRow().getName(), text) || containsText(t.getRow().getClientType(), text))
                .sorted(toolOrder(query))
                .collect(Collectors.toList());
        int from = Math.min(Math.max(query.getSkip(), 0), matched.size());
        int to = Math.min(from + Math.max(query.getLimit(), 0), matched.size());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AtlasTool tool : matched.subList(from, to)) rows.add(tableRow(tool));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tools", rows);
        out.put("total", matched.size());
        out.put("categoryCounts", categoryCounts());
        return out;
    }

    // Per-category totals for the tool list; they ignore the status and text filters.
    private Map<String, Integer> categoryCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(AtlasCategory.ALL, tools.size());
        for (AtlasTool tool : tools) counts.merge(tool.getCategory(), 1, Integer::sum);
        return counts;
    }

    private static boolean containsText(String value, String lowerText) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerText);
    }

    private static Comparator<AtlasTool> toolOrder(Query query) {
        Comparator<AtlasTool> base;
        switch (StringUtils.defaultString(query.getSortKey())) {
            case "name": base = Comparator.comparing(t -> StringUtils.defaultString(t.getRow().getName()).toLowerCase(Locale.ROOT)); break;
            case "employees": base = Comparator.comparingInt(t -> t.getUsers().size()); break;
            case "riskScore": base = Comparator.comparingDouble(t -> t.getRow().getRiskScore()); break;
            case "firstSeen": base = Comparator.comparingInt(t -> t.getRow().getFirstSeen()); break;
            case "lastSeen": base = Comparator.comparingInt(t -> t.getRow().getLastSeen()); break;
            case "status": base = Comparator.comparingInt(t -> t.getStatus().ordinal()); break;
            default: base = Comparator.comparingInt(AtlasTool::devices);
        }
        Comparator<AtlasTool> ordered = query.getSortOrder() == 1 ? base : base.reversed();
        return ordered.thenComparing(AtlasTool::key);
    }

    private Map<String, Object> tableRow(AtlasTool tool) {
        AtlasToolRow row = tool.getRow();
        Map<String, Object> out = tool.summary();
        out.put("governable", tool.canDecide());
        out.put("firstSeen", row.getFirstSeen());
        out.put("lastSeen", row.getLastSeen());
        out.put("personal", row.isPersonal());
        out.put("personalDevices", row.getPersonalDevices());
        out.put("localMcp", row.isLocalMcp());
        out.put("misconfigured", row.isMisconfigured());
        out.put("malicious", row.isMalicious());
        return out;
    }

    /** One tool for the flyout: its row, top employees and connections; null when the key is unknown. */
    public Map<String, Object> detail(String key) {
        AtlasTool tool = tools.stream().filter(t -> t.key().equals(key)).findFirst().orElse(null);
        if (tool == null) return null;
        Map<String, Object> out = tableRow(tool);

        // Employees who used the tool most recently first; 0 when no device time is known.
        Map<String, Integer> lastUsed = people.lastSeenPerUser(tool.getRow().getDeviceLastSeen());
        List<Map<String, Object>> users = new ArrayList<>();
        people.devicesPerUser(tool.getRow().getDeviceIds()).entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, Integer> e) -> lastUsed.getOrDefault(e.getKey(), 0)).reversed()
                        .thenComparing(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(DETAIL_LIST_LIMIT)
                .forEach(e -> {
                    Map<String, Object> user = new LinkedHashMap<>();
                    user.put("username", e.getKey());
                    user.put("team", people.teamOfUser(e.getKey()));
                    user.put("devices", e.getValue());
                    user.put("lastUsed", lastUsed.getOrDefault(e.getKey(), 0));
                    users.add(user);
                });
        out.put("users", users);
        out.put("capabilityCollectionIds", tool.getRow().getCapabilityCollectionIds());

        String rowType = tool.getRow().getRowType();
        boolean isAgent = AtlasAssetStatusService.ROW_TYPE_AGENT.equals(rowType);
        boolean isServer = AtlasAssetStatusService.ROW_TYPE_SERVICE.equals(rowType);
        out.put("connectionsKind", isAgent ? "mcpServers" : isServer ? "agents" : null);
        out.put("connections", isAgent ? serversOf(tool) : isServer ? agentsUsing(tool) : new ArrayList<>());
        return out;
    }

    private List<Map<String, Object>> serversOf(AtlasTool agent) {
        return agent.getRow().getMcpDevices().entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, Set<String>> e) -> e.getValue().size()).reversed().thenComparing(Map.Entry::getKey))
                .limit(DETAIL_LIST_LIMIT)
                .map(e -> connection(e.getKey(), e.getValue().size(), serverStatuses.get(lower(e.getKey()))))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> agentsUsing(AtlasTool server) {
        List<Map<String, Object>> agents = new ArrayList<>();
        for (AtlasTool candidate : tools) {
            if (!AtlasAssetStatusService.ROW_TYPE_AGENT.equals(candidate.getRow().getRowType())) continue;
            for (Map.Entry<String, Set<String>> e : candidate.getRow().getMcpDevices().entrySet()) {
                if (e.getKey().equalsIgnoreCase(server.getRow().getGroupKey())) agents.add(connection(candidate.getRow().getName(), e.getValue().size(), candidate.getStatus()));
            }
        }
        agents.sort(Comparator.comparingInt((Map<String, Object> a) -> (Integer) a.get("devices")).reversed());
        return agents.subList(0, Math.min(DETAIL_LIST_LIMIT, agents.size()));
    }

    private static Map<String, Object> connection(String name, int devices, AtlasAssetStatus status) {
        Map<String, Object> connection = new LinkedHashMap<>();
        connection.put("name", name);
        connection.put("devices", devices);
        connection.put("status", AtlasAssetStatus.nameOf(status));
        return connection;
    }

    private static String lower(String value) {
        return StringUtils.lowerCase(value, Locale.ROOT);
    }

    // Only the users on devices with a personal account; a tool with no per-device detail falls back to all its users.
    private Set<String> personalUsersOf(AtlasTool tool) {
        Set<String> personalDevices = tool.getRow().getPersonalDeviceIds();
        return personalDevices.isEmpty() ? tool.getUsers() : people.usersOf(personalDevices);
    }

    private Map<String, Object> tiles() {
        List<Map<String, Object>> categories = new ArrayList<>();
        for (String category : AtlasCategory.ORDER) {
            int count = 0, unapproved = 0, malicious = 0;
            Set<String> personalUsers = new HashSet<>();
            for (AtlasTool tool : tools) {
                if (!category.equals(tool.getCategory())) continue;
                count++;
                if (tool.needsDecision()) unapproved++;
                if (tool.getRow().isMalicious()) malicious++;
                if (tool.getRow().isPersonal()) personalUsers.addAll(personalUsersOf(tool));
            }
            Map<String, Object> tile = new LinkedHashMap<>();
            tile.put("id", category);
            tile.put("count", count);
            tile.put("unapproved", unapproved);
            tile.put("malicious", malicious);
            tile.put("personalAccounts", personalUsers.size());
            categories.add(tile);
        }
        Set<String> devices = new HashSet<>();
        for (AtlasTool tool : tools) devices.addAll(tool.getRow().getDeviceIds());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("categories", categories);
        out.put("devicesWithAi", devices.size());
        return out;
    }

    private Map<String, Object> decisions() {
        return pageDecisions(0, DECISIONS_LIMIT);
    }

    /**
     * One page of the tools worth a decision now: undecided ones that have a risk score, are new or are malicious.
     * Riskiest first, then malicious, then new, then the most widespread.
     */
    public Map<String, Object> pageDecisions(int skip, int limit) {
        List<AtlasTool> pending = tools.stream().filter(AtlasTool::needsDecision)
                .filter(t -> t.getRow().getRiskScore() > 0 || t.getRow().isMalicious() || isNew(t))
                .sorted(Comparator.comparingDouble((AtlasTool t) -> t.getRow().getRiskScore()).reversed()
                        .thenComparing((AtlasTool t) -> !t.getRow().isMalicious())
                        .thenComparing((AtlasTool t) -> !isNew(t))
                        .thenComparing(Comparator.comparingInt(AtlasTool::devices).reversed())
                        .thenComparing(AtlasTool::key))
                .collect(Collectors.toList());
        int from = Math.min(Math.max(skip, 0), pending.size());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AtlasTool tool : pending.subList(from, Math.min(from + Math.max(limit, 0), pending.size()))) {
            Map<String, Object> row = tool.summary();
            row.put("isNew", isNew(tool));
            row.put("why", tool.whyLine());
            rows.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", pending.size());
        out.put("rows", rows);
        return out;
    }

    // First seen within the last NEW_TOOL_SECONDS.
    private boolean isNew(AtlasTool tool) {
        int firstSeen = tool.getRow().getFirstSeen();
        return firstSeen > 0 && firstSeen >= now - NEW_TOOL_SECONDS;
    }

    // The tools most used in the last 30 days, by employees.
    private List<Map<String, Object>> adoption() {
        return tools.stream().filter(t -> !AtlasCategory.SKILLS.equals(t.getCategory()) && !t.getRecentUsers().isEmpty())
                .sorted(Comparator.comparingInt((AtlasTool t) -> t.getRecentUsers().size()).reversed().thenComparing(AtlasTool::key))
                .limit(ADOPTION_LIMIT)
                .map(tool -> {
                    Map<String, Object> row = tool.summary();
                    row.put("employees", tool.getRecentUsers().size());
                    return row;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> riskyConfigs() {
        int malicious = 0, misconfigured = 0, localMcp = 0, personal = 0, highRisk = 0;
        for (AtlasTool tool : tools) {
            AtlasToolRow row = tool.getRow();
            if (row.isMalicious()) malicious++;
            if (row.isMisconfigured()) misconfigured++;
            if (row.isLocalMcp()) localMcp++;
            if (row.isPersonal()) personal++;
            if (tool.needsDecision() && row.getRiskScore() >= HIGH_RISK_SCORE) highRisk++;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(configRow("malicious", malicious));
        rows.add(configRow("misconfigured", misconfigured));
        rows.add(configRow("localMcp", localMcp));
        rows.add(configRow("personal", personal));
        rows.add(configRow("highRiskUnreviewed", highRisk));
        return rows;
    }

    private static Map<String, Object> configRow(String id, int count) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("count", count);
        return row;
    }

    private Map<String, Object> statusCounts() {
        Map<String, Object> counts = new LinkedHashMap<>();
        for (AtlasAssetStatus status : AtlasAssetStatus.values()) counts.put(status.name(), 0);
        int total = 0;
        for (AtlasTool tool : tools) {
            if (!tool.governable()) continue;
            counts.merge(tool.getStatus().name(), 1, (a, b) -> (Integer) a + (Integer) b);
            total++;
        }
        counts.put("total", total);
        // Same tools as the slices above, so the donut centre equals Unapproved plus Under review.
        counts.put("needsDecision", tools.stream().filter(t -> t.governable() && t.needsDecision()).count());
        return counts;
    }

    // Tools first seen in each of the last TREND_WEEKS weeks, split by current status (there is no status history).
    private List<Map<String, Object>> newToolsTrend() {
        int start = now - TREND_WEEKS * WEEK_SECONDS;
        List<Map<String, Object>> weeks = new ArrayList<>();
        for (int i = 0; i < TREND_WEEKS; i++) {
            Map<String, Object> week = new LinkedHashMap<>();
            week.put("periodStart", start + i * WEEK_SECONDS);
            for (AtlasAssetStatus status : AtlasAssetStatus.values()) week.put(status.name(), 0);
            weeks.add(week);
        }
        for (AtlasTool tool : tools) {
            int firstSeen = tool.getRow().getFirstSeen();
            if (!tool.governable() || firstSeen < start) continue;
            weeks.get(Math.min(TREND_WEEKS - 1, (firstSeen - start) / WEEK_SECONDS)).merge(tool.getStatus().name(), 1, (a, b) -> (Integer) a + (Integer) b);
        }
        return weeks;
    }

    private List<Map<String, Object>> riskReach() {
        return tools.stream().filter(AtlasTool::governable)
                .sorted(Comparator.comparingInt(AtlasTool::devices).reversed().thenComparing(AtlasTool::key))
                .limit(RISK_REACH_LIMIT)
                .map(AtlasTool::summary)
                .collect(Collectors.toList());
    }
}
