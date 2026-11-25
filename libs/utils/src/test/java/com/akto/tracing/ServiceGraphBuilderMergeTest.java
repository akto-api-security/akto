package com.akto.tracing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.akto.dto.ApiCollection.ServiceGraphEdgeInfo;
import com.akto.tracing.bedrock.BedrockAgentTraceParser;

/**
 * The depth-aware merge behind the Bedrock service graph.
 *
 * Bedrock interceptor records vary in how much of a call they can name — a tools/list request
 * has no MCP target, an unattributable caller has no agent — so the plain additive merge would
 * let whichever record a collection saw first pin the graph to its shape for good.
 */
public class ServiceGraphBuilderMergeTest {

    private ServiceGraphEdgeInfo edge(String source, String target, Object depth) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", "mcp_server");
        if (depth != null) {
            metadata.put(BedrockAgentTraceParser.CHAIN_DEPTH, depth);
        }
        return new ServiceGraphEdgeInfo(source, target, metadata);
    }

    private Map<String, ServiceGraphEdgeInfo> edges(String key, ServiceGraphEdgeInfo edge) {
        Map<String, ServiceGraphEdgeInfo> map = new HashMap<>();
        map.put(key, edge);
        return map;
    }

    private String sourceOf(Map<String, ServiceGraphEdgeInfo> merged, String key) {
        return merged.get(key).getSourceService();
    }

    @Test
    public void testDeeperRecordSupersedes() {
        Map<String, ServiceGraphEdgeInfo> merged = ServiceGraphBuilder.getInstance()
            .mergePreferringDeeperChain(
                edges("docs.akto.io", edge("asl-gateway-demo", "docs.akto.io", 2)),
                edges("docs.akto.io", edge("asl_demo_agent_demo", "docs.akto.io", 3)));

        assertEquals("asl_demo_agent_demo", sourceOf(merged, "docs.akto.io"),
            "The record that also named the agent should win");
    }

    @Test
    public void testShallowerRecordDoesNotDemote() {
        Map<String, ServiceGraphEdgeInfo> merged = ServiceGraphBuilder.getInstance()
            .mergePreferringDeeperChain(
                edges("docs.akto.io", edge("asl_demo_agent_demo", "docs.akto.io", 3)),
                edges("docs.akto.io", edge("asl-gateway-demo", "docs.akto.io", 2)));

        assertEquals("asl_demo_agent_demo", sourceOf(merged, "docs.akto.io"),
            "A later tools/list record must not pull the node back under the gateway");
    }

    @Test
    public void testEqualDepthKeepsTheExistingEdge() {
        Map<String, ServiceGraphEdgeInfo> merged = ServiceGraphBuilder.getInstance()
            .mergePreferringDeeperChain(
                edges("docs.akto.io", edge("first", "docs.akto.io", 2)),
                edges("docs.akto.io", edge("second", "docs.akto.io", 2)));

        assertEquals("first", sourceOf(merged, "docs.akto.io"),
            "Same depth is no new information, so first-write still wins");
    }

    /** Edges written before chainDepth existed upgrade once, then follow the normal rule. */
    @Test
    public void testDepthlessExistingEdgeIsUpgradedOnce() {
        Map<String, ServiceGraphEdgeInfo> merged = ServiceGraphBuilder.getInstance()
            .mergePreferringDeeperChain(
                edges("docs.akto.io", edge("asl-gateway-demo", "docs.akto.io", null)),
                edges("docs.akto.io", edge("asl_demo_agent_demo", "docs.akto.io", 3)));

        assertEquals("asl_demo_agent_demo", sourceOf(merged, "docs.akto.io"));
    }

    /** Every other producer writes no chainDepth, so it must keep first-write-wins. */
    @Test
    public void testDepthlessIncomingEdgeNeverReplaces() {
        Map<String, ServiceGraphEdgeInfo> merged = ServiceGraphBuilder.getInstance()
            .mergePreferringDeeperChain(
                edges("some-node", edge("copilot", "some-node", null)),
                edges("some-node", edge("other", "some-node", null)));

        assertEquals("copilot", sourceOf(merged, "some-node"));
    }

    /** Mongo round-trips the int as a Double or Long, so the comparison must read via Number. */
    @Test
    public void testDepthReadFromMongoNumericTypes() {
        Map<String, ServiceGraphEdgeInfo> merged = ServiceGraphBuilder.getInstance()
            .mergePreferringDeeperChain(
                edges("docs.akto.io", edge("asl-gateway-demo", "docs.akto.io", Double.valueOf(2))),
                edges("docs.akto.io", edge("asl_demo_agent_demo", "docs.akto.io", 3)));

        assertEquals("asl_demo_agent_demo", sourceOf(merged, "docs.akto.io"),
            "A Double depth from Mongo must still compare as 2");
    }

    @Test
    public void testNewNodesAreAdded() {
        Map<String, ServiceGraphEdgeInfo> merged = ServiceGraphBuilder.getInstance()
            .mergePreferringDeeperChain(
                edges("a", edge("User", "a", 1)),
                edges("b", edge("a", "b", 2)));

        assertEquals(2, merged.size(), "Both nodes should survive the merge");
    }
}
