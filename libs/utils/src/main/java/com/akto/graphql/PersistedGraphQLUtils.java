package com.akto.graphql;

import com.akto.dto.HttpResponseParams;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.alibaba.fastjson2.JSON;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PersistedGraphQLUtils {
    private static final LoggerMaker loggerMaker = new LoggerMaker(PersistedGraphQLUtils.class, LogDb.RUNTIME);
    private static final PersistedGraphQLUtils utils = new PersistedGraphQLUtils();

    private static final String OPERATION_NAME = "operationName";
    private static final String EXTENSIONS = "extensions";
    private static final String PERSISTED_QUERY = "persistedQuery";

    private PersistedGraphQLUtils() {
    }

    public static PersistedGraphQLUtils getUtils() {
        return utils;
    }

    /**
     * Parses persisted GraphQL request and creates modified HttpResponseParams
     * with URL transformed from /graphql to /graphql/{operationType}/{operationName}
     *
     * @param responseParams The original HttpResponseParams
     * @return List of modified HttpResponseParams, one for each operation (empty if not valid persisted GraphQL)
     */
    public List<HttpResponseParams> parsePersistedGraphQLResponseParam(HttpResponseParams responseParams) {
        List<HttpResponseParams> responseParamsList = new ArrayList<>();

        try {
            // Get request payload and URL
            String requestPayload = responseParams.getRequestParams().getPayload();
            String url = responseParams.getRequestParams().getURL();

            // Quick validation checks
            if (requestPayload == null || requestPayload.isEmpty()) {
                return responseParamsList;
            }

            if (!requestPayload.contains(OPERATION_NAME)) {
                return responseParamsList;
            }

            // Only process GraphQL endpoints
            if (!HttpResponseParams.isGraphQLEndpoint(url)) {
                return responseParamsList;
            }

            // Parse the JSON payload - it's directly an array of operations
            Object parsedPayload = JSON.parse(requestPayload);

            // Handle both single object and array
            List<Map> operations = new ArrayList<>();
            if (parsedPayload instanceof List) {
                // Array of operations: [{...}, {...}]
                List jsonList = (List) parsedPayload;
                for (Object item : jsonList) {
                    if (item instanceof Map) {
                        operations.add((Map) item);
                    }
                }
            } else if (parsedPayload instanceof Map) {
                // Single operation: {...}
                operations.add((Map) parsedPayload);
            } else {
                return responseParamsList;
            }

            // Return empty if no operations found
            if (operations.isEmpty()) {
                return responseParamsList;
            }

            // Process each operation
            for (Map operation : operations) {
                if (isValidPersistedGraphQLPayload(operation)) {
                    HttpResponseParams modifiedResponse = createModifiedResponseParam(responseParams, operation);
                    if (modifiedResponse != null) {
                        responseParamsList.add(modifiedResponse);
                    }
                } else {
                    loggerMaker.infoAndAddToDb("Skipping invalid persisted GraphQL operation: missing operationName");
                }
            }

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error parsing persisted GraphQL payload: " + e.getMessage());
            return new ArrayList<>();
        }

        return responseParamsList;
    }

    /**
     * Validates if the operation map contains required fields for persisted GraphQL
     *
     * @param operation The operation map
     * @return true if valid, false otherwise
     */
    private boolean isValidPersistedGraphQLPayload(Map operation) {
        if (operation == null) {
            return false;
        }

        // Check for operationName field (required)
        Object operationName = operation.get(OPERATION_NAME);
        if (!(operationName instanceof String) || ((String) operationName).isEmpty()) {
            return false;
        }

        // Check for extensions.persistedQuery (optional but good indicator)
        Object extensions = operation.get(EXTENSIONS);
        if (extensions instanceof Map) {
            Map extMap = (Map) extensions;
            if (extMap.containsKey(PERSISTED_QUERY)) {
                return true;
            }
        }

        // If no extensions, still valid if has operationName
        return true;
    }

    /**
     * Creates a modified HttpResponseParams with transformed URL
     *
     * @param responseParams Original response params
     * @param operation Operation map containing operationName
     * @return Modified HttpResponseParams or null if operation is invalid
     */
    private HttpResponseParams createModifiedResponseParam(HttpResponseParams responseParams, Map operation) {
        try {
            String operationName = (String) operation.get(OPERATION_NAME);
            if (operationName == null || operationName.trim().isEmpty()) {
                return null;
            }

            // Extract operation type from operationName suffix
            String operationType = extractOperationType(operationName);

            // Build new URL
            String originalUrl = responseParams.getRequestParams().getURL();
            String baseUrl = originalUrl.split("\\?")[0];

            // Construct new path: /graphql/{operationType}/{operationName}
            String newUrl = baseUrl;
            if (!baseUrl.endsWith("/")) {
                newUrl += "/";
            }
            newUrl += operationType + "/" + operationName;

            // Re-add query parameters if present
            if (originalUrl.contains("?")) {
                String[] urlParts = originalUrl.split("\\?", 2);
                if (urlParts.length > 1) {
                    newUrl += "?" + urlParts[1];
                }
            }

            // Create a copy and set new URL
            HttpResponseParams httpResponseParamsCopy = responseParams.copy();
            httpResponseParamsCopy.getRequestParams().setUrl(newUrl);

            // Keep original payload structure (don't modify)
            httpResponseParamsCopy.getRequestParams().setPayload(JSON.toJSONString(operation));

            return httpResponseParamsCopy;

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error creating modified response param: " + e.getMessage());
            return null;
        }
    }

    /**
     * Extracts operation type from operationName by checking the suffix
     * Examples:
     * - "SharedUIWeb_TripItineraryQuery" → "query"
     * - "PolarisMutation" → "mutation"
     * - "LiveDataSubscription" → "subscription"
     * - "GetUser" → "query" (default)
     *
     * @param operationName The operation name
     * @return Operation type (query, mutation, or subscription)
     */
    private String extractOperationType(String operationName) {
        if (operationName == null || operationName.isEmpty()) {
            return "query";
        }

        // Convert to lowercase for case-insensitive matching
        String lowerOpName = operationName.toLowerCase();

        // Check for common suffixes
        if (lowerOpName.endsWith("mutation")) {
            return "mutation";
        } else if (lowerOpName.endsWith("subscription")) {
            return "subscription";
        } else if (lowerOpName.endsWith("query")) {
            return "query";
        }

        // Default to query (safest assumption for persisted queries)
        return "query";
    }
}
