package com.akto.agent;

import com.akto.dao.AccountSettingsDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.AgentConversationResultDao;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiCollection;
import com.akto.dto.CopilotAuthDetails;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.RawApi;
import com.akto.dto.testing.AgentConversationResult;
import com.akto.dto.testing.GenericAgentConversation;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.GenericAgentConversation.ConversationType;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import okhttp3.*;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.akto.util.Constants;
import com.akto.util.http_util.CoreHTTPClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.mongodb.client.model.Updates;

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

    // key=accountId, value="accessToken|expiresAtMs"
    private static final ConcurrentHashMap<Integer, String> copilotAccessTokenCache = new ConcurrentHashMap<>();
    
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

        ApiCollection col = ApiCollectionsDao.instance.getMeta(apiCollectionId);
        boolean isCopilot = isCopilotBotCollection(col);
        loggerMaker.infoAndAddToDb("executeAgenticTest: starting conversationId=" + conversationId + " apiCollectionId=" + apiCollectionId + " prompts=" + promptsList.size() + " copilot=" + isCopilot);

        try {
            if (isCopilot) {
                AccountSettings settings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
                CopilotAuthDetails copilotAuth = settings != null ? settings.getCopilotAuthDetails() : null;
                if (copilotAuth == null) {
                    throw new Exception("CopilotAuthDetails not configured in AccountSettings");
                }
                String accessToken = fetchAccessToken(copilotAuth);
                loggerMaker.infoAndAddToDb("executeAgenticTest: initializing Copilot agent conversationId=" + conversationId);
                initializeCopilotAgent(conversationId, col, accessToken);
            } else {
                AgenticUtils.checkAndInitializeAgent(conversationId, rawApi, apiCollectionId);
            }
        } catch(Exception e){
            loggerMaker.errorAndAddToDb("executeAgenticTest: init failed conversationId=" + conversationId + " err=" + e.getMessage());
        }

        try {
            List<AgentConversationResult> conversationResults = processConversations(promptsList, conversationId, testMode);

            boolean isVulnerable = conversationResults.get(conversationResults.size() - 1).isValidation();
            List<String> errors = new ArrayList<>();

            // Calculate total external API tokens across all conversations
            int totalExternalApiTokens = 0;
            for (AgentConversationResult result : conversationResults) {
                int tokens = result.getExternalApiTokens();
                totalExternalApiTokens += tokens;
            }

            TestResult testResult = new TestResult();
            // TODO: Fill in message field
            testResult.setMessage(null);
            testResult.setConversationId(conversationId);
            testResult.setResultTypeAgentic(true);
            testResult.setVulnerable(isVulnerable);
            testResult.setConfidence(TestResult.Confidence.HIGH);
            testResult.setErrors(errors);
            testResult.setPercentageMatch(isVulnerable ? 0.0 : 100.0);
            testResult.setExternalApiTokens(totalExternalApiTokens);
            loggerMaker.info("TestResult.externalApiTokens set to: " + testResult.getExternalApiTokens());

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
        Request request = buildOkHttpChatRequest(prompt, conversationId, isLastRequest, null, ConversationType.TEST_EXECUTION_RESULT, "", "", "", null);
        
        try (Response response = agentHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                throw new Exception("Agent server returned status code: " + response.code() + ", body: " + responseBody);
            }
            
            String responseBody = response.body() != null ? response.body().string() : "";
            return parseResponse(responseBody, conversationId, prompt);
        }
    }
    
    private Request buildOkHttpChatRequest(String prompt, String conversationId, boolean isLastRequest, String chatUrl, GenericAgentConversation.ConversationType conversationType, String accessTokenForRequest, String contextString, String userEmail, String contextSource) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("prompt", prompt);
        requestBody.put("conversationId", conversationId);
        requestBody.put("isLastRequest", isLastRequest);
        requestBody.put("conversationType", conversationType);
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

        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(requestBodyObj)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("x-akto-token", accessTokenForRequest);

        if (contextSource != null && !contextSource.isEmpty()) {
            builder.addHeader("x-context-source", contextSource);
        }

        return builder.build();
    }
    
    
    private AgentConversationResult parseResponse(String responseBody, String conversationId, String originalPrompt) throws Exception {
        try {
            loggerMaker.info("RAW RESPONSE BODY: " + responseBody);
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

            int externalApiTokens = 0;
            if(jsonNode.has("externalApiTokens")) {
                externalApiTokens = jsonNode.get("externalApiTokens").intValue();
                loggerMaker.infoAndAddToDb("EXTRACTED externalApiTokens: " + externalApiTokens);
            } else {
                loggerMaker.infoAndAddToDb("externalApiTokens field NOT FOUND in response");
            }

            Map<String,Object> toolsMetadata = new HashMap<>();
            if(jsonNode.has("toolsMetadata") && jsonNode.get("toolsMetadata").isObject()) {
                jsonNode.get("toolsMetadata").fields().forEachRemaining(entry ->
                    toolsMetadata.put(entry.getKey(), entry.getValue())
                );
            }
            AgentConversationResult result = new AgentConversationResult(conversationId, originalPrompt, response, conversation, timestamp, validation, validationMessage, finalSentPrompt, remediationMessage, externalApiTokens);
            if(toolsMetadata != null && !toolsMetadata.isEmpty()) {
                result.getToolsMetadata().putAll(toolsMetadata);
            }
            return result;
            
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

    public static boolean isCopilotBotCollection(int apiCollectionId) {
        ApiCollection col = ApiCollectionsDao.instance.getMeta(apiCollectionId);
        return isCopilotBotCollection(col);
    }

    public static boolean isCopilotBotCollection(ApiCollection col) {
        if (col == null || col.getTagsList() == null) {
            loggerMaker.infoAndAddToDb("isCopilotBotCollection: collection not found or has no tags");
            return false;
        }
        List<CollectionTags> tags = col.getTagsList();
        loggerMaker.infoAndAddToDb("isCopilotBotCollection: collection " + col.getId() + " tags=" + tags.stream().map(t -> t.getKeyName() + "=" + t.getValue()).collect(java.util.stream.Collectors.joining(", ")));
        boolean hasSource  = tags.stream().anyMatch(t -> Constants.AKTO_ENDPOINT_SOURCE_TAG.equals(t.getKeyName()) && Constants.AKTO_COPILOT_SOURCE_VALUE.equals(t.getValue()));
        boolean hasBotName = tags.stream().anyMatch(t -> Constants.AKTO_COPILOT_BOT_NAME_TAG.equals(t.getKeyName()));
        return hasSource && hasBotName;
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
            loggerMaker.infoAndAddToDb("initializeCopilotAgent: POST /initializeMCP body=" + objectMapper.writeValueAsString(requestBody));
        } catch (Exception ignored) {}
        initializeAgent(requestBody);
    }

    public String fetchAccessToken(CopilotAuthDetails auth) throws Exception {
        int accountId = Context.accountId.get();
        String cached = copilotAccessTokenCache.get(accountId);
        if (cached != null) {
            String[] parts = cached.split("\\|", 2);
            if (parts.length == 2 && Long.parseLong(parts[1]) > System.currentTimeMillis() + 5 * 60 * 1000L) {
                return parts[0];
            }
        }

        String refreshToken = auth.getRefreshToken();
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new Exception("Copilot Studio refresh token missing — user must re-authenticate.");
        }

        loggerMaker.infoAndAddToDb("fetchAccessToken: fetching new access token via refresh_token grant");

        okhttp3.RequestBody formBody = new okhttp3.FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", auth.getClientId())
            .add("client_secret", auth.getClientSecret())
            .add("refresh_token", refreshToken)
            .add("scope", "https://api.powerplatform.com/CopilotStudio.Copilots.Invoke offline_access")
            .build();

        Request tokenRequest = new Request.Builder()
            .url("https://login.microsoftonline.com/" + auth.getTenantId() + "/oauth2/v2.0/token")
            .post(formBody)
            .build();

        try (Response response = agentHttpClient.newCall(tokenRequest).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                if (response.code() == 400 || response.code() == 401) {
                    throw new Exception("Copilot Studio token refresh failed (code=" + response.code() + "). Refresh token may be expired — user must re-authenticate. Response: " + body);
                }
                throw new Exception("Copilot Studio token refresh failed (code=" + response.code() + "): " + body);
            }

            JsonNode json = objectMapper.readTree(body);
            String newAccessToken  = json.has("access_token")  ? json.get("access_token").asText()  : null;
            String newRefreshToken = json.has("refresh_token") ? json.get("refresh_token").asText() : null;
            long   expiresIn       = json.has("expires_in")    ? json.get("expires_in").asLong()    : 3600L;

            if (newAccessToken == null || newAccessToken.isEmpty()) {
                throw new Exception("Copilot Studio token response missing access_token");
            }

            long expiresAtMs = System.currentTimeMillis() + expiresIn * 1000L;

            // update in-memory cache
            copilotAccessTokenCache.put(accountId, newAccessToken + "|" + expiresAtMs);

            if (newRefreshToken != null && !newRefreshToken.isEmpty()) {
                auth.setRefreshToken(newRefreshToken);
                loggerMaker.infoAndAddToDb("fetchAccessToken: new refresh token received, persisting to AccountSettings");
            }
            AccountSettingsDao.instance.updateOneNoUpsert(
                AccountSettingsDao.generateFilter(),
                Updates.set(AccountSettings.COPILOT_AUTH_DETAILS + "." + CopilotAuthDetails.REFRESH_TOKEN, auth.getRefreshToken())
            );

            loggerMaker.infoAndAddToDb("fetchAccessToken: token obtained and cached, expiresIn=" + expiresIn + "s");
            return newAccessToken;
        }
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
    public GenericAgentConversation getResponseFromMcpServer(String prompt, String conversationId, int tokensLimit, String storedTitle, GenericAgentConversation.ConversationType conversationType, String accessTokenForRequest, String contextString, String userEmail, String contextSource) throws Exception {
        Request request = buildOkHttpChatRequest(prompt, conversationId, false, "/generic_chat", conversationType, accessTokenForRequest, contextString, userEmail, contextSource);
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
            // externalApiTokens is 0 for non-agentic conversations (e.g., ask_akto, docs_agent)
            int externalApiTokens = 0;
            return new GenericAgentConversation(title, conversationId, prompt, responseFromMcpServer, finalSentPrompt, timestamp, timestamp, tokensUsed, externalApiTokens, tokensLimit, conversationType);
        } catch (Exception e) {
            throw new Exception("Failed to get response from MCP server: " + e.getMessage());
        }
    }
    
}
