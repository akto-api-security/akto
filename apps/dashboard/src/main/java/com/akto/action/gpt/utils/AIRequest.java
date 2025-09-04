package com.akto.action.gpt.utils;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.testing.ApiExecutor;
import com.akto.util.Constants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;

public class AIRequest {
    
    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-sonnet-4-20250514";
    private static final int MAX_TOKENS = 5000;
    private static final String BETA_MCP_CLIENT = "mcp-client-2025-04-04";
    private static final String MCP_VERSION = "2023-06-01";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public String sendAnthropicRequest(String mcpUrl, String mcpType, String mcpName, 
                                     String authorizationToken, String userRole, String userContent) throws Exception {
        
        // Build the request payload
        String requestBody = buildRequestBody(mcpUrl, mcpType, mcpName, authorizationToken, userRole, userContent);
        
        // Create the HTTP request
        OriginalHttpRequest request = new OriginalHttpRequest();
        request.setMethod("POST");
        request.setUrl(ANTHROPIC_API_URL);
        request.setBody(requestBody);
        
        // Set headers
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", Arrays.asList("application/json"));
                headers.put("anthropic-version", Arrays.asList(MCP_VERSION));
        headers.put("anthropic-beta", Arrays.asList(BETA_MCP_CLIENT));
        request.setHeaders(headers);
        
        // Send the request using ApiExecutor
        OriginalHttpResponse response = ApiExecutor.sendRequest(request, false, null, false, null);
        
        if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
            return response.getBody();
        } else {
            throw new Exception("Anthropic API request failed with status code: " + 
                              response.getStatusCode() + ", Response: " + response.getBody());
        }
    }
    private String buildRequestBody(String mcpUrl, String mcpType, String mcpName, 
                                  String authorizationToken, String userRole, String userContent) throws Exception {
        
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", MODEL);
        requestBody.put("max_tokens", MAX_TOKENS);
        ArrayNode messagesArray = objectMapper.createArrayNode();
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", userRole);
        message.put("content", userContent);
        messagesArray.add(message);
        requestBody.set("messages", messagesArray);
        
        // Set MCP servers array
        ArrayNode mcpServersArray = objectMapper.createArrayNode();
        ObjectNode mcpServer = objectMapper.createObjectNode();
        mcpServer.put("type", mcpType);
        mcpServer.put("url", mcpUrl);
        mcpServer.put("name", mcpName);
        mcpServer.put("authorization_token", authorizationToken);
        mcpServersArray.add(mcpServer);
        requestBody.set("mcp_servers", mcpServersArray);
        
        return objectMapper.writeValueAsString(requestBody);
    }
    public String parseAssistantResponse(String responseBody) throws Exception {
        JsonNode responseNode = objectMapper.readTree(responseBody);
        
        // Navigate to the content array in the first choice
        JsonNode choices = responseNode.get("content");
        if (choices != null && choices.isArray() && choices.size() > 0) {
            JsonNode firstChoice = choices.get(0);
            JsonNode content = firstChoice.get("text");
            if (content != null) {
                return content.asText();
            }
        }
        
        // Alternative parsing path for different response format
        JsonNode contentArray = responseNode.path("content").path(0).path("text");
        if (!contentArray.isMissingNode()) {
            return contentArray.asText();
        }
        
        throw new Exception("Could not parse assistant response from: " + responseBody);
    }
    public static void main(String[] args) {
        try {
            String response = new AIRequest().sendAnthropicRequest("", "url", "example-mcp", "", "user", "What tools do you have available?");
            try {
                String parsedResponse = new AIRequest().parseAssistantResponse(response);
                System.out.println("Parsed response: " + parsedResponse);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
