package com.akto.service.posture;

import com.akto.util.enums.GlobalEnums.Severity;
import com.mongodb.BasicDBObject;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Builds the agent inventory page from its rows: the Summary and Analytics roll-up, and the table filtered, sorted and paged. */
public class ArgusInventoryService {

    private static final int WEEK_SECONDS = 7 * 86400;
    private static final int WEEKS = 12;
    private static final int RECENT_LIMIT = 5;
    private static final int TOOLS_LIMIT = 8;
    private static final int TOP_TEAMS = 7;
    private static final int POINTS_LIMIT = 1000;
    private static final String NO_TEAM = "none";

    private final List<ArgusInventoryRow> rows;
    private final int now;

    public ArgusInventoryService(List<ArgusInventoryRow> rows, int now) {
        this.rows = rows;
        this.now = now;
    }

    /** Filter, sort and page for the agents table; a blank field does not filter. */
    @Getter
    @Setter
    public static final class Query {
        // One of the flags in ArgusInventoryRow.matchesFlag.
        private String flag;
        // One of atRisk, attention, healthy, from the table's Posture filter; it narrows whatever flag is also set.
        private String posture;
        private String platform;
        private List<String> platforms;
        // A team name, or "none" for agents with no owning team.
        private String team;
        private String type;
        // One of ArgusInventoryRow.QUADRANTS.
        private String quad;
        // One of ArgusInventoryRow.LAST_USED.
        private String used;
        private String tool;
        // Created this many weeks ago; null for any.
        private Integer week;
        private String text;
        private String sortKey;
        // 1 ascending, anything else descending.
        private int sortOrder;
        private int skip;
        private int limit;
    }

    /** Everything the Summary and Analytics tabs show. */
    public BasicDBObject buildSummary() {
        BasicDBObject summary = new BasicDBObject();
        summary.put("generatedAt", now);
        summary.put("counts", counts());
        summary.put("posture", postureGroups());
        summary.put("trend", trend());
        summary.put("combos", combos());
        summary.put("platforms", platforms());
        summary.put("teams", teams());
        summary.put("recent", recent());
        summary.put("quads", quadrants());
        summary.put("tools", tools());
        summary.put("createdPerWeek", createdPerWeek());
        summary.put("lastUsed", lastUsed());
        summary.put("points", points());
        return summary;
    }

    /** One page of the agents table, with how many matched. */
    public BasicDBObject pageAgents(Query query) {
        String text = StringUtils.trimToEmpty(query.getText()).toLowerCase(Locale.ROOT);
        List<ArgusInventoryRow> matched = rows.stream()
                .filter(row -> matches(row, query, text))
                .sorted(rowOrder(query))
                .collect(Collectors.toList());
        int from = Math.min(Math.max(query.getSkip(), 0), matched.size());
        int to = Math.min(from + Math.max(query.getLimit(), 0), matched.size());
        List<BasicDBObject> agents = new ArrayList<>();
        for (ArgusInventoryRow row : matched.subList(from, to)) agents.add(tableRow(row));
        BasicDBObject page = new BasicDBObject();
        page.put("agents", agents);
        page.put("total", matched.size());
        page.put("generatedAt", now);
        return page;
    }

    private boolean matches(ArgusInventoryRow row, Query query, String text) {
        if (StringUtils.isNotBlank(query.getFlag()) && !row.matchesFlag(query.getFlag())) return false;
        if (StringUtils.isNotBlank(query.getPosture()) && !row.matchesFlag(query.getPosture())) return false;
        if (StringUtils.isNotBlank(query.getPlatform()) && !row.getPlatform().equals(query.getPlatform())) return false;
        if (query.getPlatforms() != null && !query.getPlatforms().isEmpty() && !query.getPlatforms().contains(row.getPlatform())) return false;
        if (StringUtils.isNotBlank(query.getTeam()) && !hasTeam(row, query.getTeam())) return false;
        if (StringUtils.isNotBlank(query.getType()) && !row.getType().equals(query.getType())) return false;
        if (StringUtils.isNotBlank(query.getQuad()) && !row.quadrant().equals(query.getQuad())) return false;
        if (StringUtils.isNotBlank(query.getUsed()) && !row.lastUsed(now).equals(query.getUsed())) return false;
        if (query.getWeek() != null && query.getWeek() != row.weeksAgo(now)) return false;
        if (StringUtils.isNotBlank(query.getTool()) && !row.getToolNames().contains(query.getTool())) return false;
        return text.isEmpty() || row.matchesText(text);
    }

    private static boolean hasTeam(ArgusInventoryRow row, String team) {
        return NO_TEAM.equals(team) ? row.getTeams().isEmpty() : row.getTeams().contains(team);
    }

    private static Comparator<ArgusInventoryRow> rowOrder(Query query) {
        Comparator<ArgusInventoryRow> base;
        switch (StringUtils.defaultString(query.getSortKey())) {
            case "name": base = Comparator.comparing(row -> row.getName().toLowerCase(Locale.ROOT)); break;
            case "tools": base = Comparator.comparingInt(ArgusInventoryRow::getTools); break;
            case "policies": base = Comparator.comparingInt(ArgusInventoryRow::getPolicies); break;
            case "seenTs": base = Comparator.comparingInt(ArgusInventoryRow::getSeenTs); break;
            case "createdTs": base = Comparator.comparingInt(ArgusInventoryRow::getCreatedTs); break;
            default: base = Comparator.comparingInt(ArgusInventoryRow::getRisk);
        }
        Comparator<ArgusInventoryRow> ordered = query.getSortOrder() == 1 ? base : base.reversed();
        return ordered.thenComparing(ArgusInventoryRow::getName);
    }

    private BasicDBObject tableRow(ArgusInventoryRow row) {
        BasicDBObject out = new BasicDBObject();
        out.put("id", row.getId());
        out.put("name", row.getName());
        out.put("type", row.getType());
        out.put("platform", row.getPlatform());
        out.put("teams", row.getTeams());
        out.put("risk", row.getRisk());
        out.put("tools", row.getTools());
        out.put("privileged", row.getPrivileged());
        out.put("policies", row.getPolicies());
        out.put("createdTs", row.getCreatedTs());
        out.put("seenTs", row.getSeenTs());
        out.put("reason", row.reason(now));
        out.put("dormant", row.isDormant());
        return out;
    }

    // How many of the rows have the flag.
    private static long count(List<ArgusInventoryRow> list, String flag) {
        return list.stream().filter(row -> row.matchesFlag(flag)).count();
    }

    private List<ArgusInventoryRow> withFlag(String flag) {
        return rows.stream().filter(row -> row.matchesFlag(flag)).collect(Collectors.toList());
    }

    private BasicDBObject counts() {
        long critical = 0;
        long noTeamHigh = 0;
        long sharedByThree = 0;
        for (ArgusInventoryRow row : rows) {
            if (Severity.CRITICAL.name().equals(ArgusPostureUtils.severityForScore(row.getRisk()))) critical++;
            if (row.getTeams().isEmpty() && row.isHigh()) noTeamHigh++;
            if (row.getTeams().size() >= 3) sharedByThree++;
        }
        BasicDBObject counts = new BasicDBObject();
        counts.put("total", rows.size());
        counts.put("newThisWeek", count(rows, "new"));
        counts.put("high", count(rows, "high"));
        counts.put("critical", critical);
        counts.put("noPolicy", count(rows, "noPolicy"));
        counts.put("dormant", count(rows, "dormant"));
        counts.put("noTeam", count(rows, "noTeam"));
        counts.put("noTeamHigh", noTeamHigh);
        counts.put("sharedByThree", sharedByThree);
        return counts;
    }

    private List<BasicDBObject> postureGroups() {
        List<BasicDBObject> groups = new ArrayList<>();
        groups.add(postureGroup(ArgusInventoryRow.AT_RISK, "At risk"));
        groups.add(postureGroup(ArgusInventoryRow.ATTENTION, "Needs attention"));
        groups.add(postureGroup(ArgusInventoryRow.HEALTHY, "Healthy"));
        return groups;
    }

    private BasicDBObject postureGroup(String key, String label) {
        BasicDBObject group = new BasicDBObject();
        group.put("key", key);
        group.put("label", label);
        group.put("count", count(rows, key));
        return group;
    }

    // Weekly points, oldest first; the at-risk line is "at risk today, by when it was created" since past scores are not stored.
    private List<BasicDBObject> trend() {
        List<BasicDBObject> points = new ArrayList<>();
        for (int weeksBack = WEEKS; weeksBack >= 0; weeksBack--) {
            int at = now - weeksBack * WEEK_SECONDS;
            int total = 0;
            int atRisk = 0;
            for (ArgusInventoryRow row : rows) {
                if (row.getCreatedTs() > at) continue;
                total++;
                if (row.matchesFlag(ArgusInventoryRow.AT_RISK)) atRisk++;
            }
            BasicDBObject point = new BasicDBObject();
            point.put("ts", at);
            point.put("total", total);
            point.put("atRisk", atRisk);
            points.add(point);
        }
        return points;
    }

    // The risky combinations; each card's id is the flag that opens its assets.
    private List<BasicDBObject> combos() {
        List<BasicDBObject> combos = new ArrayList<>();
        addCombo(combos, "comboExposed", "critical", "Anyone can reach it, and its tools can delete data or read credentials.",
                "Exposed to the internet", "Dangerous tools");
        addCombo(combos, "comboShadow", "critical", "Nobody reviewed it, and it handles sensitive data.",
                "Shadow AI", "Sensitive data");
        addCombo(combos, "comboUnreviewed", "caution", "Nobody approved it, and its tools go beyond read-only access.",
                "Unreviewed", "Privileged tools");
        addCombo(combos, "comboDormant", "caution", "Nobody uses it, but its tools can still act on your systems.",
                "Dormant", "Privileged tools");
        return combos;
    }

    // Adds a card unless no asset has the flag.
    private void addCombo(List<BasicDBObject> combos, String flag, String tone, String why, String... chips) {
        List<ArgusInventoryRow> hits = withFlag(flag);
        if (hits.isEmpty()) return;
        BasicDBObject combo = new BasicDBObject();
        combo.put("id", flag);
        combo.put("tone", tone);
        combo.put("count", hits.size());
        combo.put("parts", Arrays.asList(chips));
        combo.put("why", why);
        combo.put("platforms", platformCounts(hits));
        combos.add(combo);
    }

    // Platforms with how many of the given rows are on each, most first.
    private static List<BasicDBObject> platformCounts(List<ArgusInventoryRow> list) {
        Map<String, Integer> counts = new HashMap<>();
        for (ArgusInventoryRow row : list) counts.merge(row.getPlatform(), 1, Integer::sum);
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()));
        List<BasicDBObject> out = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : entries) out.add(nameCount(entry.getKey(), entry.getValue()));
        return out;
    }

    private static BasicDBObject nameCount(String name, int count) {
        BasicDBObject out = new BasicDBObject();
        out.put("name", name);
        out.put("count", count);
        return out;
    }

    // The biggest platform first and "Other" last.
    private List<BasicDBObject> platforms() {
        Map<String, List<ArgusInventoryRow>> byPlatform = new LinkedHashMap<>();
        for (ArgusInventoryRow row : rows) byPlatform.computeIfAbsent(row.getPlatform(), key -> new ArrayList<>()).add(row);
        List<Map.Entry<String, List<ArgusInventoryRow>>> entries = new ArrayList<>(byPlatform.entrySet());
        entries.sort(Comparator.comparing((Map.Entry<String, List<ArgusInventoryRow>> e) -> ArgusDiscoveryService.OTHER_PLATFORM.equals(e.getKey()))
                .thenComparing(e -> -e.getValue().size()));
        List<BasicDBObject> platforms = new ArrayList<>();
        for (Map.Entry<String, List<ArgusInventoryRow>> entry : entries) {
            List<ArgusInventoryRow> list = entry.getValue();
            BasicDBObject platform = new BasicDBObject();
            platform.put("name", entry.getKey());
            platform.put("total", list.size());
            platform.put("atRisk", count(list, ArgusInventoryRow.AT_RISK));
            platform.put("attention", count(list, ArgusInventoryRow.ATTENTION));
            platform.put("healthy", count(list, ArgusInventoryRow.HEALTHY));
            platform.put("high", count(list, "high"));
            platform.put("noPolicy", count(list, "noPolicy"));
            platform.put("dormant", count(list, "dormant"));
            platform.put("types", typeCounts(list));
            platforms.add(platform);
        }
        return platforms;
    }

    private static List<BasicDBObject> typeCounts(List<ArgusInventoryRow> list) {
        Map<String, Integer> counts = new TreeMap<>();
        for (ArgusInventoryRow row : list) counts.merge(row.getType(), 1, Integer::sum);
        List<BasicDBObject> out = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) out.add(nameCount(entry.getKey(), entry.getValue()));
        return out;
    }

    // "No owning team" first, then the teams with the most agents at risk; an agent with several owners counts for each.
    private List<BasicDBObject> teams() {
        Map<String, List<ArgusInventoryRow>> byTeam = new LinkedHashMap<>();
        for (ArgusInventoryRow row : rows) {
            for (String team : row.getTeams()) byTeam.computeIfAbsent(team, key -> new ArrayList<>()).add(row);
        }
        List<Map.Entry<String, List<ArgusInventoryRow>>> entries = new ArrayList<>(byTeam.entrySet());
        entries.sort(Comparator.comparingLong((Map.Entry<String, List<ArgusInventoryRow>> e) -> -count(e.getValue(), ArgusInventoryRow.AT_RISK))
                .thenComparingInt(e -> -e.getValue().size()));
        List<BasicDBObject> teams = new ArrayList<>();
        teams.add(teamStats("No owning team", true, withFlag("noTeam")));
        for (Map.Entry<String, List<ArgusInventoryRow>> entry : entries.subList(0, Math.min(TOP_TEAMS, entries.size()))) {
            teams.add(teamStats(entry.getKey(), false, entry.getValue()));
        }
        return teams;
    }

    private static BasicDBObject teamStats(String name, boolean none, List<ArgusInventoryRow> list) {
        BasicDBObject team = new BasicDBObject();
        team.put("name", name);
        team.put("none", none);
        team.put("total", list.size());
        team.put("atRisk", count(list, ArgusInventoryRow.AT_RISK));
        team.put("attention", count(list, ArgusInventoryRow.ATTENTION));
        team.put("healthy", count(list, ArgusInventoryRow.HEALTHY));
        team.put("high", count(list, "high"));
        return team;
    }

    private List<BasicDBObject> recent() {
        List<ArgusInventoryRow> newest = new ArrayList<>(rows);
        newest.sort(Comparator.comparingInt(ArgusInventoryRow::getCreatedTs).reversed());
        List<BasicDBObject> recent = new ArrayList<>();
        for (ArgusInventoryRow row : newest.subList(0, Math.min(RECENT_LIMIT, newest.size()))) {
            BasicDBObject out = new BasicDBObject();
            out.put("id", row.getId());
            out.put("name", row.getName());
            out.put("platform", row.getPlatform());
            out.put("type", row.getType());
            out.put("createdTs", row.getCreatedTs());
            out.put("policies", row.getPolicies());
            recent.add(out);
        }
        return recent;
    }

    private BasicDBObject quadrants() {
        BasicDBObject quadrants = new BasicDBObject();
        for (String quadrant : ArgusInventoryRow.QUADRANTS) {
            quadrants.put(quadrant, rows.stream().filter(row -> row.quadrant().equals(quadrant)).count());
        }
        return quadrants;
    }

    // Privileged tools first, then the most common.
    private List<BasicDBObject> tools() {
        Map<String, List<ArgusInventoryRow>> byTool = new HashMap<>();
        for (ArgusInventoryRow row : rows) {
            for (String tool : row.getToolNames()) byTool.computeIfAbsent(tool, key -> new ArrayList<>()).add(row);
        }
        List<BasicDBObject> tools = new ArrayList<>();
        for (Map.Entry<String, List<ArgusInventoryRow>> entry : byTool.entrySet()) {
            BasicDBObject tool = new BasicDBObject();
            tool.put("name", entry.getKey());
            tool.put("assets", entry.getValue().size());
            tool.put("high", count(entry.getValue(), "high"));
            tool.put("privileged", entry.getValue().stream().anyMatch(row -> row.getPrivilegedToolNames().contains(entry.getKey())));
            tools.add(tool);
        }
        tools.sort(Comparator.comparing((BasicDBObject tool) -> !tool.getBoolean("privileged"))
                .thenComparing(tool -> -tool.getInt("assets"))
                .thenComparing(tool -> tool.getString("name")));
        return new ArrayList<>(tools.subList(0, Math.min(TOOLS_LIMIT, tools.size())));
    }

    // Created per week, the oldest of the last 12 weeks first.
    private List<Long> createdPerWeek() {
        List<Long> created = new ArrayList<>();
        for (int weeksBack = WEEKS - 1; weeksBack >= 0; weeksBack--) {
            int week = weeksBack;
            created.add(rows.stream().filter(row -> row.weeksAgo(now) == week).count());
        }
        return created;
    }

    private List<BasicDBObject> lastUsed() {
        List<BasicDBObject> buckets = new ArrayList<>();
        for (String key : ArgusInventoryRow.LAST_USED) {
            BasicDBObject bucket = new BasicDBObject();
            bucket.put("key", key);
            bucket.put("count", rows.stream().filter(row -> row.lastUsed(now).equals(key)).count());
            buckets.add(bucket);
        }
        return buckets;
    }

    // The riskiest assets for the plot; the rows come riskiest first.
    private List<BasicDBObject> points() {
        List<BasicDBObject> points = new ArrayList<>();
        for (ArgusInventoryRow row : rows.subList(0, Math.min(POINTS_LIMIT, rows.size()))) {
            BasicDBObject point = new BasicDBObject();
            point.put("id", row.getId());
            point.put("name", row.getName());
            point.put("platform", row.getPlatform());
            point.put("risk", row.getRisk());
            point.put("tools", row.getTools());
            point.put("seenTs", row.getSeenTs());
            points.add(point);
        }
        return points;
    }
}
