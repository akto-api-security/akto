package com.akto.service.atlas;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** The overview's matrices and the team -> tool -> MCP server graph; every list is a fixed top-N. */
final class AtlasGraphSections {

    private static final int MATRIX_AGENTS = 5;
    private static final int MATRIX_SERVERS = 8;
    private static final int MATRIX_TEAMS = 8;
    private static final int CHAIN_COLUMN_LIMIT = 10;
    // Categories that appear as nodes in the "AI tools" column of the supply chain.
    private static final List<String> CHAIN_TOOL_CATEGORIES = Arrays.asList(AtlasCategory.AGENTS, AtlasCategory.BROWSER);

    private final List<AtlasTool> tools;
    private final AtlasPeople people;
    // Status of every known MCP server by lower-cased name.
    private final Map<String, AtlasAssetStatus> serverStatuses;

    AtlasGraphSections(List<AtlasTool> tools, AtlasPeople people, Map<String, AtlasAssetStatus> serverStatuses) {
        this.tools = tools;
        this.people = people;
        this.serverStatuses = serverStatuses;
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
            serverCols.add(col);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AtlasTool agent : agents.subList(0, Math.min(MATRIX_AGENTS, agents.size()))) {
            Map<String, Integer> byServer = new HashMap<>();
            for (Map.Entry<String, Set<String>> e : agent.getRow().getMcpDevices().entrySet()) {
                byServer.merge(lower(e.getKey()), e.getValue().size(), Integer::sum);
            }
            List<Integer> cells = new ArrayList<>();
            for (String server : servers) cells.add(byServer.getOrDefault(server, 0));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", agent.key());
            row.put("name", agent.getRow().getName());
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
            for (String category : AtlasCategory.ORDER) {
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
        out.put("categories", AtlasCategory.ORDER);
        out.put("rows", rows);
        return out;
    }

    /** Teams -> AI tools -> MCP servers; edge weight is devices. Each column keeps its busiest nodes. */
    Map<String, Object> supplyChain() {
        List<AtlasTool> chainTools = tools.stream()
                .filter(t -> CHAIN_TOOL_CATEGORIES.contains(t.getCategory()) && t.governable())
                .sorted(busiestFirst())
                .limit(CHAIN_COLUMN_LIMIT).collect(Collectors.toList());
        Map<String, Set<String>> devicesByServer = devicesByServer(chainTools);
        List<String> servers = busiest(devicesByServer, CHAIN_COLUMN_LIMIT);

        Map<String, Map<String, Integer>> teamToolDevices = new HashMap<>();
        // Distinct devices per team: a device that runs several of these tools is counted once.
        Map<String, Set<String>> devicesByTeam = new HashMap<>();
        for (AtlasTool tool : chainTools) {
            Map<String, Integer> byTeam = new HashMap<>();
            for (String deviceId : tool.getRow().getDeviceIds()) {
                String team = people.teamOfDevice(deviceId);
                if (team == null) continue;
                byTeam.merge(team, 1, Integer::sum);
                devicesByTeam.computeIfAbsent(team, k -> new HashSet<>()).add(deviceId);
            }
            teamToolDevices.put(tool.key(), byTeam);
        }
        List<String> teams = busiest(devicesByTeam, CHAIN_COLUMN_LIMIT);

        Map<String, Set<String>> members = people.membersByTeam();
        List<Map<String, Object>> teamNodes = new ArrayList<>();
        for (String team : teams) {
            Map<String, Object> teamNode = node("team:" + team, team, null, devicesByTeam.get(team).size(), null);
            if (members.containsKey(team)) teamNode.put("employees", members.get(team).size());
            teamNodes.add(teamNode);
        }
        List<Map<String, Object>> toolNodes = new ArrayList<>();
        for (AtlasTool tool : chainTools) {
            Map<String, Object> toolNode = node("tool:" + tool.key(), tool.getRow().getName(), tool.getCategory(), tool.devices(), tool.getStatus());
            toolNode.put("clientType", tool.getRow().getClientType());
            toolNodes.add(toolNode);
        }
        List<Map<String, Object>> serverNodes = new ArrayList<>();
        for (String server : servers) {
            serverNodes.add(node("mcp:" + server, server, AtlasCategory.MCP, devicesByServer.get(server).size(), serverStatuses.get(server)));
        }

        List<Map<String, Object>> edges = new ArrayList<>();
        for (AtlasTool tool : chainTools) {
            for (String team : teams) {
                Integer devices = teamToolDevices.get(tool.key()).get(team);
                if (devices != null) edges.add(edge("team:" + team, "tool:" + tool.key(), devices));
            }
            for (Map.Entry<String, Set<String>> e : tool.getRow().getMcpDevices().entrySet()) {
                String server = lower(e.getKey());
                if (servers.contains(server) && !e.getValue().isEmpty()) edges.add(edge("tool:" + tool.key(), "mcp:" + server, e.getValue().size()));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("columns", Arrays.asList(teamNodes, toolNodes, serverNodes));
        out.put("edges", edges);
        return out;
    }

    private static Comparator<AtlasTool> busiestFirst() {
        return Comparator.comparingInt(AtlasTool::devices).reversed().thenComparing(AtlasTool::key);
    }

    // Devices per lower-cased MCP server across the given tools.
    private static Map<String, Set<String>> devicesByServer(Collection<AtlasTool> sources) {
        Map<String, Set<String>> devicesByServer = new HashMap<>();
        for (AtlasTool tool : sources) {
            for (Map.Entry<String, Set<String>> e : tool.getRow().getMcpDevices().entrySet()) {
                devicesByServer.computeIfAbsent(lower(e.getKey()), k -> new HashSet<>()).addAll(e.getValue());
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

    private static String lower(String value) {
        return StringUtils.lowerCase(value, Locale.ROOT);
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
