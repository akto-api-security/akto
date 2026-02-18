package com.akto.agent;

import com.akto.dao.testing.AgentConversationResultDao;
import com.akto.dto.RawApi;
import com.akto.dto.testing.AgentConversationResult;
import com.akto.dto.testing.GenericAgentConversation;
import com.akto.dto.testing.GenericAgentConversation.ConversationType;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunConfig;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import okhttp3.*;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.akto.util.http_util.CoreHTTPClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.akto.agent.AgenticUtils.getTestModeFromRole;

public class AgentClient {
    
    private static final LoggerMaker loggerMaker = new LoggerMaker(AgentClient.class, LogDb.TESTING);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Custom HTTP client with 2-minute timeout for agent requests
    private static final OkHttpClient agentHttpClient = CoreHTTPClient.client.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS) // 2 minutes timeout
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    
    private final String agentBaseUrl;
    
    public AgentClient(String agentBaseUrl) {
        this.agentBaseUrl = agentBaseUrl.endsWith("/") ? agentBaseUrl.substring(0, agentBaseUrl.length() - 1) : agentBaseUrl;
    }
    
    public AgentClient(String agentBaseUrl, TestingRunConfig testingRunConfig) {
        this.agentBaseUrl = agentBaseUrl.endsWith("/") ? agentBaseUrl.substring(0, agentBaseUrl.length() - 1) : agentBaseUrl;
        // testingRunConfig parameter kept for backward compatibility but not used
    }

    public TestResult executeAgenticTest(RawApi rawApi, int apiCollectionId) throws Exception {
        String conversationId = UUID.randomUUID().toString();
        List<String> promptsList = rawApi.getConversationsList();
        String testMode = getTestModeFromRole();
        
        try {
            /*
             * the rawApi already has been modified by the testRole, 
             * and should have the updated auth request headers
             */
            AgenticUtils.checkAndInitializeAgent(conversationId, rawApi, apiCollectionId);
        } catch(Exception e){
        }

        try {
            List<AgentConversationResult> conversationResults = processConversations(promptsList, conversationId, testMode);
            
            boolean isVulnerable = conversationResults.get(conversationResults.size() - 1).isValidation();
            List<String> errors = new ArrayList<>();
            
            TestResult testResult = new TestResult();
            // TODO: Fill in message field
            testResult.setMessage(null);
            testResult.setConversationId(conversationId);
            testResult.setResultTypeAgentic(true);
            testResult.setVulnerable(isVulnerable);
            testResult.setConfidence(TestResult.Confidence.HIGH);
            testResult.setErrors(errors);
            testResult.setPercentageMatch(isVulnerable ? 0.0 : 100.0);
            
            // Store conversation results in MongoDB
            storeConversationResults(conversationResults);
            
            return testResult;
            
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error executing agentic test: " + e.getMessage());
            
            TestResult errorResult = new TestResult();
            errorResult.setMessage("Agentic test execution failed: " + e.getMessage());
            errorResult.setConversationId(conversationId);
            errorResult.setResultTypeAgentic(true);
            errorResult.setVulnerable(false);
            errorResult.setConfidence(TestResult.Confidence.LOW);
            errorResult.setErrors(Arrays.asList(e.getMessage()));
            errorResult.setPercentageMatch(0.0);
            
            return errorResult;
        }
    }
    
    private List<AgentConversationResult> processConversations(List<String> prompts, String conversationId, String testMode) throws Exception {
        List<AgentConversationResult> results = new ArrayList<>();
        int index = 0;
        int totalRequests = prompts.size();
        for (String prompt : prompts) {
            index++;
            try {
                AgentConversationResult result = sendChatRequest(prompt, conversationId, testMode, index == totalRequests);
                results.add(result);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error processing prompt: " + prompt + ", error: " + e.getMessage());
                throw e;
            }
        }
        
        return results;
    }
    
    private AgentConversationResult sendChatRequest(String prompt, String conversationId, String testMode, boolean isLastRequest) throws Exception {
        Request request = buildOkHttpChatRequest(prompt, conversationId, isLastRequest, null, ConversationType.TEST_EXECUTION_RESULT, "", "", "");
        
        try (Response response = agentHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                throw new Exception("Agent server returned status code: " + response.code() + ", body: " + responseBody);
            }
            
            String responseBody = response.body() != null ? response.body().string() : "";
            return parseResponse(responseBody, conversationId, prompt);
        }
    }
    
    private Request buildOkHttpChatRequest(String prompt, String conversationId, boolean isLastRequest, String chatUrl, ConversationType conversationType, String accessTokenForRequest, String contextString, String userEmail) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("prompt", prompt);
        requestBody.put("conversationId", conversationId);
        requestBody.put("isLastRequest", isLastRequest);
        if (conversationType != null) {
            requestBody.put("conversationType", conversationType);
        }
        requestBody.put("contextString", contextString);

        // Add user email to request body if available
        if (userEmail != null && !userEmail.isEmpty()) {
            requestBody.put("userEmail", userEmail);
        }

        String url = agentBaseUrl + (chatUrl != null ? chatUrl :  "/chat");
        
        String body;
        try {
            body = objectMapper.writeValueAsString(requestBody);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error serializing request body: " + e.getMessage());
            body = "{\"prompt\":\"" + prompt.replace("\"", "\\\"") + "\"}";
        }
        
        RequestBody requestBodyObj = RequestBody.create(body, MediaType.parse("application/json"));
        
        return new Request.Builder()
                .url(url)
                .post(requestBodyObj)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("x-akto-token", accessTokenForRequest)
                .build();
    }
    
    
    private AgentConversationResult parseResponse(String responseBody, String conversationId, String originalPrompt) throws Exception {
        try {
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            
            String response = jsonNode.has("response") ? jsonNode.get("response").asText() : null;
            int timestamp = jsonNode.has("timestamp") ? jsonNode.get("timestamp").intValue() : 0;
            
            List<String> conversation = new ArrayList<>();
            if (jsonNode.has("conversation") && jsonNode.get("conversation").isArray()) {
                for (JsonNode convNode : jsonNode.get("conversation")) {
                    conversation.add(convNode.asText());
                }
            }
            
            boolean validation = false;
            if (jsonNode.has("validation")) {
                validation = jsonNode.get("validation").asBoolean() || false;
            }
            String validationMessage = null;
            String remediationMessage = null;
            if(validation) {
                validationMessage = jsonNode.get("validationMessage").asText();
                remediationMessage = jsonNode.get("remediationMessage").asText();
            }

            String finalSentPrompt = null;
            if(jsonNode.has("finalSentPrompt")) {
                finalSentPrompt = jsonNode.get("finalSentPrompt").asText();
            }
            
            return new AgentConversationResult(conversationId, originalPrompt, response, conversation, timestamp, validation, validationMessage, finalSentPrompt, remediationMessage);
            
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error parsing agent response: " + e.getMessage() + ", response body: " + responseBody);
            throw new Exception("Failed to parse agent response: " + e.getMessage(), e);
        }
    }

    private void storeConversationResults(List<AgentConversationResult> conversationResults) {
        try {
            AgentConversationResultDao.instance.insertMany(conversationResults);
            loggerMaker.infoAndAddToDb("Stored " + conversationResults.size() + " conversation results in MongoDB");
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error storing conversation results: " + e.getMessage());
        }
    }

    public static boolean isRawApiValidForAgenticTest(RawApi rawApi) {
        List<String> temp = rawApi.getConversationsList();
        return (temp != null && !temp.isEmpty());
    }
    
    public boolean performHealthCheck() {
        try {
            Request healthRequest = buildOkHttpHealthCheckRequest();
            try (Response response = agentHttpClient.newCall(healthRequest).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Agent health check failed with exception: " + e.getMessage());
            return false;
        }
    }

    public void initializeAgent(String sseUrl, String authorizationToken) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("sseUrl", sseUrl);
        requestBody.put("authorization", authorizationToken);
        initializeAgent(requestBody);
    }

    public void initializeAgent(String sessionUrl, String requestHeaders, String apiRequestBody, String conversationId) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("sessionUrl", sessionUrl);
        requestBody.put("requestHeaders", requestHeaders);
        requestBody.put("requestBody", apiRequestBody);
        requestBody.put("conversationId", conversationId);
        initializeAgent(requestBody);
    }

    public void initializeAgent(Map<String, Object> requestBody) {
        try {
            Request initRequest = buildOkHttpInitializeRequest(requestBody);
            try (Response response = agentHttpClient.newCall(initRequest).execute()) {
                if (!response.isSuccessful()) {
                    loggerMaker.errorAndAddToDb("Agent initialization failed with status: " + response.code());
                }
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Agent initialization failed with exception: " + e.getMessage());
        }
    }
    
    private Request buildOkHttpHealthCheckRequest() {
        return new Request.Builder()
                .url(agentBaseUrl + "/health")
                .get()
                .addHeader("Accept", "application/json")
                .build();
    }
    
    private Request buildOkHttpInitializeRequest(Map<String, Object> requestBody) {

        String body = "";
        try {
            body = objectMapper.writeValueAsString(requestBody);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error serializing initialize request body: " + e.getMessage());
            // TODO: Fix at sse URL.
            // body = "{\"sseUrl\":\"" + sseUrl.replace("\"", "\\\"") + "\"}";
        }
        
        RequestBody requestBodyObj = RequestBody.create(body, MediaType.parse("application/json"));
        
        return new Request.Builder()
                .url(agentBaseUrl + "/initializeMCP")
                .post(requestBodyObj)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build();
    }

    // call akto's mcp server (centralized)
    public GenericAgentConversation getResponseFromMcpServer(String prompt, String conversationId, int tokensLimit, String storedTitle, ConversationType conversationType, String accessTokenForRequest, String contextString, String userEmail) throws Exception {
        Request request = buildOkHttpChatRequest(prompt, conversationId, false, "/generic_chat", conversationType, accessTokenForRequest, contextString, userEmail);
        try (Response response = agentHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                throw new Exception("MCP server returned status code: " + response.code() + ", body: " + responseBody);
            }
            String responseBody = response.body() != null ? response.body().string() : "";
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            String responseFromMcpServer = jsonNode.has("response") ? jsonNode.get("response").asText() : null;
            int timestamp = jsonNode.has("timestamp") ? jsonNode.get("timestamp").intValue() : 0;
            String finalSentPrompt = jsonNode.has("finalSentPrompt") ? jsonNode.get("finalSentPrompt").asText() : null;
            int tokensUsed = jsonNode.has("tokensUsed") ? jsonNode.get("tokensUsed").intValue() : 0;
            String title = jsonNode.has("title") ? jsonNode.get("title").asText() : null;
            if(storedTitle != null) {
                title = storedTitle;
            }
            return new GenericAgentConversation(title, conversationId, prompt, responseFromMcpServer, finalSentPrompt, timestamp, timestamp, tokensUsed, tokensLimit, conversationType);
        } catch (Exception e) {
            throw new Exception("Failed to get response from MCP server: " + e.getMessage());
        }
    }
    
}
