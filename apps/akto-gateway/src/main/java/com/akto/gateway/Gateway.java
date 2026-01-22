package com.akto.gateway;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;


public class Gateway {

    private static final Logger logger = LogManager.getLogger(Gateway.class);
    private static Gateway instance;
    private final GuardrailsClient guardrailsClient;
    private final AktoIngestAdapter aktoIngestAdapter;

    private Gateway() {
        this.guardrailsClient = new GuardrailsClient();
        this.aktoIngestAdapter = new AktoIngestAdapter();
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

            Map<String, Object> aktoIngestData = aktoIngestAdapter.convertToAktoIngestFormat(proxyData);
            logger.info("Converted to Akto ingest format");

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

            result.put("aktoIngestData", aktoIngestData);
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

    public Map<String, Object> convertToAktoIngestFormat(Map<String, Object> proxyData) {
        return aktoIngestAdapter.convertToAktoIngestFormat(proxyData);
    }

    public GuardrailsClient getGuardrailsClient() {
        return guardrailsClient;
    }

    public AktoIngestAdapter getAktoIngestAdapter() {
        return aktoIngestAdapter;
    }
}
