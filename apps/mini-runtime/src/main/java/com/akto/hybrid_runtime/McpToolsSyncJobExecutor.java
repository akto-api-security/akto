package com.akto.hybrid_runtime;

import com.akto.dao.context.Context;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.APIConfig;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiCollection;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.HttpResponseParams.Source;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.traffic.CollectionTags;
import com.akto.hybrid_parsers.HttpCallParser;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.mcp.McpSchema;
import com.akto.mcp.McpSchema.CallToolRequest;
import com.akto.mcp.McpSchema.JSONRPCRequest;
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
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.springframework.http.HttpMethod;

public class McpToolsSyncJobExecutor {

    private static final LoggerMaker logger = new LoggerMaker(McpToolsSyncJobExecutor.class, LogDb.RUNTIME);
    private static final ObjectMapper mapper = new ObjectMapper();
    public static final McpToolsSyncJobExecutor INSTANCE = new McpToolsSyncJobExecutor();
    private static final String MCP_TOOLS_LIST_REQUEST_JSON = "{\"jsonrpc\": \"2.0\", \"id\": 1, \"method\": \"tools/list\", \"params\": {}}";
    private static final String AKTO_MCP_SERVER_TAG = "mcp-server";
    private static final String LOCAL_IP = "127.0.0.1";
    public McpToolsSyncJobExecutor() {
        Json.mapper().registerModule(new SimpleModule().addSerializer(new JsonNodeExampleSerializer()));
    }

    public void runJob(APIConfig apiConfig) {
        List<ApiCollection> apiCollections = DataActorFactory.fetchInstance().fetchAllApiCollections();
        List<ApiCollection> eligibleCollections = new ArrayList<>();
        for (ApiCollection apiCollection : apiCollections) {
            if (StringUtils.isEmpty(apiCollection.getHostName())) {
                continue;
            }
            List<CollectionTags> tagsList = apiCollection.getTagsList();
            if (CollectionUtils.isEmpty(tagsList)) {
                continue;
            }
            tagsList.stream()
                .filter(t -> AKTO_MCP_SERVER_TAG.equals(t.getKeyName()))
                .findFirst()
                .ifPresent(c -> eligibleCollections.add(apiCollection));
        }

        logger.debug("Found {} collections for MCP server.", eligibleCollections.size());

        if (eligibleCollections.isEmpty()) {
            return;
        }

        eligibleCollections.forEach(apiCollection -> {
            logger.info("Starting MCP sync for apiCollectionId: {} and hostname: {}", apiCollection.getId(),
                apiCollection.getHostName());
                handleMcpDiscovery(apiCollection, apiConfig);
            }
        );
    }

    public static void handleMcpDiscovery(ApiCollection apiCollection, APIConfig apiConfig) {
        String host = apiCollection.getHostName();

        try {
            OriginalHttpRequest toolsListRequest = createToolsListRequest(host);
            String jsonrpcResponse = sendRequest(toolsListRequest);

            JSONRPCResponse toolsListResponse = (JSONRPCResponse) McpSchema.deserializeJsonRpcMessage(mapper,
                jsonrpcResponse);

            List<HttpResponseParams> responseParamsList = new ArrayList<>();

            HttpResponseParams toolsListresponseParams = convertToAktoFormat(apiCollection.getId(),
                toolsListRequest.getPath(),
                buildHeaders(host),
                HttpMethod.POST.name(),
                toolsListRequest.getBody(),
                new OriginalHttpResponse(jsonrpcResponse, Collections.emptyMap(), HttpStatus.SC_OK));

            if (toolsListresponseParams != null) {
                responseParamsList.add(toolsListresponseParams);
            }

            logger.debug("Received tools/list response. Processing tools.....");

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
            String urlWithQueryParams = toolsListRequest.getPathWithQueryParams();
            String toolsCallRequestHeaders = buildHeaders(host);

            for (Tool tool : tools) {
                JSONRPCRequest request = new JSONRPCRequest(
                    McpSchema.JSONRPC_VERSION,
                    McpSchema.METHOD_TOOLS_CALL,
                    id++,
                    new CallToolRequest(tool.getName(), generateExampleArguments(tool.getInputSchema()))
                );

                HttpResponseParams toolsCallHttpResponseParams = convertToAktoFormat(apiCollection.getId(),
                    urlWithQueryParams,
                    toolsCallRequestHeaders,
                    HttpMethod.POST.name(),
                    mapper.writeValueAsString(request),
                    new OriginalHttpResponse("", Collections.emptyMap(), HttpStatus.SC_OK));

                if (toolsCallHttpResponseParams != null) {
                    responseParamsList.add(toolsCallHttpResponseParams);
                }
            }
            Map<String, List<HttpResponseParams>> responseParamsToAccountIdMap = new HashMap<>();
            responseParamsToAccountIdMap.put(Context.accountId.get().toString(), responseParamsList);

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

    private static String buildHeaders(String host) {
        return "{\"Content-Type\":\"application/json\",\"Accept\":\"*/*\",\"host\":\"" + host + "\"}";
    }

    public static OriginalHttpRequest createToolsListRequest(String host) {
        return new OriginalHttpRequest(McpSchema.METHOD_TOOLS_LIST,
            null,
            HttpMethod.POST.name(),
            MCP_TOOLS_LIST_REQUEST_JSON,
            OriginalHttpRequest.buildHeadersMap(buildHeaders(host)),
            "http"
        );
    }

    public static String sendRequest(OriginalHttpRequest request) throws Exception {
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequestWithSse(request, true, null, false,
                new ArrayList<>(), false);
            return response.getBody();
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
            return JSONUtils.getMap(Json.pretty(example));

        } catch (Exception e) {
            logger.error("Failed to generate example arguments using OpenAPI ExampleBuilder", e);
            return Collections.emptyMap();
        }
    }

    private static HttpResponseParams convertToAktoFormat(int apiCollectionId, String path, String requestHeaders, String method,
        String body, OriginalHttpResponse response) {
        Map<String, Object> value = new HashMap<>();

        value.put("path", path);
        value.put("requestHeaders", requestHeaders);
        value.put("responseHeaders", JSONUtils.getString(response.getHeaders()));
        value.put("method", method);
        value.put("requestPayload", body);
        value.put("responsePayload", response.getBody());
        value.put("time", String.valueOf(System.currentTimeMillis() / 1000));
        value.put("statusCode", String.valueOf(response.getStatusCode()));
        value.put("type", "HTTP/1.1");
        value.put("status", String.valueOf(response.getStatusCode()));
        value.put("akto_account_id", String.valueOf(Context.accountId.get()));
        value.put("akto_vxlan_id", String.valueOf(apiCollectionId));
        value.put("source", Source.MIRRORING);
        value.put("ip", LOCAL_IP);

        String message = JSONUtils.getString(value);
        try {
            return HttpCallParser.parseKafkaMessage(message);
        } catch (Exception e) {
            logger.error("Error while parsing MCP message.");
        }
        return null;
    }
}

