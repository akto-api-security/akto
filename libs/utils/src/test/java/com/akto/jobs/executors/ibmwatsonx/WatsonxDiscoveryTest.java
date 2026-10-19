package com.akto.jobs.executors.ibmwatsonx;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** What discovery reads from the control plane, and how it names and describes agents. */
public class WatsonxDiscoveryTest {

    private FakeWatsonx ibm;
    private WatsonxClient client;

    @BeforeEach
    public void setUp() {
        ibm = new FakeWatsonx();
        ibm.on("/v1/orchestrate/tools", 200, "[{\"id\":\"tool-1\",\"name\":\"get_order_status\",\"toolkit_id\":\"kit-1\"},"
                        + "{\"id\":\"tool-2\",\"name\":\"draft_only_tool\"}]")
                .on("/v1/orchestrate/toolkits", 200, "[{\"id\":\"kit-1\",\"kind\":\"mcp\",\"name\":\"httpbinmcp\",\"tools\":[\"tool-1\"],"
                        + "\"mcp\":{\"transport\":\"sse\",\"server_url\":\"https://mcp.test\"}}]")
                .on("/v1/orchestrate/models/list", 200, "{\"resources\":[{\"id\":\"groq/openai/gpt-oss-120b\",\"label\":\"GPT OSS\",\"type\":\"groq\"}]}")
                .on("/v1/orchestrate/policies/artifacts", 200, "{\"artifacts\":[{\"id\":\"art-pii\",\"display_name\":\"PII Filter\"}]}")
                .on("/v1/orchestrate/policies/bindings", 200, "{\"bindings\":[{\"name\":\"pii\",\"artifact_id\":\"art-pii\","
                        + "\"hooks\":[\"agent_pre_invoke\"],\"agent_ids\":[\"agent-live\"],\"tool_ids\":[\"tool-1\"]}]}")
                .on("/v1/orchestrate/connections/applications", 200, "{\"applications\":[{\"connection_id\":\"conn-1\",\"app_id\":\"crm\","
                        + "\"is_configured\":true,\"environment\":\"live\",\"security_scheme\":\"oauth2\",\"preference\":\"member\"},"
                        + "{\"connection_id\":\"conn-2\",\"app_id\":\"unused\"}]}")
                .on("/v1/orchestrate/agents/external-chat", 200, "[{\"id\":\"agent-ext\",\"name\":\"currency_agent\","
                        + "\"title\":\"Currency Agent\",\"api_url\":\"https://agent.test/chat\",\"provider\":\"external_chat\"}]")
                .on("/v1/orchestrate/agents/agent-live/releases/3", 200, "{\"id\":\"agent-live\",\"llm\":\"groq/openai/gpt-oss-120b\","
                        + "\"description\":\"deployed\",\"tools\":[{\"tool_id\":\"tool-1\"}],\"collaborators\":[{\"collaborator_id\":\"agent-ext\"}],"
                        + "\"connection_ids\":[\"conn-1\",\"conn-2\"]}")
                .on("/v1/orchestrate/agents", 200, "["
                        + "{\"id\":\"agent-live\",\"name\":\"support_agent\",\"display_name\":\"Support\",\"description\":\"draft text\","
                        + "\"llm\":\"other/model\",\"tools\":[\"tool-2\"],\"environments\":[{\"name\":\"draft\"},{\"name\":\"live\",\"current_version\":3}]},"
                        + "{\"id\":\"agent-draft\",\"name\":\"support_agent_v2\",\"display_name\":\"Support\",\"tools\":[\"tool-2\"],"
                        + "\"environments\":[{\"name\":\"draft\"},{\"name\":\"live\",\"current_version\":null}]}]");
        client = new WatsonxClient(FakeWatsonx.INSTANCE, new WatsonxAuth(FakeWatsonx.INSTANCE, "k", ibm, () -> 0L), ibm);
        client.retryDelayMs = 0;
    }

    @Test
    public void liveAgentIsDescribedByItsDeployedVersion() throws Exception {
        Map<String, Object> agent = WatsonxDiscovery.discover(client).agents.get("agent-live");
        assertEquals("3", agent.get("live_version"));
        assertEquals("deployed", agent.get("description"));
        assertEquals("groq/openai/gpt-oss-120b", agent.get("llm"));
        assertEquals("GPT OSS", agent.get("model_label"));
        assertEquals(Collections.singletonList("get_order_status"), agent.get("tools"));
        assertEquals(Collections.singletonList("Currency Agent"), agent.get("collaborators"));
        assertEquals(Collections.singletonList("httpbinmcp"), agent.get("mcp_servers"));
        assertEquals(Collections.singletonList("crm (oauth2, member)"), agent.get("connections"), "only configured connections");
        assertEquals(Collections.singletonList("pii (PII Filter, agent_pre_invoke)"), agent.get("controls"));
        assertEquals(Collections.singletonList("pii (PII Filter, agent_pre_invoke)"), agent.get("tool_controls"));
        assertEquals(Arrays.asList("draft", "live"), agent.get("environments"));
    }

    @Test
    public void draftOnlyAgentIsDiscoveredFromItsDraftWithoutAReleaseCall() throws Exception {
        Map<String, Object> agent = WatsonxDiscovery.discover(client).agents.get("agent-draft");
        assertNull(agent.get("live_version"));
        assertEquals(Collections.singletonList("draft_only_tool"), agent.get("tools"));
        assertEquals(0, ibm.count("/agents/agent-draft/releases"));
    }

    @Test
    public void agentsSharingADisplayNameGetTheirInternalNameAppended() throws Exception {
        Map<String, Map<String, Object>> agents = WatsonxDiscovery.discover(client).agents;
        assertEquals("Support (support_agent)", agents.get("agent-live").get("display_name"));
        assertEquals("Support (support_agent_v2)", agents.get("agent-draft").get("display_name"));
    }

    @Test
    public void externalAgentsUseTheirTitle() throws Exception {
        Map<String, Object> agent = WatsonxDiscovery.discover(client).agents.get("agent-ext");
        assertEquals("external", agent.get("kind"));
        assertEquals("Currency Agent", agent.get("display_name"));
        assertEquals("https://agent.test/chat", agent.get("api_url"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void controlPlaneListsGatewayModelsMcpServersAndControls() throws Exception {
        Map<String, Object> controlPlane = WatsonxDiscovery.discover(client).controlPlane;
        List<Map<String, Object>> servers = (List<Map<String, Object>>) controlPlane.get("mcp_servers");
        assertEquals(1, servers.size());
        assertEquals("https://mcp.test", servers.get(0).get("server_url"));
        assertEquals(Collections.singletonList("get_order_status"), servers.get(0).get("tools"));
        List<Map<String, Object>> controls = (List<Map<String, Object>>) controlPlane.get("controls");
        assertEquals("PII Filter", controls.get(0).get("type"));
        assertEquals(Collections.singletonList("Support (support_agent)").size(), ((List<String>) controls.get(0).get("agents")).size());
        assertEquals(1, controlPlane.get("external_agents"));
        assertEquals(Collections.singletonList("crm (live, oauth2, member)"), controlPlane.get("connections"));

        List<Map<String, Object>> records = WatsonxRecords.controlPlaneRecords(controlPlane, WatsonxRecordsTest.CONTEXT, 0);
        assertEquals(2, records.size(), "one gateway record and one per MCP server");
    }

    @Test
    public void identityAddsCreatorAndPermissions() throws Exception {
        ibm.on("/connections/agents/agent-live/identity", 200, "{\"creator_email\":\"member-1\",\"connection_id\":\"id-1\","
                + "\"connections\":{\"total_tools_count\":4,\"total_scope_count\":2,\"items\":[{\"name\":\"crm\",\"icon\":\"x\"}]}}");
        Map<String, Object> agent = WatsonxDiscovery.discover(client).agents.get("agent-live");
        assertEquals("member-1", agent.get("created_by"));
        assertEquals("enabled", agent.get("agent_identity"));
        assertEquals(4, agent.get("accessible_tools_count"));
        assertEquals(Collections.singletonMap("name", "crm"), ((List<?>) agent.get("permissions")).get(0));
        assertNull(WatsonxDiscovery.discover(client).agents.get("agent-draft").get("agent_identity"), "other agents: identity not available");
    }
}
