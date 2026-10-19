package com.akto.jobs.executors.ibmwatsonx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Conversion of real-shaped Orchestrate traces (test resource ibmwatsonx/conversation_traces.json). */
public class WatsonxRecordsTest {

    static final ObjectMapper MAPPER = new ObjectMapper();
    static final WatsonxRecords.Context CONTEXT = new WatsonxRecords.Context(null, 1000, "IBM_WATSONX_CONNECTOR", "IBM_WATSONX");

    private Map<String, JsonNode> traces;
    private Map<String, Map<String, Object>> inventory;

    static Map<String, JsonNode> loadTraces() throws Exception {
        Map<String, JsonNode> byId = new HashMap<>();
        try (InputStream in = WatsonxRecordsTest.class.getResourceAsStream("/ibmwatsonx/conversation_traces.json")) {
            for (JsonNode trace : MAPPER.readTree(in)) {
                byId.put(trace.path("id").asText(), trace);
            }
        }
        return byId;
    }

    static Map<String, Object> agent(String id, String name, String displayName, String kind) {
        Map<String, Object> agent = new LinkedHashMap<>();
        agent.put("id", id);
        agent.put("name", name);
        agent.put("display_name", displayName);
        agent.put("kind", kind);
        agent.put("llm", "groq/openai/gpt-oss-120b");
        agent.put("tools", new ArrayList<String>());
        agent.put("collaborators", new ArrayList<String>());
        agent.put("controls", new ArrayList<String>());
        agent.put("tool_controls", new ArrayList<String>());
        agent.put("model_controls", new ArrayList<String>());
        agent.put("environments", Arrays.asList("draft", "live"));
        return agent;
    }

    @BeforeEach
    public void setUp() throws Exception {
        traces = loadTraces();
        inventory = new HashMap<>();
        Map<String, Object> supervisor = agent("agent-supervisor", "akto_supervisor_agent", "AKTO Supervisor Agent", "native");
        supervisor.put("collaborators", Arrays.asList("AKTO Tool Agent", "AKTO External Currency Agent"));
        inventory.put("agent-supervisor", supervisor);
        Map<String, Object> tool = agent("agent-tool", "akto_tool_agent", "AKTO Tool Agent", "native");
        tool.put("tools", Arrays.asList("get_order_status"));
        tool.put("controls", Arrays.asList("pii (PII Filter, agent_pre_invoke)"));
        inventory.put("agent-tool", tool);
        inventory.put("agent-external", agent("agent-external", "akto_external_currency_agent", "AKTO External Currency Agent", "external"));
    }

    static Map<String, String> tags(Map<String, Object> record) throws Exception {
        return MAPPER.readValue((String) record.get("tag"), Map.class);
    }

    static Map<String, String> headers(Map<String, Object> record) throws Exception {
        return MAPPER.readValue((String) record.get("requestHeaders"), Map.class);
    }

    static JsonNode request(Map<String, Object> record) throws Exception {
        return MAPPER.readTree((String) record.get("requestPayload"));
    }

    static JsonNode response(Map<String, Object> record) throws Exception {
        return MAPPER.readTree((String) record.get("responsePayload"));
    }

    @Test
    public void supervisorAndNativeCollaboratorBecomeTwoLinkedRecords() throws Exception {
        List<Map<String, Object>> records = WatsonxRecords.conversationRecords(traces.get("trace-native-collaborator"), inventory, CONTEXT);
        assertEquals(2, records.size());

        Map<String, Object> supervisor = records.get(0);
        assertEquals("AKTO Supervisor Agent", tags(supervisor).get("bot-name"));
        assertNull(tags(supervisor).get("called-by"), "the agent the user talked to is not called by anyone");
        assertEquals("Your order 1002 is processing and should arrive in 5 days.",
                response(supervisor).path("choices").get(0).path("message").path("content").asText());
        assertFalse(response(supervisor).path("choices").get(0).path("message").has("tool_calls"), "the answer carries no tool_calls (Akto shows such a message as a tool step)");
        assertEquals(4, request(supervisor).path("messages").size(), "the request is the last model input, with the tool step");
        assertEquals("akto_tool_agent", response(supervisor).path("ibmMetadata").path("collaborators-called").get(0).asText());
        assertEquals(597, response(supervisor).path("ibmMetadata").path("usage").path("inputTokens").asInt());

        Map<String, Object> collaborator = records.get(1);
        assertEquals("AKTO Tool Agent", tags(collaborator).get("bot-name"));
        assertEquals("AKTO Supervisor Agent", tags(collaborator).get("called-by"));
        assertEquals("AKTO Supervisor Agent", headers(collaborator).get("x-wxo-called-by"));
        JsonNode toolCall = response(collaborator).path("ibmMetadata").path("tools-called").get(0);
        assertEquals("get_order_status", toolCall.path("name").asText());
        assertEquals("processing", toolCall.path("result").path("status").asText());
        assertEquals("get_order_status", tags(collaborator).get("tools"));
        assertEquals("pii (PII Filter, agent_pre_invoke)", tags(collaborator).get("controls"));
    }

    @Test
    public void recordsCarryTheTraceTimeSessionUserAndEnvironment() throws Exception {
        Map<String, Object> record = WatsonxRecords.conversationRecords(traces.get("trace-native-collaborator"), inventory, CONTEXT).get(0);
        assertEquals(String.valueOf(java.time.Instant.parse("2026-10-15T08:20:04.937Z").getEpochSecond()),
                record.get("time"), "epoch seconds of the trace");
        assertEquals("session-1", headers(record).get("x-akto-installer-akto_session_id"));
        assertEquals("trace-native-collaborator", headers(record).get("x-akto-installer-akto_message_id"));
        assertEquals("member-1", headers(record).get("x-akto-installer-user_email"));
        assertEquals("live", tags(record).get("environment"));
        assertEquals("IBM_WATSONX", tags(record).get("source"));
        assertEquals("watson-orchestrate.ibm.com", headers(record).get("host"));
        assertEquals("orchestrate-traces", tags(record).get("capture"));
        assertEquals("1000", record.get("akto_account_id"));
    }

    @Test
    public void externalCollaboratorUsesItsTitleAndTheSupervisorAnswerFallsBackToItsReply() throws Exception {
        List<Map<String, Object>> records = WatsonxRecords.conversationRecords(traces.get("trace-external-collaborator"), inventory, CONTEXT);
        assertEquals(2, records.size());
        Map<String, Object> supervisor = records.get(0);
        assertEquals("100 USD is about 92.59 EUR (fixed test rates).",
                response(supervisor).path("choices").get(0).path("message").path("content").asText());
        Map<String, Object> external = records.get(1);
        assertEquals("AKTO External Currency Agent", tags(external).get("bot-name"));
        assertEquals("WATSONX_EXTERNAL_AGENT", tags(external).get("agentType"));
        assertEquals("AKTO Supervisor Agent", tags(external).get("called-by"));
    }

    @Test
    public void flowInputIsKeptAndLabelled() throws Exception {
        List<Map<String, Object>> records = WatsonxRecords.conversationRecords(traces.get("trace-flow-input"), inventory, CONTEXT);
        assertEquals(1, records.size());
        assertEquals("orchestrate-flow-input", tags(records.get(0)).get("capture"));
        assertEquals("flow-input", headers(records.get(0)).get("x-wxo-interaction"));
        assertEquals("1", request(records.get(0)).path("messages").get(0).path("content").asText());
        assertEquals("development", tags(records.get(0)).get("environment"));
    }

    @Test
    public void runStoppedBeforeAnyModelCallStillRecordsTheUserMessage() throws Exception {
        List<Map<String, Object>> records = WatsonxRecords.conversationRecords(traces.get("trace-plugin-failed"), new HashMap<>(), CONTEXT);
        assertEquals(1, records.size());
        assertEquals("akto_test_agent", tags(records.get(0)).get("bot-name"), "name from the trace when the agent is not in discovery");
        assertEquals("Summarize the capabilities of watsonx Orchestrate.",
                request(records.get(0)).path("messages").get(0).path("content").asText());
        assertEquals("draft", tags(records.get(0)).get("environment"));
    }

    @Test
    public void onlyConversationTracesAreConverted() {
        assertTrue(WatsonxRecords.isConversation(traces.get("trace-flow-input")));
        assertFalse(WatsonxRecords.isConversation(traces.get("trace-ui-call")));
    }

    @Test
    public void sourceTagCanBeOverriddenAndModelNameIsThePublicOne() throws Exception {
        WatsonxRecords.Context bedrock = new WatsonxRecords.Context("AWS_BEDROCK", 1, "t", "s");
        Map<String, Object> record = WatsonxRecords.conversationRecords(traces.get("trace-plugin-failed"), inventory, bedrock).get(0);
        assertEquals("AWS_BEDROCK", tags(record).get("source"));
        assertEquals("openai/gpt-oss-120b", WatsonxRecords.publicModel("groq/openai/gpt-oss-120b"));
        assertEquals("gpt-4o", WatsonxRecords.publicModel("gpt-4o"));
    }

    @Test
    public void agentWithoutControlsIsFlagged() throws Exception {
        Map<String, Object> record = WatsonxRecords.agentDiscoveryRecord(inventory.get("agent-supervisor"), CONTEXT, 0);
        assertEquals("none", tags(record).get("controls"));
        assertEquals("orchestrate-discovery", tags(record).get("capture"));
        assertEquals("AKTO Tool Agent, AKTO External Currency Agent", tags(record).get("collaborators"));
        assertEquals("ORCHESTRATE_AGENT", response(record).path("ibmMetadata").path("resourceType").asText());
    }
}
