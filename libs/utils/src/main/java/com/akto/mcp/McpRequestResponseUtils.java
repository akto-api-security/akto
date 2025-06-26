package com.akto.mcp;

import com.akto.dto.HttpResponseParams;
import com.akto.jsonrpc.JsonRpcUtils;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
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
    private static final Set<String> MCP_METHODS_TO_BE_HANDLED = new HashSet<>(Arrays.asList(
        McpSchema.METHOD_TOOLS_CALL,
        McpSchema.METHOD_RESOURCES_READ
    ));

    public static HttpResponseParams parseMcpResponseParams(HttpResponseParams responseParams) {
        String requestPayload = responseParams.getRequestParams().getPayload();

        Pair<Boolean, McpJsonRpcModel> mcpRequest = isMcpRequest(responseParams);
        if (!mcpRequest.getFirst()) {
            return responseParams;
        }
        handleMcpMethodCall(mcpRequest.getSecond(), requestPayload, responseParams);
        return responseParams;
    }

    public static Pair<Boolean, McpJsonRpcModel> isMcpRequest(HttpResponseParams responseParams) {
        String requestPayload = responseParams.getRequestParams().getPayload();

        if (!JsonRpcUtils.isJsonRpcRequest(responseParams)) {
            return new Pair<>(false, null);
        }

        McpJsonRpcModel mcpJsonRpcModel = JSONUtils.fromJson(requestPayload, McpJsonRpcModel.class);

        boolean isMcpRequest =
            mcpJsonRpcModel != null && McpSchema.MCP_METHOD_SET.contains(mcpJsonRpcModel.getMethod());
        return new Pair<>(isMcpRequest, mcpJsonRpcModel);
    }

    private static void handleMcpMethodCall(McpJsonRpcModel mcpJsonRpcModel, String requestPayload,
        HttpResponseParams responseParams) {

        if (!MCP_METHODS_TO_BE_HANDLED.contains(mcpJsonRpcModel.getMethod())) {
            return;
        }

        McpParams params = mcpJsonRpcModel.getParams();

        // Enforce that params is an object in the original JSON
        boolean paramsIsObject;
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(requestPayload);
            JsonNode paramsNode = root.get("params");
            paramsIsObject = paramsNode != null && paramsNode.isObject();
        } catch (Exception e) {
            // ignore, treat as not an object
            logger.error("Error parsing params as JSON-RPC object. Skipping....", e);
            return;
        }

        if (!paramsIsObject) {
            return;
        }

        String url = responseParams.getRequestParams().getURL();

        switch (mcpJsonRpcModel.getMethod()) {
            case McpSchema.METHOD_TOOLS_CALL:
                if (params != null && StringUtils.isNotBlank(params.getName())) {
                    url = HttpResponseParams.addPathParamToUrl(url, params.getName());
                }
                break;

            case McpSchema.METHOD_RESOURCES_READ:
                if (params != null && StringUtils.isNotBlank(params.getUri())) {
                    url = HttpResponseParams.addPathParamToUrl(url, params.getUri());
                }
                break;

            default:
                break;
        }
        responseParams.getRequestParams().setUrl(url);
    }
}
