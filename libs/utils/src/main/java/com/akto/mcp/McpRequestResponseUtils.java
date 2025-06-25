package com.akto.mcp;

import com.akto.dto.HttpResponseParams;
import com.akto.jsonrpc.JsonRpcUtils;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.mcp.McpJsonRpcModel.McpParams;
import com.akto.util.Pair;
import com.akto.utils.JsonUtils;
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

    public static final Set<String> MCP_METHOD_SET = new HashSet<>(Arrays.asList(
        McpSchema.METHOD_TOOLS_LIST,
        McpSchema.METHOD_TOOLS_CALL,
        McpSchema.METHOD_PROMPT_LIST,
        McpSchema.METHOD_PROMPT_GET,
        McpSchema.METHOD_RESOURCES_LIST,
        McpSchema.METHOD_RESOURCES_READ,
        McpSchema.METHOD_RESOURCES_TEMPLATES_LIST,
        McpSchema.METHOD_PING,
        McpSchema.METHOD_INITIALIZE,
        McpSchema.MCP_NOTIFICATIONS_CANCELLED_METHOD,
        McpSchema.METHOD_COMPLETION_COMPLETE,
        McpSchema.METHOD_NOTIFICATION_INITIALIZED,
        McpSchema.METHOD_LOGGING_SET_LEVEL,
        McpSchema.METHOD_RESOURCES_SUBSCRIBE,
        McpSchema.METHOD_RESOURCES_UNSUBSCRIBE,
        McpSchema.METHOD_SAMPLING_CREATE_MESSAGE,
        McpSchema.METHOD_ROOTS_LIST,
        McpSchema.METHOD_NOTIFICATION_MESSAGE,
        McpSchema.MCP_NOTIFICATIONS_PROGRESS_METHOD,
        McpSchema.METHOD_NOTIFICATION_PROMPTS_LIST_CHANGED,
        McpSchema.METHOD_NOTIFICATION_RESOURCES_LIST_CHANGED,
        McpSchema.MCP_NOTIFICATIONS_RESOURCES_UPDATED_METHOD,
        McpSchema.METHOD_NOTIFICATION_ROOTS_LIST_CHANGED,
        McpSchema.METHOD_NOTIFICATION_TOOLS_LIST_CHANGED
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
            logger.error("Error parsing params as JSON-RPC object. Skipping....", e);
        }

        if (McpSchema.METHOD_TOOLS_CALL.equals(mcpJsonRpcModel.getMethod())
            && params != null && StringUtils.isNotBlank(params.getName()) && paramsIsObject) {
            String url = responseParams.getRequestParams().getURL();

            url = HttpResponseParams.addPathParamToUrl(url, params.getName());

            HttpResponseParams httpResponseParamsCopy = responseParams.copy();
            httpResponseParamsCopy.getRequestParams().setUrl(url);

            return httpResponseParamsCopy;
        }

        return responseParams;
    }

    public static Pair<Boolean, McpJsonRpcModel> isMcpRequest(HttpResponseParams responseParams) {
        String requestPayload = responseParams.getRequestParams().getPayload();

        if (!JsonRpcUtils.isJsonRpcRequest(responseParams)) {
            return new Pair<>(false, null);
        }

        McpJsonRpcModel mcpJsonRpcModel = JsonUtils.fromJson(requestPayload, McpJsonRpcModel.class);

        boolean isMcpRequest = mcpJsonRpcModel != null && MCP_METHOD_SET.contains(mcpJsonRpcModel.getMethod());
        return new Pair<>(isMcpRequest, mcpJsonRpcModel);
    }
}
