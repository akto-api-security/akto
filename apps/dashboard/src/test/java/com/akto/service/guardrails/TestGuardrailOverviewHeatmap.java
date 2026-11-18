package com.akto.service.guardrails;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchGuardrailHostPolicyCountsResponse.HostPolicyCount;
import com.akto.service.guardrails.GuardrailOverviewHeatmap.Agent;
import com.akto.service.guardrails.GuardrailOverviewHeatmap.Result;

/* The agent by policy table: hosts grouped into agents, the busiest policies as columns, agents paged by their events. */
public class TestGuardrailOverviewHeatmap {

    private static HostPolicyCount pair(String host, String policy, int count) {
        return HostPolicyCount.newBuilder().setHost(host).setPolicy(policy).setCount(count).build();
    }

    // support-1 and support-2 are one agent; any other host is an agent of its own.
    private static final GuardrailOverviewHeatmap.AgentResolver RESOLVER = host ->
        host.startsWith("support-")
            ? new Agent("collection:1", "Support copilot", "Production")
            : new Agent("host:" + host, host.isEmpty() ? "Unknown agent" : host, "");

    @Test
    public void testHostsOfOneAgentAreSummedAndAgentsRankedByEvents() {
        Result result = GuardrailOverviewHeatmap.build(Arrays.asList(
            pair("support-1", "PII", 10), pair("support-2", "PII", 5), pair("support-2", "Secrets", 1),
            pair("refund-bot", "Secrets", 30)), RESOLVER, 0, 10);

        assertEquals(Arrays.asList("Secrets", "PII"), result.columns);  // 31 events vs 15
        assertEquals(2, result.totalRows);
        assertEquals("refund-bot", result.rows.get(0).get("agent"));
        assertEquals(Arrays.asList(30L, 0L), result.rows.get(0).get("cells"));
        assertEquals("Support copilot", result.rows.get(1).get("agent"));
        assertEquals("Production", result.rows.get(1).get("where"));
        assertEquals(Arrays.asList(1L, 15L), result.rows.get(1).get("cells"));
        assertEquals(16L, result.rows.get(1).get("total"));
    }

    @Test
    public void testRowCarriesEveryHostOfItsAgentOnce() {
        Result result = GuardrailOverviewHeatmap.build(Arrays.asList(
            pair("support-1", "PII", 10), pair("support-2", "PII", 5), pair("support-2", "Secrets", 1),
            pair("refund-bot", "Secrets", 30)), RESOLVER, 0, 10);

        assertEquals(Arrays.asList("refund-bot"), result.rows.get(0).get("hosts"));
        assertEquals(Arrays.asList("support-1", "support-2"), result.rows.get(1).get("hosts"));
    }

    @Test
    public void testPoliciesBeyondTheColumnLimitAreCountedUnderOther() {
        List<HostPolicyCount> pairs = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            pairs.add(pair("bot", "policy-" + i, 100 - i));  // policy-1 is the busiest
        }

        Result result = GuardrailOverviewHeatmap.build(pairs, RESOLVER, 0, 10);

        assertEquals(7, result.columns.size());
        assertEquals("policy-1", result.columns.get(0));
        assertEquals(GuardrailOverviewHeatmap.OTHER, result.columns.get(6));
        List<?> cells = (List<?>) result.rows.get(0).get("cells");
        assertEquals((100L - 7) + (100L - 8), cells.get(6));  // policy-7 and policy-8
        assertEquals(sumOf(pairs), result.rows.get(0).get("total"));
        // a click on the Other cell opens exactly the policies folded into it
        assertEquals(Arrays.asList("policy-7", "policy-8"), result.otherPolicies);
    }

    private static long sumOf(List<HostPolicyCount> pairs) {
        long sum = 0;
        for (HostPolicyCount p : pairs) sum += p.getCount();
        return sum;
    }

    @Test
    public void testNoOtherColumnWhenEveryPolicyHasItsOwn() {
        Result result = GuardrailOverviewHeatmap.build(Arrays.asList(pair("bot", "A", 1), pair("bot", "B", 2)), RESOLVER, 0, 10);
        assertEquals(Arrays.asList("B", "A"), result.columns);
        assertTrue(result.otherPolicies.isEmpty());
    }

    @Test
    public void testPagingKeepsTheRankAndReportsTheTotal() {
        List<HostPolicyCount> pairs = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            pairs.add(pair("agent-" + i, "PII", i * 10));  // agent-5 is the busiest
        }

        Result second = GuardrailOverviewHeatmap.build(pairs, RESOLVER, 2, 2);

        assertEquals(5, second.totalRows);
        assertEquals(2, second.rows.size());
        assertEquals("agent-3", second.rows.get(0).get("agent"));
        assertEquals("agent-2", second.rows.get(1).get("agent"));
    }

    @Test
    public void testPageBeyondTheEndIsEmptyAndZeroLimitMeansEverything() {
        List<HostPolicyCount> pairs = Arrays.asList(pair("a", "PII", 1), pair("b", "PII", 2));

        assertTrue(GuardrailOverviewHeatmap.build(pairs, RESOLVER, 5, 10).rows.isEmpty());
        assertEquals(2, GuardrailOverviewHeatmap.build(pairs, RESOLVER, 0, 0).rows.size());
    }

    @Test
    public void testBlankPolicyAndHostAreNamedNotDropped() {
        Result result = GuardrailOverviewHeatmap.build(Arrays.asList(pair("", "", 4)), RESOLVER, 0, 10);

        assertEquals(Arrays.asList("Unknown policy"), result.columns);
        assertEquals("Unknown agent", result.rows.get(0).get("agent"));
        assertEquals(4L, result.rows.get(0).get("total"));
        // no host to filter by, so the row has none to open
        assertTrue(((List<?>) result.rows.get(0).get("hosts")).isEmpty());
    }

    @Test
    public void testNoEventsGivesAnEmptyTable() {
        Result result = GuardrailOverviewHeatmap.build(new ArrayList<>(), RESOLVER, 0, 10);

        assertTrue(result.columns.isEmpty());
        assertTrue(result.rows.isEmpty());
        assertEquals(0, result.totalRows);
    }
}
