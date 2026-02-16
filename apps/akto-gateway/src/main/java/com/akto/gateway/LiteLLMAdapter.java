package com.akto.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * LiteLLM guardrails adapter that extracts the request body directly
 * Used when akto_connector=litellm in query parameters
 *
 * This adapter's sole responsibility is format conversion - extracting the body
 * and preparing the API request payload for /validate/request endpoint
 */
public class LiteLLMAdapter implements GuardrailsAdapter {

    private static final Logger logger = LogManager.getLogger(LiteLLMAdapter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public LiteLLMAdapter(GuardrailsClient guardrailsClient) {
        logger.info("LiteLLMAdapter initialized");
    }

    @Override
    public Map<String, Object> formatRequest(String url, String path,
                                              Map<String, Object> request,
                                              Map<String, Object> response) {
        logger.info("LiteLLM adapter: extracting body and formatting API request");

        // Extract body from request (format conversion only)
        String payload = extractBody(request);

        logger.debug("Extracted LiteLLM payload (length: {})",
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
        return "litellm";
    }

    /**
     * Extract body from request map
     * Returns the body as-is (string or converted to JSON string)
     */
    private String extractBody(Map<String, Object> request) {
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
            logger.warn("Failed to extract body: {}", e.getMessage());
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
