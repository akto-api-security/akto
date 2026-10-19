package com.akto.jobs.executors.ibmwatsonx;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns Orchestrate discovery and conversation traces into Akto ingestion records.
 *
 * One conversation turn becomes one record per agent that ran in it: the agent the user talked to,
 * and each collaborator it called (native or external), linked by "called-by". The request is the
 * agent's last model input (system prompt, user message, tool calls and results); the response is
 * its final answer, so Akto shows tool steps in the input and the answer as the output.
 */
public final class WatsonxRecords {

    public static final String TRAFFIC_HOST = "watson-orchestrate.ibm.com";
    public static final String DEFAULT_SOURCE_TAG = "IBM_WATSONX";
    public static final String GATEWAY_BOT = "watsonx Orchestrate AI Gateway";
    static final String CONVERSATION_TRACE = "POST /orchestrate/runs";
    static final String COLLABORATOR_TOOL = "chat_with_collaborator_";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WatsonxRecords() {
    }

    /** Fields every record carries: where it goes in Akto and which job produced it. */
    public static final class Context {
        final String sourceTag;
        final int accountId;
        final String jobType;
        final String jobSubType;

        public Context(String sourceTag, int accountId, String jobType, String jobSubType) {
            this.sourceTag = sourceTag == null || sourceTag.isEmpty() ? DEFAULT_SOURCE_TAG : sourceTag;
            this.accountId = accountId;
            this.jobType = jobType;
            this.jobSubType = jobSubType;
        }
    }

    public static boolean isConversation(JsonNode trace) {
        return CONVERSATION_TRACE.equals(trace.path("name").asText());
    }

    // ---------------------------------------------------------------------------------------
    // Conversation traffic
    // ---------------------------------------------------------------------------------------

    public static List<Map<String, Object>> conversationRecords(JsonNode trace, Map<String, Map<String, Object>> inventory,
            Context context) {
        JsonNode attributes = attributes(trace.path("metadata"));
        String environment = firstNonEmpty(WatsonxDiscovery.textOrNull(attributes, "environment.name"),
                WatsonxDiscovery.textOrNull(trace, "environment"));
        JsonNode input = trace.path("input");
        String userMessage = textOf(input.get("content"));
        // A message sent to a running flow (e.g. an option picked in a flow's form), not to the agent.
        boolean flowInput = isTruthy(input.path("additional_properties").get("tool_flow_properties"));

        List<Segment> parts = segments(trace);
        if (parts.isEmpty()) {   // e.g. a run stopped before the agent started (failed plug-in)
            Segment fallback = new Segment(WatsonxDiscovery.textOrNull(trace, "agentId"));
            fallback.depth = 1;
            parts = Collections.singletonList(fallback);
        }
        Segment root = parts.get(0);
        Map<String, Object> rootKnown = inventory.getOrDefault(root.agentId, Collections.emptyMap());
        String rootName = firstNonEmpty(str(rootKnown.get("display_name")), root.displayName);

        List<Map<String, Object>> records = new ArrayList<>();
        for (Segment part : parts) {
            Map<String, Object> known = inventory.getOrDefault(part.agentId, Collections.emptyMap());
            String bot = firstNonEmpty(str(known.get("display_name")), part.displayName, part.agentName, part.agentId, "unknown-agent");
            JsonNode last = part.generations.isEmpty() ? null : part.generations.get(part.generations.size() - 1);
            String model = null;
            for (JsonNode generation : part.generations) {
                model = firstNonEmpty(model, WatsonxDiscovery.textOrNull(generation, "model"));
            }
            if (model == null) {
                model = publicModel(str(known.get("llm")));
            }

            List<Map<String, Object>> messages = last == null ? new ArrayList<>() : chatMessages(last);
            if (messages.isEmpty()) {   // e.g. the agent started a flow without a model call carrying the conversation
                messages.add(message("user", userMessage));
            }
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("messages", messages);
            if (model != null) {
                request.put("model", model);
            }

            JsonNode lastOutput = last == null ? null : last.path("output");
            List<Map<String, Object>> toolCalls = toolCalls(part);
            List<String> collaborators = new ArrayList<>();
            for (Map<String, Object> call : toolCalls) {
                String name = str(call.get("name"));
                if (name != null && name.startsWith(COLLABORATOR_TOOL)) {
                    collaborators.add(name.substring(COLLABORATOR_TOOL.length()));
                }
            }
            String answer = lastOutput == null ? "" : textOf(lastOutput.get("content"));
            if (answer.isEmpty() && !toolCalls.isEmpty()) {
                // The agent returned a collaborator's (or tool's) reply as is, with no final model call.
                Object result = toolCalls.get(toolCalls.size() - 1).get("result");
                answer = result instanceof String ? (String) result : (result == null ? "" : json(result));
            }
            Map<String, Object> assistant = message("assistant", answer);
            String reasoning = lastOutput == null ? null : WatsonxDiscovery.textOrNull(lastOutput.path("additional_kwargs"), "reasoning");
            if (reasoning != null) {
                assistant.put("reasoning", reasoning);
            }

            long inputTokens = 0;
            long outputTokens = 0;
            for (JsonNode generation : part.generations) {
                inputTokens += generation.path("promptTokens").asLong(0) + generation.path("inputTokens").asLong(0);
                outputTokens += generation.path("completionTokens").asLong(0) + generation.path("outputTokens").asLong(0);
            }
            String calledBy = part.isCollaborator ? rootName : null;

            Map<String, Object> usage = new LinkedHashMap<>();
            usage.put("inputTokens", inputTokens);
            usage.put("outputTokens", outputTokens);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("agent-id", part.agentId);
            metadata.put("agent-name", firstNonEmpty(part.agentName, str(known.get("name"))));
            metadata.put("agent-display-name", bot);
            metadata.put("environment", environment);
            metadata.put("model", model);
            metadata.put("usage", usage);
            metadata.put("tools-called", toolCalls);
            metadata.put("collaborators-called", collaborators);
            metadata.put("called-by", calledBy);
            metadata.put("interaction", flowInput ? "flow-input" : "agent-turn");
            metadata.put("configured-tools", known.get("tools"));
            metadata.put("configured-collaborators", known.get("collaborators"));
            metadata.put("plugins", known.get("plugins"));
            metadata.put("description", known.get("description"));
            metadata.put("trace-id", WatsonxDiscovery.textOrNull(trace, "id"));
            metadata.put("session-id", WatsonxDiscovery.textOrNull(trace, "sessionId"));
            metadata.put("thread-id", WatsonxDiscovery.textOrNull(attributes, "thread.id"));
            metadata.put("conversation-id", WatsonxDiscovery.textOrNull(attributes, "conversation.id"));

            Map<String, Object> choice = new LinkedHashMap<>();
            choice.put("index", 0);
            choice.put("message", assistant);
            choice.put("finish_reason", "stop");
            Map<String, Object> responseUsage = new LinkedHashMap<>();
            responseUsage.put("prompt_tokens", inputTokens);
            responseUsage.put("completion_tokens", outputTokens);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("object", "chat.completion");
            response.put("model", model);
            response.put("choices", Collections.singletonList(choice));
            response.put("usage", responseUsage);
            response.put("ibmMetadata", metadata);

            Map<String, String> tags = agentTags(context, bot, part.agentId, model, known);
            // per-message tags as well (Akto keeps the latest value per tag on the collection)
            putIfPresent(tags, "environment", environment);
            putIfPresent(tags, "session-id", WatsonxDiscovery.textOrNull(trace, "sessionId"));
            putIfPresent(tags, "thread-id", WatsonxDiscovery.textOrNull(attributes, "thread.id"));
            putIfPresent(tags, "trace-id", WatsonxDiscovery.textOrNull(trace, "id"));
            putIfPresent(tags, "user-id", WatsonxDiscovery.textOrNull(trace, "userId"));
            putIfPresent(tags, "user-email", part.userEmail);
            putIfPresent(tags, "called-by", calledBy);
            tags.put("capture", flowInput ? "orchestrate-flow-input" : "orchestrate-traces");

            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("host", TRAFFIC_HOST);
            headers.put("content-type", "application/json");
            // read by Akto's trace views: session, message id and user
            putIfPresent(headers, "x-akto-installer-akto_session_id", WatsonxDiscovery.textOrNull(trace, "sessionId"));
            putIfPresent(headers, "x-akto-installer-akto_message_id", WatsonxDiscovery.textOrNull(trace, "id"));
            putIfPresent(headers, "x-akto-installer-user_email", part.userEmail);
            putIfPresent(headers, "x-wxo-environment", environment);
            putIfPresent(headers, "x-wxo-user-id", WatsonxDiscovery.textOrNull(trace, "userId"));
            putIfPresent(headers, "x-wxo-thread-id", WatsonxDiscovery.textOrNull(attributes, "thread.id"));
            putIfPresent(headers, "x-wxo-called-by", calledBy);
            headers.put("x-wxo-interaction", flowInput ? "flow-input" : "agent-turn");

            String actor = firstNonEmpty(part.userEmail, WatsonxDiscovery.textOrNull(trace, "userId"), "0.0.0.0");
            records.add(record(context, "/v1/orchestrate/agents/" + part.agentId + "/runs", "POST", headers, request, response,
                    actor.replace(":", "_"), traceTimeMs(trace), tags));
        }
        return records;
    }

    // ---------------------------------------------------------------------------------------
    // Discovery
    // ---------------------------------------------------------------------------------------

    /** One inventory record per agent, so agents without traffic appear in Akto too. */
    public static Map<String, Object> agentDiscoveryRecord(Map<String, Object> agent, Context context, long nowMs) {
        String bot = str(agent.get("display_name"));
        Map<String, String> tags = agentTags(context, bot, str(agent.get("id")), publicModel(str(agent.get("llm"))), agent);
        tags.put("capture", "orchestrate-discovery");
        tags.put("discovery", "true");
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("host", TRAFFIC_HOST);
        headers.put("x-wxo-discovery", "true");
        Map<String, Object> metadata = new LinkedHashMap<>(agent);
        metadata.put("resourceType", "external".equals(agent.get("kind")) ? "ORCHESTRATE_EXTERNAL_AGENT" : "ORCHESTRATE_AGENT");
        return record(context, "/v1/orchestrate/agents/" + agent.get("id"), "GET", headers, Collections.emptyMap(),
                Collections.singletonMap("ibmMetadata", metadata), "0.0.0.0", nowMs, tags);
    }

    /** The control plane itself: one AI gateway record, and one record per MCP server with its tools. */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> controlPlaneRecords(Map<String, Object> controlPlane, Context context, long nowMs) {
        List<Map<String, Object>> records = new ArrayList<>();
        List<String> modelIds = new ArrayList<>();
        for (Map<String, Object> model : (List<Map<String, Object>>) controlPlane.get("models")) {
            modelIds.add(str(model.get("id")));
        }
        List<String> serverNames = new ArrayList<>();
        for (Map<String, Object> server : (List<Map<String, Object>>) controlPlane.get("mcp_servers")) {
            serverNames.add(str(server.get("name")));
        }
        List<String> controlNames = new ArrayList<>();
        for (Map<String, Object> control : (List<Map<String, Object>>) controlPlane.get("controls")) {
            controlNames.add(str(control.get("name")));
        }

        Map<String, String> gatewayTags = baseTags(context, GATEWAY_BOT, "WATSONX_AI_GATEWAY");
        putIfPresent(gatewayTags, "models", joined(modelIds));
        putIfPresent(gatewayTags, "mcp-servers", joined(serverNames));
        gatewayTags.put("controls", controlNames.isEmpty() ? "none" : joined(controlNames));
        gatewayTags.put("external-agents", String.valueOf(controlPlane.get("external_agents")));
        Map<String, Object> gateway = new LinkedHashMap<>();
        gateway.put("resourceType", "AI_GATEWAY");
        gateway.putAll(controlPlane);
        records.add(record(context, "/v1/orchestrate/gateway", "GET", discoveryHeaders(), Collections.emptyMap(),
                Collections.singletonMap("ibmMetadata", gateway), "0.0.0.0", nowMs, gatewayTags));

        for (Map<String, Object> server : (List<Map<String, Object>>) controlPlane.get("mcp_servers")) {
            Map<String, String> tags = baseTags(context, str(server.get("name")), "WATSONX_MCP_SERVER");
            tags.put("mcp-server", "MCP Server");
            putIfPresent(tags, "tools", joined((List<String>) server.get("tools")));
            List<String> controls = (List<String>) server.get("controls");
            tags.put("controls", controls == null || controls.isEmpty() ? "none" : joined(controls));
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("resourceType", "MCP_SERVER");
            metadata.putAll(server);
            records.add(record(context, "/v1/orchestrate/toolkits/" + server.get("id"), "GET", discoveryHeaders(),
                    Collections.emptyMap(), Collections.singletonMap("ibmMetadata", metadata), "0.0.0.0", nowMs, tags));
        }
        return records;
    }

    // ---------------------------------------------------------------------------------------
    // Tags and records
    // ---------------------------------------------------------------------------------------

    /**
     * The same tags on every record of an agent (traffic and discovery), built from discovery:
     * what the agent is configured with and who owns it.
     */
    @SuppressWarnings("unchecked")
    static Map<String, String> agentTags(Context context, String bot, String agentId, String model, Map<String, Object> agent) {
        boolean known = agent != null && !agent.isEmpty();
        Map<String, String> tags = baseTags(context, bot,
                known && "external".equals(agent.get("kind")) ? "WATSONX_EXTERNAL_AGENT" : "WATSONX_ORCHESTRATE_AGENT");
        putIfPresent(tags, "agent-id", agentId);
        putIfPresent(tags, "model", model);
        if (!known) {
            return tags;
        }
        putIfPresent(tags, "model-provider", str(agent.get("model_provider")));
        putIfPresent(tags, "tools", joined((List<String>) agent.get("tools")));
        putIfPresent(tags, "collaborators", joined((List<String>) agent.get("collaborators")));
        putIfPresent(tags, "mcp-servers", joined((List<String>) agent.get("mcp_servers")));
        putIfPresent(tags, "connections", joined((List<String>) agent.get("connections")));
        List<String> controls = new ArrayList<>();
        for (String key : new String[] {"controls", "tool_controls", "model_controls"}) {
            Object values = agent.get(key);
            if (values instanceof List) {
                controls.addAll((List<String>) values);
            }
        }
        // "none" is a finding: an agent without any guardrail control
        tags.put("controls", controls.isEmpty() ? "none" : joined(controls));
        putIfPresent(tags, "created-by", str(agent.get("created_by")));
        putIfPresent(tags, "updated-by", str(agent.get("updated_by")));
        putIfPresent(tags, "created-at", str(agent.get("created_at")));
        putIfPresent(tags, "last-updated-at", str(agent.get("last_updated_at")));
        putIfPresent(tags, "agent-identity", str(agent.get("agent_identity")));
        List<String> permissionConnections = new ArrayList<>();
        Object permissions = agent.get("permissions");
        if (permissions instanceof List) {
            for (Object permission : (List<Object>) permissions) {
                if (permission instanceof Map) {
                    Map<String, Object> entry = (Map<String, Object>) permission;
                    permissionConnections.add(firstNonEmpty(str(entry.get("name")), str(entry.get("app_id"))));
                }
            }
        }
        putIfPresent(tags, "permission-connections", joined(permissionConnections));
        putIfPresent(tags, "accessible-tools-count", str(agent.get("accessible_tools_count")));
        putIfPresent(tags, "permissions-count", str(agent.get("permissions_count")));
        putIfPresent(tags, "environments", joined((List<String>) agent.get("environments")));
        return tags;
    }

    private static Map<String, String> baseTags(Context context, String bot, String agentType) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("source", context.sourceTag);
        tags.put("gen-ai", "Gen AI");
        tags.put("cloud-provider", "IBM_WATSONX");
        tags.put("cloud-source", "WATSONX_ORCHESTRATE");
        tags.put("agentType", agentType);
        putIfPresent(tags, "bot-name", bot);
        return tags;
    }

    private static Map<String, String> discoveryHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("host", TRAFFIC_HOST);
        headers.put("x-wxo-discovery", "true");
        return headers;
    }

    private static Map<String, Object> record(Context context, String path, String method, Map<String, String> requestHeaders,
            Object request, Object response, String actor, long timeMs, Map<String, String> tags) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("path", path);
        record.put("method", method);
        record.put("requestHeaders", json(requestHeaders));
        record.put("responseHeaders", json(Collections.singletonMap("content-type", "application/json")));
        record.put("requestPayload", json(request));
        record.put("responsePayload", json(response));
        record.put("ip", actor);
        record.put("destIp", "");
        // epoch seconds: the runtime parses "time" as an int (milliseconds would overflow and become "now")
        record.put("time", String.valueOf(timeMs / 1000));
        record.put("statusCode", "200");
        record.put("type", "HTTP/1.1");
        record.put("status", "OK");
        record.put("akto_account_id", String.valueOf(context.accountId));
        record.put("akto_vxlan_id", "");
        record.put("akto_job_type", context.jobType == null ? "" : context.jobType);
        record.put("akto_job_sub_type", context.jobSubType == null ? "" : context.jobSubType);
        record.put("is_pending", "false");
        record.put("source", "MIRRORING");
        record.put("tag", json(tags));
        record.put("contextSource", "AGENTIC");
        return record;
    }

    // ---------------------------------------------------------------------------------------
    // Trace structure
    // ---------------------------------------------------------------------------------------

    /** The steps of one agent within a turn. */
    static final class Segment {
        final String agentId;
        String agentName;
        String displayName;
        int depth;
        boolean isCollaborator;
        String userEmail;
        final List<JsonNode> generations = new ArrayList<>();
        final List<JsonNode> tools = new ArrayList<>();

        Segment(String agentId) {
            this.agentId = agentId;
        }
    }

    /**
     * Steps grouped by the agent that ran them, in order of first appearance. A collaborator
     * handoff shows as the caller's tool call chat_with_collaborator_<name>, followed by "agent"
     * steps of the collaborator (is_collaborator=true, agent_depth 2).
     */
    static List<Segment> segments(JsonNode trace) {
        Map<String, Segment> groups = new LinkedHashMap<>();
        Segment current = null;
        for (JsonNode step : trace.path("observations")) {
            JsonNode input = step.path("input");
            String name = step.path("name").asText();
            if (("agent".equals(name) || "LangGraph".equals(name)) && input.isObject() && input.hasNonNull("current_agent_id")) {
                String agentId = input.get("current_agent_id").asText();
                current = groups.get(agentId);
                if (current == null) {
                    current = new Segment(agentId);
                    current.agentName = WatsonxDiscovery.textOrNull(input, "current_agent");
                    current.displayName = WatsonxDiscovery.textOrNull(input, "agent_display_name");
                    current.depth = input.path("agent_depth").asInt(1);
                    current.isCollaborator = input.path("is_collaborator").asBoolean(false);
                    current.userEmail = WatsonxDiscovery.textOrNull(input.path("context"), "wxo_email_id");
                    groups.put(agentId, current);
                }
                continue;
            }
            if (current == null) {
                continue;
            }
            if ("GENERATION".equals(step.path("type").asText())) {
                current.generations.add(step);
            }
            JsonNode attributes = attributes(step.path("metadata"));
            if (attributes.hasNonNull("tool.name") && step.path("output").isObject()) {
                current.tools.add(step);
            }
        }
        return new ArrayList<>(groups.values());
    }

    static List<Map<String, Object>> chatMessages(JsonNode generation) {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (JsonNode message : generation.path("input")) {
            if (message.isObject()) {
                messages.add(message(firstNonEmpty(WatsonxDiscovery.textOrNull(message, "role"), "user"),
                        textOf(message.get("content"))));
            }
        }
        return messages;
    }

    /** The agent's tool calls (from its model's output) with each call's arguments and result. */
    static List<Map<String, Object>> toolCalls(Segment segment) {
        List<Map<String, Object>> calls = new ArrayList<>();
        for (JsonNode generation : segment.generations) {
            for (JsonNode call : generation.path("output").path("tool_calls")) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", WatsonxDiscovery.textOrNull(call, "id"));
                entry.put("name", WatsonxDiscovery.textOrNull(call, "name"));
                entry.put("arguments", WatsonxDiscovery.plain(call.has("args") ? call.get("args") : call.get("arguments")));
                calls.add(entry);
            }
        }
        Map<String, Object> results = new LinkedHashMap<>();
        for (JsonNode step : segment.tools) {
            JsonNode output = step.path("output");
            results.put(output.path("tool_call_id").asText(), asJson(output.get("content")));
        }
        for (Map<String, Object> call : calls) {
            Object id = call.get("id");
            if (id != null && results.containsKey(id)) {
                call.put("result", results.get(id));
            }
        }
        return calls;
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    /** Text of a message content: a string, {"text": ...}, or a list of parts. */
    static String textOf(JsonNode content) {
        if (content == null || content.isNull() || content.isMissingNode()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isObject()) {
            if (content.has("text")) {
                JsonNode text = content.get("text");
                return text.isContainerNode() ? text.toString() : textOf(text);
            }
            return textOf(content.get("content"));
        }
        if (content.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode part : content) {
                String text = textOf(part);
                if (!text.isEmpty()) {
                    parts.add(text);
                }
            }
            return String.join("\n", parts);
        }
        return content.asText();
    }

    /** groq/openai/gpt-oss-120b -> openai/gpt-oss-120b, as the traces report the model. */
    static String publicModel(String llm) {
        if (llm == null) {
            return null;
        }
        int first = llm.indexOf('/');
        return first >= 0 && llm.indexOf('/', first + 1) > 0 ? llm.substring(first + 1) : llm;
    }

    static long traceTimeMs(JsonNode trace) {
        String timestamp = WatsonxDiscovery.textOrNull(trace, "timestamp");
        try {
            return timestamp == null ? System.currentTimeMillis() : Instant.parse(timestamp).toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    /** Observation / trace metadata attributes, which IBM sends either as an object or as a JSON string. */
    private static JsonNode attributes(JsonNode metadata) {
        JsonNode attributes = metadata.path("attributes");
        if (attributes.isTextual()) {
            try {
                return MAPPER.readTree(attributes.asText());
            } catch (Exception e) {
                return MAPPER.createObjectNode();
            }
        }
        return attributes;
    }

    private static Object asJson(JsonNode value) {
        if (value != null && value.isTextual()) {
            String text = value.asText().trim();
            // only JSON objects/arrays: readTree would read "100 USD is ..." as the number 100
            if (!text.startsWith("{") && !text.startsWith("[")) {
                return value.asText();
            }
            try {
                return WatsonxDiscovery.plain(MAPPER.readTree(value.asText()));
            } catch (Exception e) {
                return value.asText();
            }
        }
        return WatsonxDiscovery.plain(value);
    }

    private static boolean isTruthy(JsonNode value) {
        return value != null && !value.isNull() && !(value.isContainerNode() && value.size() == 0)
                && !(value.isTextual() && value.asText().isEmpty()) && !(value.isBoolean() && !value.asBoolean());
    }

    private static Map<String, Object> message(String role, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private static void putIfPresent(Map<String, String> map, String key, String value) {
        if (value != null && !value.isEmpty()) {
            map.put(key, value);
        }
    }

    private static String joined(List<String> values) {
        if (values == null) {
            return null;
        }
        List<String> present = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                present.add(value);
            }
        }
        return present.isEmpty() ? null : String.join(", ", present);
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
