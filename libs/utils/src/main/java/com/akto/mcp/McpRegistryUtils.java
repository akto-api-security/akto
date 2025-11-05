package com.akto.mcp;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.http_util.CoreHTTPClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for checking MCP server availability in the official MCP Registry.
 * 
 * <p>This class provides functionality to:</p>
 * <ul>
 *   <li>Query the MCP Registry API to check if a server is listed</li>
 *   <li>Match server domains against registry entries</li>
 *   <li>Cache results to avoid repeated API calls</li>
 * </ul>
 * 
 * <p>Thread-safe with built-in caching and rate limiting considerations.</p>
 * 
 * @see <a href="https://registry.modelcontextprotocol.io/docs">MCP Registry API</a>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class McpRegistryUtils {
    
    // ==================== Constants ====================
    
    private static final LoggerMaker logger = new LoggerMaker(McpRegistryUtils.class, LogDb.RUNTIME);
    private static final String MCP_REGISTRY_API_BASE = "https://registry.modelcontextprotocol.io/v0/servers";
    private static final String SEARCH_QUERY_PARAM = "?search=";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    // HTTP client configuration
    private static final int CONNECTION_TIMEOUT_SECONDS = 5;
    private static final int READ_TIMEOUT_SECONDS = 10;
    private static final int MAX_RESPONSE_SIZE_BYTES = 1024 * 1024; // 1MB
    
    // Cache configuration
    private static final int CACHE_SIZE = 500;
    private static final long CACHE_EXPIRY_MS = TimeUnit.HOURS.toMillis(24); // 24 hours
    
    // HTTP headers
    private static final String HEADER_ACCEPT = "Accept";
    private static final String HEADER_USER_AGENT = "User-Agent";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String USER_AGENT_VALUE = "Akto-MCP-Client/1.0";
    
    // Response status
    private static final String STATUS_AVAILABLE = "available";
    
    // Common hostname prefixes to remove during extraction
    private static final String[] HOSTNAME_PREFIXES = {
        "mcp\\.", "api\\.", "www\\.", "dev\\.", "staging\\.", "test\\."
    };
    
    // JSON field names
    private static final String JSON_FIELD_SERVERS = "servers";
    private static final String JSON_FIELD_SERVER = "server";
    private static final String JSON_FIELD_REMOTES = "remotes";
    private static final String JSON_FIELD_URL = "url";
    private static final String JSON_FIELD_REPOSITORY = "repository";
    
    // ==================== Cache Implementation ====================
    
    /**
     * Thread-safe LRU cache with expiration for registry check results.
     */
    private static final Map<String, CacheEntry> registryCache = new LinkedHashMap<String, CacheEntry>(CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return size() > CACHE_SIZE || isExpired(eldest.getValue());
        }
    };
    
    /**
     * HTTP client configured for MCP Registry API calls.
     */
    private static final OkHttpClient httpClient = CoreHTTPClient.client.newBuilder()
            .connectTimeout(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build();
    
    /**
     * Cache entry with timestamp for expiration tracking.
     */
    private static class CacheEntry {
        final String value;
        final long timestamp;
        
        CacheEntry(String value) {
            this.value = value;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    /**
     * Check if cache entry has expired.
     */
    private static boolean isExpired(CacheEntry entry) {
        return entry == null || (System.currentTimeMillis() - entry.timestamp) > CACHE_EXPIRY_MS;
    }

    // ==================== Public API ====================
    
    /**
     * Check if an MCP server is available in the registry.
     * 
     * <p>This method:</p>
     * <ul>
     *   <li>Validates the input server name</li>
     *   <li>Checks the cache for recent results</li>
     *   <li>Queries the MCP Registry API if not cached</li>
     *   <li>Matches the server domain against registry entries</li>
     *   <li>Caches the result for future calls</li>
     * </ul>
     * 
     * @param serverName The hostname/domain of the server (e.g., "mcp.razorpay.com")
     * @return "available" if the server is found in the registry and domain matches, null otherwise
     * @throws IllegalArgumentException if serverName is null or empty after trim
     */
    public static String checkRegistryAvailability(String serverName) {
        // Validate input
        if (serverName == null || serverName.trim().isEmpty()) {
            logger.debug("checkRegistryAvailability called with null or empty serverName");
            return null;
        }
        
        serverName = serverName.trim().toLowerCase();
        
        // Check cache first
        synchronized (registryCache) {
            CacheEntry cached = registryCache.get(serverName);
            if (cached != null && !isExpired(cached)) {
                logger.debug("Cache hit for server: {}", serverName);
                return cached.value;
            }
        }
        
        // Cache miss or expired - query registry
        String result = queryRegistry(serverName);
        
        // Cache the result
        synchronized (registryCache) {
            registryCache.put(serverName, new CacheEntry(result));
        }
        
        return result;
    }
    
    // ==================== Private Implementation ====================
    
    /**
     * Query the MCP Registry API for the given server name.
     * 
     * @param serverName The normalized server name
     * @return "available" if found and matched, null otherwise
     */
    private static String queryRegistry(String serverName) {
        Response response = null;
        ResponseBody responseBody = null;
        
        try {
            // Extract search term from hostname
            String searchTerm = extractServerName(serverName);
            if (searchTerm == null || searchTerm.isEmpty()) {
                logger.warn("Failed to extract search term from serverName: {}", serverName);
                return null;
            }
            
            // Build request URL
            String encodedSearchTerm = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8.toString());
            String apiUrl = MCP_REGISTRY_API_BASE + SEARCH_QUERY_PARAM + encodedSearchTerm;
            
            logger.info("Querying MCP registry for server: {} (search term: {}) at URL: {}", 
                serverName, searchTerm, apiUrl);
            
            // Build HTTP request
            Request request = new Request.Builder()
                    .url(apiUrl)
                    .header(HEADER_ACCEPT, CONTENT_TYPE_JSON)
                    .header(HEADER_USER_AGENT, USER_AGENT_VALUE)
                    .get()
                    .build();
            
            // Execute request
            response = httpClient.newCall(request).execute();
            
            if (!response.isSuccessful()) {
                logger.error("MCP registry API returned error code: {} for server: {}", 
                    response.code(), serverName);
                return null;
            }
            
            responseBody = response.body();
            if (responseBody == null) {
                logger.error("MCP registry API returned null response body for server: {}", serverName);
                return null;
            }
            
            // Validate response size
            long contentLength = responseBody.contentLength();
            if (contentLength > MAX_RESPONSE_SIZE_BYTES) {
                logger.error("MCP registry response too large: {} bytes (max: {} bytes)", 
                    contentLength, MAX_RESPONSE_SIZE_BYTES);
                return null;
            }
            
            // Parse JSON response
            String responseString = responseBody.string();
            if (responseString == null || responseString.isEmpty()) {
                logger.warn("MCP registry returned empty response for server: {}", serverName);
                return null;
            }
            
            JsonNode rootNode = OBJECT_MAPPER.readTree(responseString);
            JsonNode serversNode = rootNode.get(JSON_FIELD_SERVERS);
            
            if (serversNode == null || !serversNode.isArray()) {
                logger.debug("No '{}' array found in registry response for server: {}", 
                    JSON_FIELD_SERVERS, serverName);
                return null;
            }
            
            if (serversNode.size() == 0) {
                logger.info("MCP server not found in registry: {}", searchTerm);
                return null;
            }
            
            // Check if any server matches our domain
            for (JsonNode serverNode : serversNode) {
                if (matchesDomain(serverNode, serverName)) {
                    logger.info("MCP server found in registry with matching domain: {} (search: {})", 
                        serverName, searchTerm);
                    return STATUS_AVAILABLE;
                }
            }
            
            logger.info("MCP servers found in registry (count: {}) but none match domain: {}", 
                serversNode.size(), serverName);
            return null;
            
        } catch (IOException e) {
            logger.error("IOException while querying MCP registry for server {}: {}", 
                serverName, e.getMessage(), e);
            return null;
        } catch (Exception e) {
            logger.error("Unexpected error while checking MCP registry for server {}: {}", 
                serverName, e.getMessage(), e);
            return null;
        } finally {
            // Ensure resources are closed
            if (responseBody != null) {
                responseBody.close();
            }
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * Check if a server node from the registry matches the given hostname.
     * 
     * <p>This method checks:</p>
     * <ul>
     *   <li>Remotes array for URL matches</li>
     *   <li>Repository URL as fallback</li>
     * </ul>
     * 
     * @param serverNode JSON node from registry response (may contain "server" object)
     * @param hostname The normalized hostname to match against (e.g., "mcp.razorpay.com")
     * @return true if any URL in the server node matches the hostname
     */
    private static boolean matchesDomain(JsonNode serverNode, String hostname) {
        if (serverNode == null || hostname == null || hostname.isEmpty()) {
            return false;
        }
        
        try {
            // Get the "server" object from the node (API may nest it)
            JsonNode server = serverNode.get(JSON_FIELD_SERVER);
            if (server == null || server.isNull()) {
                server = serverNode; // Fallback if structure is flat
            }
            
            // Check remotes array for URL matches
            JsonNode remotesNode = server.get(JSON_FIELD_REMOTES);
            if (remotesNode != null && remotesNode.isArray()) {
                for (JsonNode remote : remotesNode) {
                    if (remote == null) continue;
                    
                    String remoteUrl = remote.path(JSON_FIELD_URL).asText();
                    if (!remoteUrl.isEmpty() && urlMatchesDomain(remoteUrl, hostname)) {
                        logger.debug("Domain match found in remotes: {} matches {}", remoteUrl, hostname);
                        return true;
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Error while matching domain for hostname {}: {}", hostname, e.getMessage(), e);
        }
        
        return false;
    }
    
    /**
     * Extract domain from URL and check if it matches the given hostname.
     * 
     * @param url The URL to check (e.g., "https://mcp.notion.com/mcp")
     * @param hostname The hostname to match (e.g., "mcp.notion.com", without protocol)
     * @return true if the domain extracted from URL matches the hostname exactly
     */
    private static boolean urlMatchesDomain(String url, String hostname) {
        if (url == null || url.isEmpty() || hostname == null || hostname.isEmpty()) {
            return false;
        }
        
        try {
            // Remove protocol from URL (e.g., "https://mcp.notion.com/path" -> "mcp.notion.com/path")
            String domain = url.trim().replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://", "");
            
            // Extract domain part before first slash
            int slashIndex = domain.indexOf('/');
            if (slashIndex > 0) {
                domain = domain.substring(0, slashIndex);
            }
            
            // Remove port if present (e.g., "localhost:3000" -> "localhost")
            int colonIndex = domain.lastIndexOf(':');
            if (colonIndex > 0 && domain.substring(colonIndex + 1).matches("\\d+")) {
                domain = domain.substring(0, colonIndex);
            }
            
            // Compare with hostname (case-insensitive)
            return domain.equalsIgnoreCase(hostname.trim());
            
        } catch (Exception e) {
            logger.error("Error while parsing URL '{}' for domain match with hostname '{}': {}", 
                url, hostname, e.getMessage());
            return false;
        }
    }

    /**
     * Extract the server name from hostname for registry search.
     * 
     * <p>This method removes common prefixes and extracts the main domain name:</p>
     * <ul>
     *   <li>"mcp.razorpay.com" → "razorpay"</li>
     *   <li>"api.stripe.com" → "stripe"</li>
     *   <li>"www.notion.com" → "notion"</li>
     *   <li>"localhost" → "localhost"</li>
     * </ul>
     * 
     * @param hostname The full hostname to extract from
     * @return The extracted server name for search, or original hostname if extraction fails
     */
    private static String extractServerName(String hostname) {
        if (hostname == null || hostname.isEmpty()) {
            return hostname;
        }
        
        try {
            String processed = hostname.trim();
            
            // Remove common prefixes
            for (String prefix : HOSTNAME_PREFIXES) {
                processed = processed.replaceFirst("^" + prefix, "");
            }
            
            // Split by dots and get the main domain name (second-to-last part)
            String[] parts = processed.split("\\.");
            
            if (parts.length >= 2) {
                // Return the second-to-last part (e.g., "razorpay" from "razorpay.com")
                String extracted = parts[parts.length - 2];
                if (extracted != null && !extracted.isEmpty()) {
                    return extracted;
                }
            } else if (parts.length == 1 && parts[0] != null && !parts[0].isEmpty()) {
                // Single part hostname (e.g., "localhost")
                return parts[0];
            }
            
            // Fallback to original if extraction didn't work
            return hostname;
            
        } catch (Exception e) {
            logger.warn("Error extracting server name from hostname '{}': {}. Using original hostname.", 
                hostname, e.getMessage());
            return hostname;
        }
    }
    
    /**
     * Clear the registry cache. Useful for testing or forcing fresh lookups.
     * This method is thread-safe.
     */
    public static void clearCache() {
        synchronized (registryCache) {
            int size = registryCache.size();
            registryCache.clear();
            logger.info("Cleared MCP registry cache ({} entries removed)", size);
        }
    }
    
    /**
     * Get the current cache size. Useful for monitoring.
     * This method is thread-safe.
     * 
     * @return The number of entries currently in the cache
     */
    public static int getCacheSize() {
        synchronized (registryCache) {
            return registryCache.size();
        }
    }
}

