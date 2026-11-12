package com.akto.service.atlas;

import com.akto.util.AgenticObserveUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** The overview's matrices and the team -> tool -> MCP server graph; every list is a fixed top-N. */
final class AtlasGraphSections {

    private static final int MATRIX_AGENTS = 5;
    private static final int MATRIX_SERVERS = 8;
    private static final int MATRIX_TEAMS = 8;
    // Skills have no people-page filter to open from a cell, so they are left out of the team matrix.
    private static final List<String> TEAM_MATRIX_CATEGORIES = AtlasCategory.ORDER.stream().filter(c -> !AtlasCategory.SKILLS.equals(c)).collect(Collectors.toList());
    private static final int CHAIN_COLUMN_LIMIT = 10;
    // Categories that appear as nodes in the "AI tools" column of the supply chain.
    private static final List<String> CHAIN_TOOL_CATEGORIES = Arrays.asList(AtlasCategory.AGENTS, AtlasCategory.IDE, AtlasCategory.BROWSER);

    private final List<AtlasTool> tools;
    private final AtlasPeople people;
    // Status of every known MCP server by lower-cased name.
    private final Map<String, AtlasAssetStatus> serverStatuses;
    // The discovered MCP servers by lower-cased name; other names an agent links (a website, a browser AI app) are not servers.
    private final Map<String, AtlasTool> serverTools = new HashMap<>();

    AtlasGraphSections(List<AtlasTool> tools, AtlasPeople people, Map<String, AtlasAssetStatus> serverStatuses) {
        this.tools = tools;
        this.people = people;
        this.serverStatuses = serverStatuses;
        for (AtlasTool tool : tools) {
            AtlasToolRow row = tool.getRow();
            // A service row can also be an AI app (e.g. a chat website); the inventory lists only MCP Server ones as servers.
            if (AtlasAssetStatusService.ROW_TYPE_SERVICE.equals(row.getRowType()) && AgenticObserveUtil.CLIENT_TYPE_MCP_SERVER.equals(row.getClientType())) serverTools.put(AtlasDiscoveryService.lower(tool.getRow().getGroupKey()), tool);
        }
    }

    /** Devices where each of the busiest agents has each of the busiest MCP servers configured. */
    Map<String, Object> agentMcpMatrix() {
        List<AtlasTool> agents = tools.stream()
                .filter(t -> AtlasAssetStatusService.ROW_TYPE_AGENT.equals(t.getRow().getRowType()) && !t.getRow().getMcpDevices().isEmpty())
                .sorted(busiestFirst())
                .collect(Collectors.toList());
        List<String> servers = busiest(devicesByServer(agents), MATRIX_SERVERS);

        List<Map<String, Object>> serverCols = new ArrayList<>();
        for (String server : servers) {
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("name", server);
            col.put("status", AtlasAssetStatus.nameOf(serverStatuses.get(server)));
            // The discovered server behind the column, so a click can open it.
            col.put("tool", serverTools.get(server).summary());
            serverCols.add(col);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AtlasTool agent : agents.subList(0, Math.min(MATRIX_AGENTS, agents.size()))) {
            Map<String, Integer> byServer = new HashMap<>();
            for (Map.Entry<String, Set<String>> e : agent.getRow().getMcpDevices().entrySet()) {
                byServer.merge(AtlasDiscoveryService.lower(e.getKey()), e.getValue().size(), Integer::sum);
            }
            List<Integer> cells = new ArrayList<>();
            for (String server : servers) cells.add(byServer.getOrDefault(server, 0));
            // The agent's list fields, so a click opens its flyout.
            Map<String, Object> row = agent.summary();
            row.put("cells", cells);
            rows.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("servers", serverCols);
        out.put("rows", rows);
        return out;
    }

    /** Share of each tagged team's employees who used each category recently; empty when no user carries a team tag. */
    Map<String, Object> teamCategoryMatrix() {
        Map<String, Set<String>> members = people.membersByTeam();
        Map<String, Map<String, Set<String>>> adopters = new HashMap<>();
        for (AtlasTool tool : tools) {
            for (String user : tool.getRecentUsers()) {
                String team = people.teamOfUser(user);
                if (team == null) continue;
                adopters.computeIfAbsent(team, k -> new HashMap<>()).computeIfAbsent(tool.getCategory(), k -> new HashSet<>()).add(user);
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String team : busiest(members, MATRIX_TEAMS)) {
            int size = members.get(team).size();
            List<Long> percents = new ArrayList<>();
            for (String category : TEAM_MATRIX_CATEGORIES) {
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
        out.put("categories", TEAM_MATRIX_CATEGORIES);
        out.put("rows", rows);
        return out;
    }

    /** Teams -> AI tools -> MCP servers; edge weight is devices. Each column keeps its busiest connected nodes; a node with no incoming link is left out. */
    Map<String, Object> supplyChain() {
        // Distinct devices per team: a device that runs several of these tools is counted once.
        Map<String, Set<String>> devicesByTeam = new HashMap<>();
        Map<String, Map<String, Integer>> teamToolDevices = new HashMap<>();
        List<AtlasTool> chainTools = new ArrayList<>();
        List<AtlasTool> candidates = tools.stream()
                .filter(t -> CHAIN_TOOL_CATEGORIES.contains(t.getCategory()) && t.governable())
                .sorted(busiestFirst())
                .collect(Collectors.toList());
        // A tool no team reaches has no incoming link, so it is skipped before the top N is taken.
        for (AtlasTool tool : candidates) {
            if (chainTools.size() >= CHAIN_COLUMN_LIMIT) break;
            Map<String, Integer> byTeam = new HashMap<>();
            Map<String, Set<String>> toolTeamDevices = new HashMap<>();
            for (String deviceId : tool.getRow().getDeviceIds()) {
                String team = people.teamOfDevice(deviceId);
                if (team == null) continue;
                byTeam.merge(team, 1, Integer::sum);
                toolTeamDevices.computeIfAbsent(team, k -> new HashSet<>()).add(deviceId);
            }
            if (byTeam.isEmpty()) continue;
            toolTeamDevices.forEach((team, devices) -> devicesByTeam.computeIfAbsent(team, k -> new HashSet<>()).addAll(devices));
            teamToolDevices.put(tool.key(), byTeam);
            chainTools.add(tool);
        }
        List<String> teams = busiest(devicesByTeam, CHAIN_COLUMN_LIMIT);
        List<String> servers = busiest(devicesByServer(chainTools), CHAIN_COLUMN_LIMIT);

        List<Map<String, Object>> edges = new ArrayList<>();
        for (AtlasTool tool : chainTools) {
            for (String team : teams) {
                Integer devices = teamToolDevices.get(tool.key()).get(team);
                if (devices != null) edges.add(edge("team:" + team, "tool:" + tool.key(), devices));
            }
            for (Map.Entry<String, Set<String>> e : tool.getRow().getMcpDevices().entrySet()) {
                String server = AtlasDiscoveryService.lower(e.getKey());
                if (servers.contains(server) && !e.getValue().isEmpty()) edges.add(edge("tool:" + tool.key(), "mcp:" + server, e.getValue().size()));
            }
        }
        // A tool whose teams all fell outside the top N has no incoming link either; its servers lose theirs with it.
        Set<String> reachedTools = new HashSet<>();
        for (Map<String, Object> edge : edges) {
            if (((String) edge.get("source")).startsWith("team:")) reachedTools.add((String) edge.get("target"));
        }
        edges.removeIf(edge -> ((String) edge.get("source")).startsWith("tool:") && !reachedTools.contains(edge.get("source")));
        Set<String> reachedServers = new HashSet<>();
        Set<String> reachingTeams = new HashSet<>();
        for (Map<String, Object> edge : edges) {
            if (((String) edge.get("target")).startsWith("mcp:")) reachedServers.add((String) edge.get("target"));
            else reachingTeams.add((String) edge.get("source"));
        }

        Map<String, Set<String>> members = people.membersByTeam();
        List<Map<String, Object>> teamNodes = new ArrayList<>();
        for (String team : teams) {
            if (!reachingTeams.contains("team:" + team)) continue;
            Map<String, Object> teamNode = node("team:" + team, team, null, devicesByTeam.get(team).size(), null);
            if (members.containsKey(team)) teamNode.put("employees", members.get(team).size());
            teamNodes.add(teamNode);
        }
        List<Map<String, Object>> toolNodes = new ArrayList<>();
        for (AtlasTool tool : chainTools) {
            if (!reachedTools.contains("tool:" + tool.key())) continue;
            Map<String, Object> toolNode = node("tool:" + tool.key(), tool.getRow().getName(), tool.getCategory(), tool.devices(), tool.getStatus());
            toolNode.put("clientType", tool.getRow().getClientType());
            toolNodes.add(toolNode);
        }
        Map<String, Set<String>> devicesByServer = devicesByServer(chainTools);
        List<Map<String, Object>> serverNodes = new ArrayList<>();
        for (String server : servers) {
            if (!reachedServers.contains("mcp:" + server)) continue;
            serverNodes.add(node("mcp:" + server, server, AtlasCategory.MCP, devicesByServer.get(server).size(), serverStatuses.get(server)));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("columns", Arrays.asList(teamNodes, toolNodes, serverNodes));
        out.put("edges", edges);
        return out;
    }

    private static Comparator<AtlasTool> busiestFirst() {
        return Comparator.comparingInt(AtlasTool::devices).reversed().thenComparing(AtlasTool::key);
    }

    // Devices per lower-cased discovered MCP server across the given tools.
    private Map<String, Set<String>> devicesByServer(Collection<AtlasTool> sources) {
        Map<String, Set<String>> devicesByServer = new HashMap<>();
        for (AtlasTool tool : sources) {
            for (Map.Entry<String, Set<String>> e : tool.getRow().getMcpDevices().entrySet()) {
                String server = AtlasDiscoveryService.lower(e.getKey());
                if (serverTools.containsKey(server)) devicesByServer.computeIfAbsent(server, k -> new HashSet<>()).addAll(e.getValue());
            }
        }
        return devicesByServer;
    }

    // The keys with the most members, then by name.
    private static List<String> busiest(Map<String, Set<String>> membersByKey, int limit) {
        return membersByKey.keySet().stream()
                .sorted(Comparator.comparingInt((String key) -> membersByKey.get(key).size()).reversed().thenComparing(key -> key))
                .limit(limit).collect(Collectors.toList());
    }

    private static Map<String, Object> node(String id, String label, String category, int devices, AtlasAssetStatus status) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("label", label);
        node.put("category", category);
        node.put("devices", devices);
        node.put("status", AtlasAssetStatus.nameOf(status));
        return node;
    }

    private static Map<String, Object> edge(String source, String target, int weight) {
        Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("source", source);
        edge.put("target", target);
        edge.put("weight", weight);
        return edge;
    }
}
