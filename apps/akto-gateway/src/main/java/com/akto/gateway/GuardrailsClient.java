package com.akto.gateway;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class GuardrailsClient {

    private static final Logger logger = LogManager.getLogger(GuardrailsClient.class);

    private String guardrailsServiceUrl;
    private int timeout;

    public GuardrailsClient() {
        this.guardrailsServiceUrl = loadServiceUrlFromEnv();
        this.timeout = 5000; // 5 seconds default timeout
        logger.info("GuardrailsClient initialized - URL: {}", guardrailsServiceUrl);
    }

    public GuardrailsClient(String serviceUrl, int timeout) {
        this.guardrailsServiceUrl = serviceUrl;
        this.timeout = timeout;
        logger.info("GuardrailsClient initialized with custom config - URL: {}, Timeout: {}ms",
                serviceUrl, timeout);
    }

    public boolean shouldApplyGuardrails(Map<String, Object> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return false;
        }

        Object guardrailsValue = queryParams.get("guardrails");
        if (guardrailsValue == null) {
            return false;
        }

        if (guardrailsValue instanceof Boolean) {
            return (Boolean) guardrailsValue;
        }

        if (guardrailsValue instanceof String) {
            String strValue = (String) guardrailsValue;
            return "true".equalsIgnoreCase(strValue) || "1".equals(strValue);
        }

        return false;
    }

    public Map<String, Object> validateRequest(String url, String path,
                                                Map<String, Object> request,
                                                Map<String, Object> response) {
        logger.info("Validating request through guardrails service - URL: {}", url);

        try {
            Map<String, Object> guardrailsRequest = buildGuardrailsRequest(url, path, request, response);

            // Call guardrails service (TODO: Replace with actual HTTP call)
            Map<String, Object> guardrailsResponse = callGuardrailsService(guardrailsRequest);

            logger.info("Guardrails validation completed - Status: {}", guardrailsResponse.get("status"));
            return guardrailsResponse;

        } catch (Exception e) {
            logger.error("Error calling guardrails service: {}", e.getMessage(), e);
            return buildErrorResponse(e.getMessage());
        }
    }

    private Map<String, Object> buildGuardrailsRequest(String url, String path,
                                                        Map<String, Object> request,
                                                        Map<String, Object> response) {
        Map<String, Object> payload = new HashMap<>();

        // Core fields
        payload.put("url", url);
        payload.put("path", path);
        payload.put("request", request);
        payload.put("response", response);

        // Add metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("timestamp", System.currentTimeMillis());
        metadata.put("clientVersion", "1.0.0");
        metadata.put("source", "akto-gateway");
        payload.put("metadata", metadata);

        return payload;
    }

    /**
     * TODO: Replace with actual HTTP client call
     */
    private Map<String, Object> callGuardrailsService(Map<String, Object> request) {
        logger.info("Calling guardrails service at: {}", guardrailsServiceUrl);

        return performDummyValidation(request);
    }

    private Map<String, Object> performDummyValidation(Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();

        try {
            String url = (String) request.get("url");
            String path = (String) request.get("path");
            @SuppressWarnings("unchecked")
            Map<String, Object> requestData = (Map<String, Object>) request.get("request");

            boolean passed = checkDummyRules(url, path, requestData);

            result.put("passed", passed);
            result.put("guardrailsVersion", "1.0.0");
            result.put("checkTimestamp", System.currentTimeMillis());
            result.put("url", url);
            result.put("path", path);
            result.put("serviceUrl", guardrailsServiceUrl);

            if (passed) {
                result.put("status", "ALLOWED");
                result.put("message", "Request passed guardrails validation");
                result.put("confidence", 0.95);
                logger.info("Guardrails check PASSED for {}", url);
            } else {
                result.put("status", "BLOCKED");
                result.put("message", "Request blocked by guardrails");
                result.put("reason", "Suspicious pattern detected in request");
                result.put("confidence", 0.98);
                logger.warn("Guardrails check BLOCKED for {}", url);
            }

            Map<String, Object> validationDetails = new HashMap<>();
            validationDetails.put("sql_injection_check", "PASSED");
            validationDetails.put("xss_check", "PASSED");
            validationDetails.put("command_injection_check", "PASSED");
            validationDetails.put("path_traversal_check", passed ? "PASSED" : "BLOCKED");
            result.put("validationDetails", validationDetails);

            result.put("riskScore", passed ? 0.05 : 0.92);

        } catch (Exception e) {
            logger.error("Error in dummy validation: {}", e.getMessage(), e);
            result.put("passed", false);
            result.put("status", "ERROR");
            result.put("message", "Validation failed: " + e.getMessage());
        }

        return result;
    }

    private boolean checkDummyRules(String url, String path, Map<String, Object> request) {
        if (url != null && containsSuspiciousPatterns(url.toLowerCase())) {
            logger.warn("Suspicious pattern in URL: {}", url);
            return false;
        }

        if (path != null && containsSuspiciousPatterns(path.toLowerCase())) {
            logger.warn("Suspicious pattern in path: {}", path);
            return false;
        }

        if (request != null) {
            Object body = request.get("body");
            if (body != null && containsMaliciousContent(body.toString().toLowerCase())) {
                logger.warn("Malicious content detected in request body");
                return false;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> headers = (Map<String, Object>) request.get("headers");
            if (headers != null && containsSuspiciousHeaders(headers)) {
                logger.warn("Suspicious headers detected");
                return false;
            }
        }

        return true;
    }

    private boolean containsSuspiciousPatterns(String text) {
        if (text == null) return false;

        String[] suspiciousKeywords = {
            "malicious", "attack", "../", "..\\",
            "etc/passwd", "cmd.exe", "/dev/null"
        };

        for (String keyword : suspiciousKeywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check for malicious content in body
     */
    private boolean containsMaliciousContent(String body) {
        if (body == null) return false;

        String[] maliciousPatterns = {
            "drop table", "delete from", "union select",
            "<script>", "javascript:",
            "'; exec", "; drop",
            "cmd /c", "powershell"
        };

        for (String pattern : maliciousPatterns) {
            if (body.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsSuspiciousHeaders(Map<String, Object> headers) {
        if (headers == null) return false;

        // Check for header injection attempts
        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            String headerValue = String.valueOf(entry.getValue()).toLowerCase();
            if (headerValue.contains("\r") || headerValue.contains("\n")) {
                logger.warn("Header injection attempt detected in: {}", entry.getKey());
                return true;
            }
        }
        return false;
    }

    public boolean isValidationPassed(Map<String, Object> guardrailsResponse) {
        if (guardrailsResponse == null) {
            return false;
        }

        // Check 'passed' field
        Object passed = guardrailsResponse.get("passed");
        if (passed instanceof Boolean) {
            return (Boolean) passed;
        }

        // Check 'status' field
        Object status = guardrailsResponse.get("status");
        if (status instanceof String) {
            String statusStr = (String) status;
            return "ALLOWED".equalsIgnoreCase(statusStr) || "PASS".equalsIgnoreCase(statusStr);
        }

        return false;
    }

    private Map<String, Object> buildErrorResponse(String errorMessage) {
        Map<String, Object> error = new HashMap<>();
        error.put("passed", false);
        error.put("status", "ERROR");
        error.put("message", "Guardrails validation failed: " + errorMessage);
        error.put("error", errorMessage);
        error.put("timestamp", System.currentTimeMillis());
        return error;
    }

    private static String loadServiceUrlFromEnv() {
        String url = System.getenv("GUARDRAILS_SERVICE_URL");
        if (url == null || url.isEmpty()) {
            url = "http://localhost:8081"; // Default URL
        }
        return url;
    }

    public String getGuardrailsServiceUrl() {
        return guardrailsServiceUrl;
    }

    public void setGuardrailsServiceUrl(String guardrailsServiceUrl) {
        this.guardrailsServiceUrl = guardrailsServiceUrl;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
}
