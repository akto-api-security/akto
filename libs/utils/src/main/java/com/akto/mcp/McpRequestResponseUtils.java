package com.akto.mcp;

import com.akto.dto.HttpResponseParams;
import com.akto.jsonrpc.JsonRpcUtils;
import com.akto.dao.common.LoggerMaker;
import com.akto.dao.common.LoggerMaker.LogDb;
import com.akto.mcp.McpJsonRpcModel.McpParams;
import com.akto.util.JSONUtils;
import com.akto.util.Pair;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class McpRequestResponseUtils {

    private static final LoggerMaker logger = new LoggerMaker(McpRequestResponseUtils.class, LogDb.RUNTIME);

    public static final String MCP_TOOL_LIST_METHOD = "tools/list";
    public static final String MCP_TOOL_CALL_METHOD = "tools/call";
    public static final String MCP_PROMPTS_LIST_METHOD = "prompts/list";
    public static final String MCP_PROMPTS_GET_METHOD = "prompts/get";
    public static final String MCP_RESOURCES_LIST_METHOD = "resources/list";
    public static final String MCP_RESOURCES_READ_METHOD = "resources/read";
    public static final String MCP_RESOURCES_TEMPLATE_LIST_METHOD = "resources/templates/list";
    public static final String MCP_PING_METHOD = "ping";
    public static final String MCP_INITIALIZE_METHOD = "initialize";
    public static final String MCP_NOTIFICATIONS_CANCELLED_METHOD = "notifications/cancelled";
    public static final String MCP_COMPLETION_COMPLETE_METHOD = "completion/complete";
    public static final String MCP_NOTIFICATIONS_INITIALIZED_METHOD = "notifications/initialized";
    public static final String MCP_LOGGING_SET_LEVEL_METHOD = "logging/setLevel";
    public static final String MCP_RESOURCES_SUBSCRIBE_METHOD = "resources/subscribe";
    public static final String MCP_RESOURCES_UNSUBSCRIBE_METHOD = "resources/unsubscribe";
    public static final String MCP_SAMPLING_CREATE_MESSAGE_METHOD = "sampling/createMessage";
    public static final String MCP_ROOTS_LIST_METHOD = "roots/list";
    public static final String MCP_NOTIFICATIONS_MESSAGE_METHOD = "notifications/message";
    public static final String MCP_NOTIFICATIONS_PROGRESS_METHOD = "notifications/progress";
    public static final String MCP_NOTIFICATIONS_PROMPTS_LIST_CHANGED_METHOD = "notifications/prompts/list_changed";
    public static final String MCP_NOTIFICATIONS_RESOURCES_LIST_CHANGED_METHOD = "notifications/resources/list_changed";
    public static final String MCP_NOTIFICATIONS_RESOURCES_UPDATED_METHOD = "notifications/resources/updated";
    public static final String MCP_NOTIFICATIONS_ROOTS_LIST_CHANGED_METHOD = "notifications/roots/list_changed";
    public static final String MCP_NOTIFICATIONS_TOOLS_LIST_CHANGED_METHOD = "notifications/tools/list_changed";

    public static final Set<String> MCP_METHOD_SET = new HashSet<>(Arrays.asList(
        MCP_TOOL_LIST_METHOD,
        MCP_TOOL_CALL_METHOD,
        MCP_PROMPTS_LIST_METHOD,
        MCP_PROMPTS_GET_METHOD,
        MCP_RESOURCES_LIST_METHOD,
        MCP_RESOURCES_READ_METHOD,
        MCP_RESOURCES_TEMPLATE_LIST_METHOD,
        MCP_PING_METHOD,
        MCP_INITIALIZE_METHOD,
        MCP_NOTIFICATIONS_CANCELLED_METHOD,
        MCP_COMPLETION_COMPLETE_METHOD,
        MCP_NOTIFICATIONS_INITIALIZED_METHOD,
        MCP_LOGGING_SET_LEVEL_METHOD,
        MCP_RESOURCES_SUBSCRIBE_METHOD,
        MCP_RESOURCES_UNSUBSCRIBE_METHOD,
        MCP_SAMPLING_CREATE_MESSAGE_METHOD,
        MCP_ROOTS_LIST_METHOD,
        MCP_NOTIFICATIONS_MESSAGE_METHOD,
        MCP_NOTIFICATIONS_PROGRESS_METHOD,
        MCP_NOTIFICATIONS_PROMPTS_LIST_CHANGED_METHOD,
        MCP_NOTIFICATIONS_RESOURCES_LIST_CHANGED_METHOD,
        MCP_NOTIFICATIONS_RESOURCES_UPDATED_METHOD,
        MCP_NOTIFICATIONS_ROOTS_LIST_CHANGED_METHOD,
        MCP_NOTIFICATIONS_TOOLS_LIST_CHANGED_METHOD
    ));

    public static HttpResponseParams parseMcpResponseParams(HttpResponseParams responseParams) {
        String requestPayload = responseParams.getRequestParams().getPayload();

        Pair<Boolean, McpJsonRpcModel> mcpRequest = isMcpRequest(responseParams);
        if (!mcpRequest.getFirst()) {
            return responseParams;
        }

        McpJsonRpcModel mcpJsonRpcModel = mcpRequest.getSecond();
        McpParams params = mcpJsonRpcModel.getParams();

        // Enforce that params is an object in the original JSON
        boolean paramsIsObject = false;
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(requestPayload);
            JsonNode paramsNode = root.get("params");
            paramsIsObject = paramsNode != null && paramsNode.isObject();
        } catch (Exception e) {
            // ignore, treat as not an object
        }

        if (MCP_TOOL_CALL_METHOD.equals(mcpJsonRpcModel.getMethod())
            && params != null && StringUtils.isNotBlank(params.getName()) && paramsIsObject) {
            String url = responseParams.getRequestParams().getURL();
            url = HttpResponseParams.addPathParamToUrl(url, params.getName());

            responseParams.getRequestParams().setUrl(url);
        }

        return responseParams;
    }

    public static Pair<Boolean, McpJsonRpcModel> isMcpRequest(HttpResponseParams responseParams) {
        String requestPayload = responseParams.getRequestParams().getPayload();

        if (!JsonRpcUtils.isJsonRpcRequest(responseParams)) {
            return new Pair<>(false, null);
        }

        McpJsonRpcModel mcpJsonRpcModel = JSONUtils.fromJson(requestPayload, McpJsonRpcModel.class);

        boolean isMcpRequest = mcpJsonRpcModel != null && MCP_METHOD_SET.contains(mcpJsonRpcModel.getMethod());
        return new Pair<>(isMcpRequest, mcpJsonRpcModel);
    }
}
