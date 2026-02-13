package com.akto.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Standard guardrails adapter that extracts payload from request body
 * Used for standard guardrails flow (guardrails=true)
 *
 * This adapter's sole responsibility is format conversion - extracting the payload
 * from body and preparing the API request payload for /validate/request endpoint
 */
public class StandardGuardrailsAdapter implements GuardrailsAdapter {

    private static final Logger logger = LogManager.getLogger(StandardGuardrailsAdapter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public StandardGuardrailsAdapter(GuardrailsClient guardrailsClient) {
        logger.info("StandardGuardrailsAdapter initialized");
    }

    @Override
    public Map<String, Object> formatRequest(String url, String path,
                                              Map<String, Object> request,
                                              Map<String, Object> response) {
        logger.info("Standard adapter: extracting payload and formatting API request");

        // Extract payload from request body
        String payload = extractPayload(request);

        logger.debug("Extracted standard payload (length: {})",
                payload != null ? payload.length() : 0);

        // Extract contextSource from request, default to "AGENTIC" if not provided
        String contextSource = extractContextSource(request);

        // Format the API request for /validate/request endpoint
        Map<String, Object> apiRequest = new HashMap<>();
        apiRequest.put("payload", payload != null ? payload : "");
        apiRequest.put("contextSource", contextSource);

        logger.debug("Formatted API request for guardrails service with contextSource: {}", contextSource);
        return apiRequest;
    }

    @Override
    public String getAdapterName() {
        return "standard";
    }

    /**
     * Extract payload from request map
     * Returns the body as-is (string or converted to JSON string)
     */
    private String extractPayload(Map<String, Object> request) {
        if (request == null) {
            return null;
        }

        Object body = request.get("body");
        if (body == null) {
            return null;
        }

        try {
            if (body instanceof String) {
                return (String) body;
            } else {
                // Convert object to JSON string
                return objectMapper.writeValueAsString(body);
            }
        } catch (Exception e) {
            logger.warn("Failed to extract payload: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract contextSource from request map
     * Returns the contextSource value if present, defaults to "AGENTIC"
     */
    private String extractContextSource(Map<String, Object> request) {
        if (request == null) {
            return "AGENTIC";
        }

        Object contextSource = request.get("contextSource");
        if (contextSource instanceof String) {
            return (String) contextSource;
        }

        return "AGENTIC";
    }
}
