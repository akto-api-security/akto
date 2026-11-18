package com.akto.action;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestMcpAgentActionAgenticContext {

    private static final String IDENTITY_ONLY = "This is only the asset/device identity — use the akto_agentic_* tools "
            + "(with the collectionIds, assetName, or deviceId below) to fetch its data before answering.";

    private static Map<String, Object> data(String scope) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scope", scope);
        return data;
    }

    @Test
    public void anAssetIsStillSentAsIdentityOnly() {
        Map<String, Object> data = data("asset");
        data.put("assetName", "crm-mcp");

        assertEquals("CONTEXT SCOPE: Single agentic asset. " + IDENTITY_ONLY + "\n\n{\"scope\":\"asset\",\"assetName\":\"crm-mcp\"}",
                McpAgentAction.agenticObserveContext(data));
    }

    @Test
    public void aDeviceAndAnUnknownScopeKeepTheirLabels() {
        assertTrue(McpAgentAction.agenticObserveContext(data("device")).startsWith("CONTEXT SCOPE: Single device. " + IDENTITY_ONLY));
        assertTrue(McpAgentAction.agenticObserveContext(data("something-else")).startsWith("CONTEXT SCOPE: Agentic context. " + IDENTITY_ONLY));
        assertTrue(McpAgentAction.agenticObserveContext(new HashMap<>()).startsWith("CONTEXT SCOPE: Agentic context. "));
    }

    @Test
    public void theDiscoveryOverviewIsSentWholeAndTheAgentIsToAnswerFromIt() {
        Map<String, Object> todo = new HashMap<>();
        todo.put("id", "exposedMcp");
        todo.put("title", "1 MCP server reachable from the internet with no authentication");
        todo.put("assets", Collections.singletonList(Collections.singletonMap("id", 42)));
        Map<String, Object> data = data(McpAgentAction.SCOPE_AGENT_DISCOVERY);
        data.put("todos", Arrays.asList(todo));

        String context = McpAgentAction.agenticObserveContext(data);

        assertTrue(context.startsWith("CONTEXT SCOPE: Agent discovery overview. This is the Agent discovery page the user is looking at"));
        assertTrue("the findings are in the context", context.contains("1 MCP server reachable from the internet with no authentication"));
        assertTrue("the asset id is there for the agent's tools", context.contains("\"id\":42"));
        assertFalse("not the identity-only wording", context.contains("only the asset/device identity"));
    }

    @Test
    public void theDiscoveryScopeMatchesTheOneThePageSends() {
        assertEquals("argus_agent_discovery", McpAgentAction.SCOPE_AGENT_DISCOVERY);
    }
}
