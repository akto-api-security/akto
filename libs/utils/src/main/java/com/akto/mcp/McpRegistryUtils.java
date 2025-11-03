package com.akto.mcp;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class McpRegistryUtils {
    private static final LoggerMaker logger = new LoggerMaker(McpRegistryUtils.class, LogDb.RUNTIME);
    private static final String MCP_REGISTRY_API = "https://registry.modelcontextprotocol.io/v0/servers?search=";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int TIMEOUT = 5000; // 5 seconds timeout

    /**
     * Check if an MCP server is available in the registry
     * @param serverName The name of the server to search for
     * @return "available" if found in registry, null otherwise
     */
    public static String checkRegistryAvailability(String serverName) {
        if (serverName == null || serverName.isEmpty()) {
            return null;
        }

        try {
            // Extract the actual server name from hostname
            // e.g., "mcp.razorpay.com" -> "razorpay"
            String searchTerm = extractServerName(serverName);
            
            String urlString = MCP_REGISTRY_API + URLEncoder.encode(searchTerm, StandardCharsets.UTF_8.toString());
            logger.info("Checking MCP registry for: " + searchTerm + " at URL: " + urlString);
            
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT);
            connection.setReadTimeout(TIMEOUT);
            connection.setRequestProperty("Accept", "application/json");
            
            int responseCode = connection.getResponseCode();
            
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                // Parse JSON response
                JsonNode rootNode = OBJECT_MAPPER.readTree(response.toString());
                JsonNode serversNode = rootNode.get("servers");
                
                if (serversNode != null && serversNode.isArray() && serversNode.size() > 0) {
                    // Iterate through results and check if any match our domain
                    for (JsonNode serverNode : serversNode) {
                        if (matchesDomain(serverNode, serverName)) {
                            logger.info("MCP server found in registry with matching domain: " + searchTerm);
                            return "available";
                        }
                    }
                    logger.info("MCP servers found in registry but none match domain: " + serverName);
                    return null;
                }
                
                logger.info("MCP server not found in registry: " + searchTerm);
                return null;
                
            } else {
                logger.error("Failed to check MCP registry, HTTP response code: " + responseCode);
                return null;
            }
            
        } catch (Exception e) {
            logger.error("Error checking MCP registry: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Check if a server node from registry matches our domain
     * @param serverNode JSON node from registry response (contains "server" object)
     * @param hostname The hostname to match against (e.g., "mcp.razorpay.com")
     * @return true if domain matches
     */
    private static boolean matchesDomain(JsonNode serverNode, String hostname) {
        try {
            String normalizedHostname = hostname.toLowerCase().trim();
            
            // Get the "server" object from the node
            JsonNode server = serverNode.get("server");
            if (server == null || server.isNull()) {
                server = serverNode; // Fallback if structure is different
            }
            
            // Check remotes array for URL matches
            JsonNode remotesNode = server.get("remotes");
            if (remotesNode != null && remotesNode.isArray()) {
                for (JsonNode remote : remotesNode) {
                    JsonNode urlNode = remote.get("url");
                    if (urlNode != null && !urlNode.isNull()) {
                        String remoteUrl = urlNode.asText();
                        if (urlMatchesDomain(remoteUrl, normalizedHostname)) {
                            return true;
                        }
                    }
                }
            }
            
            // Also check repository URL as fallback
            JsonNode repositoryNode = server.get("repository");
            if (repositoryNode != null && !repositoryNode.isNull()) {
                JsonNode repoUrlNode = repositoryNode.get("url");
                if (repoUrlNode != null && !repoUrlNode.isNull()) {
                    String repoUrl = repoUrlNode.asText();
                    if (urlMatchesDomain(repoUrl, normalizedHostname)) {
                        return true;
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Error matching domain for server node", e);
        }
        
        return false;
    }
    
    /**
     * Extract domain from URL and check if it matches our hostname
     * @param url The URL to check (e.g., "https://mcp.notion.com/mcp")
     * @param hostname The hostname to match (e.g., "mcp.notion.com")
     * @return true if domain matches
     */
    private static boolean urlMatchesDomain(String url, String hostname) {
        if (url == null || hostname == null) {
            return false;
        }
        
        try {
            url = url.toLowerCase().trim();
            hostname = hostname.toLowerCase().trim();
            
            // Remove protocol (http://, https://)
            String urlWithoutProtocol = url.replaceFirst("^https?://", "");
            
            // Extract domain (part before first /)
            String urlDomain = urlWithoutProtocol.split("/")[0];
            
            // Remove port if present (e.g., "localhost:3000" -> "localhost")
            urlDomain = urlDomain.split(":")[0];
            
            // Check if domains match exactly (including subdomain)
            return urlDomain.equals(hostname);
            
        } catch (Exception e) {
            logger.error("Error parsing URL for domain match", e);
            return false;
        }
    }

    /**
     * Extract the server name from hostname for registry search
     * Examples:
     * - "mcp.razorpay.com" -> "razorpay"
     * - "api.stripe.com" -> "stripe"
     * - "localhost" -> "localhost"
     */
    private static String extractServerName(String hostname) {
        if (hostname == null || hostname.isEmpty()) {
            return hostname;
        }
        
        // Remove common prefixes
        hostname = hostname.replaceFirst("^(mcp\\.|api\\.|www\\.|dev\\.|staging\\.)", "");
        
        // Split by dots and get the main domain name (second-to-last part)
        String[] parts = hostname.split("\\.");
        if (parts.length >= 2) {
            // Return the second-to-last part (e.g., "razorpay" from "razorpay.com")
            return parts[parts.length - 2];
        }
        
        return hostname;
    }
}

