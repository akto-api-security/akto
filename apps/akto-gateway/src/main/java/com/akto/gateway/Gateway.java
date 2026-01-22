package com.akto.gateway;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Central Gateway class providing gateway functionality for Akto services.
 * Handles HTTP proxy requests and guardrails integration.
 */
public class Gateway {

    private static final Logger logger = LogManager.getLogger(Gateway.class);
    private static Gateway instance;
    private final GuardrailsClient guardrailsClient;

    private Gateway() {
        this.guardrailsClient = new GuardrailsClient();
        logger.info("Gateway instance initialized");
    }

    /**
     * Get singleton instance of Gateway
     * @return Gateway instance
     */
    public static synchronized Gateway getInstance() {
        if (instance == null) {
            instance = new Gateway();
        }
        return instance;
    }

    /**
     * Process HTTP proxy request with the new structure
     * Expected format:
     * {
     *   "url": "http://example.com/api/endpoint",
     *   "path": "/api/endpoint",
     *   "request": {
     *      "method": "GET|POST|...",
     *      "headers": {},
     *      "body": "...",
     *      "queryParams": {}
     *   },
     *   "response": {
     *      "headers": {},
     *      "payload": "...",
     *      "protocol": "HTTP/1.1",
     *      "statusCode": 200,
     *      "status": "SUCCESS"
     *   }
     * }
     *
     * @param proxyData Full proxy request data
     * @return Processed response
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> processHttpProxy(Map<String, Object> proxyData) {
        logger.info("Processing HTTP proxy request");

        try {
            // Extract core fields
            String url = (String) proxyData.get("url");
            String path = (String) proxyData.get("path");
            Map<String, Object> request = (Map<String, Object>) proxyData.get("request");
            Map<String, Object> response = (Map<String, Object>) proxyData.get("response");

            // Validate required fields
            if (url == null || url.isEmpty()) {
                logger.warn("Missing required field: url");
                return buildErrorResponse("Missing required field: url");
            }

            if (path == null || path.isEmpty()) {
                logger.warn("Missing required field: path");
                return buildErrorResponse("Missing required field: path");
            }

            if (request == null) {
                logger.warn("Missing required field: request");
                return buildErrorResponse("Missing required field: request");
            }

            // Extract request details
            String method = (String) request.get("method");
            Map<String, Object> queryParams = (Map<String, Object>) request.get("queryParams");

            logger.info("Request - Method: {}, URL: {}, Path: {}", method, url, path);

            // Check if guardrails should be applied using the client
            boolean shouldApplyGuardrails = guardrailsClient.shouldApplyGuardrails(queryParams);

            Map<String, Object> guardrailsResponse = null;
            if (shouldApplyGuardrails) {
                logger.info("Guardrails enabled - calling guardrails service");

                // Delegate to GuardrailsClient
                guardrailsResponse = guardrailsClient.validateRequest(url, path, request, response);

                // Check if guardrails blocked the request
                if (guardrailsResponse != null && !guardrailsClient.isValidationPassed(guardrailsResponse)) {
                    logger.warn("Request blocked by guardrails");
                    return buildGuardrailsBlockedResponse(guardrailsResponse);
                }

                logger.info("Request passed guardrails validation");
            }

            // Process the proxy request
            Map<String, Object> processedResponse = executeProxyRequest(url, path, request, response);

            // Build successful response
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("url", url);
            result.put("path", path);
            result.put("method", method);
            result.put("guardrailsApplied", shouldApplyGuardrails);

            if (guardrailsResponse != null) {
                result.put("guardrailsResult", guardrailsResponse);
            }

            result.put("proxyResponse", processedResponse);
            result.put("timestamp", System.currentTimeMillis());

            logger.info("HTTP proxy request processed successfully");
            return result;

        } catch (Exception e) {
            logger.error("Error processing HTTP proxy request: {}", e.getMessage(), e);
            return buildErrorResponse("Error processing request: " + e.getMessage());
        }
    }

    /**
     * Execute the actual proxy request
     * TODO: Implement actual HTTP client call to target URL
     */
    private Map<String, Object> executeProxyRequest(String url, String path,
                                                     Map<String, Object> request,
                                                     Map<String, Object> response) {
        logger.info("Executing proxy request to: {}", url);

        // Dummy implementation - just echoing back the request/response
        Map<String, Object> result = new HashMap<>();
        result.put("url", url);
        result.put("path", path);
        result.put("request", request);
        result.put("response", response);
        result.put("executedAt", System.currentTimeMillis());
        result.put("status", "EXECUTED");

        // TODO: Replace with actual HTTP client call
        // Example using HttpClient (Java 11+):
        //
        // try {
        //     HttpClient httpClient = HttpClient.newHttpClient();
        //
        //     // Build request
        //     HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
        //         .uri(URI.create(url))
        //         .timeout(Duration.ofSeconds(30));
        //
        //     // Add method and body
        //     String method = (String) request.get("method");
        //     Object body = request.get("body");
        //
        //     if ("POST".equals(method) || "PUT".equals(method)) {
        //         String bodyStr = (body != null) ? body.toString() : "";
        //         requestBuilder.POST(HttpRequest.BodyPublishers.ofString(bodyStr));
        //     } else {
        //         requestBuilder.GET();
        //     }
        //
        //     // Add headers
        //     Map<String, Object> headers = (Map<String, Object>) request.get("headers");
        //     if (headers != null) {
        //         for (Map.Entry<String, Object> header : headers.entrySet()) {
        //             requestBuilder.header(header.getKey(), String.valueOf(header.getValue()));
        //         }
        //     }
        //
        //     // Send request
        //     HttpResponse<String> httpResponse = httpClient.send(
        //         requestBuilder.build(),
        //         HttpResponse.BodyHandlers.ofString()
        //     );
        //
        //     // Build response
        //     result.put("statusCode", httpResponse.statusCode());
        //     result.put("responseBody", httpResponse.body());
        //     result.put("responseHeaders", httpResponse.headers().map());
        //
        // } catch (Exception e) {
        //     logger.error("Error executing proxy request: {}", e.getMessage(), e);
        //     result.put("error", e.getMessage());
        // }

        return result;
    }

    /**
     * Build error response
     */
    private Map<String, Object> buildErrorResponse(String errorMessage) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", errorMessage);
        error.put("timestamp", System.currentTimeMillis());
        return error;
    }

    /**
     * Build response when request is blocked by guardrails
     */
    private Map<String, Object> buildGuardrailsBlockedResponse(Map<String, Object> guardrailsResponse) {
        Map<String, Object> blocked = new HashMap<>();
        blocked.put("success", false);
        blocked.put("blocked", true);
        blocked.put("reason", "Request blocked by guardrails");
        blocked.put("guardrailsResult", guardrailsResponse);
        blocked.put("timestamp", System.currentTimeMillis());
        return blocked;
    }

    /**
     * Get the guardrails client instance
     * @return GuardrailsClient instance
     */
    public GuardrailsClient getGuardrailsClient() {
        return guardrailsClient;
    }

    /**
     * Legacy method for backward compatibility
     */
    public Map<String, Object> processRequest(Map<String, Object> request) {
        logger.info("Processing request through gateway (legacy method)");
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", request);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
}
