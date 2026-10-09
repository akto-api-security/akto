package com.akto.tracing.bedrock;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.akto.dto.ApiCollection.ServiceGraphEdgeInfo;
import com.akto.tracing.TraceParseResult;
import com.fasterxml.jackson.databind.ObjectMapper;

public class BedrockAgentTraceParserTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Test parsing a valid Bedrock Agent (non-AgentCore) trace
     */
    @Test
    public void testParseValidBedrockAgentTrace() throws Exception {
        String bedrockTraceJson = getBedrockAgentTraceJson();

        BedrockAgentTraceParser parser = BedrockAgentTraceParser.getInstance();

        // Test canParse
        assertTrue(parser.canParse(bedrockTraceJson), "Should be able to parse Bedrock Agent trace");

        // Test parse
        TraceParseResult result = parser.parse(bedrockTraceJson, "my-chat-agent");

        assertNotNull(result, "Parse result should not be null");
        assertNotNull(result.getTrace(), "Trace should not be null");
        assertNotNull(result.getSpans(), "Spans should not be null");

        // Verify trace details
        assertEquals("my-chat-agent", result.getTrace().getAiAgentName(), "Agent name should match");
        assertEquals("bedrock-agent", parser.getSourceType(), "Source type should be bedrock-agent");

        // Verify spans
        assertEquals(3, result.getSpans().size(), "Should have 3 spans (agent + 2 tool calls)");
        assertEquals("my-chat-agent", result.getSpans().get(0).getName(), "First span should be agent");
        assertEquals("browser", result.getSpans().get(1).getName(), "Second span should be browser tool");
        assertEquals("code_interpreter", result.getSpans().get(2).getName(), "Third span should be code_interpreter tool");

        // Verify metadata
        Map<String, Object> metadata = result.getMetadata();
        assertEquals("my-chat-agent", metadata.get("botName"), "Metadata should contain botName");
        // Pins current behaviour, which is arguably wrong: resolveAgentType tests the harness
        // key with has(), so the blank "harness-execution-role" AWS sends on a plain Bedrock
        // invoke reads as AgentCore. Its sibling extractExecutionRoleValue documents exactly
        // this trap ("presence alone isn't enough") and checks for a value instead. Changing
        // resolveAgentType would drop the tool edges such records currently get, so it is left
        // alone here rather than fixed as a side effect of the chain work.
        assertEquals("AGENTCORE", metadata.get("agentType"), "Blank harness role still reads as AgentCore");
        assertEquals("amazon.nova-micro-v1:0", metadata.get("model"), "Metadata should contain model");
    }

    /**
     * Test parsing a valid AgentCore Harness trace
     */
    @Test
    public void testParseValidAgentCoreHarnessTrace() throws Exception {
        String agentCoreTraceJson = getAgentCoreHarnessTraceJson();

        BedrockAgentTraceParser parser = BedrockAgentTraceParser.getInstance();

        // Test canParse
        assertTrue(parser.canParse(agentCoreTraceJson), "Should be able to parse AgentCore trace");

        // Test parse
        TraceParseResult result = parser.parse(agentCoreTraceJson, "my-harness");

        assertNotNull(result, "Parse result should not be null");
        assertEquals("my-harness", result.getTrace().getAiAgentName(), "Harness name should match");

        // Verify metadata contains harness-specific information
        Map<String, Object> metadata = result.getMetadata();
        assertEquals("AGENTCORE", metadata.get("agentType"), "Metadata should indicate AGENTCORE type");
    }

    /**
     * Test service graph extraction for Bedrock Agent
     */
    @Test
    public void testExtractServiceGraphBedrockAgent() throws Exception {
        String bedrockTraceJson = getBedrockAgentTraceJson();

        BedrockAgentTraceParser parser = BedrockAgentTraceParser.getInstance();

        Map<String, ServiceGraphEdgeInfo> edges = parser.extractServiceGraph(bedrockTraceJson);

        assertNotNull(edges, "Edges should not be null");
        assertFalse(edges.isEmpty(), "Should have at least one edge");

        // Verify LLM model edge exists
        assertTrue(edges.containsKey("amazon.nova-micro-v1:0"), "Should have model edge");
        ServiceGraphEdgeInfo modelEdge = edges.get("amazon.nova-micro-v1:0");
        assertNotNull(modelEdge, "Model edge should not be null");

        // Verify edge metadata
        Map<String, Object> metadata = modelEdge.getMetadata();
        assertEquals("llmCall", metadata.get("type"), "Edge type should be llmCall");
        assertEquals("Call to model", metadata.get("edgeParam"), "Edge param should be correct");
    }

    /**
     * Test service graph extraction for AgentCore
     */
    @Test
    public void testExtractServiceGraphAgentCore() throws Exception {
        String agentCoreTraceJson = getAgentCoreHarnessTraceJson();

        BedrockAgentTraceParser parser = BedrockAgentTraceParser.getInstance();

        Map<String, ServiceGraphEdgeInfo> edges = parser.extractServiceGraph(agentCoreTraceJson);

        assertNotNull(edges, "Edges should not be null");

        // AgentCore should have model, tools, and skills edges
        assertTrue(edges.size() >= 2, "AgentCore should have at least 2 edges (model + tools/skills)");

        // Verify model edge
        assertTrue(edges.containsKey("amazon.nova-lite-v1:0"), "Should have model edge");

        // Verify tools edge
        assertTrue(edges.keySet().stream().anyMatch(k -> k.contains("browser")), "Should have tools edge");
    }

    /**
     * Test invalid input handling
     */
    @Test
    public void testCannotParseInvalidInput() {
        BedrockAgentTraceParser parser = BedrockAgentTraceParser.getInstance();

        // Test with null
        assertFalse(parser.canParse(null), "Should not parse null");

        // Test with invalid JSON
        assertFalse(parser.canParse("{invalid json"), "Should not parse invalid JSON");

        // Test with a payload carrying none of the execution-role keys
        assertFalse(parser.canParse("{\"someField\": \"value\"}"), "Should not parse without an execution role");

        // Test with missing traceData
        String missingTraceData = "{\"model\": \"test\", \"bedrock-execution-role\": \"role\"}";
        assertFalse(parser.canParse(missingTraceData), "Should not parse without traceData");
    }

    /**
     * Test handling of tool calls extraction
     */
    @Test
    public void testToolCallsExtraction() throws Exception {
        String bedrockTraceJson = getBedrockAgentTraceJson();

        BedrockAgentTraceParser parser = BedrockAgentTraceParser.getInstance();
        TraceParseResult result = parser.parse(bedrockTraceJson, "my-chat-agent");

        // Should have: 1 agent span + 2 tool call spans
        assertEquals(3, result.getSpans().size(), "Should extract correct number of spans");

        // Verify span kinds
        assertEquals("agent", result.getSpans().get(0).getSpanKind(), "First span should be agent");
        assertEquals("tool", result.getSpans().get(1).getSpanKind(), "Second span should be tool");
        assertEquals("tool", result.getSpans().get(2).getSpanKind(), "Third span should be tool");
    }

    // ==================== Chain shape ====================

    private static final String GATEWAY_ROLE_ARN =
        "arn:aws:iam::041877753357:role/asl-gateway-service-role-demo";
    private static final String GATEWAY_ROLE = "asl-gateway-service-role-demo";
    private static final String GATEWAY_POLICIES = "asl-gateway-logs-demo,invoke-akto-guardrails-interceptor";
    private static final String TOOL = "mac-akto-api-mcp___searchDocumentation";

    /** awsMetadata as the gateway interceptor sends it: a role, no model, one tool call. */
    private String gatewayInterceptorMetadata(String executionRole, String policies) {
        return "{\n" +
            "  \"harness-execution-role\": \"" + executionRole + "\",\n" +
            "  \"harness-role-policies\": \"" + policies + "\",\n" +
            "  \"model\": \"\",\n" +
            "  \"traceData\": {\"toolsSummary\": {\"tools\": [\"" + TOOL + "\"], \"totalToolCalls\": 1}}\n" +
            "}";
    }

    private Map<String, String> gatewayTags() {
        Map<String, String> tags = new HashMap<>();
        tags.put("gateway-name", "asl-gateway-demo");
        tags.put("gateway-execution-role-arn", GATEWAY_ROLE_ARN);
        tags.put("mcp-server", "MCP Server");
        tags.put("mcp-server-host", "docs.akto.io");
        return tags;
    }

    private void assertEdge(Map<String, ServiceGraphEdgeInfo> edges, String node, String source,
            String type, int depth) {
        ServiceGraphEdgeInfo edge = edges.get(node);
        assertNotNull(edge, node + " should be a node");
        assertEquals(source, edge.getSourceService(), node + " should hang off " + source);
        assertEquals(type, edge.getMetadata().get("type"), node + " should be a " + type);
        assertEquals(depth, edge.getMetadata().get(BedrockAgentTraceParser.CHAIN_DEPTH),
            node + " should sit at depth " + depth);
    }

    /**
     * A resolvable caller yields the full chain. Before this, agent and MCP server competed for
     * one node slot and the agent simply vanished from any gateway-fronted record.
     */
    @Test
    public void testChainSplicesAgentBetweenGatewayAndMcpServer() throws Exception {
        Map<String, String> tags = gatewayTags();
        tags.put("agent-name", "asl_demo_agent_demo");

        Map<String, ServiceGraphEdgeInfo> edges = BedrockAgentTraceParser.getInstance()
            .extractServiceGraph(gatewayInterceptorMetadata("asl-demo-agent-execution-demo-karan",
                "AmazonBedrockAgentCoreRuntimePolicy,AktoDemoS3Read"),
                "docs.akto.io.asl-gateway-demo", tags);

        assertEdge(edges, "asl-gateway-demo", "User", "gateway", 1);
        assertEdge(edges, "asl_demo_agent_demo", "asl-gateway-demo", "agent", 2);
        assertEdge(edges, "docs.akto.io", "asl_demo_agent_demo", "mcp_server", 3);
        assertEdge(edges, TOOL, "docs.akto.io", "mcp_tool", 4);

        // Each node shows the role that is actually its own.
        assertEquals(GATEWAY_ROLE, edges.get("asl-gateway-demo").getMetadata().get("role"),
            "Gateway shows its own role name, not the resources ARN blob");
        assertEquals("asl-demo-agent-execution-demo-karan",
            edges.get("asl_demo_agent_demo").getMetadata().get("role"), "Agent shows the agent's role");
        assertEquals(GATEWAY_ROLE, edges.get("docs.akto.io").getMetadata().get("role"),
            "MCP server shows the gateway role that reaches it");
        assertEquals("docs.akto.io", edges.get("docs.akto.io").getMetadata().get("host"),
            "MCP server keeps its real host");

        // The interceptor aliases the gateway's role onto the harness- keys and a resolved agent
        // overwrites them, so the gateway's policy names are not on this record at all.
        assertNull(edges.get("asl-gateway-demo").getMetadata().get("policies"),
            "Gateway policies are absent once an agent has claimed the harness- keys");
        assertEquals(2, ((java.util.List<?>) edges.get("asl_demo_agent_demo").getMetadata()
            .get("policies")).size(), "Agent keeps its own policies");
    }

    /**
     * No resolvable caller: bot-name names the MCP target, so it must NOT become an agent node.
     * This is the shape that already shipped, and it has to stay byte-identical — policies
     * included, since with no agent the harness- keys still hold the gateway's own.
     */
    @Test
    public void testChainWithoutAgentKeepsGatewayToMcpServerShape() throws Exception {
        Map<String, ServiceGraphEdgeInfo> edges = BedrockAgentTraceParser.getInstance()
            .extractServiceGraph(gatewayInterceptorMetadata(GATEWAY_ROLE, GATEWAY_POLICIES),
                "docs.akto.io.asl-gateway-demo", gatewayTags());

        assertEdge(edges, "asl-gateway-demo", "User", "gateway", 1);
        assertEdge(edges, "docs.akto.io", "asl-gateway-demo", "mcp_server", 2);
        assertEdge(edges, TOOL, "docs.akto.io", "mcp_tool", 3);
        assertFalse(edges.containsKey("docs.akto.io.asl-gateway-demo"),
            "bot-name names the MCP target here and must not fork an agent node");

        for (String node : new String[]{"asl-gateway-demo", "docs.akto.io"}) {
            assertEquals(GATEWAY_ROLE, edges.get(node).getMetadata().get("role"), node + " role");
            assertEquals(2, ((java.util.List<?>) edges.get(node).getMetadata().get("policies")).size(),
                node + " keeps the gateway's policy list");
        }
    }

    /** No gateway and no MCP target: the plain User -> agent chain, unchanged. */
    @Test
    public void testChainWithoutGatewayIsUserToAgent() throws Exception {
        Map<String, ServiceGraphEdgeInfo> edges = BedrockAgentTraceParser.getInstance()
            .extractServiceGraph(getAgentCoreHarnessTraceJson(), "my-harness", null);

        assertEdge(edges, "my-harness", "User", "agent", 1);
        assertEdge(edges, "browser", "my-harness", "tool", 2);
        assertEquals("amazon.nova-lite-v1:0", edges.get("amazon.nova-lite-v1:0").getTargetService(),
            "The agent is what calls the model");
        assertEquals("my-harness", edges.get("amazon.nova-lite-v1:0").getSourceService(),
            "Model hangs off the agent, not the leaf tool");
    }

    /** A gateway with no role to show is not worth drawing, so the hop is skipped. */
    @Test
    public void testGatewayHopNeedsARole() throws Exception {
        Map<String, String> tags = gatewayTags();
        tags.remove("gateway-execution-role-arn");

        Map<String, ServiceGraphEdgeInfo> edges = BedrockAgentTraceParser.getInstance()
            .extractServiceGraph(gatewayInterceptorMetadata(GATEWAY_ROLE, GATEWAY_POLICIES),
                "docs.akto.io.asl-gateway-demo", tags);

        assertFalse(edges.containsKey("asl-gateway-demo"), "Roleless gateway is not drawn");
        assertEdge(edges, "docs.akto.io", "User", "mcp_server", 1);
    }

    /** Older senders that only carry gateway-role-resources still get a Gateway hop. */
    @Test
    public void testGatewayRoleFallsBackToResourcesTag() throws Exception {
        Map<String, String> tags = gatewayTags();
        tags.remove("gateway-execution-role-arn");
        tags.put("gateway-role-resources", "arn:aws:lambda:us-east-1:1234:function:x");

        Map<String, ServiceGraphEdgeInfo> edges = BedrockAgentTraceParser.getInstance()
            .extractServiceGraph(gatewayInterceptorMetadata(GATEWAY_ROLE, GATEWAY_POLICIES),
                "docs.akto.io.asl-gateway-demo", tags);

        assertEquals("arn:aws:lambda:us-east-1:1234:function:x",
            edges.get("asl-gateway-demo").getMetadata().get("role"), "Falls back to the older tag");
    }

    // ==================== Mock Data Generators ====================

    /**
     * Generate mock Bedrock Agent trace (non-AgentCore)
     */
    private String getBedrockAgentTraceJson() {
        return "{\n" +
            "    \"harness-configured-tools\": \"\",\n" +
            "    \"harness-configured-skills\": \"\",\n" +
            "    \"model\": \"amazon.nova-micro-v1:0\",\n" +
            "    \"harness-execution-role\": \"\",\n" +
            "    \"bedrock-execution-role\": \"AmazonBedrockExecutionRoleForAgents_MGLQ0HG3XK\",\n" +
            "    \"traceData\": {\n" +
            "    \"executionFlow\": [\n" +
            "      {\n" +
            "        \"step\": 0,\n" +
            "        \"type\": \"agent\",\n" +
            "        \"name\": \"my-chat-agent\",\n" +
            "        \"action\": \"orchestrate\",\n" +
            "        \"description\": \"Agent orchestrating tool calls\"\n" +
            "      },\n" +
            "      {\n" +
            "        \"step\": 1,\n" +
            "        \"type\": \"tool-call\",\n" +
            "        \"tool\": \"browser\",\n" +
            "        \"action\": \"unknown\",\n" +
            "        \"toolUseId\": \"tooluse_2iScZsv7AiMjcBUF3TUi0N\"\n" +
            "      },\n" +
            "      {\n" +
            "        \"step\": 2,\n" +
            "        \"type\": \"tool-call\",\n" +
            "        \"tool\": \"code_interpreter\",\n" +
            "        \"action\": \"unknown\",\n" +
            "        \"toolUseId\": \"tooluse_MmQxy1w8z7P2AiDiSQSqcF\"\n" +
            "      }\n" +
            "    ],\n" +
            "    \"toolsSummary\": {\n" +
            "      \"agentOrchestrator\": \"my-chat-agent\",\n" +
            "      \"tools\": [\"browser\", \"code_interpreter\"],\n" +
            "      \"actions\": [\"unknown\"],\n" +
            "      \"totalToolCalls\": 2,\n" +
            "      \"executionPattern\": \"my-chat-agent→browser→code_interpreter\"\n" +
            "    }\n" +
            "  }\n" +
            "}";
    }

    /**
     * Generate mock AgentCore Harness trace
     */
    private String getAgentCoreHarnessTraceJson() {
        return "{\n" +
            "    \"harness-configured-tools\": \"aws_browser_v1:agentcore_browser, aws_codeinterpreter_v1:agentcore_code_interpreter\",\n" +
            "    \"harness-configured-skills\": \"awsSkills:{}\",\n" +
            "    \"model\": \"amazon.nova-lite-v1:0\",\n" +
            "    \"harness-execution-role\": \"AmazonBedrockAgentCoreHarnessDefaultServiceRole-fr53w\",\n" +
            "    \"bedrock-execution-role\": \"\",\n" +
            "    \"traceData\": {\n" +
            "    \"executionFlow\": [\n" +
            "      {\n" +
            "        \"step\": 0,\n" +
            "        \"type\": \"agent\",\n" +
            "        \"name\": \"my-harness\",\n" +
            "        \"action\": \"orchestrate\",\n" +
            "        \"description\": \"Agent orchestrating tool calls\"\n" +
            "      },\n" +
            "      {\n" +
            "        \"step\": 1,\n" +
            "        \"type\": \"tool-call\",\n" +
            "        \"tool\": \"browser\",\n" +
            "        \"action\": \"unknown\",\n" +
            "        \"toolUseId\": \"tooluse_abc123\"\n" +
            "      }\n" +
            "    ],\n" +
            "    \"toolsSummary\": {\n" +
            "      \"agentOrchestrator\": \"my-harness\",\n" +
            "      \"tools\": [\"browser\"],\n" +
            "      \"actions\": [\"unknown\"],\n" +
            "      \"totalToolCalls\": 1,\n" +
            "      \"executionPattern\": \"my-harness→browser\"\n" +
            "    }\n" +
            "  }\n" +
            "}";
    }
}
