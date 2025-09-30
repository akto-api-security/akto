package com.akto.imperva.parser;

import com.akto.dao.context.Context;
import com.akto.dto.HttpResponseParams.Source;
import com.akto.dto.upload.FileUploadError;
import com.akto.dto.upload.SwaggerUploadLog;
import com.akto.imperva.model.*;
import com.akto.imperva.model.DataTypeDto.ParameterDrillDown;
import com.akto.open_api.parser.ParserResult;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**
 * Parser for Imperva API schema JSON format.
 * Converts Imperva discovered endpoint schema to Akto's internal format.
 *
 * Key concepts:
 * - For XML: Each child element represents a separate API call sample
 * - For JSON: Each element in body's dataTypes array represents a separate sample
 * - Responses are skipped for now (added as generic 200 OK)
 */
public class ImpervaSchemaParser {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ImpervaSchemaParser.class, LogDb.DASHBOARD);
    private static final ObjectMapper mapper = new ObjectMapper();

    // Message keys for akto format
    private interface mKeys {
        String akto_account_id = "akto_account_id";
        String path = "path";
        String method = "method";
        String requestHeaders = "requestHeaders";
        String requestPayload = "requestPayload";
        String responseHeaders = "responseHeaders";
        String responsePayload = "responsePayload";
        String status = "status";
        String statusCode = "statusCode";
        String ip = "ip";
        String time = "time";
        String type = "type";
        String source = "source";
    }

    /**
     * Converts Imperva JSON schema to Akto format.
     *
     * @param impervaJsonString The Imperva schema JSON string
     * @param uploadId Upload ID for tracking
     * @param useHost Whether to use the host from schema
     * @return ParserResult containing parsed data and errors
     */
    public static ParserResult convertImpervaSchemaToAkto(String impervaJsonString, String uploadId, boolean useHost) {
        List<FileUploadError> fileLevelErrors = new ArrayList<>();
        List<SwaggerUploadLog> uploadLogs = new ArrayList<>();

        try {
            // Parse JSON to ImpervaSchema model
            JsonNode rootNode = mapper.readTree(impervaJsonString);
            JsonNode dataNode = rootNode.get("data");

            if (dataNode == null) {
                fileLevelErrors.add(new FileUploadError("Missing 'data' node in Imperva schema", FileUploadError.ErrorType.ERROR));
                return createErrorResult(fileLevelErrors, uploadLogs);
            }

            ImpervaSchema schema = mapper.treeToValue(dataNode, ImpervaSchema.class);
            return convertImpervaSchemaToAkto(schema, uploadId, useHost);

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error parsing Imperva schema: " + e.getMessage());
            fileLevelErrors.add(new FileUploadError("Failed to parse Imperva schema: " + e.getMessage(), FileUploadError.ErrorType.ERROR));
        }

        ParserResult result = new ParserResult();
        result.setFileErrors(fileLevelErrors);
        result.setUploadLogs(uploadLogs);
        result.setTotalCount(uploadLogs.size());
        return result;
    }

    /**
     * Converts ImpervaSchema object to Akto format.
     *
     * @param schema The ImpervaSchema object
     * @param uploadId Upload ID for tracking
     * @param useHost Whether to use the host from schema
     * @return ParserResult containing parsed data and errors
     */
    public static ParserResult convertImpervaSchemaToAkto(ImpervaSchema schema, String uploadId, boolean useHost) {
        List<FileUploadError> fileLevelErrors = new ArrayList<>();
        List<SwaggerUploadLog> uploadLogs = new ArrayList<>();

        try {
            if (schema.getMethod() == null || schema.getResource() == null) {
                fileLevelErrors.add(new FileUploadError("Missing required fields: method or resource", FileUploadError.ErrorType.ERROR));
                return createErrorResult(fileLevelErrors, uploadLogs);
            }

            loggerMaker.debugAndAddToDb("Processing Imperva API: " + schema.getMethod() + " " + schema.getResource(), LogDb.DASHBOARD);

            // Parse authentication info
            Map<String, String> authHeaders = parseAuthenticationInfo(schema.getAuthenticationInfo());

            // Extract all request samples
            uploadLogs = extractRequestSamples(schema, authHeaders, uploadId, useHost);

            loggerMaker.infoAndAddToDb("Generated " + uploadLogs.size() + " samples from Imperva schema");

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error parsing Imperva schema: " + e.getMessage());
            fileLevelErrors.add(new FileUploadError("Failed to parse Imperva schema: " + e.getMessage(), FileUploadError.ErrorType.ERROR));
        }

        ParserResult result = new ParserResult();
        result.setFileErrors(fileLevelErrors);
        result.setUploadLogs(uploadLogs);
        result.setTotalCount(uploadLogs.size());
        return result;
    }

    /**
     * Extracts all request samples from Imperva schema.
     *
     * For XML/SOAP: Creates one sample per child in the children array
     * For JSON: Creates one sample per element in body's dataTypes array
     */
    private static List<SwaggerUploadLog> extractRequestSamples(
        ImpervaSchema schema,
        Map<String, String> authHeaders,
        String uploadId,
        boolean useHost
    ) {
        List<SwaggerUploadLog> logs = new ArrayList<>();

        String hostName = schema.getHostName();
        String method = schema.getMethod();
        String resource = schema.getResource();
        String fullPath = (useHost && hostName != null) ? "https://" + hostName + resource : resource;

        RequestDrillDown request = schema.getRequest();
        if (request == null || request.getContentTypeToRequestBody() == null) {
            loggerMaker.infoAndAddToDb("No request data found in Imperva schema");
            return logs;
        }

        // Iterate through each content-type
        for (Map.Entry<String, ParameterDrillDown[]> entry : request.getContentTypeToRequestBody().entrySet()) {
            String contentType = entry.getKey();
            ParameterDrillDown[] bodyArray = entry.getValue();

            if (bodyArray == null || bodyArray.length == 0) {
                loggerMaker.infoAndAddToDb("No body parameters found for content-type: " + contentType);
                continue;
            }

            // Get the "body" parameter (usually first element)
            ParameterDrillDown bodyParam = bodyArray[0];

            if (bodyParam.getDataTypes() == null || bodyParam.getDataTypes().length == 0) {
                loggerMaker.infoAndAddToDb("No dataTypes found for body in content-type: " + contentType);
                continue;
            }

            // Determine if XML/SOAP or JSON
            if (isXmlContentType(contentType)) {
                // XML: Generate samples from children
                logs.addAll(generateXmlSamples(
                    bodyParam, contentType, method, fullPath, hostName, authHeaders, uploadId
                ));
            } else {
                // JSON: Generate samples from dataTypes array
                logs.addAll(generateJsonSamples(
                    bodyParam, contentType, method, fullPath, hostName, authHeaders, uploadId
                ));
            }
        }

        return logs;
    }

    /**
     * Checks if content-type is XML or SOAP based.
     */
    private static boolean isXmlContentType(String contentType) {
        return contentType != null && (
            contentType.contains("xml") ||
            contentType.contains("soap")
        );
    }

    /**
     * For XML: Generate one sample per child in the children array.
     * Each child represents a different API call (e.g., createProfile, updateProfile).
     */
    private static List<SwaggerUploadLog> generateXmlSamples(
        ParameterDrillDown bodyParam,
        String contentType,
        String method,
        String path,
        String hostName,
        Map<String, String> authHeaders,
        String uploadId
    ) {
        List<SwaggerUploadLog> logs = new ArrayList<>();

        // Get first dataType (should be Object)
        DataTypeDto bodyDataType = bodyParam.getDataTypes()[0];

        if (bodyDataType.getChildren() == null || bodyDataType.getChildren().length == 0) {
            loggerMaker.infoAndAddToDb("No children found in XML body dataType");
            return logs;
        }

        loggerMaker.infoAndAddToDb("Generating " + bodyDataType.getChildren().length + " XML samples from children");

        // Each child represents a separate API call sample
        for (ParameterDrillDown child : bodyDataType.getChildren()) {
            try {
                // Generate sample for this specific child
                Object sampleData = generateSampleForChild(child);
                String payload = generateXmlString(sampleData);

                // Create SwaggerUploadLog entry
                SwaggerUploadLog log = createSwaggerUploadLog(
                    method, path, contentType, payload, authHeaders, uploadId, hostName
                );
                logs.add(log);

            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error generating XML sample for child: " + child.getName());
            }
        }

        return logs;
    }

    /**
     * For JSON: Generate one sample per element in body's dataTypes array.
     * Each dataTypes element represents a different schema variation.
     */
    private static List<SwaggerUploadLog> generateJsonSamples(
        ParameterDrillDown bodyParam,
        String contentType,
        String method,
        String path,
        String hostName,
        Map<String, String> authHeaders,
        String uploadId
    ) {
        List<SwaggerUploadLog> logs = new ArrayList<>();

        loggerMaker.infoAndAddToDb("Generating " + bodyParam.getDataTypes().length + " JSON samples from dataTypes array");

        // Each element in dataTypes array is a separate sample
        for (DataTypeDto dataType : bodyParam.getDataTypes()) {
            try {
                // Generate sample including all children of this dataType
                Object sampleData = generateSampleFromDataType(dataType);

                String payload;
                if (contentType.contains("yaml")) {
                    payload = generateYamlString(sampleData);
                } else if (contentType.contains("x-www-form-urlencoded")) {
                    payload = generateFormUrlencodedString(sampleData);
                } else {
                    // Default to JSON
                    payload = mapper.writeValueAsString(sampleData);
                }

                // Create SwaggerUploadLog entry
                SwaggerUploadLog log = createSwaggerUploadLog(
                    method, path, contentType, payload, authHeaders, uploadId, hostName
                );
                logs.add(log);

            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error generating JSON sample");
            }
        }

        return logs;
    }

    /**
     * Generates sample data for a single child (used for XML).
     * Returns a Map with the child as the root element.
     */
    private static Object generateSampleForChild(ParameterDrillDown child) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (child.getName() != null && child.getDataTypes() != null && child.getDataTypes().length > 0) {
            Object childValue = generateSampleFromDataType(child.getDataTypes()[0]);
            result.put(child.getName(), childValue);
        }

        return result;
    }

    /**
     * Recursively generates sample from DataTypeDto (includes all children).
     */
    private static Object generateSampleFromDataType(DataTypeDto dataType) {
        if (dataType == null || StringUtils.isEmpty(dataType.getType())) {
            return null;
        }

        String type = dataType.getType().toLowerCase();

        switch (type) {
            case "object":
                Map<String, Object> objectData = new LinkedHashMap<>();
                if (dataType.getChildren() != null) {
                    for (ParameterDrillDown child : dataType.getChildren()) {
                        if (child.getName() != null && child.getDataTypes() != null && child.getDataTypes().length > 0) {
                            Object childValue = generateSampleFromDataType(child.getDataTypes()[0]);
                            objectData.put(child.getName(), childValue);
                        }
                    }
                }
                return objectData;

            case "array":
                List<Object> arrayData = new ArrayList<>();
                if (dataType.getChildren() != null && dataType.getChildren().length > 0) {
                    ParameterDrillDown arrayChild = dataType.getChildren()[0];
                    if (arrayChild.getDataTypes() != null && arrayChild.getDataTypes().length > 0) {
                        Object arrayElement = generateSampleFromDataType(arrayChild.getDataTypes()[0]);
                        arrayData.add(arrayElement);
                    }
                }
                return arrayData;

            case "string":
                return "sample-string";
            case "number":
                return 123;
            case "boolean":
                return true;
            default:
                return "sample-value";
        }
    }

    /**
     * Creates a SwaggerUploadLog entry with the given data.
     */
    private static SwaggerUploadLog createSwaggerUploadLog(
        String method,
        String path,
        String contentType,
        String payload,
        Map<String, String> authHeaders,
        String uploadId,
        String hostName
    ) throws Exception {

        // Build request headers
        Map<String, String> requestHeaders = new HashMap<>(authHeaders);
        requestHeaders.put("Content-Type", contentType);
        if (!StringUtils.isEmpty(hostName)) {
            requestHeaders.put("host", hostName);
        }

        // Build generic response
        Map<String, String> responseHeaders = new HashMap<>();

        // Create aktoFormat message
        Map<String, String> messageObject = new HashMap<>();
        messageObject.put(mKeys.akto_account_id, Context.accountId.get().toString());
        messageObject.put(mKeys.path, path);
        messageObject.put(mKeys.method, method.toUpperCase());
        messageObject.put(mKeys.requestHeaders, mapper.writeValueAsString(requestHeaders));
        messageObject.put(mKeys.requestPayload, payload != null ? payload : "");
        messageObject.put(mKeys.responseHeaders, mapper.writeValueAsString(responseHeaders));
        messageObject.put(mKeys.responsePayload, "");
        messageObject.put(mKeys.status, "OK");
        messageObject.put(mKeys.statusCode, "200");
        messageObject.put(mKeys.ip, "null");
        messageObject.put(mKeys.time, Context.now() + "");
        messageObject.put(mKeys.type, "HTTP");
        messageObject.put(mKeys.source, Source.IMPERVA.name());

        // Create log entry
        SwaggerUploadLog log = new SwaggerUploadLog();
        log.setMethod(method.toUpperCase());
        log.setUrl(path);
        log.setUploadId(uploadId);
        log.setAktoFormat(mapper.writeValueAsString(messageObject));

        return log;
    }

    /**
     * Parses authentication information from Imperva schema.
     * Auth location format: http-req-header-<header-key>
     * Example: http-req-header-authorization -> extracts "authorization" as header key
     */
    private static Map<String, String> parseAuthenticationInfo(AuthenticationInfo authInfo) {
        Map<String, String> authHeaders = new HashMap<>();

        if (authInfo == null) {
            return authHeaders;
        }

        try {
            AuthParameterLocation[] authParamLocations = authInfo.getAuthParameterLocations();
            if (authParamLocations != null && authParamLocations.length > 0) {
                for (AuthParameterLocation location : authParamLocations) {
                    if (location != null && location.getAuthParameterLocation() != null) {
                        String authLocation = location.getAuthParameterLocation();

                        // Extract header key from format: http-req-header-<header-key>
                        if (authLocation.startsWith("http-req-header-")) {
                            String headerKey = authLocation.substring("http-req-header-".length());
                            // Capitalize first letter for standard header format
                            if (!StringUtils.isEmpty(headerKey)) {
                                String formattedHeaderKey = capitalizeHeaderKey(headerKey);
                                authHeaders.put(formattedHeaderKey, "sample-token");
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error parsing authentication info: " + e.getMessage());
        }

        return authHeaders;
    }

    /**
     * Capitalizes header key properly.
     * Example: "authorization" -> "Authorization", "x-api-key" -> "X-Api-Key"
     */
    private static String capitalizeHeaderKey(String headerKey) {
        if (StringUtils.isEmpty(headerKey)) {
            return headerKey;
        }

        String[] parts = headerKey.split("-");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                result.append("-");
            }
            String part = parts[i];
            if (!StringUtils.isEmpty(part)) {
                result.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    result.append(part.substring(1).toLowerCase());
                }
            }
        }

        return result.toString();
    }

    /**
     * Simple XML string generation.
     * Handles XML attributes (names starting with '-') properly.
     * Example: "-xmlns" becomes an attribute: <element xmlns="value">
     */
    private static String generateXmlString(Object data) {
        if (data instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> mapData = (Map<String, Object>) data;
            StringBuilder xml = new StringBuilder();

            for (Map.Entry<String, Object> entry : mapData.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                // Separate attributes (starting with -) from elements
                Map<String, Object> attributes = new java.util.LinkedHashMap<>();
                Map<String, Object> elements = new java.util.LinkedHashMap<>();

                if (value instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> childMap = (Map<String, Object>) value;
                    for (Map.Entry<String, Object> childEntry : childMap.entrySet()) {
                        if (childEntry.getKey().startsWith("-")) {
                            // This is an attribute - remove the '-' prefix
                            attributes.put(childEntry.getKey().substring(1), childEntry.getValue());
                        } else {
                            // This is a child element
                            elements.put(childEntry.getKey(), childEntry.getValue());
                        }
                    }

                    // Generate opening tag with attributes
                    xml.append("<").append(key);
                    for (Map.Entry<String, Object> attr : attributes.entrySet()) {
                        xml.append(" ").append(attr.getKey()).append("=\"").append(attr.getValue()).append("\"");
                    }
                    xml.append(">");

                    // Generate child elements
                    if (!elements.isEmpty()) {
                        xml.append(generateXmlString(elements));
                    }

                    xml.append("</").append(key).append(">");
                } else if (value instanceof List) {
                    xml.append("<").append(key).append(">");
                    xml.append(generateXmlString(value));
                    xml.append("</").append(key).append(">");
                } else {
                    // Simple value
                    xml.append("<").append(key).append(">");
                    xml.append(value);
                    xml.append("</").append(key).append(">");
                }
            }
            return xml.toString();
        } else if (data instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> listData = (List<Object>) data;
            StringBuilder xml = new StringBuilder();
            for (Object item : listData) {
                xml.append(generateXmlString(item));
            }
            return xml.toString();
        }
        return data != null ? data.toString() : "";
    }

    /**
     * Simple YAML string generation from object data.
     */
    private static String generateYamlString(Object data) {
        try {
            return convertToYaml(data, 0);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error generating YAML: " + e.getMessage());
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private static String convertToYaml(Object data, int indent) {
        StringBuilder indentBuilder = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            indentBuilder.append("  ");
        }
        String indentStr = indentBuilder.toString();

        if (data instanceof Map) {
            Map<String, Object> mapData = (Map<String, Object>) data;
            StringBuilder yaml = new StringBuilder();
            for (Map.Entry<String, Object> entry : mapData.entrySet()) {
                yaml.append(indentStr).append(entry.getKey()).append(": ");
                if (entry.getValue() instanceof Map || entry.getValue() instanceof List) {
                    yaml.append("\n").append(convertToYaml(entry.getValue(), indent + 1));
                } else {
                    yaml.append(entry.getValue()).append("\n");
                }
            }
            return yaml.toString();
        } else if (data instanceof List) {
            List<Object> listData = (List<Object>) data;
            StringBuilder yaml = new StringBuilder();
            for (Object item : listData) {
                yaml.append(indentStr).append("- ");
                if (item instanceof Map || item instanceof List) {
                    yaml.append("\n").append(convertToYaml(item, indent + 1));
                } else {
                    yaml.append(item).append("\n");
                }
            }
            return yaml.toString();
        }
        return data != null ? data.toString() : "";
    }

    /**
     * Form URL encoded string generation from object data.
     */
    @SuppressWarnings("unchecked")
    private static String generateFormUrlencodedString(Object data) {
        try {
            if (data instanceof Map) {
                Map<String, Object> mapData = (Map<String, Object>) data;
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, Object> entry : mapData.entrySet()) {
                    if (sb.length() > 0) {
                        sb.append("&");
                    }
                    sb.append(entry.getKey()).append("=").append(entry.getValue());
                }
                return java.net.URLEncoder.encode(sb.toString(), "UTF-8");
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error generating form urlencoded: " + e.getMessage());
        }
        return "";
    }

    /**
     * Helper method to create error result.
     */
    private static ParserResult createErrorResult(List<FileUploadError> errors, List<SwaggerUploadLog> logs) {
        ParserResult result = new ParserResult();
        result.setFileErrors(errors);
        result.setUploadLogs(logs);
        result.setTotalCount(0);
        return result;
    }
}