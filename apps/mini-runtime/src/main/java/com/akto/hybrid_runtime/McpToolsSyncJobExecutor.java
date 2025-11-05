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
import com.akto.dto.traffic.SampleData;
import com.akto.hybrid_parsers.HttpCallParser;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.mcp.McpSchema;
import com.akto.mcp.McpSchema.CallToolRequest;
import com.akto.mcp.McpSchema.ClientCapabilities;
import com.akto.mcp.McpSchema.Implementation;
import com.akto.mcp.McpSchema.InitializeRequest;
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
import com.akto.testing.ApiExecutor;
import com.akto.util.Constants;
import com.akto.util.JSONUtils;
import com.akto.util.Pair;
import com.akto.utils.McpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.swagger.oas.inflector.examples.ExampleBuilder;
import io.swagger.oas.inflector.examples.models.Example;
import io.swagger.oas.inflector.processors.JsonNodeExampleSerializer;
import io.swagger.util.Json;
import io.swagger.v3.oas.models.media.Schema;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.springframework.http.HttpMethod;

public class McpToolsSyncJobExecutor {

    private static final LoggerMaker logger = new LoggerMaker(McpToolsSyncJobExecutor.class, LogDb.RUNTIME);
    private static final ObjectMapper mapper = new ObjectMapper();
    public static final String MCP_TOOLS_LIST_REQUEST_JSON =
        "{\"jsonrpc\": \"2.0\", \"id\": 1, \"method\": \"" + McpSchema.METHOD_TOOLS_LIST + "\", \"params\": {}}";
    public static final String MCP_RESOURCE_LIST_REQUEST_JSON =
        "{\"jsonrpc\": \"2.0\", \"id\": 1, \"method\": \"" + McpSchema.METHOD_RESOURCES_LIST + "\", \"params\": {}}";
    public static final String LOCAL_IP = "127.0.0.1";
    private ServerCapabilities mcpServerCapabilities = null;

    public static final McpToolsSyncJobExecutor INSTANCE = new McpToolsSyncJobExecutor();

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
            try {
                Set<String> normalizedSampleDataSet = getNormalizedSampleData(apiCollection.getId());
                List<HttpResponseParams> initResponseList = initializeMcpServerCapabilities(apiCollection,
                    normalizedSampleDataSet);
                List<HttpResponseParams> toolsResponseList = handleMcpToolsDiscovery(apiCollection,
                    normalizedSampleDataSet, false, null);
                List<HttpResponseParams> resourcesResponseList = handleMcpResourceDiscovery(apiCollection,
                    normalizedSampleDataSet, false, null);
                List<HttpResponseParams> responseParamsToProcess = new ArrayList<>();
                responseParamsToProcess.addAll(initResponseList);
                responseParamsToProcess.addAll(toolsResponseList);
                responseParamsToProcess.addAll(resourcesResponseList);
                processResponseParams(apiConfig, responseParamsToProcess);
            } catch (Exception e) {
                logger.error("Error while running MCP sync job for apiCollectionId: {} and hostname: {}",
                    apiCollection.getId(), apiCollection.getHostName(), e);
            }
        });
    }

    private List<HttpResponseParams> initializeMcpServerCapabilities(ApiCollection apiCollection, Set<String> normalizedSampleDataSet) {
        String host = apiCollection.getHostName();
        List<HttpResponseParams> responseParamsList = new ArrayList<>();
        try {
            JSONRPCRequest initializeRequest = new JSONRPCRequest(
                McpSchema.JSONRPC_VERSION,
                McpSchema.METHOD_INITIALIZE,
                String.valueOf(0),
                new InitializeRequest(
                    McpSchema.LATEST_PROTOCOL_VERSION,
                    new ClientCapabilities(
                        null,
                        null,
                        null
                    ),
                    new Implementation("akto-api-security", "1.0.0")
                )
            );
            Pair<JSONRPCResponse, HttpResponseParams> responsePair = getMcpMethodResponse(
                host, McpSchema.METHOD_INITIALIZE, JSONUtils.getString(initializeRequest), apiCollection);
            InitializeResult initializeResult = JSONUtils.fromJson(responsePair.getFirst().getResult(),
                InitializeResult.class);
            if (initializeResult == null || initializeResult.getCapabilities() == null) {
                return Collections.emptyList();
            }
            mcpServerCapabilities = initializeResult.getCapabilities();
            if (!normalizedSampleDataSet.contains(McpSchema.METHOD_INITIALIZE)) {
                responseParamsList.add(responsePair.getSecond());
            }
        } catch (Exception e) {
            logger.error("Error while initializing MCP server capabilities for hostname: {}", host, e);
        }

        return responseParamsList;
    }

    public List<HttpResponseParams> handleMcpToolsDiscovery(ApiCollection apiCollection,
                                                             Set<String> normalizedSampleDataSet, boolean isRecon, McpServer mcpServer) {

        String host ="";
        List<HttpResponseParams> responseParamsList = new ArrayList<>();
        if(isRecon) {
            host = mcpServer.getIp() + ":" + mcpServer.getPort();
            responseParamsList = getHttpResponseParamsforTools(mcpServer.getTools(), mcpServer.getUrl(), Source.MCP_RECON, host, Collections.EMPTY_SET);
        }
        else {
            host = apiCollection.getHostName();
            try {
                if (mcpServerCapabilities != null && mcpServerCapabilities.getTools() == null) {
                    logger.debug("Skipping tools discovery as MCP server capabilities do not support tools.");
                    return responseParamsList;
                }

                Pair<JSONRPCResponse, HttpResponseParams> toolsListResponsePair = getMcpMethodResponse(
                        host, McpSchema.METHOD_TOOLS_LIST, MCP_TOOLS_LIST_REQUEST_JSON, apiCollection);

                if (toolsListResponsePair.getSecond() != null && !normalizedSampleDataSet.contains(
                        McpSchema.METHOD_TOOLS_LIST)) {
                    responseParamsList.add(toolsListResponsePair.getSecond());
                }
                logger.debug("Received tools/list response. Processing tools.....");

                ListToolsResult toolsResult = JSONUtils.fromJson(toolsListResponsePair.getFirst().getResult(),
                        ListToolsResult.class);

                if (toolsResult != null && !CollectionUtils.isEmpty(toolsResult.getTools())) {
                    int id = 2;
                    String urlWithQueryParams = toolsListResponsePair.getSecond().getRequestParams().getURL();
                    responseParamsList = getHttpResponseParamsforTools(toolsResult.getTools(), urlWithQueryParams, Source.MIRRORING, host, normalizedSampleDataSet);
                } else {
                    logger.debug("Skipping as List Tools Result is null or Tools are empty");
                }
            } catch (Exception e) {
                logger.error("Error while discovering mcp tools for hostname: {}", host, e);
            }

        }
        return responseParamsList;
    }

    private List<HttpResponseParams> getHttpResponseParamsforTools(List<Tool> tools, String url, Source source, String host,
                                                                     Set<String> normalizedSampleDataSet) {
        List<HttpResponseParams> responseParamsList = new ArrayList<>();
        try {
            int id = 1;
            String toolsCallRequestHeaders = buildHeaders(host);
            for (Tool tool : tools) {

                if (!normalizedSampleDataSet.isEmpty() && normalizedSampleDataSet.contains(McpSchema.METHOD_TOOLS_CALL + "/" + tool.getName())) {
                    logger.debug("Skipping tool {} as it is already present in the db.", tool.getName());
                    continue;
                }

                JSONRPCRequest request = new JSONRPCRequest(
                        McpSchema.JSONRPC_VERSION,
                        McpSchema.METHOD_TOOLS_CALL,
                        id++,
                        new CallToolRequest(tool.getName(), McpToolsSyncJobExecutor.generateExampleArguments(tool.getInputSchema()))
                );

                HttpResponseParams toolsCallHttpResponseParams = McpToolsSyncJobExecutor.convertToAktoFormat(0,
                        url,
                        toolsCallRequestHeaders,
                        HttpMethod.POST.name(),
                        mapper.writeValueAsString(request),
                        new OriginalHttpResponse("", Collections.emptyMap(), HttpStatus.SC_OK));

                if (toolsCallHttpResponseParams != null) {
                    toolsCallHttpResponseParams.setSource(source);
                    responseParamsList.add(toolsCallHttpResponseParams);
                }
            }
        } catch (Exception e) {
            logger.error("Error while discovering mcp tools for hostname: {}", host, e);
        }
        return responseParamsList;
    }

    public List<HttpResponseParams> handleMcpResourceDiscovery(ApiCollection apiCollection,
        Set<String> normalizedSampleDataSet, boolean isRecon, McpServer mcpServer) {

        String host ="";
        List<HttpResponseParams> responseParamsList = new ArrayList<>();

        if(isRecon) {
            host = mcpServer.getIp() + ":" + mcpServer.getPort();
            responseParamsList = getHttpResponseParamsforResource(mcpServer.getResources(), mcpServer.getUrl(), Source.MCP_RECON, host, Collections.EMPTY_SET);
        }
        else {
            host = apiCollection.getHostName();
            try {
                if (mcpServerCapabilities != null && mcpServerCapabilities.getResources() == null) {
                    logger.debug("Skipping resources discovery as MCP server capabilities do not support resources.");
                    return responseParamsList;
                }
                Pair<JSONRPCResponse, HttpResponseParams> resourcesListResponsePair = getMcpMethodResponse(
                        host, McpSchema.METHOD_RESOURCES_LIST, MCP_RESOURCE_LIST_REQUEST_JSON, apiCollection);

                if (resourcesListResponsePair.getSecond() != null && !normalizedSampleDataSet.contains(
                        McpSchema.METHOD_RESOURCES_LIST)) {
                    responseParamsList.add(resourcesListResponsePair.getSecond());
                }
                logger.debug("Received resources/list response. Processing resources.....");

                ListResourcesResult resourcesResult = JSONUtils.fromJson(resourcesListResponsePair.getFirst().getResult(),
                        ListResourcesResult.class);

                if (resourcesResult != null && !CollectionUtils.isEmpty(resourcesResult.getResources())) {
                    int id = 2;
                    String urlWithQueryParams = resourcesListResponsePair.getSecond().getRequestParams().getURL();

                    responseParamsList = getHttpResponseParamsforResource(resourcesResult.getResources(), urlWithQueryParams, Source.MIRRORING, host, normalizedSampleDataSet);
                } else {
                    logger.debug("Skipping as List Resource Result is null or Resources are empty");
                }
            } catch (Exception e) {
                logger.error("Error while discovering mcp resources for hostname: {}", host, e);
            }

        }
        return responseParamsList;
    }

    private List<HttpResponseParams> getHttpResponseParamsforResource(List<Resource> resources, String url, Source source, String host, Set<String> normalizedSampleDataSet) {
        List<HttpResponseParams> responseParamsList = new ArrayList<>();
        try {
            int id = 1;
            String resourceCallRequestHeaders = buildHeaders(host);
            for (Resource resource : resources) {

                if (normalizedSampleDataSet.contains(McpSchema.METHOD_RESOURCES_READ + "/" + resource.getUri())) {
                    logger.debug("Skipping resource {} as it is already present in the db.", resource.getUri());
                    continue;
                }

                JSONRPCRequest request = new JSONRPCRequest(
                        McpSchema.JSONRPC_VERSION,
                        McpSchema.METHOD_RESOURCES_READ,
                        id++,
                        new ReadResourceRequest(resource.getUri())
                );

                HttpResponseParams readResourceHttpResponseParams = McpToolsSyncJobExecutor.convertToAktoFormat(0,
                        url,
                        resourceCallRequestHeaders,
                        HttpMethod.POST.name(),
                        mapper.writeValueAsString(request),
                        new OriginalHttpResponse("", Collections.emptyMap(), HttpStatus.SC_OK));

                if (readResourceHttpResponseParams != null) {
                    readResourceHttpResponseParams.setSource(source);
                    responseParamsList.add(readResourceHttpResponseParams);
                }
            }
        } catch (Exception e) {
            logger.error("Error while discovering mcp resources for hostname: {}", host, e);
        }
        return responseParamsList;
    }

    public static void processResponseParams(APIConfig apiConfig, List<HttpResponseParams> responseParamsList) {
        if (CollectionUtils.isEmpty(responseParamsList)) {
            logger.debug("No response params to process for MCP sync job.");
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
            true,
            AccountSettings.DEFAULT_CENTRAL_KAFKA_TOPIC_NAME
        );
    }

    public static Map<String, Object> generateExampleArguments(JsonSchema inputSchema) {
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

    public Pair<JSONRPCResponse, HttpResponseParams> getMcpMethodResponse(String host, String mcpMethod,
        String mcpMethodRequestJson, ApiCollection apiCollection) throws Exception {
        OriginalHttpRequest mcpRequest = createRequest(host, mcpMethod, mcpMethodRequestJson);
        String jsonrpcResponse = sendRequest(mcpRequest);

        JSONRPCResponse rpcResponse = (JSONRPCResponse) McpSchema.deserializeJsonRpcMessage(mapper, jsonrpcResponse);

        if (rpcResponse.getError() != null) {
            String errorMessage = "Error in JSONRPC response from " + mcpMethod + ": " +
                rpcResponse.getError().getMessage();
            throw new Exception(errorMessage);
        }

        int apiCollectionId = apiCollection != null ? apiCollection.getId() : 0;
        HttpResponseParams responseParams = convertToAktoFormat(
            apiCollectionId,
            mcpRequest.getPathWithQueryParams(),
            buildHeaders(host),
            HttpMethod.POST.name(),
            mcpRequest.getBody(),
            new OriginalHttpResponse(jsonrpcResponse, Collections.emptyMap(), HttpStatus.SC_OK)
        );

        return new Pair<>(rpcResponse, responseParams);
    }

    private OriginalHttpRequest createRequest(String host, String mcpMethod, String mcpMethodRequestJson) {
        return new OriginalHttpRequest("",
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
                new ArrayList<>(), false, true);
            return response.getBody();
        } catch (Exception e) {
            logger.error("Error while making request to MCP server.", e);
            throw e;
        }
    }

    public static HttpResponseParams convertToAktoFormat(int apiCollectionId, String path, String requestHeaders, String method,
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

    private static Set<String> getNormalizedSampleData(int apiCollectionId) {
        List<SampleData> sampleDataList = DataActorFactory.fetchInstance().fetchSampleData(
            new HashSet<>(Collections.singletonList(apiCollectionId)), 0);

        Set<String> result = new HashSet<>();
        sampleDataList.stream()
            .map(d -> d.getId().getUrl())
            .forEach(url -> {
                try {
                    String path = new URI(url).getPath();
                    int toolsIndex = path.indexOf("tools");
                    int resourcesIndex = path.indexOf("resources");
                    int initializeIndex = path.indexOf("initialize");

                    if (toolsIndex != -1) {
                        result.add(path.substring(toolsIndex));
                    } else if (resourcesIndex != -1) {
                        result.add(path.substring(resourcesIndex));
                    } else if (initializeIndex != -1) {
                        result.add(path.substring(initializeIndex));
                    }
                } catch (Exception e) {
                    logger.error("Error while normalizing sample data URL: {}", url, e);
                }
            });
        return result;
    }
}

