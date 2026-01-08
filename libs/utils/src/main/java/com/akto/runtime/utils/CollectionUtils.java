package com.akto.runtime.utils;

import com.akto.dto.ApiCollection;
import com.akto.dto.HttpResponseParams;
import com.google.gson.Gson;

import java.util.*;

/**
 * CollectionUtils - Shared utility methods for API collection resolution.
 *
 * Provides common methods used by both mini-runtime and fast-discovery to ensure
 * consistent collection ID assignment.
 *
 * Methods extracted from HttpCallParser to enable code reuse.
 */
public class CollectionUtils {

    private static final Gson gson = new Gson();

    /**
     * Extract header value (case-insensitive).
     *
     * @param headers Map of header name to list of values
     * @param headerKey Header name to search for (case-insensitive)
     * @return First header value if found, null otherwise
     */
    public static String getHeaderValue(Map<String, List<String>> headers, String headerKey) {
        if (headers == null) return null;
        for (String k: headers.keySet()) {
            if (k.equalsIgnoreCase(headerKey)) {
                List<String> values = headers.getOrDefault(k, new ArrayList<>());
                if (values.size() > 0) return values.get(0);
                return null;
            }
        }
        return null;
    }

    /**
     * Get hostname for collection creation.
     *
     * For AI agent traffic (N8N, LangChain, Copilot), reconstructs the full hostname
     * by prepending bot/workflow name to the base hostname.
     * For regular traffic, returns the hostname from headers as-is.
     *
     * @param responseParam The HTTP response parameters
     * @return The hostname to use for collection creation
     */
    public static String getHostnameForCollection(HttpResponseParams responseParam) {
        // Get base hostname from headers
        String baseHostname = getHeaderValue(responseParam.getRequestParams().getHeaders(), "host");
        if (baseHostname == null || baseHostname.isEmpty()) {
            return baseHostname;
        }

        // Check if this is AI agent traffic
        String tagsJson = responseParam.getTags();
        if (tagsJson == null || tagsJson.isEmpty()) {
            return baseHostname;
        }

        // Parse tags JSON to check for AI agent markers
        try {
            Map<String, String> tags = gson.fromJson(tagsJson, Map.class);

            // Check for N8N workflow
            String n8nWorkflow = tags.get("n8n-workflow-name");
            if (n8nWorkflow != null && !n8nWorkflow.isEmpty()) {
                return n8nWorkflow + "." + baseHostname;
            }

            // Check for LangChain bot
            String langchainBot = tags.get("langchain-bot-name");
            if (langchainBot != null && !langchainBot.isEmpty()) {
                return langchainBot + "." + baseHostname;
            }

            // Check for GitHub Copilot workspace
            String copilotWorkspace = tags.get("copilot-workspace");
            if (copilotWorkspace != null && !copilotWorkspace.isEmpty()) {
                return copilotWorkspace + "." + baseHostname;
            }
        } catch (Exception e) {
            // Failed to parse tags, use base hostname
        }

        return baseHostname;
    }

    /**
     * Check if hostname-based collection should be used.
     *
     * Hostname-based collections are used when:
     * 1. ApiCollection.useHost flag is true (default)
     * 2. Source is MIRRORING
     * 3. Hostname exists and has alphabetic characters (not just numbers/symbols)
     *
     * @param hostName The hostname extracted from headers
     * @param source The traffic source (MIRRORING, HAR, etc.)
     * @return true if hostname-based collection should be used
     */
    public static boolean useHostCondition(String hostName, HttpResponseParams.Source source) {
        List<HttpResponseParams.Source> whiteListSource = Arrays.asList(HttpResponseParams.Source.MIRRORING);
        boolean hostNameCondition;
        if (hostName == null) {
            hostNameCondition = false;
        } else {
            // Check if hostname has alphabetic characters (not just numbers/symbols)
            // If lowercase equals uppercase, it means no alphabetic characters
            hostNameCondition = !(hostName.toLowerCase().equals(hostName.toUpperCase()));
        }
        return whiteListSource.contains(source) && hostNameCondition && ApiCollection.useHost;
    }
}
