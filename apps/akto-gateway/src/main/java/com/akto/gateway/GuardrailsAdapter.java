package com.akto.gateway;

import java.util.Map;

/**
 * Strategy interface for different guardrails validation adapters
 * Adapters are responsible for format conversion only - extracting/formatting
 * the payload to be sent to the guardrails service API
 */
public interface GuardrailsAdapter {

    /**
     * Format the request to prepare the payload for guardrails API call
     * Returns the API request format: {"payload": "string", "contextSource": "string"}
     *
     * @param url The request URL
     * @param path The request path
     * @param request The request object containing method, headers, body, queryParams
     * @param response The response object (optional, can be null)
     * @return Formatted API request payload ready to be sent to /validate/request endpoint
     */
    Map<String, Object> formatRequest(String url, String path,
                                       Map<String, Object> request,
                                       Map<String, Object> response);

    /**
     * Get the name/type of this adapter
     * @return Adapter name (e.g., "standard", "lightllm")
     */
    String getAdapterName();
}
