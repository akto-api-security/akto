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
import com.akto.dto.type.URLMethods;
import com.akto.hybrid_parsers.HttpCallParser;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.mcp.McpRequestResponseUtils;
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
import com.akto.util.HttpRequestResponseUtils;
import com.akto.util.JSONUtils;
import com.akto.util.McpSseEndpointHelper;
import com.akto.util.Pair;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.mongodb.BasicDBObject;
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

    public static final String TRANSPORT_SSE = "SSE";
    public static final String TRANSPORT_HTTP = "HTTP";

    public static final String DEFAULT_SSE_CALLBACK_URL = "/sse";
    public static final String DEFAULT_HTTP_CALLBACK_URL = "/mcp";

    public static String mcpSessionId = null;
    public static String mcpTransportType = null;

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

            boolean hasMcpServerTag = tagsList.stream()
                .anyMatch(t -> Constants.AKTO_MCP_SERVER_TAG.equalsIgnoreCase(t.getKeyName()));

            boolean sourceEndpoint = tagsList.stream()
                .anyMatch(t -> "source".equals(t.getKeyName()) && CONTEXT_SOURCE.ENDPOINT.name()
                    .equalsIgnoreCase(t.getValue()));

            if (hasMcpServerTag && !sourceEndpoint) {
                eligibleCollections.add(apiCollection);
            }
        }

        logger.debug("Found {} collections for MCP server.", eligibleCollections.size());

        if (eligibleCollections.isEmpty()) {
            return;
        }

        eligibleCollections.forEach(apiCollection -> {
            logger.info("Starting MCP sync for apiCollectionId: {} and hostname: {}", apiCollection.getId(),
                apiCollection.getHostName());
            try {
                // reset mcp server capabilities, session id and transport type to null for each collection for safer side
                mcpServerCapabilities = null;
                mcpSessionId = null;
                mcpTransportType = null;

                Set<String> normalizedSampleDataSet = getNormalizedSampleData(apiCollection.getId());
                List<HttpResponseParams> initResponseList = initializeMcpServerCapabilities(apiCollection,
                    normalizedSampleDataSet);
                List<HttpResponseParams> toolsResponseList = handleMcpToolsDiscovery(apiCollection,
                    normalizedSampleDataSet);
                List<HttpResponseParams> resourcesResponseList = handleMcpResourceDiscovery(apiCollection,
                    normalizedSampleDataSet);
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

        initNotifications(apiCollection);

        return responseParamsList;
    }

    private void initNotifications(ApiCollection apiCollection) {
        try {
            JSONRPCRequest initNotificationsRequest = new JSONRPCRequest(
                McpSchema.JSONRPC_VERSION,
                McpSchema.METHOD_NOTIFICATION_INITIALIZED,
                null,
                null
            );

            // no response - just for init purpose so that mcp server doesn't fail the subsequent requests
            getMcpMethodResponse(
                apiCollection.getHostName(), McpSchema.METHOD_NOTIFICATION_INITIALIZED,
                JSONUtils.getString(initNotificationsRequest), apiCollection);

        } catch (Exception e) {
            logger.error("Error while initializing notifications for MCP server for hostname: {}",
                apiCollection.getHostName(), e);
        }
    }

    private List<HttpResponseParams> handleMcpToolsDiscovery(ApiCollection apiCollection,
        Set<String> normalizedSampleDataSet) {
        String host = apiCollection.getHostName();

        List<HttpResponseParams> responseParamsList = new ArrayList<>();
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
                String toolsCallRequestHeaders = buildHeaders(host);

                for (Tool tool : toolsResult.getTools()) {
                    if (normalizedSampleDataSet.contains(McpSchema.METHOD_TOOLS_CALL + "/" + tool.getName())) {
                        logger.debug("Skipping tool {} as it is already present in the db.", tool.getName());
                        continue;
                    }
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

    private List<HttpResponseParams> handleMcpResourceDiscovery(ApiCollection apiCollection,
        Set<String> normalizedSampleDataSet) {
        String host = apiCollection.getHostName();

        List<HttpResponseParams> responseParamsList = new ArrayList<>();
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
                String toolsCallRequestHeaders = buildHeaders(host);

                for (Resource resource : resourcesResult.getResources()) {
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

    @SuppressWarnings("rawtypes")
    public static Map<String, Object> generateExampleArguments(JsonSchema inputSchema) {
        if (inputSchema == null) {
            return Collections.emptyMap();
        }
        try {
            String inputSchemaJson = mapper.writeValueAsString(inputSchema);
            Schema<?> openApiSchema = io.swagger.v3.core.util.Json.mapper().readValue(inputSchemaJson, Schema.class);
            Map<String, Schema> schemaDefinitions = buildSchemaDefinitions(inputSchema);
            Example example = ExampleBuilder.fromSchema(openApiSchema, schemaDefinitions);
            return JSONUtils.getMap(Json.pretty(example));

        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Failed to generate example arguments using OpenAPI ExampleBuilder. message: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("rawtypes")
    private static Map<String, Schema> buildSchemaDefinitions(JsonSchema inputSchema) {
        Map<String, Schema> schemaDefinitions = new HashMap<>();
        addSchemaDefinitions(schemaDefinitions, inputSchema.getDefs());
        addSchemaDefinitions(schemaDefinitions, inputSchema.getDefinitions());
        return schemaDefinitions;
    }

    @SuppressWarnings("rawtypes")
    private static void addSchemaDefinitions(Map<String, Schema> schemaDefinitions, Map<String, Object> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : definitions.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            try {
                String schemaJson = mapper.writeValueAsString(entry.getValue());
                Schema<?> schema = io.swagger.v3.core.util.Json.mapper().readValue(schemaJson, Schema.class);
                schemaDefinitions.put(entry.getKey(), schema);
            } catch (Exception e) {
                logger.errorAndAddToDb(e, "Failed to parse schema definition for key: " + entry.getKey() + ". message: " + e.getMessage());
            }
        }
    }

    public Pair<JSONRPCResponse, HttpResponseParams> getMcpMethodResponse(String host, String mcpMethod,
        String mcpMethodRequestJson, ApiCollection apiCollection) throws Exception {
        OriginalHttpRequest mcpRequest = null;
        if (StringUtils.isBlank(mcpTransportType)) {
            mcpRequest = createRequest(host, mcpMethod, mcpMethodRequestJson, apiCollection);
            mcpTransportType = detectAndSetTransportType(mcpRequest, apiCollection);
        } else {
            mcpRequest = createRequest(host, mcpMethod, mcpMethodRequestJson, apiCollection);
        }
        String jsonrpcResponse = sendRequest(mcpRequest, apiCollection);

        // no response for notification initialized. So return empty response.
        if (McpSchema.METHOD_NOTIFICATION_INITIALIZED.equals(mcpMethod)) {
            return new Pair<>(new JSONRPCResponse(
                McpSchema.JSONRPC_VERSION, null, Collections.emptyMap(), null
            ), null);
        }

        if (McpSchema.METHOD_TOOLS_LIST.equals(mcpMethod)) {
            jsonrpcResponse = "{\n\t\"result\": {\n\t\t\"tools\": [\n\t\t\t{\n\t\t\t\t\"outputSchema\": {\n\t\t\t\t\t\"type\": \"object\", \n\t\t\t\t\t\"additionalProperties\": true\n\t\t\t\t}, \n\t\t\t\t\"description\": \"Search for customer quote settings using flexible filter criteria.\\n\\nUse this tool when you need to:\\n- Find customer quote settings by EAR codes (customer identifiers).\\n- Locate the default customer quote settings.\\n- Find customer quote settings with specific feature flags enabled.\\n- Identify inactive customer quote settings for cleanup or auditing.\\n- List all active customer configurations.\\n\\nArgs:\\n    search: Search criteria including:\\n        - filtering: List of filter groups (AND/OR conditions).\\n        - paging: Page number and size for pagination.\\n        - sorting: Optional field and direction for ordering results.\\n\\nReturns:\\n    Success response with structure:\\n    {\\n        \\\"success\\\": true,\\n        \\\"data\\\": {\\n            \\\"results\\\": [{\\n                \\\"id\\\": \\\"uuid-string\\\",\\n                \\\"ears\\\": [\\\"EAR123\\\", \\\"EAR456\\\"],\\n                \\\"is_active\\\": true,\\n                \\\"is_default\\\": false,\\n                \\\"current_version\\\": 5,\\n                \\\"created_date\\\": \\\"2024-01-01T00:00:00Z\\\",\\n                \\\"admin\\\": {...}\\n            }],\\n            \\\"total_count\\\": 42,\\n            \\\"page_number\\\": 1,\\n            \\\"page_size\\\": 100\\n        }\\n    }\\n    Or error response: {\\\"success\\\": false, \\\"error\\\": \\\"error message\\\"}\\n\\nExamples:\\n    # Find settings by EAR code\\n    {\\n        \\\"search\\\": {\\n            \\\"filtering\\\": [{\\n                \\\"condition\\\": \\\"AND\\\",\\n                \\\"fields\\\": [{\\n                    \\\"field\\\": \\\"ears\\\",\\n                    \\\"operator\\\": \\\"in\\\",\\n                    \\\"value\\\": [\\\"EAR123\\\"]\\n                }]\\n            }]\\n        }\\n    }\\n\\n    # Find all active settings\\n    {\\n        \\\"search\\\": {\\n            \\\"filtering\\\": [{\\n                \\\"condition\\\": \\\"AND\\\",\\n                \\\"fields\\\": [{\\n                    \\\"field\\\": \\\"is_active\\\",\\n                    \\\"operator\\\": \\\"eq\\\",\\n                    \\\"value\\\": true\\n                }]\\n            }]\\n        }\\n    }\\n\\nNote:\\n    - Multiple filter fields create AND conditions within a group.\\n    - Use filtering groups with OR operator for complex queries.\\n    - Default page size is 100, maximum is 1000.\\n    - Results are sorted by created_date descending by default.\", \n\t\t\t\t\"_meta\": {\n\t\t\t\t\t\"_fastmcp\": {\n\t\t\t\t\t\t\"tags\": [\n\t\t\t\t\t\t\t\"cqs\", \n\t\t\t\t\t\t\t\"read\", \n\t\t\t\t\t\t\t\"safe\", \n\t\t\t\t\t\t\t\"search\"\n\t\t\t\t\t\t]\n\t\t\t\t\t}, \n\t\t\t\t\t\"readOnly\": true, \n\t\t\t\t\t\"usage\": \"search\"\n\t\t\t\t}, \n\t\t\t\t\"inputSchema\": {\n\t\t\t\t\t\"required\": [\n\t\t\t\t\t\t\"search\"\n\t\t\t\t\t], \n\t\t\t\t\t\"type\": \"object\", \n\t\t\t\t\t\"$defs\": {\n\t\t\t\t\t\t\"AdminConfigurationSearchInput\": {\n\t\t\t\t\t\t\t\"description\": \"Admin Configuration Search Input Model.\", \n\t\t\t\t\t\t\t\"type\": \"object\", \n\t\t\t\t\t\t\t\"properties\": {\n\t\t\t\t\t\t\t\t\"channels_enabled\": {\n\t\t\t\t\t\t\t\t\t\"default\": null, \n\t\t\t\t\t\t\t\t\t\"anyOf\": [\n\t\t\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\t\t\"type\": \"boolean\"\n\t\t\t\t\t\t\t\t\t\t}, \n\t\t\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\t\t\"type\": \"null\"\n\t\t\t\t\t\t\t\t\t\t}\n\t\t\t\t\t\t\t\t\t]\n\t\t\t\t\t\t\t\t}, \n\t\t\t\t\t\t\t\t\"mp_variance_to_dat_enabled\": {\n\t\t\t\t\t\t\t\t\t\"default\": null, \n\t\t\t\t\t\t\t\t\t\"anyOf\": [\n\t\t\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\t\t\"type\": \"boolean\"\n\t\t\t\t\t\t\t\t\t\t}, \n\t\t\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\t\t\"type\": \"null\"\n\t\t\t\t\t\t\t\t\t\t}\n\t\t\t\t\t\t\t\t\t]\n\t\t\t\t\t\t\t\t}, \n\t\t\t\t\t\t\t\t\"mileage_band_enabled\": {\n\t\t\t\t\t\t\t\t\t\"default\": null, \n\t\t\t\t\t\t\t\t\t\"anyOf\": [\n\t\t\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\t\t\"type\": \"boolean\"\n\t\t\t\t\t\t\t\t\t\t}, \n\t\t\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\t\t\"type\": \"null\"\n\t\t\t\t\t\t\t\t\t\t}\n\t\t\t\t\t\t\t\t\t]\n\t\t\t\t\t\t\t\t}, \n\t\t\t\t\t\t\t\t\"bid_interaction_type_enabled\": {\n\t\t\t\t\t\t\t\t\t\"default\": null, \n\t\t\t\t\t\t\t\t\t\"anyOf\": [\n\t\t\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\t\t\"type\": \"boolean\"\n\t\t\t\t\t\t\t\t\t\t}, \n\t\t\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\t\t\"type\": \"null\"\n\t\t\t\t\t\t\t\t\t\t}\n\t\t\t\t\t\t\t\t\t]\n\t\t\t\t\t\t\t\t}, \n\t\t\t\t\t\t\t\t\"mp_target_margin_enabled\": {\n\t\t\t\t\t\t\t\t\t\"default\": null, \n\t\t\t\t\t\t\t\t\t\"anyOf\": [\n\t\t\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\t\t\"type\": \"boolean\"\n\t\t\t\t\t\t\t\t\t\t}, \n\t\t\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\t\t\"type\": \"null\"\n\t\t\t\t\t\t\t\t\t\t}\n\t\t\t\t\t\t\t\t\t]\n\t\t\t\t\t\t\t\t}, \n\t\t\t\t\t\t\t\t\"platforms_enabled\": {\n\t\t\t\t\t\t\t\t\t\"default\": null, \n\t\t\t\t\t\t\t\t\t\"anyOf\": [\n\t\t\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\t\t\"type\": \"boolean\"\n\t\t\t\t\t\t\t\t\t\t}, \n\t\t\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\t\t\"type\": \"null\"\n\t\t\t\t\t\t\t\t\t\t}\n\t\t\t\t\t\t\t\t\t]\n\t\t\t\t\t\t\t\t}, \n\t\t\t\t\t\t\t\t\"rate_incrementation_enabled\": {\n\t\t\t\t\t\t\t\t\t\"default\": null, \n\t\t\t\t\t\t\t\t\t\"anyOf\": [\n\t\t\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\t\t\"type\": \"boolean\"\n\t\t\t\t\t\t\t\t\t\t}, \n\t\t\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\t\t\"type\": \"null\"\n\t\t\t\t\t\t\t\t\t\t}\n\t\t\t\t\t\t\t\t\t]\n\t\t\t\t\t\t\t\t}, \n\t\t\t\t\t\t\t\t\"floor_ceiling_enabled\": {\n\t\t\t\t\t\t\t\t\t\"default\": null, \n\t\t\t\t\t\t\t\t\t\"anyOf\": [\n\t\t\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\t\t\"type\": \"boolean\"\n\t\t\t\t\t\t\t\t\t\t}, \n\t\t\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\t\t\"type\": \"null\"\n\t\t\t\t\t\t\t\t\t\t}\n\t\t\t\t\t\t\t\t\t]\n\t\t\t\t\t\t\t\t}\n\t\t\t\t\t\t\t}\n\t\t\t\t\t\t}, \n\t\t\t\t\t\t\"CustomerQuoteSettingsSearchInput\": {\n\t\t\t\t\t\t\t\"description\": \"Customer Quote Settings Search Request Model.\\n\\nInclude only the field(s) that you want to search for. If multiple\\nfields are included, the search will be an AND operation.\", \n\t\t\t\t\t\t\t\"type\": \"object\", \n\t\t\t\t\t\t\t\"properties\": {\n\t\t\t\t\t\t\t\t\"is_active\": {\n\t\t\t\t\t\t\t\t\t\"default\": null, \n\t\t\t\t\t\t\t\t\t\"anyOf\": [\n\t\t\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\t\t\"type\": \"boolean\"\n\t\t\t\t\t\t\t\t\t\t}, \n\t\t\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\t\t\"type\": \"null\"\n\t\t\t\t\t\t\t\t\t\t}\n\t\t\t\t\t\t\t\t\t]\n\t\t\t\t\t\t\t\t}, \n\t\t\t\t\t\t\t\t\"id\": {\n\t\t\t\t\t\t\t\t\t\"default\": null, \n\t\t\t\t\t\t\t\t\t\"anyOf\": [\n\t\t\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\t\t\"format\": \"uuid\", \n\t\t\t\t\t\t\t\t\t\t\t\"type\": \"string\"\n\t\t\t\t\t\t\t\t\t\t}, \n\t\t\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\t\t\"type\": \"null\"\n\t\t\t\t\t\t\t\t\t\t}\n\t\t\t\t\t\t\t\t\t]\n\t\t\t\t\t\t\t\t}, \n\t\t\t\t\t\t\t\t\"admin_config\": {\n\t\t\t\t\t\t\t\t\t\"default\": null, \n\t\t\t\t\t\t\t\t\t\"anyOf\": [\n\t\t\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\t\t\"$ref\": \"#/$defs/AdminConfigurationSearchInput\"\n\t\t\t\t\t\t\t\t\t\t}, \n\t\t\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\t\t\"type\": \"null\"\n\t\t\t\t\t\t\t\t\t\t}\n\t\t\t\t\t\t\t\t\t]\n\t\t\t\t\t\t\t\t}, \n\t\t\t\t\t\t\t\t\"ears\": {\n\t\t\t\t\t\t\t\t\t\"default\": null, \n\t\t\t\t\t\t\t\t\t\"anyOf\": [\n\t\t\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\t\t\"items\": {\n\t\t\t\t\t\t\t\t\t\t\t\t\"type\": \"string\", \n\t\t\t\t\t\t\t\t\t\t\t\t\"pattern\": \"^-?[a-zA-Z0-9]{1,15}$\"\n\t\t\t\t\t\t\t\t\t\t\t}, \n\t\t\t\t\t\t\t\t\t\t\t\"type\": \"array\"\n\t\t\t\t\t\t\t\t\t\t}, \n\t\t\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\t\t\"type\": \"null\"\n\t\t\t\t\t\t\t\t\t\t}\n\t\t\t\t\t\t\t\t\t]\n\t\t\t\t\t\t\t\t}, \n\t\t\t\t\t\t\t\t\"is_alt_default\": {\n\t\t\t\t\t\t\t\t\t\"default\": null, \n\t\t\t\t\t\t\t\t\t\"anyOf\": [\n\t\t\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\t\t\"type\": \"boolean\"\n\t\t\t\t\t\t\t\t\t\t}, \n\t\t\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\t\t\"type\": \"null\"\n\t\t\t\t\t\t\t\t\t\t}\n\t\t\t\t\t\t\t\t\t]\n\t\t\t\t\t\t\t\t}, \n\t\t\t\t\t\t\t\t\"is_default\": {\n\t\t\t\t\t\t\t\t\t\"default\": null, \n\t\t\t\t\t\t\t\t\t\"anyOf\": [\n\t\t\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\t\t\"type\": \"boolean\"\n\t\t\t\t\t\t\t\t\t\t}, \n\t\t\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\t\t\"type\": \"null\"\n\t\t\t\t\t\t\t\t\t\t}\n\t\t\t\t\t\t\t\t\t]\n\t\t\t\t\t\t\t\t}, \n\t\t\t\t\t\t\t\t\"alt_default_cqs_id\": {\n\t\t\t\t\t\t\t\t\t\"default\": null, \n\t\t\t\t\t\t\t\t\t\"anyOf\": [\n\t\t\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\t\t\"format\": \"uuid\", \n\t\t\t\t\t\t\t\t\t\t\t\"type\": \"string\"\n\t\t\t\t\t\t\t\t\t\t}, \n\t\t\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\t\t\"type\": \"null\"\n\t\t\t\t\t\t\t\t\t\t}\n\t\t\t\t\t\t\t\t\t]\n\t\t\t\t\t\t\t\t}\n\t\t\t\t\t\t\t}\n\t\t\t\t\t\t}\n\t\t\t\t\t}, \n\t\t\t\t\t\"properties\": {\n\t\t\t\t\t\t\"search\": {\n\t\t\t\t\t\t\t\"$ref\": \"#/$defs/CustomerQuoteSettingsSearchInput\"\n\t\t\t\t\t\t}\n\t\t\t\t\t}\n\t\t\t\t}, \n\t\t\t\t\"name\": \"search_customer_quote_settings\"\n\t\t\t}, \n\t\t\t{\n\t\t\t\t\"outputSchema\": {\n\t\t\t\t\t\"type\": \"object\", \n\t\t\t\t\t\"additionalProperties\": true\n\t\t\t\t}, \n\t\t\t\t\"description\": \"Load complete customer quote settings by ID.\\n\\nUse this tool when you need to:\\n- Examine a customer's complete pricing configuration.\\n- Review pricing strategies for a specific customer.\\n- Inspect feature flags and admin settings.\\n- View historical versions of customer quote settings.\\n- Analyze premiums and price ranges.\\n\\nArgs:\\n    cqs_id: The unique identifier (UUID string) of the customer quote settings.\\n    version: Optional version number to load (omit for current active version).\\n        - Must be a positive integer.\\n        - If specified, loads historical configuration at that version.\\n\\nReturns:\\n    Success response with structure:\\n    {\\n        \\\"success\\\": true,\\n        \\\"data\\\": {\\n            \\\"id\\\": \\\"uuid-string\\\",\\n            \\\"ears\\\": [\\\"EAR123\\\"],\\n            \\\"is_active\\\": true,\\n            \\\"is_default\\\": false,\\n            \\\"current_version\\\": 5,\\n            \\\"admin\\\": {...feature flags...},\\n            \\\"versions\\\": [{\\n                \\\"version\\\": 5,\\n                \\\"pricing_strategies\\\": [...],\\n                \\\"premiums\\\": [...],\\n                \\\"price_ranges\\\": [...]\\n            }]\\n        }\\n    }\\n    Or error response: {\\\"success\\\": false, \\\"error\\\": \\\"error message\\\"}\\n\\nExamples:\\n    # Get the current active version of a CQS\\n    {\\n        \\\"cqs_id\\\": \\\"a1b2c3d4-e5f6-7890-1234-567890abcdef\\\"\\n    }\\n\\n    # Get a specific historical version of a CQS\\n    {\\n        \\\"cqs_id\\\": \\\"a1b2c3d4-e5f6-7890-1234-567890abcdef\\\",\\n        \\\"version\\\": 3\\n    }\\n\\nNote:\\n    - By default, returns the currently active version.\\n    - Historical versions are immutable and preserved for audit.\\n    - Customer quote settings include ALL pricing strategies, premiums, and price ranges.\\n    - Invalid UUID format will return an error.\\n    - Non-existent customer quote settings or version will return DoesNotExistError.\", \n\t\t\t\t\"_meta\": {\n\t\t\t\t\t\"_fastmcp\": {\n\t\t\t\t\t\t\"tags\": [\n\t\t\t\t\t\t\t\"cqs\", \n\t\t\t\t\t\t\t\"get\", \n\t\t\t\t\t\t\t\"read\", \n\t\t\t\t\t\t\t\"safe\"\n\t\t\t\t\t\t]\n\t\t\t\t\t}, \n\t\t\t\t\t\"readOnly\": true, \n\t\t\t\t\t\"usage\": \"read\"\n\t\t\t\t}, \n\t\t\t\t\"inputSchema\": {\n\t\t\t\t\t\"required\": [\n\t\t\t\t\t\t\"cqs_id\"\n\t\t\t\t\t], \n\t\t\t\t\t\"type\": \"object\", \n\t\t\t\t\t\"properties\": {\n\t\t\t\t\t\t\"version\": {\n\t\t\t\t\t\t\t\"default\": null, \n\t\t\t\t\t\t\t\"anyOf\": [\n\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\"type\": \"integer\"\n\t\t\t\t\t\t\t\t}, \n\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\"type\": \"null\"\n\t\t\t\t\t\t\t\t}\n\t\t\t\t\t\t\t]\n\t\t\t\t\t\t}, \n\t\t\t\t\t\t\"cqs_id\": {\n\t\t\t\t\t\t\t\"type\": \"string\"\n\t\t\t\t\t\t}\n\t\t\t\t\t}\n\t\t\t\t}, \n\t\t\t\t\"name\": \"get_customer_quote_settings\"\n\t\t\t}, \n\t\t\t{\n\t\t\t\t\"outputSchema\": {\n\t\t\t\t\t\"type\": \"object\", \n\t\t\t\t\t\"additionalProperties\": true\n\t\t\t\t}, \n\t\t\t\t\"description\": \"Load the details of a specific pricing strategy from customer quote settings.\\n\\nUse this tool when you need to:\\n- Examine how a specific pricing strategy is configured.\\n- Review matching criteria for a particular rule.\\n- Check floor and ceiling rate settings.\\n- Inspect associated premiums and price ranges.\\n- View historical strategy configuration from past versions.\\n\\nArgs:\\n    cqs_id: The unique identifier (UUID string) of the quote settings.\\n    strategy_id: The unique identifier (UUID string) of the strategy.\\n    version: Optional version number to load from (omit for current active).\\n        - Must be a positive integer.\\n        - If specified, retrieves strategy from that historical version.\\n\\nReturns:\\n    Success response with structure:\\n    {\\n        \\\"success\\\": true,\\n        \\\"data\\\": {\\n            \\\"id\\\": \\\"uuid-string\\\",\\n            \\\"quote_characteristics\\\": {...},\\n            \\\"pricing_strategy_details\\\": {...},\\n            ...\\n        }\\n    }\\n    Or error response: {\\\"success\\\": false, \\\"error\\\": \\\"error message\\\"}\\n\\nExamples:\\n    # Get a strategy from the current active version\\n    {\\n        \\\"cqs_id\\\": \\\"a1b2c3d4-e5f6-7890-1234-567890abcdef\\\",\\n        \\\"strategy_id\\\": \\\"b1c2d3e4-f5g6-7890-1234-567890abcdef\\\"\\n    }\\n\\n    # Get a strategy from a specific historical version\\n    {\\n        \\\"cqs_id\\\": \\\"a1b2c3d4-e5f6-7890-1234-567890abcdef\\\",\\n        \\\"strategy_id\\\": \\\"c1d2e3f4-g5h6-7890-1234-567890abcdef\\\",\\n        \\\"version\\\": 2\\n    }\\n\\nNote:\\n    - By default, retrieves from currently active version.\\n    - Strategy may not exist in all versions (returns error if not found).\\n    - Invalid UUID format will return an error.\\n    - DoesNotExistError if customer quote settings, version, or strategy not found.\\n    - Historical versions are immutable.\", \n\t\t\t\t\"_meta\": {\n\t\t\t\t\t\"_fastmcp\": {\n\t\t\t\t\t\t\"tags\": [\n\t\t\t\t\t\t\t\"cqs\", \n\t\t\t\t\t\t\t\"get\", \n\t\t\t\t\t\t\t\"read\", \n\t\t\t\t\t\t\t\"safe\", \n\t\t\t\t\t\t\t\"strategy\"\n\t\t\t\t\t\t]\n\t\t\t\t\t}, \n\t\t\t\t\t\"readOnly\": true, \n\t\t\t\t\t\"usage\": \"read\"\n\t\t\t\t}, \n\t\t\t\t\"inputSchema\": {\n\t\t\t\t\t\"required\": [\n\t\t\t\t\t\t\"cqs_id\", \n\t\t\t\t\t\t\"strategy_id\"\n\t\t\t\t\t], \n\t\t\t\t\t\"type\": \"object\", \n\t\t\t\t\t\"properties\": {\n\t\t\t\t\t\t\"version\": {\n\t\t\t\t\t\t\t\"default\": null, \n\t\t\t\t\t\t\t\"anyOf\": [\n\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\"type\": \"integer\"\n\t\t\t\t\t\t\t\t}, \n\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\"type\": \"null\"\n\t\t\t\t\t\t\t\t}\n\t\t\t\t\t\t\t]\n\t\t\t\t\t\t}, \n\t\t\t\t\t\t\"cqs_id\": {\n\t\t\t\t\t\t\t\"type\": \"string\"\n\t\t\t\t\t\t}, \n\t\t\t\t\t\t\"strategy_id\": {\n\t\t\t\t\t\t\t\"type\": \"string\"\n\t\t\t\t\t\t}\n\t\t\t\t\t}\n\t\t\t\t}, \n\t\t\t\t\"name\": \"get_pricing_strategy\"\n\t\t\t}, \n\t\t\t{\n\t\t\t\t\"outputSchema\": {\n\t\t\t\t\t\"type\": \"object\", \n\t\t\t\t\t\"additionalProperties\": true\n\t\t\t\t}, \n\t\t\t\t\"description\": \"Run a health check on customer quote settings to diagnose potential issues.\\n\\nUse this tool when you need to:\\n- Identify missing or incomplete configuration.\\n- Find duplicate or conflicting pricing strategies.\\n- Detect feature flag mismatches (e.g., floor rates used but disabled).\\n- Validate customer quote settings before deploying to production.\\n- Troubleshoot pricing issues reported by users.\\n\\nArgs:\\n    cqs_id: The unique identifier (UUID string) of the customer quote settings to analyze.\\n    version: Optional version number to analyze (omit for current active).\\n        - Must be a positive integer.\\n        - If specified, analyzes that historical version.\\n\\nReturns:\\n    Success response with structure:\\n    {\\n        \\\"success\\\": true,\\n        \\\"data\\\": {\\n            \\\"status\\\": \\\"ok\\\" | \\\"review\\\" | \\\"action_required\\\",\\n            \\\"context\\\": {\\n                \\\"cqs_id\\\": \\\"uuid-string\\\"\\n            },\\n            \\\"summary\\\": {\\n                \\\"ears\\\": [\\\"EAR123\\\"],\\n                \\\"is_active\\\": true,\\n                \\\"current_version\\\": 5,\\n                \\\"evaluated_version\\\": 5,\\n                \\\"pricing_strategy_count\\\": 12,\\n                \\\"premium_count\\\": 5,\\n                \\\"price_range_count\\\": 3\\n            },\\n            \\\"findings\\\": [\\n                {\\n                    \\\"severity\\\": \\\"error\\\" | \\\"warning\\\" | \\\"info\\\",\\n                    \\\"code\\\": \\\"error_code\\\",\\n                    \\\"message\\\": \\\"Human-readable description\\\"\\n                }\\n            ]\\n        }\\n    }\\n    Or error response: {\\\"success\\\": false, \\\"error\\\": \\\"error message\\\"}\\n\\nExamples:\\n    # Triage the current active version\\n    {\\n        \\\"cqs_id\\\": \\\"a1b2c3d4-e5f6-7890-1234-567890abcdef\\\"\\n    }\\n\\nNote:\\n    - Status \\\"ok\\\": No issues found, customer quote settings are healthy.\\n    - Status \\\"review\\\": Warnings found, should be reviewed.\\n    - Status \\\"action_required\\\": Errors found, requires immediate attention.\\n    - Checks performed:\\n        * Customer quote settings active status\\n        * Presence of pricing strategies\\n        * Presence of premiums and price ranges\\n        * Version integrity\\n        * Missing configurations\\n    - By default, analyzes the currently active version.\\n    - Invalid UUID format will return an error.\\n    - DoesNotExistError if customer quote settings or version not found.\", \n\t\t\t\t\"_meta\": {\n\t\t\t\t\t\"_fastmcp\": {\n\t\t\t\t\t\t\"tags\": [\n\t\t\t\t\t\t\t\"cqs\", \n\t\t\t\t\t\t\t\"diagnostic\", \n\t\t\t\t\t\t\t\"read\", \n\t\t\t\t\t\t\t\"safe\", \n\t\t\t\t\t\t\t\"triage\"\n\t\t\t\t\t\t]\n\t\t\t\t\t}, \n\t\t\t\t\t\"readOnly\": true, \n\t\t\t\t\t\"usage\": \"read\"\n\t\t\t\t}, \n\t\t\t\t\"inputSchema\": {\n\t\t\t\t\t\"required\": [\n\t\t\t\t\t\t\"cqs_id\"\n\t\t\t\t\t], \n\t\t\t\t\t\"type\": \"object\", \n\t\t\t\t\t\"properties\": {\n\t\t\t\t\t\t\"version\": {\n\t\t\t\t\t\t\t\"default\": null, \n\t\t\t\t\t\t\t\"anyOf\": [\n\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\"type\": \"integer\"\n\t\t\t\t\t\t\t\t}, \n\t\t\t\t\t\t\t\t{\n\t\t\t\t\t\t\t\t\t\"type\": \"null\"\n\t\t\t\t\t\t\t\t}\n\t\t\t\t\t\t\t]\n\t\t\t\t\t\t}, \n\t\t\t\t\t\t\"cqs_id\": {\n\t\t\t\t\t\t\t\"type\": \"string\"\n\t\t\t\t\t\t}\n\t\t\t\t\t}\n\t\t\t\t}, \n\t\t\t\t\"name\": \"triage_customer_quote_settings\"\n\t\t\t}\n\t\t]\n\t}, \n\t\"id\": 1, \n\t\"jsonrpc\": \"2.0\"\n}";
        }

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

    private OriginalHttpRequest createRequest(String host, String mcpMethod, String mcpMethodRequestJson, ApiCollection apiCollection) {
        String mcpEndpoint = apiCollection.getSseCallbackUrl();

        if (StringUtils.isBlank(mcpEndpoint)) {
            mcpEndpoint = TRANSPORT_HTTP.equals(mcpTransportType) ? DEFAULT_HTTP_CALLBACK_URL : DEFAULT_SSE_CALLBACK_URL;
        }
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
            URLMethods.Method.POST.name(),
            mcpMethodRequestJson,
            OriginalHttpRequest.buildHeadersMap(buildHeaders(host)),
            "HTTP/1.1"
        );
    }
    
    private String buildHeaders(String host) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("accept", "*/*");
        headers.put("host", host);

        // Add mcp-session-id if available
        if (StringUtils.isNoneBlank(mcpSessionId)) {
            headers.put("mcp-session-id", mcpSessionId);
        }

        if (TRANSPORT_HTTP.equalsIgnoreCase(mcpTransportType)) {
            headers.put(HttpRequestResponseUtils.HEADER_ACCEPT,
                HttpRequestResponseUtils.APPLICATION_JSON + ","
                    + HttpRequestResponseUtils.TEXT_EVENT_STREAM_CONTENT_TYPE);
        }

        return JSONUtils.getString(headers);
    }

    private String sendRequest(OriginalHttpRequest request, ApiCollection apiCollection) throws Exception {
        try {

            OriginalHttpResponse response;
            if (TRANSPORT_HTTP.equals(mcpTransportType)) {
                // Use standard HTTP POST for streamable responses
                // Use sendRequestSkipSse to prevent ApiExecutor from trying SSE
                logger.infoAndAddToDb("Using HTTP transport for MCP server: " + apiCollection.getHostName());

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
                    logger.infoAndAddToDb("Extracted mcp-session-id from response");
                } else {
                    logger.infoAndAddToDb("No mcp sessionId found. skipping adding mcp session id in subsequent requests");
                }

                // Check if response is text/event-stream and extract JSON-RPC from it
                String contentType = HttpRequestResponseUtils.getHeaderValue(response.getHeaders(), HttpRequestResponseUtils.CONTENT_TYPE);
                if (contentType != null && contentType.toLowerCase().contains(HttpRequestResponseUtils.TEXT_EVENT_STREAM_CONTENT_TYPE)) {
                    logger.infoAndAddToDb("Received text/event-stream response, extracting JSON-RPC message");
                    String parsedResponse = McpRequestResponseUtils.parseResponse(contentType, response.getBody());
                    return extractFirstDataFromParsedSse(parsedResponse);
                }
            } else {
                // Use SSE transport
                logger.infoAndAddToDb("Using SSE transport for MCP server: " + apiCollection.getHostName());
                McpSseEndpointHelper.addSseEndpointHeader(request, apiCollection.getId());
                response = ApiExecutor.sendRequestWithSse(request, true, null, false,
                    new ArrayList<>(), false, true);
            }
            return response.getBody();
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error while making request to MCP server: " + e.getMessage());
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
        // Try SSE first; if sseCallbackUrl was blank, use DEFAULT_SSE_CALLBACK_URL for the probe
        boolean callbackWasEmpty = StringUtils.isBlank(apiCollection.getSseCallbackUrl());
        if (callbackWasEmpty) {
            apiCollection.setSseCallbackUrl(DEFAULT_SSE_CALLBACK_URL);
            request.setUrl(DEFAULT_SSE_CALLBACK_URL);
        }

        String transportType;
        try {
            logger.info("Attempting to detect transport type for MCP server: {}", apiCollection.getHostName());

            // Clone request for SSE detection to avoid modifying original
            OriginalHttpRequest sseTestRequest = request.copy();
            McpSseEndpointHelper.addSseEndpointHeader(sseTestRequest, apiCollection.getId());
            ApiExecutor.sendRequestWithSse(sseTestRequest, true, null, false,
                new ArrayList<>(), false, true);

            // If SSE works, update the collection
            logger.infoAndAddToDb("Detected SSE transport for MCP server: " + apiCollection.getHostName());
            transportType = TRANSPORT_SSE;
        } catch (Exception sseException) {
            logger.infoAndAddToDb("SSE transport failed, falling back to HTTP transport: " + sseException.getMessage());
            // Fall back to HTTP - no need to test, just store it
            if (callbackWasEmpty) {
                apiCollection.setSseCallbackUrl(DEFAULT_HTTP_CALLBACK_URL);
                request.setUrl(DEFAULT_HTTP_CALLBACK_URL);
            }
            transportType = TRANSPORT_HTTP;
        }

        return transportType;
    }
}
