package com.akto.tracing.alibaba;

import com.akto.dto.ApiCollection.ServiceGraphEdgeInfo;
import com.akto.tracing.TraceParseResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * alibabaMetadata as the AKTO Alibaba connector sends it → service-graph edges (what the
 * dashboard's agent graph draws) and traces.
 */
public class AlibabaCloudTraceParserTest {

    private final AlibabaCloudTraceParser parser = AlibabaCloudTraceParser.getInstance();

    private static ServiceGraphEdgeInfo edge(Map<String, ServiceGraphEdgeInfo> edges, String node) {
        ServiceGraphEdgeInfo e = edges.get(node);
        assertNotNull(e, "missing node " + node + " in " + edges.keySet());
        return e;
    }

    @Test
    public void gatewayMcpToolCall() throws Exception {
        String json = "{\"alibaba-source\":\"AI_GATEWAY\",\"api-type\":\"MCP\",\"gateway-name\":\"akto-test-gw\","
            + "\"gateway-id\":\"gw-1\",\"gateway-endpoint\":\"env-1.alicloudapi.com\",\"gateway-plugins\":[\"akto-guardrails\",\"key-auth\"],"
            + "\"consumers\":[\"team-a\"],\"region\":\"ap-southeast-1\",\"mcp-server\":\"httpbinmcp\",\"mcp-tool\":\"echo\","
            + "\"endpoint\":\"env-1.alicloudapi.com/mcp-servers/httpbinmcp\",\"model\":\"\",\"traceData\":{\"toolsSummary\":{\"tools\":[\"echo\"],\"totalToolCalls\":1}}}";
        Map<String, ServiceGraphEdgeInfo> edges = parser.extractServiceGraph(json, "akto-test-gw");

        ServiceGraphEdgeInfo gw = edge(edges, "akto-test-gw");
        assertEquals("User", gw.getSourceService());
        assertEquals("gateway", gw.getMetadata().get("type"));
        assertEquals("env-1.alicloudapi.com", gw.getMetadata().get("endpointUrl"));
        assertEquals(Arrays.asList("akto-guardrails", "key-auth"), gw.getMetadata().get("plugins"));
        assertEquals(Arrays.asList("team-a"), gw.getMetadata().get("consumers"));

        ServiceGraphEdgeInfo server = edge(edges, "httpbinmcp");
        assertEquals("akto-test-gw", server.getSourceService());
        assertEquals("mcp_server", server.getMetadata().get("type"));

        ServiceGraphEdgeInfo tool = edge(edges, "echo");
        assertEquals("httpbinmcp", tool.getSourceService());
        assertEquals("mcp_tool", tool.getMetadata().get("type"));
        assertEquals("echo", tool.getMetadata().get("lastToolInvoked"));
        assertEquals(3, edges.size(), "no model node for an MCP call");
    }

    @Test
    public void gatewayModelApi() throws Exception {
        String json = "{\"alibaba-source\":\"AI_GATEWAY\",\"api-type\":\"LLM\",\"gateway-name\":\"gw\",\"api-name\":\"qwen-chat\",\"model\":\"qwen-plus\"}";
        Map<String, ServiceGraphEdgeInfo> edges = parser.extractServiceGraph(json, "gw");
        assertEquals("gw", edge(edges, "qwen-chat").getSourceService());
        assertEquals("api", edge(edges, "qwen-chat").getMetadata().get("type"));
        assertEquals("qwen-chat", edge(edges, "qwen-plus").getSourceService());
        assertEquals("llmCall", edge(edges, "qwen-plus").getMetadata().get("type"));
    }

    @Test
    public void agentRunAgentWithRoleAndPolicies() throws Exception {
        String json = "{\"alibaba-source\":\"AGENTRUN\",\"agent-name\":\"agent-quick-hrnli\",\"agent-id\":\"99c5\",\"model\":\"qwen3-max\","
            + "\"execution-role\":\"acs:ram::1:role/AgentRunExecutionRole\",\"role-policies\":[\"AliyunBailianFullAccess\",\"AliyunAgentRunReadOnlyAccess\"],"
            + "\"configured-tools\":[\"baidu_search\",\"load_skills\"],"
            + "\"traceData\":{\"toolsSummary\":{\"tools\":[\"baidu_search\"],\"totalToolCalls\":2}}}";
        Map<String, ServiceGraphEdgeInfo> edges = parser.extractServiceGraph(json, "agent-quick-hrnli");

        ServiceGraphEdgeInfo agent = edge(edges, "agent-quick-hrnli");
        assertEquals("User", agent.getSourceService());
        assertEquals("agent", agent.getMetadata().get("type"));
        assertEquals("acs:ram::1:role/AgentRunExecutionRole", agent.getMetadata().get("role"));
        assertEquals(Arrays.asList("AliyunBailianFullAccess", "AliyunAgentRunReadOnlyAccess"), agent.getMetadata().get("policies"));
        assertEquals(Arrays.asList("baidu_search", "load_skills"), agent.getMetadata().get("toolsList"));
        assertEquals("agent-quick-hrnli", edge(edges, "qwen3-max").getSourceService());
        ServiceGraphEdgeInfo tool = edge(edges, "baidu_search");
        assertEquals("tool", tool.getMetadata().get("type"));
        assertEquals(2, tool.getMetadata().get("totalToolCalls"));
    }

    @Test
    public void modelStudioDirectCallAndAgentApp() throws Exception {
        Map<String, ServiceGraphEdgeInfo> direct = parser.extractServiceGraph(
            "{\"alibaba-source\":\"MODEL_STUDIO\",\"model\":\"qwen3.8-max\",\"workspace-id\":\"ws-1\"}", "ws-1_qwen3.8-max");
        assertEquals(1, direct.size());
        assertEquals("User", edge(direct, "qwen3.8-max").getSourceService());
        assertEquals("ws-1", edge(direct, "qwen3.8-max").getMetadata().get("workspaceId"));

        Map<String, ServiceGraphEdgeInfo> app = parser.extractServiceGraph(
            "{\"alibaba-source\":\"MODEL_STUDIO\",\"model\":\"qwen-plus\",\"app-id\":\"a1\",\"app-name\":\"support-bot\"}", "support-bot");
        assertEquals("agent", edge(app, "support-bot").getMetadata().get("type"));
        assertEquals("support-bot", edge(app, "qwen-plus").getSourceService());
    }

    @Test
    public void discoveryDrawsTheInventoryWithoutTraffic() throws Exception {
        String gateway = "{\"resourceType\":\"GATEWAY\",\"gateway-name\":\"akto-test-gw\",\"plugins\":[\"key-auth\"],"
            + "\"mcpServers\":[\"httpbinmcp\"],\"modelApis\":[\"qwen-chat\"],\"agentApis\":[]}";
        assertTrue(parser.isDiscovery(gateway));
        Map<String, ServiceGraphEdgeInfo> g = parser.extractServiceGraph(gateway, "akto-test-gw");
        assertEquals(Arrays.asList("key-auth"), edge(g, "akto-test-gw").getMetadata().get("plugins"));
        assertEquals("akto-test-gw", edge(g, "httpbinmcp").getSourceService());
        assertEquals("api", edge(g, "qwen-chat").getMetadata().get("type"));

        String mcp = "{\"resourceType\":\"MCP_SERVER\",\"gateway-name\":\"akto-test-gw\",\"resource-name\":\"httpbinmcp\",\"tools\":[\"echo\",\"get\"]}";
        Map<String, ServiceGraphEdgeInfo> s = parser.extractServiceGraph(mcp, "akto-test-gw");
        assertEquals("httpbinmcp", edge(s, "echo").getSourceService());
        assertEquals("httpbinmcp", edge(s, "get").getSourceService());
        assertEquals(Arrays.asList("echo", "get"), edge(s, "httpbinmcp").getMetadata().get("toolsList"));

        String agent = "{\"resourceType\":\"AGENTRUN_AGENT\",\"agent-name\":\"agent-quick-hrnli\",\"execution-role\":\"acs:ram::1:role/R\"}";
        Map<String, ServiceGraphEdgeInfo> a = parser.extractServiceGraph(agent, "agent-quick-hrnli");
        assertEquals("acs:ram::1:role/R", edge(a, "agent-quick-hrnli").getMetadata().get("role"));
    }

    @Test
    public void traceCarriesRealTokenCountsAndToolSpans() throws Exception {
        String json = "{\"alibaba-source\":\"AGENTRUN\",\"agent-name\":\"a\",\"model\":\"qwen3-max\",\"statusCode\":200,"
            + "\"usage\":{\"inputTokens\":120,\"outputTokens\":30},\"traceData\":{\"executionFlow\":["
            + "{\"step\":0,\"type\":\"agent\",\"name\":\"a\"},"
            + "{\"step\":1,\"type\":\"tool-call\",\"tool\":\"baidu_search\",\"result\":\"error: quota exceeded\"}]}}";
        assertFalse(parser.isDiscovery(json));
        TraceParseResult r = parser.parse(json, "a");
        assertEquals(150, r.getTrace().getTotalTokens());
        assertEquals(120, r.getTrace().getTotalInputTokens());
        assertEquals("a - qwen3-max", r.getTrace().getName());
        assertEquals(2, r.getSpans().size());
        assertEquals("error", r.getSpans().get(1).getStatus());
        assertEquals(r.getSpans().get(0).getId(), r.getSpans().get(1).getParentSpanId());
    }

    @Test
    public void rejectsWhatIsNotAlibabaMetadata() {
        assertFalse(parser.canParse("{\"model\":\"x\",\"traceData\":{}}"));
        assertFalse(parser.canParse("not json"));
        assertFalse(parser.canParse(null));
        List<String> nothing = Arrays.asList();
        assertTrue(nothing.isEmpty());
    }
}
