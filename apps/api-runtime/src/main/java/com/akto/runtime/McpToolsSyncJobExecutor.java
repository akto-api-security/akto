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
import com.akto.util.HttpRequestResponseUtils;
import com.akto.util.McpSseEndpointHelper;
import com.akto.mcp.McpRequestResponseUtils;
import com.akto.util.Constants;
import com.akto.util.JSONUtils;
import com.akto.util.Pair;
import com.akto.mcp.McpRegistryUtils;
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
    private String mcpSessionId = null;

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
        
        // Check registry status AFTER confirming MCP server capabilities
        if (!initResponseList.isEmpty()) {
            updateRegistryStatusAfterMcpConfirmation(apiCollection);
        }
        
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

    private void updateRegistryStatusAfterMcpConfirmation(ApiCollection apiCollection) {
        try {
            String hostName = apiCollection.getHostName();
            String registryStatus = McpRegistryUtils.checkRegistryAvailability(hostName);
            
            logger.info("Registry status for MCP server {} (after capability confirmation): {}", 
                hostName, registryStatus);
            
            if (registryStatus != null) {
                // Update the collection with registry status
                Bson filter = Filters.eq(ApiCollection.ID, apiCollection.getId());
                Bson update = Updates.set(ApiCollection.REGISTRY_STATUS, registryStatus);
                ApiCollectionsDao.instance.updateOne(filter, update);
            }
        } catch (Exception e) {
            logger.error("Error updating registry status for collection: " + apiCollection.getId(), e);
        }
    }



    private List<HttpResponseParams> initializeMcpServerCapabilities(ApiCollection apiCollection, String authHeader) {
        String host = apiCollection.getHostName();
        List<HttpResponseParams> responseParamsList = new ArrayList<>();

        // Always reset to avoid stale data from previous server
        mcpServerCapabilities = null;
        mcpSessionId = null;

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
                host, authHeader, McpSchema.METHOD_INITIALIZE, JSONUtils.getString(initializeRequest), apiCollection);

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

        initNotifications(apiCollection, authHeader);

        return responseParamsList;
    }

    private void initNotifications(ApiCollection apiCollection, String authHeader) {
        try {
            JSONRPCRequest initNotificationsRequest = new JSONRPCRequest(
                McpSchema.JSONRPC_VERSION,
                McpSchema.METHOD_NOTIFICATION_INITIALIZED,
                null,
                null
            );

            // no response
            getMcpMethodResponse(
                apiCollection.getHostName(), authHeader, McpSchema.METHOD_NOTIFICATION_INITIALIZED,
                JSONUtils.getString(initNotificationsRequest), apiCollection);

        } catch (Exception e) {
            logger.error("Error while initializing notifications for MCP server for hostname: {}",
                apiCollection.getHostName(), e);
        }
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

        if (!McpSchema.METHOD_NOTIFICATION_INITIALIZED.equals(mcpMethod)) {
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

        logger.info("No response for {} method. skipping....", mcpMethod);

        return new Pair<>(new JSONRPCResponse(
            McpSchema.JSONRPC_VERSION, null, Collections.emptyMap(), null
        ), null);
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
        return new OriginalHttpRequest(path,
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

        // Add mcp-session-id if available
        String sessionHeader = "";
        if (StringUtils.isNoneBlank(mcpSessionId)) {
            sessionHeader = ",\"mcp-session-id\":\"" + mcpSessionId + "\"";
        }

        return "{\"Content-Type\":\"application/json\",\"Accept\":\"*/*\",\"host\":\"" + host + "\"" + authHeader + sessionHeader + "}";
    }

    private String sendRequest(OriginalHttpRequest request, ApiCollection apiCollection) throws Exception {
        try {
            String transportType = apiCollection.getMcpTransportType();

            // If transport type is not set, try to detect it
            if (transportType == null || transportType.isEmpty()) {
                transportType = detectAndSetTransportType(request, apiCollection);
            }

            OriginalHttpResponse response;
            if (TRANSPORT_HTTP.equals(transportType)) {
                // Use standard HTTP POST for streamable responses
                // Use sendRequestSkipSse to prevent ApiExecutor from trying SSE
                logger.info("Using HTTP transport for MCP server: {}", apiCollection.getHostName());

                // Add Accept header for HTTP transport to support both JSON and event-stream responses
                Map<String, List<String>> headers = request.getHeaders();
                if (headers == null) {
                    headers = new HashMap<>();
                    request.setHeaders(headers);
                }
                headers.put(HttpRequestResponseUtils.HEADER_ACCEPT, Collections.singletonList(
                    HttpRequestResponseUtils.APPLICATION_JSON + ","
                        + HttpRequestResponseUtils.TEXT_EVENT_STREAM_CONTENT_TYPE));

                response = ApiExecutor.sendRequestSkipSse(request, true, null, false, new ArrayList<>(), false);

                // Extract session ID from response headers if present
                String sessionId = HttpRequestResponseUtils.getHeaderValue(response.getHeaders(), "mcp-session-id");
                if (StringUtils.isNotBlank(sessionId)) {
                    mcpSessionId = sessionId;
                    logger.info("Extracted mcp-session-id from response");
                } else {
                    logger.info("No mcp sessionId found. skipping adding mcp session id in subsequent requests");
                }

                // Check if response is text/event-stream and extract JSON-RPC from it
                String contentType = HttpRequestResponseUtils.getHeaderValue(response.getHeaders(), HttpRequestResponseUtils.CONTENT_TYPE);
                if (contentType != null && contentType.toLowerCase().contains(HttpRequestResponseUtils.TEXT_EVENT_STREAM_CONTENT_TYPE)) {
                    logger.info("Received text/event-stream response, extracting JSON-RPC message");
                    String parsedResponse = McpRequestResponseUtils.parseResponse(contentType, response.getBody());
                    return extractFirstDataFromParsedSse(parsedResponse);
                }
            } else {
                // Use SSE transport
                logger.info("Using SSE transport for MCP server: {}", apiCollection.getHostName());
                McpSseEndpointHelper.addSseEndpointHeader(request, apiCollection.getId());
                response = ApiExecutor.sendRequestWithSse(request, true, null, false,
                    new ArrayList<>(), false, true);
            }
            return response.getBody();
        } catch (Exception e) {
            logger.error("Error while making request to MCP server: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    private String extractFirstDataFromParsedSse(String parsedSseJson) {
        try {
            // The McpRequestResponseUtils.parseResponse returns: {"events": [{"data": "..."}, ...]}
            // We need to extract the first event's data field
            BasicDBObject parsed = BasicDBObject.parse(parsedSseJson);
            List<BasicDBObject> events = (List<BasicDBObject>) parsed.get("events");

            if (events == null || events.isEmpty()) {
                logger.warn("No events found in parsed SSE response");
                return "{}";
            }

            String firstData = events.get(0).getString("data");
            if (firstData == null || firstData.isEmpty()) {
                logger.warn("First event has no data");
                return "{}";
            }

            logger.debug("Extracted first data from {} SSE events", events.size());
            return firstData;
        } catch (Exception e) {
            logger.error("Error extracting data from parsed SSE: {}", e.getMessage(), e);
            return "{}";
        }
    }

    private String detectAndSetTransportType(OriginalHttpRequest request, ApiCollection apiCollection) throws Exception {
        // Try SSE first if sseCallbackUrl is set
        if (apiCollection.getSseCallbackUrl() != null && !apiCollection.getSseCallbackUrl().isEmpty()) {
            try {
                logger.info("Attempting to detect transport type for MCP server: {}", apiCollection.getHostName());
                
                // Clone request for SSE detection to avoid modifying original
                OriginalHttpRequest sseTestRequest = request.copy();
                McpSseEndpointHelper.addSseEndpointHeader(sseTestRequest, apiCollection.getId());
                ApiExecutor.sendRequestWithSse(sseTestRequest, true, null, false,
                    new ArrayList<>(), false, true);
                
                // If SSE works, update the collection
                ApiCollectionsDao.instance.updateTransportType(apiCollection, TRANSPORT_SSE);
                logger.info("Detected SSE transport for MCP server: {}", apiCollection.getHostName());
                return TRANSPORT_SSE;
            } catch (Exception sseException) {
                logger.info("SSE transport failed, falling back to HTTP transport: {}", sseException.getMessage());
                // Fall back to HTTP - no need to test, just store it
                ApiCollectionsDao.instance.updateTransportType(apiCollection, TRANSPORT_HTTP);
                return TRANSPORT_HTTP;
            }
        }
        
        // Default to HTTP if no sseCallbackUrl
        logger.info("No SSE callback URL found, using HTTP transport for: {}", apiCollection.getHostName());
        ApiCollectionsDao.instance.updateTransportType(apiCollection, TRANSPORT_HTTP);
        return TRANSPORT_HTTP;
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
