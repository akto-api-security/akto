package com.akto.service.guardrails;

import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchGuardrailHostPolicyCountsResponse.HostPolicyCount;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// The agent by policy table of the Guardrail overview. The threat backend counts events per host and
// policy; this groups the hosts into agents, keeps the busiest policies as columns (the rest are
// "Other"), ranks the agents by their events and returns one page of them.
public class GuardrailOverviewHeatmap {

    public static final int MAX_POLICY_COLUMNS = 6;
    public static final String OTHER = "Other";
    static final String UNKNOWN_POLICY = "Unknown policy";

    /** Who a host belongs to. Hosts of one agent share a key. */
    public static class Agent {
        final String key;
        final String name;
        final String where;

        public Agent(String key, String name, String where) {
            this.key = key;
            this.name = name;
            this.where = where;
        }
    }

    public interface AgentResolver {
        Agent resolve(String host);
    }

    public static class Result {
        public final List<String> columns;
        // The policies counted under the Other column, so a click on it can open exactly those.
        public final List<String> otherPolicies;
        public final List<Map<String, Object>> rows;
        public final int totalRows;

        Result(List<String> columns, List<String> otherPolicies, List<Map<String, Object>> rows, int totalRows) {
            this.columns = columns;
            this.otherPolicies = otherPolicies;
            this.rows = rows;
            this.totalRows = totalRows;
        }
    }

    private static class AgentRow {
        final Agent agent;
        final long[] cells;
        // The hosts the agent's events came from, so a click on its row can open them.
        final Set<String> hosts = new LinkedHashSet<>();
        long total;

        AgentRow(Agent agent, int columns) {
            this.agent = agent;
            this.cells = new long[columns];
        }
    }

    private static String policyName(String policy) {
        return policy == null || policy.isEmpty() ? UNKNOWN_POLICY : policy;
    }

    public static Result build(List<HostPolicyCount> pairs, AgentResolver resolver, int skip, int limit) {
        Map<String, Long> perPolicy = new HashMap<>();
        for (HostPolicyCount pair : pairs) {
            perPolicy.merge(policyName(pair.getPolicy()), (long) pair.getCount(), Long::sum);
        }
        List<String> byEvents = new ArrayList<>(perPolicy.keySet());
        byEvents.sort(Comparator.comparing((String p) -> perPolicy.get(p)).reversed().thenComparing(Comparator.naturalOrder()));

        List<String> columns = new ArrayList<>(byEvents.subList(0, Math.min(MAX_POLICY_COLUMNS, byEvents.size())));
        List<String> otherPolicies = new ArrayList<>();
        if (byEvents.size() > MAX_POLICY_COLUMNS) {
            columns.add(OTHER);
            otherPolicies.addAll(byEvents.subList(MAX_POLICY_COLUMNS, byEvents.size()));
        }
        Map<String, Integer> columnOf = new HashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            columnOf.put(columns.get(i), i);
        }

        Map<String, AgentRow> agents = new LinkedHashMap<>();
        for (HostPolicyCount pair : pairs) {
            Agent agent = resolver.resolve(pair.getHost());
            AgentRow row = agents.computeIfAbsent(agent.key, k -> new AgentRow(agent, columns.size()));
            Integer column = columnOf.get(policyName(pair.getPolicy()));
            // A policy with no column of its own is counted under Other, the last column.
            row.cells[column != null ? column : columns.size() - 1] += pair.getCount();
            row.total += pair.getCount();
            if (!pair.getHost().isEmpty()) {
                row.hosts.add(pair.getHost());
            }
        }

        List<AgentRow> ranked = new ArrayList<>(agents.values());
        ranked.sort(Comparator.comparingLong((AgentRow r) -> r.total).reversed().thenComparing(r -> r.agent.name));

        int from = Math.min(Math.max(skip, 0), ranked.size());
        int to = limit > 0 ? Math.min(from + limit, ranked.size()) : ranked.size();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AgentRow row : ranked.subList(from, to)) {
            List<Long> cells = new ArrayList<>();
            for (long cell : row.cells) {
                cells.add(cell);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("agent", row.agent.name);
            out.put("where", row.agent.where);
            out.put("cells", cells);
            out.put("total", row.total);
            out.put("hosts", new ArrayList<>(row.hosts));
            rows.add(out);
        }
        return new Result(columns, otherPolicies, rows, ranked.size());
    }
}
