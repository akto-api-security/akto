package com.akto.agent;

import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.testing.AgentConversationResult;
import com.akto.dto.testing.TestResult;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import okhttp3.*;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.akto.util.http_util.CoreHTTPClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.akto.agent.AgenticUtils.getTestModeFromRole;
import static com.akto.runtime.utils.Utils.convertOriginalReqRespToString;

public class AgentClient {
    
    private static final LoggerMaker loggerMaker = new LoggerMaker(AgentClient.class, LogDb.TESTING);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private static final OkHttpClient agentHttpClient = CoreHTTPClient.client.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    
    private final String agentBaseUrl;
    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    public AgentClient(String agentBaseUrl) {
        this.agentBaseUrl = agentBaseUrl == null || agentBaseUrl.isEmpty() ? "" : agentBaseUrl.endsWith("/") ? agentBaseUrl.substring(0, agentBaseUrl.length() - 1) : agentBaseUrl;
    }

    public List<TestResult> executeAgenticTest(RawApi rawApi, int apiCollectionId) throws Exception {
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

            List<TestResult> testResults = fetchTestResultsFromUtilityServer(conversationId, isVulnerable, errors);
            if (testResults.isEmpty()) {
                TestResult testResult = new TestResult();
                testResult.setMessage(null);
                setAgenticResultFields(testResult, conversationId, isVulnerable, errors, isVulnerable ? 0.0 : 100.0, TestResult.Confidence.HIGH);
                testResults.add(testResult);
            }

            storeConversationResults(conversationResults);
            return testResults;

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error executing agentic test: " + e.getMessage());
            TestResult errorResult = new TestResult();
            errorResult.setMessage("Agentic test execution failed: " + e.getMessage());
            setAgenticResultFields(errorResult, conversationId, false, Arrays.asList(e.getMessage()), 0.0, TestResult.Confidence.LOW);
            return Collections.singletonList(errorResult);
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
        Request request = buildOkHttpChatRequest(prompt, conversationId, isLastRequest);

        Call call = agentHttpClient.newCall(request);
        call.timeout().timeout(120, TimeUnit.SECONDS);
        
        try (Response response = call.execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                throw new Exception("Agent server returned status code: " + response.code() + ", body: " + responseBody);
            }
            
            String responseBody = response.body() != null ? response.body().string() : "";
            return parseResponse(responseBody, conversationId, prompt);
        }
    }
    
    private Request buildOkHttpChatRequest(String prompt, String conversationId, boolean isLastRequest) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("prompt", prompt);
        requestBody.put("conversationId", conversationId);
        requestBody.put("isLastRequest", isLastRequest);
        
        String body;
        try {
            body = objectMapper.writeValueAsString(requestBody);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error serializing request body: " + e.getMessage());
            body = "{\"prompt\":\"" + prompt.replace("\"", "\\\"") + "\"}";
        }
        
        RequestBody requestBodyObj = RequestBody.create(body, MediaType.parse("application/json"));
        
        return new Request.Builder()
                .url(agentBaseUrl + "/chat")
                .post(requestBodyObj)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
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

    private static void setAgenticResultFields(TestResult tr, String conversationId, boolean vulnerable, List<String> errors, double percentageMatch, TestResult.Confidence confidence) {
        tr.setConversationId(conversationId);
        tr.setResultTypeAgentic(true);
        tr.setVulnerable(vulnerable);
        tr.setConfidence(confidence != null ? confidence : TestResult.Confidence.HIGH);
        tr.setErrors(errors != null ? errors : new ArrayList<>());
        tr.setPercentageMatch(percentageMatch);
    }

    private List<TestResult> fetchTestResultsFromUtilityServer(String conversationId, boolean isVulnerable, List<String> errors) {
        ApiExecutionJobStore store = ApiExecutionJobStoreRegistry.get();
        if (store == null) {
            return new ArrayList<>();
        }
        try {
            List<String> jobIds = store.getJobIdsByConversationId(conversationId);
            List<TestResult> results = new ArrayList<>();
            for (String jobId : jobIds) {
                ApiExecutionJobStore.JobEntry entry = store.get(jobId);
                if (entry == null) {
                    continue;
                }
                if (entry.getStatus() == ApiExecutionJobStore.Status.PENDING) {
                    continue;
                }
                TestResult tr = new TestResult();
                double percentageMatch = isVulnerable ? 0.0 : 100.0;
                setAgenticResultFields(tr, conversationId, isVulnerable, errors != null ? errors : new ArrayList<>(), percentageMatch, TestResult.Confidence.HIGH);
                if (entry.getStatus() == ApiExecutionJobStore.Status.COMPLETED && entry.getResponse() != null) {
                    OriginalHttpResponse ohr = entry.getResponse();
                    OriginalHttpRequest req = entry.getRequest();
                    String message = convertOriginalReqRespToString(req, ohr);
                    tr.setMessage(message);
                } else if (entry.getStatus() == ApiExecutionJobStore.Status.FAILED) {
                    tr.setMessage("API execution failed: " + (entry.getErrorMessage() != null ? entry.getErrorMessage() : "Execution failed"));
                } else {
                    continue;
                }
                results.add(tr);
            }
            return results;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error reading conversation results from store: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private void storeConversationResults(List<AgentConversationResult> conversationResults) {
        try {
            dataActor.storeConversationResults(conversationResults);
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
    
}
