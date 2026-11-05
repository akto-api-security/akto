package com.akto.agent;

import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.ApiCollection;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.traffic.CollectionTags;
import com.akto.util.Constants;
import com.akto.util.HttpRequestResponseUtils;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import static com.akto.agent.AgenticUtils.getTestModeFromRole;
import static com.akto.agent.AgenticUtils.isCopilotBotCollection;
import static com.akto.agent.AgenticUtils.isLiteLLMAgent;
import static com.akto.runtime.utils.Utils.convertOriginalReqRespToString;

public class AgentClient {
    
    private static final LoggerMaker loggerMaker = new LoggerMaker(AgentClient.class, LogDb.TESTING);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String JSON_KEY_PROMPT = "prompt";
    private static final String JSON_KEY_CONVERSATION_ID = "conversationId";
    private static final String JSON_KEY_IS_LAST_REQUEST = "isLastRequest";
    private static final String JSON_KEY_TESTING_RUN_RESULT_SUMMARY_ID = "testingRunResultSummaryId";
    private static final String JSON_KEY_FETCH_QUERY_RECORDS = "shouldFetchRecords";

    /** Runs this worker sent agentic tests for, so only those runs ask the agent server to consolidate learnings. */
    private static final int MAX_TRACKED_AGENTIC_SUMMARIES = 1000;
    private static final Set<String> agenticSummaryIds = Collections.synchronizedSet(Collections.newSetFromMap(
            new LinkedHashMap<String, Boolean>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_TRACKED_AGENTIC_SUMMARIES;
                }
            }));
    static final int CONSOLIDATE_MAX_ATTEMPTS = 3;
    private static final long CONSOLIDATE_BACKOFF_MILLIS = 2000;
    /** The agent server answers 202 before it starts consolidating, so the call itself is short. */
    private static final OkHttpClient consolidateHttpClient = CoreHTTPClient.client.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build();
    /** One dedicated thread; at most one call per finished run, so a small queue is plenty. */
    private static final ExecutorService consolidateExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(50),
            (task, executor) -> loggerMaker.errorAndAddToDb("consolidateLearnings: queue full, a finished run's learnings will not be consolidated"));

    private static final OkHttpClient agentHttpClient = CoreHTTPClient.client.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    private static final OkHttpClient clientWithLongTimeout = CoreHTTPClient.client.newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(Constants.AGENT_CHAT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(Constants.AGENT_CHAT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build();
    
    private final String agentBaseUrl;
    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    public AgentClient(String agentBaseUrl) {
        this.agentBaseUrl = agentBaseUrl == null || agentBaseUrl.isEmpty() ? "" : agentBaseUrl.endsWith("/") ? agentBaseUrl.substring(0, agentBaseUrl.length() - 1) : agentBaseUrl;
    }

    public List<TestResult> executeAgenticTest(RawApi rawApi, int apiCollectionId, String testingRunResultSummaryId, AgentLearningContext learningContext) throws Exception {
        String conversationId = UUID.randomUUID().toString();
        if (testingRunResultSummaryId != null && !testingRunResultSummaryId.isEmpty()) {
            agenticSummaryIds.add(testingRunResultSummaryId);
        }
        List<String> promptsList = rawApi.getConversationsList();
        String testMode = getTestModeFromRole();

        ApiCollection col = dataActor.fetchApiCollectionMeta(apiCollectionId);
        boolean isCopilot = isCopilotBotCollection(col);
        boolean shouldFetchRecords = false;
        if(!isCopilot){
            shouldFetchRecords = isLiteLLMAgent(col);
            if(shouldFetchRecords){
                ApiExecutionJobStore store = ApiExecutionJobStoreRegistry.get();
                if (store != null) {
                    store.markLiteLLMConversation(conversationId);
                }
            }
        }
        loggerMaker.infoAndAddToDb("executeAgenticTest: starting conversationId=" + conversationId + " apiCollectionId=" + apiCollectionId + " prompts=" + promptsList.size() + " copilot=" + isCopilot);

        try {
            if (isCopilot) {
                String accessToken = HttpRequestResponseUtils.getHeaderValue(
                    rawApi.getRequest() != null ? rawApi.getRequest().getHeaders() : null,
                    "Authorization"
                );
                if (accessToken == null || accessToken.isEmpty()) {
                    throw new Exception("Init failed for Copilot Bot. No Authorization token found in request");
                }
                loggerMaker.infoAndAddToDb("executeAgenticTest: initializing Copilot agent conversationId=" + conversationId);
                initializeCopilotAgent(conversationId, col, accessToken);
            } else {
                /*
                 * the rawApi already has been modified by the testRole,
                 * and should have the updated auth request headers
                 */
                AgenticUtils.checkAndInitializeAgent(conversationId, rawApi, apiCollectionId);
            }
        } catch(Exception e){
            loggerMaker.errorAndAddToDb("executeAgenticTest: init failed conversationId=" + conversationId + " err=" + e.getMessage());
            TestResult errorResult = new TestResult();
            errorResult.setMessage("Agentic test execution failed: " + e.getMessage());
            setAgenticResultFields(errorResult, conversationId, false, Arrays.asList(e.getMessage()), 0.0, TestResult.Confidence.LOW);
            return Collections.singletonList(errorResult);
        }

        try {
            List<AgentConversationResult> conversationResults = processConversations(promptsList, conversationId, testMode, testingRunResultSummaryId, shouldFetchRecords, learningContext);
            boolean isVulnerable = conversationResults.get(conversationResults.size() - 1).isValidation();
            List<String> errors = new ArrayList<>();

            List<TestResult> testResults = fetchTestResultsFromUtilityServer(conversationId, isVulnerable, errors);
            if (testResults.isEmpty()) {
                TestResult testResult = new TestResult();
                testResult.setMessage(null);
                setAgenticResultFields(testResult, conversationId, isVulnerable, errors, isVulnerable ? 0.0 : 100.0, TestResult.Confidence.HIGH);
                testResults.add(testResult);
            }

            List<String> docIds = storeConversationResults(conversationResults);
            if(shouldFetchRecords){
                linkJobIdsToConversationResultDocIds(conversationId, docIds);
            }
            return testResults;

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error executing agentic test: " + e.getMessage());
            TestResult errorResult = new TestResult();
            errorResult.setMessage("Agentic test execution failed: " + e.getMessage());
            setAgenticResultFields(errorResult, conversationId, false, Arrays.asList(e.getMessage()), 0.0, TestResult.Confidence.LOW);
            return Collections.singletonList(errorResult);
        }
    }
    
    private List<AgentConversationResult> processConversations(List<String> prompts, String conversationId, String testMode, String testingRunResultSummaryId, boolean shouldFetchRecords, AgentLearningContext learningContext) throws Exception {
        List<AgentConversationResult> results = new ArrayList<>();
        int index = 0;
        int totalRequests = prompts.size();
        for (String prompt : prompts) {
            index++;
            try {
                AgentConversationResult result = sendChatRequest(prompt, conversationId, testMode, index == totalRequests, testingRunResultSummaryId, shouldFetchRecords, learningContext);
                results.add(result);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error processing prompt: " + prompt + ", error: " + e.getMessage());
                throw e;
            }
        }
        
        return results;
    }
    
    private AgentConversationResult sendChatRequest(String prompt, String conversationId, String testMode, boolean isLastRequest, String testingRunResultSummaryId, boolean shouldFetchRecords, AgentLearningContext learningContext) throws Exception {
        Request request = buildOkHttpChatRequest(prompt, conversationId, isLastRequest, testingRunResultSummaryId, shouldFetchRecords, learningContext);

        Call call = clientWithLongTimeout.newCall(request);

        try (Response response = call.execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                throw new Exception("Agent server returned status code: " + response.code() + ", body: " + responseBody);
            }
            
            String responseBody = response.body() != null ? response.body().string() : "";
            return parseResponse(responseBody, conversationId, prompt);
        }
    }
    
    static Map<String, Object> buildChatRequestBody(String prompt, String conversationId, boolean isLastRequest, String testingRunResultSummaryId, boolean shouldFetchRecords, AgentLearningContext learningContext) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(JSON_KEY_PROMPT, prompt);
        requestBody.put(JSON_KEY_CONVERSATION_ID, conversationId);
        requestBody.put(JSON_KEY_IS_LAST_REQUEST, isLastRequest);
        requestBody.put(JSON_KEY_TESTING_RUN_RESULT_SUMMARY_ID, testingRunResultSummaryId != null ? testingRunResultSummaryId : "");
        requestBody.put(JSON_KEY_FETCH_QUERY_RECORDS, shouldFetchRecords);
        if (learningContext != null) {
            learningContext.addTo(requestBody);
        }
        return requestBody;
    }

    private Request buildOkHttpChatRequest(String prompt, String conversationId, boolean isLastRequest, String testingRunResultSummaryId, boolean shouldFetchRecords, AgentLearningContext learningContext) {
        Map<String, Object> requestBody = buildChatRequestBody(prompt, conversationId, isLastRequest, testingRunResultSummaryId, shouldFetchRecords, learningContext);

        String body;
        try {
            body = objectMapper.writeValueAsString(requestBody);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error serializing request body: " + e.getMessage());
            body = "{\"" + JSON_KEY_PROMPT + "\":\"" + prompt.replace("\"", "\\\"") + "\"}";
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
                // asText() on a JSON null returns the string "null"; an absent message stays null.
                validationMessage = jsonNode.hasNonNull("validationMessage") ? jsonNode.get("validationMessage").asText() : null;
                remediationMessage = jsonNode.hasNonNull("remediationMessage") ? jsonNode.get("remediationMessage").asText() : null;
            }

            String finalSentPrompt = null;
            if(jsonNode.has("finalSentPrompt")) {
                finalSentPrompt = jsonNode.get("finalSentPrompt").asText();
            }

            Map<String,Object> toolsMetadata = new HashMap<>();
            if(jsonNode.has("toolsMetadata") && jsonNode.get("toolsMetadata").isObject()) {
                jsonNode.get("toolsMetadata").fields().forEachRemaining(entry -> {
                    JsonNode toolNode = entry.getValue();
                    Map<String, String> meta = new HashMap<>();
                    meta.put("name", toolNode.path("name").asText(""));
                    meta.put("source", toolNode.path("source").asText(""));
                    meta.put("dataTrace", toolNode.path("dataTrace").asText(""));
                    toolsMetadata.put(entry.getKey(), meta);
                });
            }

            
            return new AgentConversationResult(conversationId, originalPrompt, response, conversation, timestamp, validation, validationMessage, finalSentPrompt, remediationMessage, toolsMetadata);
            
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

    private List<String> storeConversationResults(List<AgentConversationResult> conversationResults) {
        try {
            return dataActor.storeConversationResults(conversationResults);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error storing conversation results: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private void linkJobIdsToConversationResultDocIds(String conversationId, List<String> docIds) {
        if (docIds == null || docIds.isEmpty()) {
            return;
        }
        ApiExecutionJobStore store = ApiExecutionJobStoreRegistry.get();
        if (store == null) {
            return;
        }
        List<String> jobIds = store.getJobIdsByConversationId(conversationId);
        if (jobIds.size() != docIds.size()) {
            loggerMaker.errorAndAddToDb("linkJobIdsToConversationResultDocIds: jobId/docId count mismatch for conversationId="
                    + conversationId + " jobIds=" + jobIds.size() + " docIds=" + docIds.size());
        }
        int count = Math.min(jobIds.size(), docIds.size());
        for (int i = 0; i < count; i++) {
            store.putConversationResultDocId(jobIds.get(i), docIds.get(i));
        }
    }

    

    private static String getTagValue(List<CollectionTags> tags, String keyName) {
        if (tags == null) return null;
        return tags.stream()
            .filter(t -> keyName.equals(t.getKeyName()))
            .map(CollectionTags::getValue)
            .findFirst().orElse(null);
    }

    public void initializeCopilotAgent(String conversationId, ApiCollection col, String accessToken) {
        List<CollectionTags> tags = col != null ? col.getTagsList() : null;
        String environmentId = getTagValue(tags, Constants.AKTO_COPILOT_BOT_ENVIRONMENT_TAG);
        String agentSchema   = getTagValue(tags, Constants.AKTO_COPILOT_BOT_SCHEMA_TAG);

        Map<String, Object> copilotConfig = new HashMap<>();
        copilotConfig.put("environmentId", environmentId);
        copilotConfig.put("agentSchema", agentSchema);
        copilotConfig.put("accessToken", accessToken);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("conversationId", conversationId);
        requestBody.put("copilotConfig", copilotConfig);

        try {
            loggerMaker.infoAndAddToDb("initializeCopilotAgent: POST /initializeMCP ");
        } catch (Exception ignored) {}
        initializeAgent(requestBody);
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

    public void initializeAgent(String sessionUrl, String requestHeaders, String apiRequestBody, String requestMethod, String conversationId) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("sessionUrl", sessionUrl);
        requestBody.put("requestHeaders", requestHeaders);
        requestBody.put("requestBody", apiRequestBody);
        requestBody.put("requestMethod", requestMethod != null && !requestMethod.isEmpty() ? requestMethod : "POST");
        requestBody.put("conversationId", conversationId);
        initializeAgent(requestBody);
    }

    /**
     * Asks the agent server, off the caller's thread, to write the final learnings of a finished run. Only runs this
     * worker sent agentic tests for are sent; the agent server's version ids make a repeated call a no-op.
     */
    public boolean consolidateLearningsInBackground(String testingRunResultSummaryId) {
        if (testingRunResultSummaryId == null || !agenticSummaryIds.remove(testingRunResultSummaryId)) {
            return false;
        }
        Integer accountId = Context.accountId.get();
        consolidateExecutor.execute(() -> {
            Context.accountId.set(accountId);
            consolidateLearnings(testingRunResultSummaryId, CONSOLIDATE_BACKOFF_MILLIS);
        });
        return true;
    }

    /** True once the agent server accepted the call. Retries, with backoff, only an unreachable server, 429 and 5xx. */
    boolean consolidateLearnings(String testingRunResultSummaryId, long backoffMillis) {
        String failure = null;
        for (int attempt = 1; attempt <= CONSOLIDATE_MAX_ATTEMPTS; attempt++) {
            try (Response response = consolidateHttpClient.newCall(buildConsolidateRequest(testingRunResultSummaryId)).execute()) {
                if (response.isSuccessful()) {
                    loggerMaker.infoAndAddToDb("consolidateLearnings: accepted for summaryId=" + testingRunResultSummaryId);
                    return true;
                }
                failure = "status " + response.code();
                if (response.code() != 429 && response.code() < 500) {
                    break;
                }
            } catch (Exception e) {
                failure = e.getMessage();
            }
            if (attempt < CONSOLIDATE_MAX_ATTEMPTS && !sleepQuietly(backoffMillis * attempt)) {
                break;
            }
        }
        loggerMaker.errorAndAddToDb("consolidateLearnings: not accepted for summaryId=" + testingRunResultSummaryId + ": " + failure);
        return false;
    }

    private static boolean sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private Request buildConsolidateRequest(String testingRunResultSummaryId) throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(JSON_KEY_TESTING_RUN_RESULT_SUMMARY_ID, testingRunResultSummaryId);
        return new Request.Builder()
                .url(agentBaseUrl + "/consolidateLearnings")
                .post(RequestBody.create(objectMapper.writeValueAsString(requestBody), MediaType.parse("application/json")))
                .addHeader("Accept", "application/json")
                .build();
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
