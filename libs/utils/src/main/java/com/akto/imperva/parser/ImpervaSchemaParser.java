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
     * Converts ImpervaSchema object to Akto format.
     *
     * @param schema The ImpervaSchema object
     * @param uploadId Upload ID for tracking
     * @param useHost Whether to use the host from schema
     * @param generateMultipleSamples true = old logic (multiple samples per XML child), false = new logic (merged samples with responses)
     * @return ParserResult containing parsed data and errors
     */
    public static ParserResult convertImpervaSchemaToAkto(ImpervaSchema schema, String uploadId, boolean useHost, boolean generateMultipleSamples) {
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
            uploadLogs = extractRequestSamples(schema, authHeaders, uploadId, useHost, generateMultipleSamples);

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
     * For XML/SOAP:
     *   - If generateMultipleSamples=true: Creates one sample per child in the children array (old logic)
     *   - If generateMultipleSamples=false: Creates one merged sample with all children (new logic)
     * For JSON: Creates one sample per element in body's dataTypes array
     */
    private static List<SwaggerUploadLog> extractRequestSamples(
        ImpervaSchema schema,
        Map<String, String> authHeaders,
        String uploadId,
        boolean useHost,
        boolean generateMultipleSamples
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

        // Parse request headers once (shared across all content-types)
        Map<String, String> requestHeaders = parseRequestHeaders(request);
        loggerMaker.infoAndAddToDb("Parsed " + requestHeaders.size() + " request headers from Imperva schema");

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

            // Get matching response for this content-type
            ResponseDrillDown matchingResponse = getMatchingResponse(schema, contentType);

            // will need this afterwards
            
            // // Determine if XML/SOAP or JSON
            // if (isXmlContentType(contentType)) {
            //     // XML: Generate samples from children
            //     logs.addAll(generateXmlSamples(
            //         bodyParam, contentType, method, fullPath, hostName, authHeaders, uploadId,
            //         generateMultipleSamples, matchingResponse
            //     ));
            // } else {
            //     // JSON: Generate samples from dataTypes array
            //     logs.addAll(generateJsonSamples(
            //         bodyParam, contentType, method, fullPath, hostName, authHeaders, uploadId,
            //         matchingResponse
            //     ));
            // }
            logs.addAll(generateJsonSamples(bodyParam, contentType, method, fullPath, hostName,
                    authHeaders, requestHeaders, uploadId, matchingResponse));
        }

        return logs;
    }

    /**
     * Gets matching response for the given content-type from 200 responses only.
     */
    private static ResponseDrillDown getMatchingResponse(ImpervaSchema schema, String requestContentType) {
        if (schema.getResponses() == null) {
            return null;
        }

        // Only consider 200 responses
        ResponseDrillDown response200 = schema.getResponses().get("200");
        if (response200 == null || response200.getContentTypeToResponseBody() == null) {
            return null;
        }

        // Check if this response has matching content-type
        if (response200.getContentTypeToResponseBody().containsKey(requestContentType)) {
            return response200;
        }

        return null;
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
     * For XML:
     * - If generateMultipleSamples=true: Generate one sample per child in the children array (old logic)
     * - If generateMultipleSamples=false: Generate one merged sample with all children (new logic)
     */
    private static List<SwaggerUploadLog> generateXmlSamples(
        ParameterDrillDown bodyParam,
        String contentType,
        String method,
        String path,
        String hostName,
        Map<String, String> authHeaders,
        String uploadId,
        boolean generateMultipleSamples,
        ResponseDrillDown matchingResponse
    ) {
        List<SwaggerUploadLog> logs = new ArrayList<>();

        // Get first dataType (should be Object)
        DataTypeDto bodyDataType = bodyParam.getDataTypes()[0];

        if (bodyDataType.getChildren() == null || bodyDataType.getChildren().length == 0) {
            loggerMaker.infoAndAddToDb("No children found in XML body dataType");
            return logs;
        }

        if (generateMultipleSamples) {
            // OLD LOGIC: One sample per child
            loggerMaker.infoAndAddToDb("Generating " + bodyDataType.getChildren().length + " XML samples from children (old logic)");

            for (ParameterDrillDown child : bodyDataType.getChildren()) {
                try {
                    Object sampleData = generateSampleForChild(child);
                    String requestPayload = generateXmlString(sampleData);

                    SwaggerUploadLog log = createSwaggerUploadLog(
                        method, path, contentType, requestPayload, authHeaders, uploadId, hostName,
                        null, null, 200
                    );
                    logs.add(log);

                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Error generating XML sample for child: " + child.getName());
                }
            }
        } else {
            // NEW LOGIC: One merged sample with all children
            loggerMaker.infoAndAddToDb("Generating 1 merged XML sample with all children (new logic)");

            try {
                // Generate merged sample with all children
                Object mergedSample = generateSampleFromDataType(bodyDataType);
                String requestPayload = generateXmlString(mergedSample);

                // Extract response if available
                String responsePayload = null;
                Map<String, String> responseHeaders = new HashMap<>();
                if (matchingResponse != null && matchingResponse.getContentTypeToResponseBody() != null) {
                    ParameterDrillDown[] responseBodyArray = matchingResponse.getContentTypeToResponseBody().get(contentType);
                    if (responseBodyArray != null && responseBodyArray.length > 0) {
                        ParameterDrillDown responseBody = responseBodyArray[0];
                        if (responseBody.getDataTypes() != null && responseBody.getDataTypes().length > 0) {
                            Object responseSample = generateSampleFromDataType(responseBody.getDataTypes()[0]);
                            responsePayload = generateXmlString(responseSample);
                        }
                    }
                }

                SwaggerUploadLog log = createSwaggerUploadLog(
                    method, path, contentType, requestPayload, authHeaders, uploadId, hostName,
                    responsePayload, responseHeaders, 200
                );
                logs.add(log);

            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error generating merged XML sample");
            }
        }

        return logs;
    }

    /**
     * For JSON: Generate one sample per element in body's dataTypes array.
     * Each dataTypes element represents a different schema variation.
     * Now includes response matching.
     */
    private static List<SwaggerUploadLog> generateJsonSamples(
        ParameterDrillDown bodyParam,
        String contentType,
        String method,
        String path,
        String hostName,
        Map<String, String> authHeaders,
        Map<String, String> requestHeaders,
        String uploadId,
        ResponseDrillDown matchingResponse
    ) {
        List<SwaggerUploadLog> logs = new ArrayList<>();

        loggerMaker.infoAndAddToDb("Generating " + bodyParam.getDataTypes().length + " JSON samples from dataTypes array");

        // Each element in dataTypes array is a separate sample
        for (DataTypeDto dataType : bodyParam.getDataTypes()) {
            try {
                // Generate request sample including all children of this dataType
                Object sampleData = generateSampleFromDataType(dataType);

                String requestPayload;
                if (contentType.contains("yaml")) {
                    requestPayload = generateYamlString(sampleData);
                } else if (contentType.contains("x-www-form-urlencoded")) {
                    requestPayload = generateFormUrlencodedString(sampleData);
                } else {
                    // Default to JSON
                    requestPayload = mapper.writeValueAsString(sampleData);
                }

                // Extract response if available
                String responsePayload = null;
                Map<String, String> responseHeaders = new HashMap<>();
                if (matchingResponse != null && matchingResponse.getContentTypeToResponseBody() != null) {
                    ParameterDrillDown[] responseBodyArray = matchingResponse.getContentTypeToResponseBody().get(contentType);
                    if (responseBodyArray != null && responseBodyArray.length > 0) {
                        ParameterDrillDown responseBody = responseBodyArray[0];
                        if (responseBody.getDataTypes() != null && responseBody.getDataTypes().length > 0) {
                            Object responseSample = generateSampleFromDataType(responseBody.getDataTypes()[0]);

                            if (contentType.contains("yaml")) {
                                responsePayload = generateYamlString(responseSample);
                            } else if (contentType.contains("x-www-form-urlencoded")) {
                                responsePayload = generateFormUrlencodedString(responseSample);
                            } else {
                                responsePayload = mapper.writeValueAsString(responseSample);
                            }
                        }
                    }
                }

                // Create SwaggerUploadLog entry
                SwaggerUploadLog log = createSwaggerUploadLog(
                    method, path, contentType, requestPayload, authHeaders, uploadId, hostName,
                    responsePayload, responseHeaders, 200
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
                return "sample_string";
            case "number":
                return 123;
            case "boolean":
                return true;
            default:
                return "sample_value";
        }
    }

    /**
     * Creates a SwaggerUploadLog entry with the given data.
     */
    private static SwaggerUploadLog createSwaggerUploadLog(
        String method,
        String path,
        String contentType,
        String requestPayload,
        Map<String, String> authHeaders,
        String uploadId,
        String hostName,
        String responsePayload,
        Map<String, String> additionalResponseHeaders,
        int statusCode
    ) throws Exception {

        // Build request headers
        Map<String, String> requestHeaders = new HashMap<>(authHeaders);
        requestHeaders.put("Content-Type", contentType);
        if (!StringUtils.isEmpty(hostName)) {
            requestHeaders.put("host", hostName);
        }

        // Build response headers
        Map<String, String> responseHeaders = new HashMap<>();
        if (additionalResponseHeaders != null) {
            responseHeaders.putAll(additionalResponseHeaders);
        }
        if (responsePayload != null && !StringUtils.isEmpty(contentType)) {
            responseHeaders.put("Content-Type", contentType);
        }

        // Create aktoFormat message
        Map<String, String> messageObject = new HashMap<>();
        messageObject.put(mKeys.akto_account_id, Context.accountId.get().toString());
        messageObject.put(mKeys.path, path);
        messageObject.put(mKeys.method, method.toUpperCase());
        messageObject.put(mKeys.requestHeaders, mapper.writeValueAsString(requestHeaders));
        messageObject.put(mKeys.requestPayload, requestPayload != null ? requestPayload : "");
        messageObject.put(mKeys.responseHeaders, mapper.writeValueAsString(responseHeaders));
        messageObject.put(mKeys.responsePayload, responsePayload != null ? responsePayload : "");
        messageObject.put(mKeys.status, statusCode == 200 ? "OK" : String.valueOf(statusCode));
        messageObject.put(mKeys.statusCode, String.valueOf(statusCode));
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
                                authHeaders.put(headerKey, "sample_token");
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
     * Parses request headers from RequestDrillDown.
     * Handles both static and dynamic header values.
     */
    private static Map<String, String> parseRequestHeaders(RequestDrillDown request) {
        Map<String, String> headers = new HashMap<>();

        if (request == null || request.getHeaderList() == null) {
            return headers;
        }

        try {
            for (HeaderDto header : request.getHeaderList()) {
                if (header == null || StringUtils.isEmpty(header.getKey())) {
                    continue;
                }

                // Handle different header types
                if ("COOKIE".equalsIgnoreCase(header.getType())) {
                    // Parse cookies
                    String cookieValue = parseCookies(header);
                    if (!StringUtils.isEmpty(cookieValue)) {
                        headers.put("Cookie", cookieValue);
                    }
                } else {
                    // Regular HEADER type
                    String headerValue = getHeaderValue(header);
                    if (!StringUtils.isEmpty(headerValue)) {
                        headers.put(header.getKey(), headerValue);
                    }
                }
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error parsing request headers: " + e.getMessage());
        }

        return headers;
    }

    /**
     * Parses response headers from ResponseDrillDown.
     */
    private static Map<String, String> parseResponseHeaders(ResponseDrillDown response) {
        Map<String, String> headers = new HashMap<>();

        if (response == null || response.getHeaderList() == null) {
            return headers;
        }

        try {
            for (HeaderDto header : response.getHeaderList()) {
                if (header == null || StringUtils.isEmpty(header.getKey())) {
                    continue;
                }

                // Handle different header types
                if ("COOKIE".equalsIgnoreCase(header.getType())) {
                    // For response, cookies typically use Set-Cookie header
                    String cookieValue = parseCookies(header);
                    if (!StringUtils.isEmpty(cookieValue)) {
                        // Note: Multiple Set-Cookie headers might exist, but we're simplifying here
                        headers.put("Set-Cookie", cookieValue);
                    }
                } else {
                    // Regular HEADER type
                    String headerValue = getHeaderValue(header);
                    if (!StringUtils.isEmpty(headerValue)) {
                        headers.put(header.getKey(), headerValue);
                    }
                }
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error parsing response headers: " + e.getMessage());
        }

        return headers;
    }

    /**
     * Gets header value from HeaderDto.
     * Uses static value if available, otherwise generates sample from dataTypes.
     */
    private static String getHeaderValue(HeaderDto header) {
        try {
            // If header has static value
            if (header.getHeaderWithValue() != null && header.getHeaderWithValue()
                && !StringUtils.isEmpty(header.getValue())) {
                return header.getValue();
            }

            // Generate sample from dataTypes
            if (header.getDataTypes() != null && header.getDataTypes().length > 0) {
                Object sample = generateSampleFromDataType(header.getDataTypes()[0]);
                if (sample != null) {
                    return sample.toString();
                }
            }

            // Default sample value
            return "sample-header-value";
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error getting header value for key: " + header.getKey());
            return "sample-header-value";
        }
    }

    /**
     * Parses cookies from HeaderDto and formats as Cookie header value.
     * Format: "key1=value1; key2=value2"
     */
    private static String parseCookies(HeaderDto header) {
        try {
            if (header.getCookies() == null || header.getCookies().length == 0) {
                // If no cookies array but has value, use that
                if (!StringUtils.isEmpty(header.getValue())) {
                    return header.getValue();
                }
                return null;
            }

            StringBuilder cookieStr = new StringBuilder();
            for (HeaderDto.CookieDto cookie : header.getCookies()) {
                if (cookie == null || StringUtils.isEmpty(cookie.getKey())) {
                    continue;
                }

                if (cookieStr.length() > 0) {
                    cookieStr.append("; ");
                }

                cookieStr.append(cookie.getKey()).append("=");

                // Generate cookie value from dataTypes
                if (cookie.getDataTypes() != null && cookie.getDataTypes().length > 0) {
                    Object sample = generateSampleFromDataType(cookie.getDataTypes()[0]);
                    if (sample != null) {
                        cookieStr.append(sample.toString());
                    } else {
                        cookieStr.append("sample-cookie-value");
                    }
                } else {
                    cookieStr.append("sample-cookie-value");
                }
            }

            return cookieStr.length() > 0 ? cookieStr.toString() : null;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error parsing cookies");
            return null;
        }
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