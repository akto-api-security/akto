package com.akto.mcp;

import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.McpAuditInfo;
import com.akto.dto.RawApi;
import com.akto.jsonrpc.JsonRpcUtils;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.mcp.McpJsonRpcModel.McpParams;
import com.akto.mcp.McpSchema.JSONRPCResponse;
import com.akto.mcp.McpSchema.JsonSchema;
import com.akto.mcp.McpSchema.ListToolsResult;
import com.akto.mcp.McpSchema.Tool;
import com.akto.util.HttpRequestResponseUtils;
import com.akto.util.JSONUtils;
import com.akto.util.Pair;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.swagger.oas.inflector.examples.ExampleBuilder;
import io.swagger.oas.inflector.examples.models.Example;
import io.swagger.oas.inflector.processors.JsonNodeExampleSerializer;
import io.swagger.util.Json;
import io.swagger.v3.oas.models.media.Schema;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import static com.akto.util.Constants.AKTO_MCP_RESOURCES_TAG;
import static com.akto.util.Constants.AKTO_MCP_TOOLS_TAG;
import static com.akto.util.Constants.HOST_HEADER;
import static com.akto.util.Constants.STDIO_TRANSPORT;
import static com.akto.util.Constants.X_TRANSPORT_HEADER;


public final class McpRequestResponseUtils {

    private static final LoggerMaker logger = new LoggerMaker(McpRequestResponseUtils.class, LogDb.RUNTIME);
    private static final Set<String> MCP_METHODS_TO_BE_HANDLED = new HashSet<>(Arrays.asList(
        McpSchema.METHOD_TOOLS_CALL,
        McpSchema.METHOD_RESOURCES_READ
    ));
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static DataActor dataActor = DataActorFactory.fetchInstance();

    public static List<HttpResponseParams> parseMcpResponseParams(HttpResponseParams responseParams) {
        String requestPayload = responseParams.getRequestParams().getPayload();

        Pair<Boolean, McpJsonRpcModel> mcpRequest = isMcpRequest(responseParams);
        if (!mcpRequest.getFirst()) {
            return Arrays.asList(responseParams);
        }

        handleMcpMethodCall(mcpRequest.getSecond(), requestPayload, responseParams);

        List<HttpResponseParams> responseParamsList = new ArrayList<>();
        responseParamsList.add(responseParams);
        if (McpSchema.METHOD_TOOLS_LIST.equals(mcpRequest.getSecond().getMethod())) {
            List<HttpResponseParams> expanded = handleToolsListForStreamableHttp(responseParams);
            responseParamsList.addAll(expanded);
        }
        return responseParamsList;
    }

    public static Pair<Boolean, McpJsonRpcModel> isMcpRequest(HttpResponseParams responseParams) {
        String requestPayload = responseParams.getRequestParams().getPayload();

        if (!JsonRpcUtils.validateAndParseJsonRpc(responseParams).getFirst()) {
            return new Pair<>(false, null);
        }

        McpJsonRpcModel mcpJsonRpcModel = JSONUtils.fromJson(requestPayload, McpJsonRpcModel.class);

        boolean isMcpRequest =
            mcpJsonRpcModel != null && McpSchema.MCP_METHOD_SET.contains(mcpJsonRpcModel.getMethod());
        return new Pair<>(isMcpRequest, mcpJsonRpcModel);
    }

    public static boolean isMcpRequest(RawApi rawApi) {
        String requestPayload = rawApi.getRequest().getBody();
        if(StringUtils.isEmpty(requestPayload)) {
            return false;
        }
        if(!requestPayload.contains(JsonRpcUtils.JSONRPC_KEY)) {
            return false;
        }
        McpJsonRpcModel mcpJsonRpcModel = JSONUtils.fromJson(requestPayload, McpJsonRpcModel.class);
        if(mcpJsonRpcModel == null) {
            return false;
        }
        return McpSchema.MCP_METHOD_SET.contains(mcpJsonRpcModel.getMethod());
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
        String mcpHost = extractMcpHostFromResponseParams(responseParams);

        McpAuditInfo auditInfo = null;

        try {
            switch (mcpJsonRpcModel.getMethod()) {
                case McpSchema.METHOD_TOOLS_CALL:
                    if (params != null && StringUtils.isNotBlank(params.getName())) {
                        url = HttpResponseParams.addPathParamToUrl(url, params.getName());
                        String name = params.getName() != null ? params.getName() : "";
                        auditInfo = new McpAuditInfo(
                            Context.now(), "", AKTO_MCP_TOOLS_TAG, 0,
                            name, "", null, responseParams.getRequestParams().getApiCollectionId(),
                            mcpHost
                        );
                    }
                    break;

                case McpSchema.METHOD_RESOURCES_READ:
                    if (params != null && StringUtils.isNotBlank(params.getUri())) {
                        url = HttpResponseParams.addPathParamToUrl(url, params.getUri());
                        String uri = params.getUri() != null ? params.getUri() : "";
                        auditInfo = new McpAuditInfo(
                            Context.now(), "", AKTO_MCP_RESOURCES_TAG, 0,
                            uri, "", null, responseParams.getRequestParams().getApiCollectionId(),
                            mcpHost
                        );
                    }
                    break;

                default:
                    break;
            }
        } catch (Exception e) {
            logger.error("Error forming auditInfo or processing MCP method call", e);
        }

        if (auditInfo != null) {
            try {
                dataActor.insertMCPAuditDataLog(auditInfo);
            } catch (Exception e) {
                logger.error("Error inserting MCP audit data log", e);
            }
        }
        responseParams.getRequestParams().setUrl(url);
    }

    private static List<HttpResponseParams> handleToolsListForStreamableHttp(HttpResponseParams responseParams) {
        try {
            String responseBody = responseParams.getPayload();
            if (StringUtils.isEmpty(responseBody)) {
                return Collections.emptyList();
            }

            responseBody = responseBody.trim();
            String extractedData;
            if ((responseBody.startsWith("{") && responseBody.endsWith("}")) || (responseBody.startsWith("[")
                && responseBody.endsWith("]"))) {
                extractedData = responseBody;
            } else {
                extractedData = extractDataFromResponse(responseBody); // in case of streamable http
            }

            if (StringUtils.isEmpty(extractedData)) {
                return Collections.emptyList();
            }

            JSONRPCResponse jsonRpcResponse;
            try {
                jsonRpcResponse = (JSONRPCResponse) McpSchema.deserializeJsonRpcMessage(OBJECT_MAPPER,
                    extractedData);
            } catch (Exception e) {
                logger.error("Error parsing tools list response as JSON-RPC. Skipping adding tools/call samples");
                return Collections.emptyList();
            }
            if (jsonRpcResponse == null || jsonRpcResponse.getResult() == null) {
                return Collections.emptyList();
            }

            ListToolsResult toolsResult;
            try {
                toolsResult = JSONUtils.fromJson(jsonRpcResponse.getResult(), ListToolsResult.class);
            } catch (Exception e) {
                logger.error("Error parsing tools list result from extracted data", e);
                return Collections.emptyList();
            }

            if (toolsResult == null || CollectionUtils.isEmpty(toolsResult.getTools())) {
                return Collections.emptyList();
            }

            // Generate one HttpResponseParams per tool by copying original and replacing
            // request with tools/call
            List<HttpResponseParams> out = new ArrayList<>();
            int id = 2;
            String url = responseParams.getRequestParams().getURL();
            url = url.replace(McpSchema.METHOD_TOOLS_LIST, McpSchema.METHOD_TOOLS_CALL);
            for (Tool tool : toolsResult.getTools()) {
                McpSchema.JSONRPCRequest newReq = new McpSchema.JSONRPCRequest(
                    McpSchema.JSONRPC_VERSION,
                    McpSchema.METHOD_TOOLS_CALL,
                    id++,
                    new McpSchema.CallToolRequest(tool.getName(), generateExampleArguments(tool.getInputSchema())));
                String newReqString = JSONUtils.getString(newReq);
                HttpResponseParams cloned = responseParams.copy();
                cloned.getRequestParams().setUrl(url);
                if (cloned.getRequestParams() != null) {
                    cloned.getRequestParams().setPayload(newReqString);
                }

                cloned.setPayload("");
                cloned.setHeaders(Collections.emptyMap());
                modifyOriginalHttpMessage(cloned, newReqString);
                handleMcpMethodCall(new McpJsonRpcModel(
                        McpSchema.JSONRPC_VERSION,
                        McpSchema.METHOD_TOOLS_CALL,
                        new McpParams(tool.getName(), null), String.valueOf(id)),
                    cloned.getRequestParams().getPayload(),
                    cloned);
                out.add(cloned);
            }
            return out;
        } catch (Exception e) {
            logger.error("Error handling tools/list for streamable http", e);
        }

        return Collections.emptyList();
    }

    private static void modifyOriginalHttpMessage(HttpResponseParams responseParams, String newRequestPayload) {
        String origReqJson = responseParams.getOrig();
        try {
            Map<String, Object> origReq = JSONUtils.getMap(origReqJson);
            origReq.put("requestPayload", newRequestPayload);
            origReq.remove("responsePayload");
            origReq.remove("responseHeaders");
            responseParams.setOrig(JSONUtils.getString(origReq));
        } catch (Exception e) {
            logger.error("Error parsing original HTTP message as JSON. Not updating sample data for MCP tools/call", e);
        }
    }

    private static String extractDataFromResponse(String responseBody) {
        try {
            String[] lines = responseBody.split("\n");
            for (String line : lines) {
                if (line.startsWith("data:")) {
                    return line.substring(5).trim();
                }
            }
        } catch (Exception e) {
            logger.error("Error extracting data for tools list response", e);
        }
        return null;
    }

    public static Map<String, Object> generateExampleArguments(JsonSchema inputSchema) {
        if (inputSchema == null) {
            return Collections.emptyMap();
        }
        try {
            String inputSchemaJson = OBJECT_MAPPER.writeValueAsString(inputSchema);
            Schema<?> openApiSchema = io.swagger.v3.core.util.Json.mapper().readValue(inputSchemaJson, Schema.class);
            Example example = ExampleBuilder.fromSchema(openApiSchema, null);
            Json.mapper().registerModule(new SimpleModule().addSerializer(new JsonNodeExampleSerializer()));
            return JSONUtils.getMap(Json.pretty(example));
        } catch (Exception e) {
            logger.error("Failed to generate example arguments using OpenAPI ExampleBuilder", e);
            return Collections.emptyMap();
        }
    }

    public static String extractMcpHostFromResponseParams(HttpResponseParams responseParams) {
        if (responseParams == null || responseParams.getRequestParams() == null) {
            return null;
        }
        Map<String, List<String>> reqHeaders = responseParams.getRequestParams().getHeaders();

        String hostRaw = null;
        String url = responseParams.getRequestParams().getURL();
        if (StringUtils.isNotBlank(url)) {
            try {
                URI uri = new URI(url.trim());
                if (uri.getHost() != null) {
                    hostRaw = uri.getHost();
                }
            } catch (Exception e) {
                logger.debug("Failed to parse request URL for host: " + e.getMessage());
            }
        }
        if (StringUtils.isBlank(hostRaw) && reqHeaders != null) {
            hostRaw = HttpRequestResponseUtils.getHeaderValue(reqHeaders, HOST_HEADER);
        }
        if (hostRaw == null || StringUtils.isBlank(hostRaw)) {
            return null;
        }
        final String host = hostRaw.trim();

        // If x-transport is STDIO, return MCP name; else return parsed host
        if (reqHeaders != null && STDIO_TRANSPORT.equals(HttpRequestResponseUtils.getHeaderValue(reqHeaders, X_TRANSPORT_HEADER))) {
            return extractServiceNameFromHost(host);
        }
        return host;
    }

    private static String extractServiceNameFromHost(String host) {
        if (host == null || host.isEmpty()) {
            return null;
        }
        String[] parts = host.split("\\.");
        if (parts.length < 3) {
            return null;
        }
        StringBuilder sb = new StringBuilder(parts[2]);
        for (int i = 3; i < parts.length; i++) {
            sb.append(".").append(parts[i]);
        }
        return sb.toString();
    }
}
