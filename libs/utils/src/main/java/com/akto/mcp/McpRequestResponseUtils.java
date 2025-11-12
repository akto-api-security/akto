package com.akto.mcp;

import com.akto.billing.UsageMetricUtils;
import com.akto.dao.MCollection;
import com.akto.dao.McpAuditInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.McpAuditInfo;
import com.akto.dto.RawApi;
import com.akto.dto.billing.FeatureAccess;
import com.akto.gpt.handlers.gpt_prompts.McpMethodAnalyzer;
import com.akto.gpt.handlers.gpt_prompts.TestExecutorModifier;
import com.akto.jsonrpc.JsonRpcUtils;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.mcp.McpJsonRpcModel.McpParams;
import com.akto.test_editor.TestingUtilsSingleton;
import com.akto.util.HttpRequestResponseUtils;
import com.akto.util.Pair;
import com.akto.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;

import java.util.*;

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

        // Check if this is a JSON-RPC request first
        if (!JsonRpcUtils.isJsonRpcRequest(responseParams)) {
            return responseParams;
        }

        // Check if response is a JSON-RPC error - if yes, discard it
        // This handles both recognized MCP methods and unrecognized/typo methods
        // This handles both JSON and event-stream responses
        if (isMcpErrorResponse(responseParams)) {
            logger.info("Discarding JSON-RPC request with error response");
            return null;
        }

        // Now check if it's a recognized MCP method for special handling
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

    public static boolean isMcpRequest(RawApi rawApi) {
        String requestPayload = rawApi.getRequest().getBody();
        if(StringUtils.isEmpty(requestPayload)) {
            return false;
        }
        if(!requestPayload.contains(JsonRpcUtils.JSONRPC_KEY)) {
            return false;
        }
        McpJsonRpcModel mcpJsonRpcModel = JsonUtils.fromJson(requestPayload, McpJsonRpcModel.class);
        if(mcpJsonRpcModel == null) {
            return false;
        }
        return McpSchema.MCP_METHOD_SET.contains(mcpJsonRpcModel.getMethod());
    }

    private static boolean isMcpErrorResponse(HttpResponseParams responseParams) {
        String responsePayload = responseParams.getPayload();
        if (responsePayload == null || responsePayload.isEmpty()) {
            return false;
        }

        String contentType = HttpRequestResponseUtils.getHeaderValue(
            responseParams.getHeaders(), HttpRequestResponseUtils.CONTENT_TYPE
        );

        String jsonToCheck = responsePayload;

        if (contentType != null && contentType.toLowerCase().contains(HttpRequestResponseUtils.TEXT_EVENT_STREAM_CONTENT_TYPE)) {
            try {
                String parsedSse = parseResponse(contentType, responsePayload);
                BasicDBObject parsed = BasicDBObject.parse(parsedSse);
                List<BasicDBObject> events = (List<BasicDBObject>) parsed.get("events");

                if (events == null || events.isEmpty()) {
                    return false;
                }

                jsonToCheck = events.get(0).getString("data");
                if (jsonToCheck == null || jsonToCheck.isEmpty()) {
                    return false;
                }
            } catch (Exception e) {
                logger.debug("Error parsing event-stream for MCP error check: " + e.getMessage());
                return false;
            }
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(jsonToCheck);
            if (root == null || !root.isObject()) {
                return true;
            }

            JsonNode jsonrpcNode = root.get(McpSchema.MCP_JSONRPC_KEY);
            if (jsonrpcNode == null || !McpSchema.JSONRPC_VERSION.equals(jsonrpcNode.asText())) {
                return true;
            }

            if (root.has(McpSchema.MCP_ERROR_KEY) && !root.get(McpSchema.MCP_ERROR_KEY).isNull()) {
                return true;
            }

            JsonNode resultNode = root.get(McpSchema.MCP_RESULT_KEY);
            if (resultNode == null || resultNode.isNull() || resultNode.isMissingNode()) {
                return true;
            }

            if (resultNode.isObject() && resultNode.has("isError")) {
                JsonNode isErrorNode = resultNode.get("isError");
                return isErrorNode.isBoolean() && isErrorNode.asBoolean();
            }

            return false;
        } catch (Exception e) {
            logger.debug("Error parsing JSON for MCP error check: " + e.getMessage());
            return false;
        }
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
            setAuditData(auditInfo);
        }
        responseParams.getRequestParams().setUrl(url);
    }

    public static void setAuditData(McpAuditInfo auditInfo) {
        try {
            // Check if record with same type, resourceName, and hostCollectionId already exists
            BasicDBObject findQuery = new BasicDBObject();
            findQuery.put("type", auditInfo.getType());
            findQuery.put("resourceName", auditInfo.getResourceName());
           // findQuery.put("hostCollectionId", auditInfo.getHostCollectionId());  //removing this check for now to avoid auditing same mcp servers from different hosts

            McpAuditInfo existingRecord = McpAuditInfoDao.instance.findOne(findQuery);

            if (existingRecord != null) {
                // Update the existing record with new lastDetected timestamp
                BasicDBObject update = new BasicDBObject();
                update.put(MCollection.SET, new BasicDBObject("lastDetected", Context.now()));

                McpAuditInfoDao.instance.updateOne(findQuery, update);
                logger.info("Updated existing MCP audit record for type: " + auditInfo.getType() +
                           ", resourceName: " + auditInfo.getResourceName());
            } else {
                // Insert new record
                McpAuditInfoDao.instance.insertOne(auditInfo);
                logger.info("Inserted new MCP audit record for type: " + auditInfo.getType() +
                           ", resourceName: " + auditInfo.getResourceName());
            }
        } catch (Exception e) {
            logger.error("Error handling MCP audit data log", e);
        }
    }
    public static BasicDBObject removeMcpRelatedParams(BasicDBObject reqObj) {
        if (reqObj == null) {
            return new BasicDBObject();
        }
        
        BasicDBObject cleanedObj = new BasicDBObject();
        for (String key : reqObj.keySet()) {
            if (isMcpRelatedField(key)) {
                continue;
            } else if ("params".equals(key)) {
                Object paramsValue = reqObj.get(key);
                if (paramsValue instanceof BasicDBObject) {
                    BasicDBObject paramsObj = (BasicDBObject) paramsValue;
                    BasicDBObject cleanedParams = new BasicDBObject();
                    for (String paramKey : paramsObj.keySet()) {
                        if (!"name".equals(paramKey)) {
                            cleanedParams.put(paramKey, paramsObj.get(paramKey));
                        }
                    }
                    cleanedObj.put(key, cleanedParams);
                } else {
                    cleanedObj.put(key, paramsValue);
                }
            } else {
                cleanedObj.put(key, reqObj.get(key));
            }
        }
        
        return cleanedObj;
    }
    
    private static boolean isMcpRelatedField(String fieldName) {
        return "jsonrpc".equals(fieldName) || 
               "method".equals(fieldName) || 
               "id".equals(fieldName);
    }

    public static String analyzeMcpRequestMethod(ApiInfo.ApiInfoKey apiInfoKey, String requestBody) {
        if (apiInfoKey == null || requestBody == null) {
            return "POST";
        }

        FeatureAccess featureAccess = UsageMetricUtils.getFeatureAccessSaas(Context.accountId.get(),
        TestExecutorModifier._AKTO_GPT_AI);
        if (!featureAccess.getIsGranted()) {
            return "POST";
        }
        
        try {
            BasicDBObject queryData = new BasicDBObject();
            queryData.put(TestExecutorModifier._REQUEST, requestBody);
            queryData.put("apiInfoKey", apiInfoKey);
            
            McpMethodAnalyzer analyzer = new McpMethodAnalyzer();
            BasicDBObject response = analyzer.handle(queryData);
            
            if (response != null && response.containsKey("method")) {
                String method = response.getString("method");
                if (method != null && !method.trim().isEmpty()) {
                    return method.toUpperCase();
                }
            }
        } catch (Exception e) {
            // Log error and fallback to POST
            System.err.println("Error analyzing MCP request method: " + e.getMessage());
        }
        
        return "POST";
    }

    public static String parseResponse(String contentType, String body) {
        if (body == null || body.trim().isEmpty()) {
            return "{\"error\":true,\"message\":\"Empty response\"}";
        }

        String trimmed = body.trim();

        try {
            if (contentType != null && contentType.toLowerCase().contains("event-stream")
                    || trimmed.startsWith("data:") || trimmed.contains("event:")) {
                return extractSseJson(trimmed);
            }
            if (trimmed.startsWith("{") && trimmed.endsWith("}")
                    || trimmed.startsWith("[") && trimmed.endsWith("]")) {
                return trimmed; // Already valid JSON
            }
            if (trimmed.toLowerCase().contains("<html") || trimmed.toLowerCase().contains("<!doctype")) {
                return "{\"error\":true,\"message\":\"HTML response\"}";
            }
            return "{\"error\":true,\"message\":\"error message: " + TestingUtilsSingleton.escapeJsonString(trimmed) + "\"}";

        } catch (Exception e) {
            return "{\"error\":true,\"message\":\"error message: " + TestingUtilsSingleton.escapeJsonString(e.getMessage()) + "\"}";
        }
    }

    private static String extractSseJson(String sse) {
        String[] lines = sse.split("\\r?\\n");

        String currentEvent = null;
        String currentId = null;
        Integer currentRetry = null;
        StringBuilder currentData = new StringBuilder();
        List<BasicDBObject> events = new ArrayList<>();

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                // Blank line signifies the end of the current event, push it to the events list
                if (currentData.length() > 0 || currentEvent != null || currentId != null || currentRetry != null) {
                    BasicDBObject eventJson = new BasicDBObject();
                    eventJson.append("data", currentData.toString().trim());
                    events.add(eventJson);
                }
                // Reset for next event
                currentEvent = null;
                currentId = null;
                currentRetry = null;
                currentData.setLength(0); // clear the current data
            } else if (line.startsWith(":")) {
                // Comment line, ignore
                continue;
            } else {
                // Parse the fields
                int idx = line.indexOf(":");
                if (idx != -1) {
                    String field = line.substring(0, idx).trim();
                    String value = line.substring(idx + 1).trim();

                    switch (field) {
                        case "event":
                            currentEvent = value;
                            break;
                        case "id":
                            currentId = value;
                            break;
                        case "retry":
                            try {
                                currentRetry = Integer.parseInt(value);
                            } catch (NumberFormatException ignore) {
                            }
                            break;
                        default:
                            currentData.append(value).append("\n");
                            break;
                    }
                }
            }
        }

        // Final flush for the last event if there was no blank line
        if (currentData.length() > 0 || currentEvent != null || currentId != null || currentRetry != null) {
            BasicDBObject eventJson = new BasicDBObject();
            eventJson.append("data", currentData.toString().trim());
            events.add(eventJson);
        }

        return new BasicDBObject().append("events", events).toJson();
    }

}
