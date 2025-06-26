package com.akto.runtime;


import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dto.APIConfig;
import com.akto.dto.ApiCollection;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.HttpResponseParams.Source;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.traffic.CollectionTags;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.mcp.McpSchema;
import com.akto.mcp.McpSchema.CallToolRequest;
import com.akto.mcp.McpSchema.ClientCapabilities;
import com.akto.mcp.McpSchema.InitializeResult;
import com.akto.mcp.McpSchema.JSONRPCRequest;
import com.akto.mcp.McpSchema.JSONRPCResponse;
import com.akto.mcp.McpSchema.JsonSchema;
import com.akto.mcp.McpSchema.ListResourcesResult;
import com.akto.mcp.McpSchema.ListToolsResult;
import com.akto.mcp.McpSchema.ReadResourceRequest;
import com.akto.mcp.McpSchema.Resource;
import com.akto.mcp.McpSchema.ServerCapabilities;
import com.akto.mcp.McpSchema.Tool;
import com.akto.parsers.HttpCallParser;
import com.akto.testing.ApiExecutor;
import com.akto.util.Constants;
import com.akto.util.JSONUtils;
import com.akto.util.Pair;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Projections;
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

public class McpServerSyncJobExecutor {

    private static final LoggerMaker logger = new LoggerMaker(McpServerSyncJobExecutor.class, LogDb.RUNTIME);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String MCP_TOOLS_LIST_REQUEST_JSON =
        "{\"jsonrpc\": \"2.0\", \"id\": 1, \"method\": \"" + McpSchema.METHOD_TOOLS_LIST + "\", \"params\": {}}";
    private static final String MCP_RESOURCE_LIST_REQUEST_JSON =
        "{\"jsonrpc\": \"2.0\", \"id\": 1, \"method\": \"" + McpSchema.METHOD_RESOURCES_LIST + "\", \"params\": {}}";
    private static final String LOCAL_IP = "127.0.0.1";
    private ServerCapabilities mcpServerCapabilities = null;

    public static final McpServerSyncJobExecutor INSTANCE = new McpServerSyncJobExecutor();

    public McpServerSyncJobExecutor() {
        Json.mapper().registerModule(new SimpleModule().addSerializer(new JsonNodeExampleSerializer()));
    }

    public void runJob(APIConfig apiConfig) {
        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject(),
            Projections.exclude("urls", "conditions"));
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
                .filter(t -> Constants.AKTO_MCP_SERVER_TAG.equals(t.getKeyName()))
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
            List<HttpResponseParams> initResponseList = initializeMcpServerCapabilities(apiCollection);
            List<HttpResponseParams> toolsResponseList = handleMcpToolsDiscovery(apiCollection);
            List<HttpResponseParams> resourcesResponseList =  handleMcpResourceDiscovery(apiCollection);
            processResponseParams(apiConfig, new ArrayList<HttpResponseParams>() {{
                addAll(initResponseList);
                addAll(toolsResponseList);
                addAll(resourcesResponseList);
            }});
        });
    }

    private List<HttpResponseParams> initializeMcpServerCapabilities(ApiCollection apiCollection) {
        String host = apiCollection.getHostName();
        List<HttpResponseParams> responseParamsList = new ArrayList<>();
        try {
            JSONRPCRequest initializeRequest = new JSONRPCRequest(
                McpSchema.JSONRPC_VERSION,
                McpSchema.METHOD_INITIALIZE,
                0,
                new ClientCapabilities(null, null, null)
            );
            Pair<JSONRPCResponse, HttpResponseParams> responsePair = getMcpMethodResponse(
                host, McpSchema.METHOD_INITIALIZE, JSONUtils.getString(initializeRequest), apiCollection);
            InitializeResult initializeResult = JSONUtils.fromJson(responsePair.getFirst().getResult(),
                InitializeResult.class);
            if (initializeResult == null || initializeResult.getCapabilities() == null) {
                return Collections.emptyList();
            }
            mcpServerCapabilities = initializeResult.getCapabilities();
            responseParamsList.add(responsePair.getSecond());
        } catch (Exception e) {
            logger.error("Error while initializing MCP server capabilities for hostname: {}", host, e);
        }

        return responseParamsList;
    }

    private List<HttpResponseParams> handleMcpToolsDiscovery(ApiCollection apiCollection) {
        String host = apiCollection.getHostName();

        List<HttpResponseParams> responseParamsList = new ArrayList<>();
        try {
            if (mcpServerCapabilities != null && mcpServerCapabilities.getTools() == null) {
                logger.debug("Skipping tools discovery as MCP server capabilities do not support tools.");
                return responseParamsList;
            }

            Pair<JSONRPCResponse, HttpResponseParams> toolsListResponsePair = getMcpMethodResponse(
                host, McpSchema.METHOD_TOOLS_LIST, MCP_TOOLS_LIST_REQUEST_JSON, apiCollection);

            if (toolsListResponsePair.getSecond() != null) {
                responseParamsList.add(toolsListResponsePair.getSecond());
            }
            logger.debug("Received tools/list response. Processing tools.....");

            ListToolsResult toolsResult = JSONUtils.fromJson(toolsListResponsePair.getFirst().getResult(),
                ListToolsResult.class);

            if (toolsResult != null && !CollectionUtils.isEmpty(toolsResult.getTools())) {
                int id = 2;
                String urlWithQueryParams = toolsListResponsePair.getSecond().getRequestParams().getURL();
                String toolsCallRequestHeaders = buildHeaders(host);

                for (Tool tool : toolsResult.getTools()) {
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
            } else {
                logger.debug("Skipping as List Resource Result is null or Resources are empty");
            }
        } catch (Exception e) {
            logger.error("Error while discovering mcp and its tools for hostname: {}", host, e);
        }
        return responseParamsList;
    }

    private List<HttpResponseParams> handleMcpResourceDiscovery(ApiCollection apiCollection) {
        String host = apiCollection.getHostName();

        List<HttpResponseParams> responseParamsList = new ArrayList<>();
        try {
            if (mcpServerCapabilities != null && mcpServerCapabilities.getResources() == null) {
                logger.debug("Skipping tools discovery as MCP server capabilities do not support tools.");
                return responseParamsList;
            }
            Pair<JSONRPCResponse, HttpResponseParams> resourcesListResponsePair = getMcpMethodResponse(
                host, McpSchema.METHOD_RESOURCES_LIST, MCP_RESOURCE_LIST_REQUEST_JSON, apiCollection);

            if (resourcesListResponsePair.getSecond() != null) {
                responseParamsList.add(resourcesListResponsePair.getSecond());
            }
            logger.debug("Received resources/list response. Processing tools.....");

            ListResourcesResult resourcesResult = JSONUtils.fromJson(resourcesListResponsePair.getFirst().getResult(),
                ListResourcesResult.class);

            if (resourcesResult != null && !CollectionUtils.isEmpty(resourcesResult.getResources())) {
                int id = 2;
                String urlWithQueryParams = resourcesListResponsePair.getSecond().getRequestParams().getURL();
                String toolsCallRequestHeaders = buildHeaders(host);

                for (Resource resource : resourcesResult.getResources()) {
                    JSONRPCRequest request = new JSONRPCRequest(
                        McpSchema.JSONRPC_VERSION,
                        McpSchema.METHOD_RESOURCES_READ,
                        id++,
                        new ReadResourceRequest(resource.getUri())
                    );

                    HttpResponseParams readResourceHttpResponseParams = convertToAktoFormat(apiCollection.getId(),
                        urlWithQueryParams,
                        toolsCallRequestHeaders,
                        HttpMethod.POST.name(),
                        mapper.writeValueAsString(request),
                        new OriginalHttpResponse("", Collections.emptyMap(), HttpStatus.SC_OK));

                    if (readResourceHttpResponseParams != null) {
                        responseParamsList.add(readResourceHttpResponseParams);
                    }
                }
            } else {
                logger.debug("Skipping as List Resource Result is null or Resources are empty");
            }
        } catch (Exception e) {
            logger.error("Error while discovering mcp resources for hostname: {}", host, e);
        }
        return responseParamsList;
    }

    private void processResponseParams(APIConfig apiConfig, List<HttpResponseParams> responseParamsList) {
        if (CollectionUtils.isEmpty(responseParamsList)) {
            logger.debug("No response params to process for MCP tools sync job.");
            return;
        }
        Map<String, List<HttpResponseParams>> responseParamsToAccountIdMap = new HashMap<>();
        responseParamsToAccountIdMap.put(Context.accountId.get().toString(), responseParamsList);

        Main.handleResponseParams(responseParamsToAccountIdMap,
            new HashMap<>(),
            false,
            new HashMap<>(),
            apiConfig,
            true,
            true
        );
    }

    private Map<String, Object> generateExampleArguments(JsonSchema inputSchema) {
        if (inputSchema == null) {
            return Collections.emptyMap();
        }
        try {
            String inputSchemaJson = mapper.writeValueAsString(inputSchema);
            Schema openApiSchema = io.swagger.v3.core.util.Json.mapper().readValue(inputSchemaJson, Schema.class);
            Example example = ExampleBuilder.fromSchema(openApiSchema, null);
            return JSONUtils.getMap(Json.pretty(example));

        } catch (Exception e) {
            logger.error("Failed to generate example arguments using OpenAPI ExampleBuilder", e);
            return Collections.emptyMap();
        }
    }

    private Pair<JSONRPCResponse, HttpResponseParams> getMcpMethodResponse(String host, String mcpMethod,
        String mcpMethodRequestJson, ApiCollection apiCollection) throws Exception {
        OriginalHttpRequest mcpRequest = createRequest(host, mcpMethod, mcpMethodRequestJson);
        String jsonrpcResponse = sendRequest(mcpRequest);

        JSONRPCResponse rpcResponse = (JSONRPCResponse) McpSchema.deserializeJsonRpcMessage(mapper, jsonrpcResponse);

        if (rpcResponse.getError() != null) {
            String errorMessage = "Error in JSONRPC response from " + mcpMethod + ": " +
                rpcResponse.getError().getMessage();
            throw new Exception(errorMessage);
        }

        HttpResponseParams responseParams = convertToAktoFormat(
            apiCollection.getId(),
            mcpRequest.getPathWithQueryParams(),
            buildHeaders(host),
            HttpMethod.POST.name(),
            mcpRequest.getBody(),
            new OriginalHttpResponse(jsonrpcResponse, Collections.emptyMap(), HttpStatus.SC_OK)
        );

        return new Pair<>(rpcResponse, responseParams);
    }

    private OriginalHttpRequest createRequest(String host, String mcpMethod, String mcpMethodRequestJson) {
        return new OriginalHttpRequest(mcpMethod,
            null,
            HttpMethod.POST.name(),
            mcpMethodRequestJson,
            OriginalHttpRequest.buildHeadersMap(buildHeaders(host)),
            "HTTP/1.1"
        );
    }

    private String buildHeaders(String host) {
        return "{\"Content-Type\":\"application/json\",\"Accept\":\"*/*\",\"host\":\"" + host + "\"}";
    }

    private String sendRequest(OriginalHttpRequest request) throws Exception {
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequestWithSse(request, true, null, false,
                new ArrayList<>(), false);
            return response.getBody();
        } catch (Exception e) {
            logger.error("Error while making request to MCP server.", e);
            throw e;
        }
    }

    private HttpResponseParams convertToAktoFormat(int apiCollectionId, String path, String requestHeaders, String method,
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
