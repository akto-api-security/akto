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
import com.akto.mcp.McpSchema.Implementation;
import com.akto.mcp.McpSchema.InitializeRequest;
import com.akto.mcp.McpSchema.InitializeResult;
import com.akto.mcp.McpSchema.JSONRPCRequest;
import com.akto.mcp.McpSchema.JSONRPCResponse;
import com.akto.mcp.McpSchema.JsonSchema;
import com.akto.mcp.McpSchema.GetPromptRequest;
import com.akto.mcp.McpSchema.ListPromptsResult;
import com.akto.mcp.McpSchema.ListResourcesResult;
import com.akto.mcp.McpSchema.ListToolsResult;
import com.akto.mcp.McpSchema.Prompt;
import com.akto.mcp.McpSchema.ReadResourceRequest;
import com.akto.mcp.McpSchema.Resource;
import com.akto.mcp.McpSchema.ServerCapabilities;
import com.akto.mcp.McpSchema.Tool;
import com.akto.parsers.HttpCallParser;
import com.akto.testing.ApiExecutor;
import com.akto.util.McpSseEndpointHelper;
import com.akto.util.Constants;
import com.akto.util.JSONUtils;
import com.akto.util.Pair;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;
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
    private static final String MCP_TOOLS_LIST_REQUEST_JSON =
        "{\"jsonrpc\": \"2.0\", \"id\": 1, \"method\": \"" + McpSchema.METHOD_TOOLS_LIST + "\", \"params\": {}}";
    private static final String MCP_RESOURCE_LIST_REQUEST_JSON =
        "{\"jsonrpc\": \"2.0\", \"id\": 1, \"method\": \"" + McpSchema.METHOD_RESOURCES_LIST + "\", \"params\": {}}";
    private static final String MCP_PROMPTS_LIST_REQUEST_JSON =
        "{\"jsonrpc\": \"2.0\", \"id\": 1, \"method\": \"" + McpSchema.METHOD_PROMPT_LIST + "\", \"params\": {}}";
    private static final String LOCAL_IP = "127.0.0.1";
    
    // MCP Transport types
    private static final String TRANSPORT_SSE = "SSE";
    private static final String TRANSPORT_HTTP = "HTTP";
    
    private ServerCapabilities mcpServerCapabilities = null;

    public static final McpToolsSyncJobExecutor INSTANCE = new McpToolsSyncJobExecutor();

    public McpToolsSyncJobExecutor() {
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
            runJobforCollection(apiCollection, apiConfig, "");
        });
    }

    public void runJobforCollection(ApiCollection apiCollection, APIConfig apiConfig, String authHeader) {
        if (apiCollection == null || StringUtils.isEmpty(apiCollection.getHostName())) {
            logger.error("Invalid ApiCollection provided for MCP sync job.");
            return;
        }

        logger.info("Starting MCP sync for apiCollectionId: {} and hostname: {}", apiCollection.getId(),
        apiCollection.getHostName());
        List<HttpResponseParams> initResponseList = initializeMcpServerCapabilities(apiCollection, authHeader);
        List<HttpResponseParams> toolsResponseList = handleMcpToolsDiscovery(apiCollection, authHeader);
        List<HttpResponseParams> resourcesResponseList = handleMcpResourceDiscovery(apiCollection, authHeader);
        List<HttpResponseParams> promptsResponseList = handleMcpPromptsDiscovery(apiCollection, authHeader);
        processResponseParams(apiConfig, new ArrayList<HttpResponseParams>() {{
            addAll(initResponseList);
            addAll(toolsResponseList);
            addAll(resourcesResponseList);
            addAll(promptsResponseList);
        }});
    }



    private List<HttpResponseParams> initializeMcpServerCapabilities(ApiCollection apiCollection, String authHeader) {
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
            String initializeJson = JSONUtils.getString(initializeRequest);
            logger.info("Initialize request JSON: {}", initializeJson);
            Pair<JSONRPCResponse, HttpResponseParams> responsePair = getMcpMethodResponse(
                host, authHeader, McpSchema.METHOD_INITIALIZE, initializeJson, apiCollection);
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

    private List<HttpResponseParams> handleMcpToolsDiscovery(ApiCollection apiCollection, String authHeader) {
        String host = apiCollection.getHostName();

        List<HttpResponseParams> responseParamsList = new ArrayList<>();
        try {
            if (mcpServerCapabilities != null && mcpServerCapabilities.getTools() == null) {
                logger.debug("Skipping tools discovery as MCP server capabilities do not support tools.");
                return responseParamsList;
            }

            Pair<JSONRPCResponse, HttpResponseParams> toolsListResponsePair = getMcpMethodResponse(
                host, authHeader, McpSchema.METHOD_TOOLS_LIST, MCP_TOOLS_LIST_REQUEST_JSON, apiCollection);

            if (toolsListResponsePair.getSecond() != null) {
                responseParamsList.add(toolsListResponsePair.getSecond());
            }
            logger.debug("Received tools/list response. Processing tools.....");

            ListToolsResult toolsResult = JSONUtils.fromJson(toolsListResponsePair.getFirst().getResult(),
                ListToolsResult.class);

            if (toolsResult != null && !CollectionUtils.isEmpty(toolsResult.getTools())) {
                int id = 2;
                String urlWithQueryParams = toolsListResponsePair.getSecond().getRequestParams().getURL();
                String toolsCallRequestHeaders = buildHeaders(host, authHeader);

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
                logger.debug("Skipping as List Tools Result is null or Tools are empty");
            }
        } catch (Exception e) {
            logger.error("Error while discovering mcp tools for hostname: {}", host, e);
        }
        return responseParamsList;
    }

    private List<HttpResponseParams> handleMcpResourceDiscovery(ApiCollection apiCollection, String authHeader) {
        String host = apiCollection.getHostName();

        List<HttpResponseParams> responseParamsList = new ArrayList<>();
        try {
            if (mcpServerCapabilities != null && mcpServerCapabilities.getResources() == null) {
                logger.debug("Skipping resources discovery as MCP server capabilities do not support resources.");
                return responseParamsList;
            }
            Pair<JSONRPCResponse, HttpResponseParams> resourcesListResponsePair = getMcpMethodResponse(
                host, authHeader, McpSchema.METHOD_RESOURCES_LIST, MCP_RESOURCE_LIST_REQUEST_JSON, apiCollection);

            if (resourcesListResponsePair.getSecond() != null) {
                responseParamsList.add(resourcesListResponsePair.getSecond());
            }
            logger.debug("Received resources/list response. Processing resources.....");

            ListResourcesResult resourcesResult = JSONUtils.fromJson(resourcesListResponsePair.getFirst().getResult(),
                ListResourcesResult.class);

            if (resourcesResult != null && !CollectionUtils.isEmpty(resourcesResult.getResources())) {
                int id = 2;
                String urlWithQueryParams = resourcesListResponsePair.getSecond().getRequestParams().getURL();
                String resourcesReadRequestHeaders = buildHeaders(host, authHeader);

                for (Resource resource : resourcesResult.getResources()) {
                    JSONRPCRequest request = new JSONRPCRequest(
                        McpSchema.JSONRPC_VERSION,
                        McpSchema.METHOD_RESOURCES_READ,
                        id++,
                        new ReadResourceRequest(resource.getUri())
                    );

                    HttpResponseParams readResourceHttpResponseParams = convertToAktoFormat(apiCollection.getId(),
                        urlWithQueryParams,
                        resourcesReadRequestHeaders,
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

    private List<HttpResponseParams> handleMcpPromptsDiscovery(ApiCollection apiCollection, String authHeader) {
        String host = apiCollection.getHostName();

        List<HttpResponseParams> responseParamsList = new ArrayList<>();
        try {
            if (mcpServerCapabilities != null && mcpServerCapabilities.getPrompts() == null) {
                logger.debug("Skipping prompts discovery as MCP server capabilities do not support prompts.");
                return responseParamsList;
            }
            Pair<JSONRPCResponse, HttpResponseParams> promptsListResponsePair = getMcpMethodResponse(
                host, authHeader, McpSchema.METHOD_PROMPT_LIST, MCP_PROMPTS_LIST_REQUEST_JSON, apiCollection);

            if (promptsListResponsePair.getSecond() != null) {
                responseParamsList.add(promptsListResponsePair.getSecond());
            }
            logger.debug("Received prompts/list response. Processing prompts.....");

            ListPromptsResult promptsResult = JSONUtils.fromJson(promptsListResponsePair.getFirst().getResult(),
                ListPromptsResult.class);

            if (promptsResult != null && !CollectionUtils.isEmpty(promptsResult.getPrompts())) {
                int id = 2;
                String urlWithQueryParams = promptsListResponsePair.getSecond().getRequestParams().getURL();
                String promptsGetRequestHeaders = buildHeaders(host, authHeader);

                for (Prompt prompt : promptsResult.getPrompts()) {
                    JSONRPCRequest request = new JSONRPCRequest(
                        McpSchema.JSONRPC_VERSION,
                        McpSchema.METHOD_PROMPT_GET,
                        id++,
                        new GetPromptRequest(prompt.getName(), generateExampleArguments(prompt))
                    );

                    HttpResponseParams getPromptHttpResponseParams = convertToAktoFormat(apiCollection.getId(),
                        urlWithQueryParams,
                        promptsGetRequestHeaders,
                        HttpMethod.POST.name(),
                        mapper.writeValueAsString(request),
                        new OriginalHttpResponse("", Collections.emptyMap(), HttpStatus.SC_OK));

                    if (getPromptHttpResponseParams != null) {
                        responseParamsList.add(getPromptHttpResponseParams);
                    }
                }

            } else {
                logger.debug("Skipping as List Prompts Result is null or Prompts are empty");
            }
        } catch (Exception e) {
            logger.error("Error while discovering mcp prompts for hostname: {}", host, e);
        }
        return responseParamsList;
    }
    
    private Map<String, Object> generateExampleArguments(Prompt prompt) {
        if (prompt == null || CollectionUtils.isEmpty(prompt.getArguments())) {
            return Collections.emptyMap();
        }
        Map<String, Object> args = new HashMap<>();
        prompt.getArguments().forEach(arg -> {
            if (arg.getRequired() != null && arg.getRequired()) {
                args.put(arg.getName(), "example_" + arg.getName());
            }
        });
        return args;
    }

    private void processResponseParams(APIConfig apiConfig, List<HttpResponseParams> responseParamsList) {
        if (CollectionUtils.isEmpty(responseParamsList)) {
            logger.debug("No response params to process for MCP sync.");
            return;
        }
        Map<String, List<HttpResponseParams>> responseParamsToAccountIdMap = new HashMap<>();
        responseParamsToAccountIdMap.put(Context.accountId.get().toString(), responseParamsList);

        Main.handleResponseParams(responseParamsToAccountIdMap,
            new HashMap<>(),
            false,
            new HashMap<>(),
            apiConfig,
            false,
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

    private Pair<JSONRPCResponse, HttpResponseParams> getMcpMethodResponse(String host, String authHeader,  String mcpMethod,
        String mcpMethodRequestJson, ApiCollection apiCollection) throws Exception {
        OriginalHttpRequest mcpRequest = createRequest(host, authHeader, mcpMethod, mcpMethodRequestJson, apiCollection);
        String jsonrpcResponse = sendRequest(mcpRequest, apiCollection);

        JSONRPCResponse rpcResponse = (JSONRPCResponse) McpSchema.deserializeJsonRpcMessage(mapper, jsonrpcResponse);

        if (rpcResponse.getError() != null) {
            String errorMessage = "Error in JSONRPC response from " + mcpMethod + ": " +
                rpcResponse.getError().getMessage();
            throw new Exception(errorMessage);
        }

        HttpResponseParams responseParams = convertToAktoFormat(
            apiCollection.getId(),
            mcpRequest.getPathWithQueryParams(),
            buildHeaders(host, authHeader),
            HttpMethod.POST.name(),
            mcpRequest.getBody(),
            new OriginalHttpResponse(jsonrpcResponse, Collections.emptyMap(), HttpStatus.SC_OK)
        );

        return new Pair<>(rpcResponse, responseParams);
    }

    private OriginalHttpRequest createRequest(String host, String authHeader, String mcpMethod, String mcpMethodRequestJson, ApiCollection apiCollection) {
        String mcpEndpoint = apiCollection.getSseCallbackUrl();
        String path = mcpMethod;
        String queryParams = null;

        if (mcpEndpoint != null && !mcpEndpoint.isEmpty()) {
            // For HTTP transport, use the full endpoint path directly
            // For SSE transport, extract query params if present
            if (mcpEndpoint.contains("?")) {
                String[] parts = mcpEndpoint.split("\\?", 2);
                // Use the endpoint path if it looks like a valid path
                if (parts[0].startsWith("/")) {
                    path = parts[0];
                }
                queryParams = parts[1];
            } else if (mcpEndpoint.startsWith("/")) {
                // If it's just a path without query params
                path = mcpEndpoint;
            }
        }
        
        // Build full URL with scheme and host
        // Assume HTTPS for MCP servers (standard practice)
        //todo: add protocol based on the mcpEndpoint
        String fullUrl = "https://" + host + path;
        
        return new OriginalHttpRequest(fullUrl, 
            queryParams,
            HttpMethod.POST.name(),
            mcpMethodRequestJson,
            OriginalHttpRequest.buildHeadersMap(buildHeaders(host, authHeader)),
            "HTTP/1.1"
        );
    }

    private String buildHeaders(String host, String authHeader) {
        //add authHeader if not empty
        if (authHeader == null || authHeader.isEmpty()) {
            authHeader = "";
        } else {
            authHeader = "," + authHeader;
        }
        return "{\"Content-Type\":\"application/json\",\"Accept\":\"*/*\",\"host\":\"" + host + "\"" + authHeader + "}";
    }

    private String sendRequest(OriginalHttpRequest request, ApiCollection apiCollection) throws Exception {
        try {
            String transportType = apiCollection.getMcpTransportType();
            
            // If transport type is not set, try to detect it
            if (transportType == null || transportType.isEmpty()) {
                transportType = detectAndSetTransportType(request, apiCollection);
            }
            
            // Log request details for debugging
            logger.info("Sending MCP request - URL: {}, Method: {}, Body length: {}", 
                request.getUrl(), request.getMethod(), 
                request.getBody() != null ? request.getBody().length() : 0);
            logger.debug("Request body: {}", request.getBody());
            
            OriginalHttpResponse response;
            if (TRANSPORT_HTTP.equals(transportType)) {
                // Use standard HTTP POST for streamable responses
                // Use sendRequestSkipSse to prevent ApiExecutor from trying SSE
                logger.info("Using HTTP transport for MCP server: {}", apiCollection.getHostName());
                response = ApiExecutor.sendRequestSkipSse(request, true, null, false, new ArrayList<>(), false);
            } else {
                // Use SSE transport
                logger.info("Using SSE transport for MCP server: {}", apiCollection.getHostName());
                McpSseEndpointHelper.addSseEndpointHeader(request, apiCollection.getId());
                response = ApiExecutor.sendRequestWithSse(request, true, null, false,
                    new ArrayList<>(), false, true);
            }
            
            logger.info("Received MCP response - Status: {}, Body length: {}", 
                response.getStatusCode(), 
                response.getBody() != null ? response.getBody().length() : 0);
            logger.debug("Response body: {}", response.getBody());
            
            return response.getBody();
        } catch (Exception e) {
            logger.error("Error while making request to MCP server: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    private String detectAndSetTransportType(OriginalHttpRequest request, ApiCollection apiCollection) throws Exception {
        // Try SSE first if sseCallbackUrl is set
        if (apiCollection.getSseCallbackUrl() != null && !apiCollection.getSseCallbackUrl().isEmpty()) {
            try {
                logger.info("Attempting to detect transport type for MCP server: {}", apiCollection.getHostName());
                
                // Clone request for SSE detection to avoid modifying original
                OriginalHttpRequest sseTestRequest = cloneRequest(request);
                McpSseEndpointHelper.addSseEndpointHeader(sseTestRequest, apiCollection.getId());
                ApiExecutor.sendRequestWithSse(sseTestRequest, true, null, false,
                    new ArrayList<>(), false, true);
                
                // If SSE works, update the collection
                updateTransportType(apiCollection, TRANSPORT_SSE);
                logger.info("Detected SSE transport for MCP server: {}", apiCollection.getHostName());
                return TRANSPORT_SSE;
            } catch (Exception sseException) {
                logger.info("SSE transport failed, falling back to HTTP transport: {}", sseException.getMessage());
                // Fall back to HTTP - no need to test, just store it
                updateTransportType(apiCollection, TRANSPORT_HTTP);
                return TRANSPORT_HTTP;
            }
        }
        
        // Default to HTTP if no sseCallbackUrl
        logger.info("No SSE callback URL found, using HTTP transport for: {}", apiCollection.getHostName());
        updateTransportType(apiCollection, TRANSPORT_HTTP);
        return TRANSPORT_HTTP;
    }
    
    private OriginalHttpRequest cloneRequest(OriginalHttpRequest request) {
        // Create a shallow clone to avoid modifying the original during detection
        Map<String, List<String>> headersCopy = new HashMap<>();
        if (request.getHeaders() != null) {
            request.getHeaders().forEach((key, value) -> {
                headersCopy.put(key, new ArrayList<>(value));
            });
        }
        return new OriginalHttpRequest(
            request.getUrl(),
            request.getQueryParams(),
            request.getMethod(),
            request.getBody(),
            headersCopy,
            request.getType()
        );
    }
    
    private void updateTransportType(ApiCollection apiCollection, String transportType) {
        try {
            Bson filter = Filters.eq(ApiCollection.ID, apiCollection.getId());
            Bson update = Updates.set(ApiCollection.MCP_TRANSPORT_TYPE, transportType);
            ApiCollectionsDao.instance.updateOne(filter, update);
            apiCollection.setMcpTransportType(transportType);
            logger.info("Updated transport type to {} for collection: {}", transportType, apiCollection.getId());
        } catch (Exception e) {
            logger.warn("Failed to update transport type in database: {}", e.getMessage());
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
