package com.akto.mcp;

import com.akto.dao.McpAuditInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.McpAuditInfo;
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

import static com.akto.util.Constants.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class McpRequestResponseUtils {
    private static final LoggerMaker logger = new LoggerMaker(McpRequestResponseUtils.class, LogDb.RUNTIME);
    private static final Set<String> MCP_METHODS_TO_BE_HANDLED = new HashSet<>(Arrays.asList(
        McpSchema.METHOD_TOOLS_CALL,
        McpSchema.METHOD_RESOURCES_READ
    ));

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

        McpJsonRpcModel mcpJsonRpcModel = JsonUtils.fromJson(requestPayload, McpJsonRpcModel.class);

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
            JsonNode root = OBJECT_MAPPER.readTree(requestPayload);
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

        McpAuditInfo auditInfo = null;

        switch (mcpJsonRpcModel.getMethod()) {
            case McpSchema.METHOD_TOOLS_CALL:
                if (params != null && StringUtils.isNotBlank(params.getName())) {
                    url = HttpResponseParams.addPathParamToUrl(url, params.getName());

                    // Create audit info for MCP Tool call
                    auditInfo = new McpAuditInfo(
                            Context.now(), "", AKTO_MCP_TOOLS_TAG, 0,
                            params.getName(), "", null,
                            responseParams.getRequestParams().getApiCollectionId()
                    );
                }
                break;

            case McpSchema.METHOD_RESOURCES_READ:
                if (params != null && StringUtils.isNotBlank(params.getUri())) {
                    url = HttpResponseParams.addPathParamToUrl(url, params.getUri());

                    // Create audit info for MCP Resource read
                    auditInfo = new McpAuditInfo(
                            Context.now(), "", AKTO_MCP_RESOURCES_TAG, 0,
                            params.getName(), "", null,
                            responseParams.getRequestParams().getApiCollectionId()
                    );
                }
                break;

            case McpSchema.METHOD_PROMPT_GET:
                if (params != null && StringUtils.isNotBlank(params.getName())) {
                    url = HttpResponseParams.addPathParamToUrl(url, params.getName());

                    // Create audit info for MCP Resource read
                    auditInfo = new McpAuditInfo(
                            Context.now(), "", AKTO_MCP_PROMPTS_TAG, 0,
                            params.getName(), "", null,
                            responseParams.getRequestParams().getApiCollectionId()
                    );
                }
                break;

            default:
                break;
        }

        if (auditInfo != null) {
            try {
                McpAuditInfoDao.instance.insertOne(auditInfo);
            } catch (Exception e) {
                logger.error("Error inserting MCP audit data log", e);
            }
        }
        responseParams.getRequestParams().setUrl(url);
    }
}
