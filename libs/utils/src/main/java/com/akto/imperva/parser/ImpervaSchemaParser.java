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
import com.akto.util.Pair;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.apache.commons.lang3.StringUtils;
import com.akto.open_api.parser.Parser.mKeys;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

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
    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));

    private static final String SAMPLE_TOKEN = "sample_token";
    private static final String SAMPLE_HEADER_VALUE = "sample_header_value";
    private static final String SAMPLE_COOKIE_VALUE = "sample_cookie_value";
    private static final String SAMPLE_QUERY_PARAM_VALUE = "sample_query_value";

    private static final Set<String> VALID_CONTENT_TYPES = new HashSet<>(Arrays.asList(
        "application/json",
        "application/xml",
        "application/x-www-form-urlencoded",
        "application/yaml",
        "application/x-yaml",
        "application/soap+xml",
        "text/xml",
        "text/plain",
        "text/html",
        "multipart/form-data"
    ));

    public static ParserResult convertImpervaSchemaToAkto(ImpervaSchema schema, String uploadId, boolean useHost, boolean generateMultipleSamples) {
        List<FileUploadError> fileLevelErrors = new ArrayList<>();
        List<SwaggerUploadLog> uploadLogs = new ArrayList<>();

        try {
            if (schema.getMethod() == null || schema.getResource() == null) {
                fileLevelErrors.add(new FileUploadError("Missing required fields: method or resource", FileUploadError.ErrorType.ERROR));
                return createErrorResult(fileLevelErrors, uploadLogs);
            }

            loggerMaker.debugAndAddToDb("Processing Imperva API: " + schema.getMethod() + " " + schema.getResource(), LogDb.DASHBOARD);

            // Extract all request samples
            uploadLogs = extractRequestSamples(schema, uploadId, useHost, generateMultipleSamples);

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

    private static List<SwaggerUploadLog> extractRequestSamples(
        ImpervaSchema schema,
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

        Map<String, String> requestHeaders = parseHeaders(request.getHeaderList(), true);
        Map<String, String> authHeaders = parseAuthenticationInfo(schema.getAuthenticationInfo());
        requestHeaders.putAll(authHeaders);
        loggerMaker.infoAndAddToDb("Parsed " + requestHeaders.size() + " request headers from Imperva schema");

        fullPath = appendQueryParamsToPath(fullPath, request.getQueryParamList());

        for (Map.Entry<String, ParameterDrillDown[]> entry : request.getContentTypeToRequestBody().entrySet()) {
            String contentType = entry.getKey();
            ParameterDrillDown[] bodyArray = entry.getValue();

            if (!isValidContentType(contentType)) {
                loggerMaker.infoAndAddToDb("Skipping invalid/malformed content-type: " + contentType);
                continue;
            }

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

            // Get matching response for this content-type (returns headers + body)
            Pair<Map<String, String>, ParameterDrillDown[]> matchingResponse = getMatchingResponse(schema, contentType);

            Map<String, String> responseHeaders = new HashMap<>();
            if (matchingResponse != null) {
                responseHeaders = matchingResponse.getFirst();
            }

            // Generate samples (request/response payloads)
            List<Pair<String, String>> samples = generateSamples(bodyParam, contentType, matchingResponse);

            // Create upload logs from samples
            for (Pair<String, String> sample : samples) {
                try {
                    SwaggerUploadLog log = createSwaggerUploadLog(
                        method, fullPath, contentType, sample.getFirst(), requestHeaders,
                        uploadId, hostName, sample.getSecond(), responseHeaders, 200
                    );
                    logs.add(log);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Error creating upload log");
                }
            }
        }

        return logs;
    }

    private static Pair<Map<String, String>, ParameterDrillDown[]> getMatchingResponse(ImpervaSchema schema, String requestContentType) {
        if (schema.getResponses() == null) {
            return null;
        }

        // Only consider 200 responses
        ResponseDrillDown response200 = schema.getResponses().get("200");
        if (response200 == null || response200.getContentTypeToResponseBody() == null) {
            return null;
        }

        // Check if this response has matching content-type
        if (!response200.getContentTypeToResponseBody().containsKey(requestContentType)) {
            return null;
        }

        // Parse headers
        Map<String, String> responseHeaders = parseHeaders(response200.getHeaderList(), false);

        // Get response body
        ParameterDrillDown[] responseBodyArray = response200.getContentTypeToResponseBody().get(requestContentType);

        return new Pair<>(responseHeaders, responseBodyArray);
    }

    /**
     * Validates if content-type is in whitelist.
     */
    private static boolean isValidContentType(String contentType) {
        if (StringUtils.isEmpty(contentType)) {
            return false;
        }
        return VALID_CONTENT_TYPES.contains(contentType);
    }

    private static List<Pair<String, String>> generateSamples(
        ParameterDrillDown bodyParam,
        String contentType,
        Pair<Map<String, String>, ParameterDrillDown[]> matchingResponse
    ) {
        List<Pair<String, String>> samples = new ArrayList<>();

        loggerMaker.infoAndAddToDb("Generating " + bodyParam.getDataTypes().length + " samples from dataTypes array");

        for (DataTypeDto dataType : bodyParam.getDataTypes()) {
            try {
                // Generate request sample including all children of this dataType
                Object sampleData = generateSampleFromDataType(dataType);
                String requestPayload = generatePayloadString(sampleData, contentType);

                // Generate response payload if available
                String responsePayload = generateResponsePayload(matchingResponse, contentType);

                samples.add(new Pair<>(requestPayload, responsePayload));

            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error generating sample");
            }
        }

        return samples;
    }

    private static String generatePayloadString(Object sampleData, String contentType) throws Exception {
        if (contentType.contains("yaml")) {
            return generateYamlString(sampleData);
        } else if (contentType.contains("x-www-form-urlencoded")) {
            return generateFormUrlencodedString(sampleData);
        } else {
            return mapper.writeValueAsString(sampleData);
        }
    }

    private static String generateResponsePayload(
        Pair<Map<String, String>, ParameterDrillDown[]> matchingResponse,
        String contentType
    ) throws Exception {
        if (matchingResponse == null) {
            return null;
        }

        ParameterDrillDown[] responseBodyArray = matchingResponse.getSecond();
        if (responseBodyArray == null || responseBodyArray.length == 0) {
            return null;
        }

        ParameterDrillDown responseBody = responseBodyArray[0];
        if (responseBody.getDataTypes() == null || responseBody.getDataTypes().length == 0) {
            return null;
        }

        Object responseSample = generateSampleFromDataType(responseBody.getDataTypes()[0]);
        return generatePayloadString(responseSample, contentType);
    }

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

    private static SwaggerUploadLog createSwaggerUploadLog(
        String method,
        String path,
        String contentType,
        String requestPayload,
        Map<String, String> mergedRequestHeaders,
        String uploadId,
        String hostName,
        String responsePayload,
        Map<String, String> parsedResponseHeaders,
        int statusCode
    ) throws Exception {

        // Add standard request headers
        Map<String, String> requestHeaders = new HashMap<>(mergedRequestHeaders);
        requestHeaders.put("Content-Type", contentType);
        if (!StringUtils.isEmpty(hostName)) {
            requestHeaders.put("host", hostName);
        }

        // Build response headers
        Map<String, String> responseHeaders = new HashMap<>();
        if (parsedResponseHeaders != null) {
            responseHeaders.putAll(parsedResponseHeaders);
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
                            if (!StringUtils.isEmpty(headerKey)) {
                                authHeaders.put(headerKey, SAMPLE_TOKEN);
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

    private static Map<String, String> parseHeaders(HeaderDto[] headerList, boolean isRequest) {
        Map<String, String> headers = new HashMap<>();

        if (headerList == null) {
            return headers;
        }

        try {
            for (HeaderDto header : headerList) {
                if (header == null || StringUtils.isEmpty(header.getKey())) {
                    continue;
                }

                // Handle different header types
                if ("COOKIE".equalsIgnoreCase(header.getType())) {
                    String cookieValue = parseCookies(header);
                    if (!StringUtils.isEmpty(cookieValue)) {
                        headers.put(isRequest ? "Cookie" : "Set-Cookie", cookieValue);
                    }
                } else {
                    String headerValue = getHeaderValue(header);
                    if (!StringUtils.isEmpty(headerValue)) {
                        headers.put(header.getKey(), headerValue);
                    }
                }
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error parsing headers: " + e.getMessage());
        }

        return headers;
    }

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
            return SAMPLE_HEADER_VALUE;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error getting header value for key: " + header.getKey());
            return SAMPLE_HEADER_VALUE;
        }
    }

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
                        cookieStr.append(SAMPLE_COOKIE_VALUE);
                    }
                } else {
                    cookieStr.append(SAMPLE_COOKIE_VALUE);
                }
            }

            return cookieStr.length() > 0 ? cookieStr.toString() : null;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error parsing cookies");
            return null;
        }
    }

    private static String appendQueryParamsToPath(String path, ParameterDrillDown[] queryParamList) {
        if (queryParamList == null || queryParamList.length == 0) {
            return path;
        }
    
        try {
            List<NameValuePair> params = Arrays.stream(queryParamList)
                    .filter(Objects::nonNull)
                    .filter(param -> !StringUtils.isEmpty(param.getName()))
                    .map(param -> {
                        String value = SAMPLE_QUERY_PARAM_VALUE;
    
                        if (param.getDataTypes() != null && param.getDataTypes().length > 0) {
                            Object sample = generateSampleFromDataType(param.getDataTypes()[0]);
                            if (sample != null) {
                                value = sample.toString();
                            }
                        }
    
                        return new BasicNameValuePair(param.getName(), value);
                    })
                    .collect(Collectors.toList());
    
            if (params.isEmpty()) {
                return path;
            }
    
            String queryString = URLEncodedUtils.format(params, StandardCharsets.UTF_8);
            return path + "?" + queryString;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error appending query params to path");
            return path;
        }
    }
    
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
            return yamlMapper.writeValueAsString(data);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error generating YAML: " + e.getMessage());
            return "";
        }
    }

    /**
     * Form URL encoded string generation from object data using Apache HttpClient.
     */
    private static String generateFormUrlencodedString(Object data) {
        if (!(data instanceof Map)) {
            return "";
        }
    
        try {
            Map<?, ?> rawMap = (Map<?, ?>) data;
            List<NameValuePair> params = rawMap.entrySet().stream()
                    .filter(entry -> entry.getKey() instanceof String && entry.getValue() != null)
                    .map(entry -> new BasicNameValuePair(
                            (String) entry.getKey(),
                            entry.getValue().toString()
                    ))
                    .collect(Collectors.toList());
    
            return URLEncodedUtils.format(params, StandardCharsets.UTF_8);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error generating form urlencoded: " + e.getMessage());
            return "";
        }
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