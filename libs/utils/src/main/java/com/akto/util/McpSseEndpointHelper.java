package com.akto.util;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.OriginalHttpRequest;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;

import java.net.URL;
import java.util.Collections;
import java.util.HashMap;

/**
 * Utility class for handling MCP SSE endpoints
 * This class provides centralized functionality for adding SSE endpoint headers
 * to requests that target MCP collections.
 */
public class McpSseEndpointHelper {
    
    private static final LoggerMaker loggerMaker = new LoggerMaker(McpSseEndpointHelper.class, LogDb.TESTING);
    
    /**
     * Adds SSE endpoint header for MCP collections to enable dynamic SSE endpoints
     * 
     * @param request The HTTP request to modify
     * @param apiCollectionId The collection ID
     */
    public static void addSseEndpointHeader(OriginalHttpRequest request, int apiCollectionId) {
        if (request == null) {
            loggerMaker.debug("Request is null, skipping SSE endpoint header addition");
            return;
        }
        
        try {
            // Check if this is an MCP collection by looking up the ApiCollection
            ApiCollection apiCollection = ApiCollectionsDao.instance.findOne(
                Filters.eq(ApiCollection.ID, apiCollectionId)
            );
            
            if (apiCollection != null && apiCollection.getSseCallbackUrl() != null && !apiCollection.getSseCallbackUrl().isEmpty()) {
                String sseEndpoint = apiCollection.getSseCallbackUrl();
                
                // If it's a full URL, extract just the path
                if (!sseEndpoint.startsWith("/")) {
                    try {
                        URL url = new URL(sseEndpoint);
                        sseEndpoint = url.getPath() + (url.getQuery() != null ? "?" + url.getQuery() : "");
                        loggerMaker.debug("Extracted SSE endpoint path from full URL: {}", sseEndpoint);
                    } catch (Exception e) {
                        // If URL parsing fails, use as-is
                        loggerMaker.warn("Failed to parse SSE callback URL: {}. Using as-is.", sseEndpoint);
                    }
                }
                
                // Add SSE endpoint header for MCP requests
                if (request.getHeaders() == null) {
                    request.setHeaders(new HashMap<>());
                }
                request.getHeaders().put("x-akto-sse-endpoint", Collections.singletonList(sseEndpoint));
                
                loggerMaker.debug("Added SSE endpoint header: {} for collection: {}", sseEndpoint, apiCollectionId);
            } else {
                loggerMaker.debug("Collection {} is not an MCP collection or has no SSE callback URL", apiCollectionId);
            }
        } catch (Exception e) {
            loggerMaker.warn("Failed to add SSE endpoint header for collection {}: {}", apiCollectionId, e.getMessage());
        }
    }

    /**
     * Detects if the given server URL supports SSE (text/event-stream)
     * @param serverUrl The server URL to check
     * @return true if SSE is supported, false otherwise
     */
    public static boolean detectSseSupport(String serverUrl) {
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(serverUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.connect();
            String contentType = conn.getHeaderField("Content-Type");
            boolean isSse = conn.getResponseCode() == 200 && contentType != null && contentType.toLowerCase().contains("text/event-stream");
            conn.disconnect();
            return isSse;
        } catch (Exception e) {
            loggerMaker.error("Error checking SSE support: " + e.getMessage(), LogDb.TESTING);
            return false;
        }
    }
}