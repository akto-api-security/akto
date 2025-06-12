package com.akto.hybrid_runtime;

import com.akto.dao.context.Context;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.APIConfig;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiCollection;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.traffic.CollectionTags;
import com.akto.hybrid_parsers.HttpCallParser;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.mcp.McpSchema;
import com.akto.mcp.McpSchema.JSONRPCResponse;
import com.akto.mcp.McpSchema.JsonSchema;
import com.akto.mcp.McpSchema.ListToolsResult;
import com.akto.mcp.McpSchema.Tool;
import com.akto.testing.ApiExecutor;
import com.akto.util.JSONUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.swagger.oas.inflector.examples.ExampleBuilder;
import io.swagger.oas.inflector.examples.models.Example;
import io.swagger.oas.inflector.processors.JsonNodeExampleSerializer;
import io.swagger.util.Json;
import io.swagger.v3.oas.models.media.Schema;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections.CollectionUtils;
import org.apache.http.HttpStatus;

public class McpToolsSyncJobExecutor {

    private static final LoggerMaker logger = new LoggerMaker(McpToolsSyncJobExecutor.class, LogDb.RUNTIME);
    private static final ObjectMapper mapper = new ObjectMapper();

    public static final McpToolsSyncJobExecutor INSTANCE = new McpToolsSyncJobExecutor();

    public void runJob() {
        logger.info("Staring MCP Sync Job");
        List<ApiCollection> apiCollections = DataActorFactory.fetchInstance().fetchAllApiCollections();
        List<ApiCollection> eligibleCollections = new ArrayList<>();
        for (ApiCollection apiCollection : apiCollections) {
            List<CollectionTags> tagsList = apiCollection.getTagsList();
            if (CollectionUtils.isEmpty(tagsList)) {
                continue;
            }
            tagsList.stream()
                .filter(t -> "mcp-server".equals(t.getKeyName()))
                .findFirst()
                .ifPresent(c -> eligibleCollections.add(apiCollection));
        }

        logger.info("Found {} collections for MCP server.", eligibleCollections.size());

        if (eligibleCollections.isEmpty()) {
            return;
        }

        eligibleCollections.forEach(
            McpToolsSyncJobExecutor::handleMcpDiscovery
        );
    }

    public static void handleMcpDiscovery(ApiCollection apiCollection) {
        String host = apiCollection.getHostName();

        try {
            OriginalHttpRequest toolsListRequest = createToolsListRequest(host);
            JSONRPCResponse toolsListResponse = sendRequest(toolsListRequest);

            logger.info("Received tools/list response. Processing tools.....");

            // Parse tools from response
            ListToolsResult toolsResult = JSONUtils.fromJson(toolsListResponse.getResult(), ListToolsResult.class);

            if (toolsResult == null) {
                logger.error("Skipping as List Tool Result is null");
                return;
            }
            List<Tool> tools = toolsResult.getTools();
            if (CollectionUtils.isEmpty(tools)) {
                logger.error("No tools found in MCP server");
                return;
            }
            int id = 2;
            List<HttpResponseParams> responseParamsList = new ArrayList<>();
            for (Tool tool : tools) {
                String toolName = tool.getName();
                JsonSchema inputSchema = tool.getInputSchema();
                Map<String, Object> exampleArguments = generateExampleArguments(inputSchema);

                // Build request body
                Map<String, Object> params = new HashMap<>();
                params.put("name", toolName);
                params.put("arguments", exampleArguments);
                Map<String, Object> reqBody = new HashMap<>();
                reqBody.put("jsonrpc", "2.0");
                reqBody.put("id", id++);
                reqBody.put("method", "tools/call");
                reqBody.put("params", params);
                String reqBodyStr = mapper.writeValueAsString(reqBody);

                OriginalHttpRequest callRequest = new OriginalHttpRequest(toolsListRequest.getUrl(), null, "POST",
                    reqBodyStr, buildHeaders(host), "http");

                HttpResponseParams httpResponseParams = convertToAktoFormat(callRequest,
                    new OriginalHttpResponse("{}", Collections.emptyMap(), HttpStatus.SC_OK));

                responseParamsList.add(httpResponseParams);
            }
            Map<String, List<HttpResponseParams>> responseParamsToAccountIdMap = new HashMap<>();
            logger.info(Context.accountId.get().toString());
            responseParamsToAccountIdMap.put(Context.accountId.get().toString(), responseParamsList);

            String configName = System.getenv("AKTO_CONFIG_NAME");
            APIConfig apiConfig = DataActorFactory.fetchInstance().fetchApiConfig(configName);
            if (apiConfig == null) {
                apiConfig = new APIConfig(configName,"access-token", 1, 10_000_000, Main.sync_threshold_time); // this sync threshold time is used for deleting sample data
            }

            Main.handleResponseParams(responseParamsToAccountIdMap,
                new HashMap<>(),
                false,
                new HashMap<>(),
                apiConfig,
                true,
                false,
                AccountSettings.DEFAULT_CENTRAL_KAFKA_TOPIC_NAME
            );
        } catch (Exception e) {
            logger.error("Error wile discovering mcp and its tools for hostname: {}", host, e);
        }
    }

    private static Map<String, List<String>> buildHeaders(String host) {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", Collections.singletonList("application/json"));
        headers.put("Accept", Collections.singletonList("*/*"));
        headers.put("host", Collections.singletonList(host));
        return headers;
    }

    public static OriginalHttpRequest createToolsListRequest(String host) {
        String url = "/tools/list";
        String method = "POST";
        String body = "{\"jsonrpc\": \"2.0\", \"id\": 1, \"method\": \"tools/list\", \"params\": {}}";
        Map<String, List<String>> headers = buildHeaders(host);
        String type = "http";
        return new OriginalHttpRequest(url, null, method, body, headers, type);
    }

    public static JSONRPCResponse sendRequest(OriginalHttpRequest request) throws Exception {
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequestWithSse(request, true, null, false,
                new ArrayList<>(), false);
            JSONRPCResponse jsonrpcResponse = (JSONRPCResponse) McpSchema.deserializeJsonRpcMessage(new ObjectMapper(), response.getBody());
            if (jsonrpcResponse.getError() != null) {
                logger.warn("Received error response from mcp server. Request: {}, Response: {}", request, jsonrpcResponse);
            }
            return jsonrpcResponse;
        } catch (Exception e) {
            logger.error("Error while making request to MCP server.", e);
            throw e;
        }
    }

    // Helper to generate example arguments from inputSchema
    private static Map<String, Object> generateExampleArguments(JsonSchema inputSchema) {
        if (inputSchema == null) {
            return Collections.emptyMap();
        }
        try {
            // Convert inputSchema to OpenAPI Schema
            String inputSchemaJson = mapper.writeValueAsString(inputSchema);
            Schema openApiSchema = io.swagger.v3.core.util.Json.mapper().readValue(inputSchemaJson, Schema.class);
            Example example = ExampleBuilder.fromSchema(openApiSchema, null);
            SimpleModule simpleModule = new SimpleModule().addSerializer(new JsonNodeExampleSerializer());
            Json.mapper().registerModule(simpleModule);
            return JSONUtils.getMap(Json.pretty(example));

        } catch (Exception e) {
            logger.error("Failed to generate example arguments using OpenAPI ExampleBuilder", e);
            return Collections.emptyMap();
        }
    }


    private static HttpResponseParams convertToAktoFormat(OriginalHttpRequest request,
        OriginalHttpResponse response) {
        Map<String, Object> value = new HashMap<>();

        value.put("path", request.getUrl());
        value.put("requestHeaders", JSONUtils.getString(request.getHeaders()));
        value.put("responseHeaders", JSONUtils.getString(response.getHeaders()));
        value.put("method", request.getMethod());
        value.put("requestPayload", request.getBody());
        value.put("responsePayload", response.getBody());
        value.put("time", String.valueOf(System.currentTimeMillis() / 1000));
        value.put("statusCode", String.valueOf(response.getStatusCode()));
        value.put("type", request.getType());
        value.put("status", String.valueOf(response.getStatusCode()));
        value.put("akto_account_id", String.valueOf(Context.accountId.get()));

        String message = JSONUtils.getString(value);
        try {
            return HttpCallParser.parseKafkaMessage(message);
        } catch (Exception e) {
            logger.error("Error while parsing MCP message.");
        }
        return null;
    }
}

