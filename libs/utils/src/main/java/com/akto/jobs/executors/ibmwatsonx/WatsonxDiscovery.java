package com.akto.jobs.executors.ibmwatsonx;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Discovers everything an Orchestrate instance (IBM's agent control plane) manages: native agents
 * (draft and live, with the deployed version of live agents), external agents, tools, MCP servers
 * (toolkits), AI gateway models, controls bound to agents/tools/models, configured connections, and
 * agents' identity and permissions.
 */
public final class WatsonxDiscovery {

    private WatsonxDiscovery() {
    }

    public static final class Result {
        /** By agent id: what each agent is configured with. */
        public final Map<String, Map<String, Object>> agents;
        /** The AI gateway / control plane summary. */
        public final Map<String, Object> controlPlane;

        Result(Map<String, Map<String, Object>> agents, Map<String, Object> controlPlane) {
            this.agents = agents;
            this.controlPlane = controlPlane;
        }
    }

    public static Result discover(WatsonxClient client) throws IOException {
        Map<String, JsonNode> tools = byId(client.tools());
        JsonNode nativeAgents = client.agents();
        JsonNode externalAgents = client.externalAgents();
        Map<String, JsonNode> allAgents = byId(nativeAgents);
        allAgents.putAll(byId(externalAgents));
        Map<String, JsonNode> toolkits = byId(client.toolkits());
        Map<String, JsonNode> models = byId(client.models());
        Map<String, String> controlTypes = new HashMap<>();
        for (JsonNode type : client.controlTypes()) {
            controlTypes.put(type.path("id").asText(), firstText(type, "display_name", "name"));
        }
        List<JsonNode> bindings = list(client.controlBindings());
        Map<String, JsonNode> configuredConnections = new LinkedHashMap<>();
        for (JsonNode connection : client.connections()) {
            if (connection.path("is_configured").asBoolean(false) || connection.path("credentials_entered").asBoolean(false)) {
                configuredConnections.put(connection.path("connection_id").asText(), connection);
            }
        }

        Map<String, Map<String, Object>> inventory = new LinkedHashMap<>();
        for (JsonNode agent : nativeAgents) {
            inventory.put(agent.path("id").asText(), describe(client, agent, false, tools, allAgents, toolkits, models,
                    controlTypes, bindings, configuredConnections));
        }
        for (JsonNode agent : externalAgents) {
            inventory.put(agent.path("id").asText(), describe(client, agent, true, tools, allAgents, toolkits, models,
                    controlTypes, bindings, configuredConnections));
        }
        disambiguateDisplayNames(inventory);

        Map<String, Object> controlPlane = new LinkedHashMap<>();
        List<Map<String, Object>> modelList = new ArrayList<>();
        for (Map.Entry<String, JsonNode> model : models.entrySet()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", model.getKey());
            entry.put("label", textOrNull(model.getValue(), "label"));
            entry.put("provider", textOrNull(model.getValue(), "type"));
            entry.put("controls", controlsOn("model", Collections.singletonList(model.getKey()), bindings, controlTypes));
            modelList.add(entry);
        }
        controlPlane.put("models", modelList);
        List<Map<String, Object>> servers = new ArrayList<>();
        for (JsonNode toolkit : toolkits.values()) {
            if (!"mcp".equals(toolkit.path("kind").asText())) {
                continue;
            }
            List<String> toolIds = texts(toolkit.path("tools"));
            Map<String, Object> server = new LinkedHashMap<>();
            server.put("id", toolkit.path("id").asText());
            server.put("name", textOrNull(toolkit, "name"));
            server.put("description", textOrNull(toolkit, "description"));
            server.put("transport", textOrNull(toolkit.path("mcp"), "transport"));
            server.put("server_url", textOrNull(toolkit.path("mcp"), "server_url"));
            server.put("tools", names(toolkit.path("tools"), tools));
            server.put("controls", controlsOn("tool", toolIds, bindings, controlTypes));
            servers.add(server);
        }
        controlPlane.put("mcp_servers", servers);
        controlPlane.put("control_types", new ArrayList<>(new TreeSet<>(controlTypes.values())));
        List<Map<String, Object>> controls = new ArrayList<>();
        for (JsonNode binding : bindings) {
            Map<String, Object> control = new LinkedHashMap<>();
            control.put("name", firstText(binding, "display_name", "name"));
            control.put("type", controlTypes.get(binding.path("artifact_id").asText()));
            control.put("hooks", texts(binding.path("hooks")));
            control.put("agents", names(binding.path("agent_ids"), allAgents));
            control.put("tools", names(binding.path("tool_ids"), tools));
            control.put("models", texts(binding.path("model_ids")));
            controls.add(control);
        }
        controlPlane.put("controls", controls);
        TreeSet<String> connectionNames = new TreeSet<>();
        for (JsonNode connection : configuredConnections.values()) {
            connectionNames.add(firstText(connection, "name", "app_id") + " (" + connection.path("environment").asText()
                    + ", " + connection.path("security_scheme").asText() + ", " + connection.path("preference").asText() + ")");
        }
        controlPlane.put("connections", new ArrayList<>(connectionNames));
        controlPlane.put("external_agents", externalAgents.size());
        return new Result(inventory, controlPlane);
    }

    private static Map<String, Object> describe(WatsonxClient client, JsonNode agent, boolean external,
            Map<String, JsonNode> tools, Map<String, JsonNode> allAgents, Map<String, JsonNode> toolkits,
            Map<String, JsonNode> models, Map<String, String> controlTypes, List<JsonNode> bindings,
            Map<String, JsonNode> configuredConnections) throws IOException {
        String id = agent.path("id").asText();
        String liveVersion = liveVersion(agent);
        JsonNode deployed = agent;
        if (liveVersion != null && !external) {
            JsonNode release = client.release(id, liveVersion);
            if (release != null && release.isObject()) {
                deployed = release;
            }
        }
        List<String> toolIds = new ArrayList<>();
        for (JsonNode tool : deployed.path("tools")) {
            String toolId = refId(tool, "tool_id");
            if (toolId != null) {
                toolIds.add(toolId);
            }
        }
        String llm = textOrNull(deployed, "llm");
        JsonNode model = llm == null ? null : models.get(llm);

        List<String> agentConnections = new ArrayList<>();
        JsonNode connectionIds = deployed.has("connection_ids") ? deployed.path("connection_ids") : agent.path("connection_ids");
        for (JsonNode connectionId : connectionIds) {
            JsonNode connection = configuredConnections.get(connectionId.asText());
            if (connection != null) {
                agentConnections.add(firstText(connection, "name", "app_id") + " (" + connection.path("security_scheme").asText()
                        + ", " + connection.path("preference").asText() + ")");
            }
        }

        JsonNode identity = client.identity(id, external);
        JsonNode identityConnections = identity == null ? null : identity.path("connections");
        List<Map<String, Object>> permissions = new ArrayList<>();
        if (identityConnections != null) {
            for (JsonNode item : identityConnections.path("items")) {
                Map<String, Object> permission = new LinkedHashMap<>();
                item.fields().forEachRemaining(field -> {
                    if (!"icon".equals(field.getKey())) {
                        permission.put(field.getKey(), plain(field.getValue()));
                    }
                });
                permissions.add(permission);
            }
        }

        Map<String, Object> description = new LinkedHashMap<>();
        description.put("id", id);
        description.put("kind", external ? "external" : "native");
        description.put("name", textOrNull(agent, "name"));
        // external agents have a title instead of a display name
        description.put("display_name", firstText(agent, "display_name", "title", "name"));
        description.put("description", textOrNull(deployed, "description"));
        description.put("instructions", textOrNull(deployed, "instructions"));
        description.put("llm", llm);
        description.put("model_label", model == null ? null : textOrNull(model, "label"));
        description.put("model_provider", model == null ? null : textOrNull(model, "type"));
        description.put("style", textOrNull(deployed, "style"));
        List<String> environments = new ArrayList<>();
        for (JsonNode environment : agent.path("environments")) {
            environments.add(environment.path("name").asText());
        }
        description.put("environments", environments);
        description.put("live_version", liveVersion);
        description.put("tools", names(deployed.path("tools"), tools));
        description.put("collaborators", names(deployed.path("collaborators"), allAgents));
        description.put("mcp_servers", mcpServers(toolIds, tools, toolkits));
        description.put("knowledge_base", plain(deployed.path("knowledge_base")));
        description.put("plugins", plain(deployed.path("plugins")));
        description.put("connections", new ArrayList<>(new TreeSet<>(agentConnections)));
        description.put("controls", controlsOn("agent", Collections.singletonList(id), bindings, controlTypes));
        description.put("tool_controls", controlsOn("tool", toolIds, bindings, controlTypes));
        description.put("model_controls", controlsOn("model", Collections.singletonList(llm), bindings, controlTypes));
        description.put("created_by", identity != null ? textOrNull(identity, "creator_email") : textOrNull(agent, "created_by"));
        description.put("updated_by", identity != null ? textOrNull(identity, "last_updater_email") : textOrNull(agent, "updated_by"));
        description.put("created_at", identity != null ? textOrNull(identity, "created_at") : textOrNull(agent, "created_on"));
        description.put("last_updated_at", identity != null ? textOrNull(identity, "updated_at") : textOrNull(agent, "updated_at"));
        description.put("agent_identity", identity == null ? null
                : (identity.hasNonNull("connection_id") ? "enabled" : "disabled"));
        description.put("permissions", permissions);
        description.put("accessible_tools_count", identityConnections == null ? null : number(identityConnections, "total_tools_count"));
        description.put("permissions_count", identityConnections == null ? null : number(identityConnections, "total_scope_count"));
        description.put("connections_count", identityConnections == null ? null : number(identityConnections, "total_connections"));
        description.put("api_url", textOrNull(agent, "api_url"));
        description.put("provider", textOrNull(agent, "provider"));
        description.put("auth_scheme", textOrNull(agent, "auth_scheme"));
        return description;
    }

    // ---------------------------------------------------------------------------------------

    /**
     * Agents are named after their display name in Akto (one collection per name). Two agents
     * sharing a display name would merge, so both get their internal name appended.
     */
    static void disambiguateDisplayNames(Map<String, Map<String, Object>> inventory) {
        Map<String, Integer> counts = new HashMap<>();
        for (Map<String, Object> agent : inventory.values()) {
            counts.merge(String.valueOf(agent.get("display_name")), 1, Integer::sum);
        }
        for (Map<String, Object> agent : inventory.values()) {
            String displayName = String.valueOf(agent.get("display_name"));
            if (counts.get(displayName) > 1 && agent.get("name") != null && !displayName.equals(agent.get("name"))) {
                agent.put("display_name", displayName + " (" + agent.get("name") + ")");
            }
        }
    }

    /** The live version number, when the agent is deployed (a "live" environment with a current version). */
    static String liveVersion(JsonNode agent) {
        for (JsonNode environment : agent.path("environments")) {
            JsonNode version = environment.get("current_version");
            if ("live".equals(environment.path("name").asText()) && version != null && !version.isNull()) {
                return version.asText();
            }
        }
        return null;
    }

    /** IDs (agent list) or references (deployed version: {"tool_id"|"collaborator_id": ...}) -> readable names. */
    static List<String> names(JsonNode items, Map<String, JsonNode> known) {
        List<String> result = new ArrayList<>();
        for (JsonNode item : items) {
            String id = refId(item, "tool_id", "collaborator_id");
            JsonNode ref = id == null ? null : known.get(id);
            String name = item.isObject() ? firstText(item, "display_name", "name") : null;
            if (name == null && ref != null) {
                name = firstText(ref, "display_name", "title", "name");
            }
            result.add(name != null ? name : (id != null ? id : item.toString()));
        }
        return result;
    }

    private static List<String> mcpServers(List<String> toolIds, Map<String, JsonNode> tools, Map<String, JsonNode> toolkits) {
        TreeSet<String> servers = new TreeSet<>();
        for (String toolId : toolIds) {
            JsonNode tool = tools.get(toolId);
            JsonNode toolkit = tool == null ? null : toolkits.get(tool.path("toolkit_id").asText());
            if (toolkit != null && "mcp".equals(toolkit.path("kind").asText())) {
                servers.add(toolkit.path("name").asText());
            }
        }
        return new ArrayList<>(servers);
    }

    /** "<binding> (<control type>, <hooks>)" for each control bound to any of these agent/tool/model ids. */
    static List<String> controlsOn(String kind, List<String> ids, List<JsonNode> bindings, Map<String, String> controlTypes) {
        TreeSet<String> found = new TreeSet<>();
        for (JsonNode binding : bindings) {
            for (JsonNode boundId : binding.path(kind + "_ids")) {
                if (ids.contains(boundId.asText())) {
                    String type = controlTypes.getOrDefault(binding.path("artifact_id").asText(), "control");
                    String name = firstText(binding, "display_name", "name");
                    found.add((name != null ? name : type) + " (" + type + ", " + String.join(",", texts(binding.path("hooks"))) + ")");
                    break;
                }
            }
        }
        return new ArrayList<>(found);
    }

    static String refId(JsonNode item, String... keys) {
        if (item == null || item.isNull()) {
            return null;
        }
        if (!item.isObject()) {
            return item.asText();
        }
        if (item.hasNonNull("id")) {
            return item.get("id").asText();
        }
        for (String key : keys) {
            if (item.hasNonNull(key)) {
                return item.get(key).asText();
            }
        }
        return null;
    }

    static Map<String, JsonNode> byId(JsonNode items) {
        Map<String, JsonNode> map = new LinkedHashMap<>();
        for (JsonNode item : items) {
            if (item.hasNonNull("id")) {
                map.put(item.get("id").asText(), item);
            }
        }
        return map;
    }

    static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = textOrNull(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    static String textOrNull(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() || value.asText().isEmpty() ? null : value.asText();
    }

    private static Object number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.numberValue();
    }

    private static List<String> texts(JsonNode items) {
        List<String> result = new ArrayList<>();
        for (JsonNode item : items) {
            result.add(item.asText());
        }
        return result;
    }

    private static List<JsonNode> list(JsonNode items) {
        List<JsonNode> result = new ArrayList<>();
        items.forEach(result::add);
        return result;
    }

    /** A JSON node as plain Java values (maps, lists, strings, numbers), for the outgoing records. */
    static Object plain(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            node.fields().forEachRemaining(field -> map.put(field.getKey(), plain(field.getValue())));
            return map;
        }
        if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            node.forEach(value -> values.add(plain(value)));
            return values;
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        return node.asText();
    }
}
