package com.akto.agent;

import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.testing.AgentConversationResult;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.testing.ApiExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AgentClient {
    
    private static final LoggerMaker loggerMaker = new LoggerMaker(AgentClient.class, LogDb.TESTING);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final String agentBaseUrl;
    private final TestingRunConfig testingRunConfig;
    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    
    public AgentClient(String agentBaseUrl) {
        this.agentBaseUrl = agentBaseUrl.endsWith("/") ? agentBaseUrl.substring(0, agentBaseUrl.length() - 1) : agentBaseUrl;
        this.testingRunConfig = new TestingRunConfig();
    }
    
    public AgentClient(String agentBaseUrl, TestingRunConfig testingRunConfig) {
        this.agentBaseUrl = agentBaseUrl.endsWith("/") ? agentBaseUrl.substring(0, agentBaseUrl.length() - 1) : agentBaseUrl;
        this.testingRunConfig = testingRunConfig;
    }

    public TestResult executeAgenticTest(RawApi rawApi) throws Exception {
        String conversationId = UUID.randomUUID().toString();
        String prompts = rawApi.getRequest().getHeaders().get("x-agent-conversations").get(0);
        List<String> promptsList = Arrays.asList(prompts.split(","));
        
        try {
            List<AgentConversationResult> conversationResults = processConversations(promptsList, conversationId);
            
            boolean isVulnerable = conversationResults.get(conversationResults.size() - 1).isValidation();
            List<String> errors = new ArrayList<>();
            
            TestResult testResult = new TestResult();
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
            loggerMaker.errorAndAddToDb("Error executing agentic test: " + e.getMessage(), LogDb.TESTING);
            
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
    
    public List<AgentConversationResult> processConversations(List<String> prompts, String conversationId) throws Exception {
        List<AgentConversationResult> results = new ArrayList<>();
        
        for (String prompt : prompts) {
            try {
                AgentConversationResult result = sendChatRequest(prompt, conversationId);
                results.add(result);
                
                if (!result.isValidation()) {
                    loggerMaker.infoAndAddToDb("Validation failed for prompt: " + prompt + ", breaking conversation loop");
                    break;
                }
                
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error processing prompt: " + prompt + ", error: " + e.getMessage(), LogDb.TESTING);
                throw e;
            }
        }
        
        return results;
    }
    
    public AgentConversationResult sendChatRequest(String prompt, String conversationId) throws Exception {
        OriginalHttpRequest request = buildChatRequest(prompt);
        OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, testingRunConfig, false, new ArrayList<>(), false);
        
        if (response.getStatusCode() != 200) {
            throw new Exception("Agent server returned status code: " + response.getStatusCode() + ", body: " + response.getBody());
        }
        
        return parseResponse(response.getBody(), conversationId, prompt);
    }
    
    private OriginalHttpRequest buildChatRequest(String prompt) {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", Arrays.asList("application/json"));
        headers.put("Accept", Arrays.asList("application/json"));
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("prompt", prompt);
        
        String body;
        try {
            body = objectMapper.writeValueAsString(requestBody);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error serializing request body: " + e.getMessage(), LogDb.TESTING);
            body = "{\"prompt\":\"" + prompt.replace("\"", "\\\"") + "\"}";
        }
        
        return new OriginalHttpRequest(
            agentBaseUrl + "/chat",
            null,
            "POST",
            body,
            headers,
            null
        );
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
            
            return new AgentConversationResult(conversationId, originalPrompt, response, conversation, timestamp, validation);
            
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error parsing agent response: " + e.getMessage() + ", response body: " + responseBody, LogDb.TESTING);
            throw new Exception("Failed to parse agent response: " + e.getMessage(), e);
        }
    }

    private void storeConversationResults(List<AgentConversationResult> conversationResults) {
        try {
            dataActor.storeConversationResults(conversationResults);
        } catch (Exception e) {
            loggerMaker.error("Error storing conversation results: " + e.getMessage(), LogDb.TESTING);
        }
    }

    public static boolean isRawApiValidForAgenticTest(RawApi rawApi) {
        return rawApi.getRequest().getHeaders().containsKey("x-agent-conversations");
    }
    
    public boolean performHealthCheck() {
        try {
            OriginalHttpRequest healthRequest = buildHealthCheckRequest();
            OriginalHttpResponse response = ApiExecutor.sendRequest(healthRequest, true, testingRunConfig, false, new ArrayList<>(), true);
            
            return response.getStatusCode() == 200;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Agent health check failed with exception: " + e.getMessage(), LogDb.TESTING);
            return false;
        }
    }
    public void initializeAgent(String sseUrl, String authorizationToken) {
        try {
            OriginalHttpRequest initRequest = buildInitializeRequest(sseUrl, authorizationToken);
            ApiExecutor.sendRequest(initRequest, true, testingRunConfig, false, new ArrayList<>(), true);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Agent initialization failed with exception: " + e.getMessage(), LogDb.TESTING);
        }
    }
    
    private OriginalHttpRequest buildHealthCheckRequest() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Accept", Arrays.asList("application/json"));
        
        return new OriginalHttpRequest(
            agentBaseUrl + "/health",
            null,
            "GET",
            null,
            headers,
            null
        );
    }
    
    private OriginalHttpRequest buildInitializeRequest(String sseUrl, String authorizationToken) {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", Arrays.asList("application/json"));
        headers.put("Accept", Arrays.asList("application/json"));
       
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("sseUrl", sseUrl);
        requestBody.put("authorization", authorizationToken);
        
        String body;
        try {
            body = objectMapper.writeValueAsString(requestBody);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error serializing initialize request body: " + e.getMessage(), LogDb.TESTING);
            body = "{\"sseUrl\":\"" + sseUrl.replace("\"", "\\\"") + "\"}";
        }
        
        return new OriginalHttpRequest(
            agentBaseUrl + "/initializeMCP",
            null,
            "POST",
            body,
            headers,
            null
        );
    }
}
