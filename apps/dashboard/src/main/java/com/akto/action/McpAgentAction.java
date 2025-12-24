package com.akto.action;

import com.akto.dao.ApiTokensDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.AgentConversationDao;
import com.akto.dao.testing.AgentConversationResultDao;
import com.akto.dto.ApiToken;
import com.akto.dto.User;
import com.akto.dto.testing.AgentConversation;
import com.akto.dto.testing.AgentConversationResult;
import com.akto.util.McpTokenGenerator;
import com.akto.util.http_util.CoreHTTPClient;
import com.akto.utils.RandomString;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import okhttp3.*;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class McpAgentAction extends UserAction implements ServletRequestAware {

    private static final Logger logger = LoggerFactory.getLogger(McpAgentAction.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String AI_AGENT_URL = System.getenv().getOrDefault("AI_AGENT_URL", "http://localhost:3001");
    private static final OkHttpClient aiAgentHttpClient = CoreHTTPClient.client.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private HttpServletRequest servletRequest;
    private String message;
    private String conversationId;
    private BasicDBObject response;
    private String mcpToken;
    private String agentEndpoint;
    private int limit;

    public String generateToken() {
        try {
            User sUser = getSUser();
            int accountId = Context.accountId.get();
            String contextSource = servletRequest.getHeader("x-context-source");
            if (contextSource == null || contextSource.isEmpty()) contextSource = "API";

            String apiKey = getOrCreateApiKey(sUser.getLogin(), accountId);
            McpTokenGenerator tokenGenerator = McpTokenGenerator.fromEnvironment();
            String token = tokenGenerator.generateToken(accountId, contextSource, apiKey);

            BasicDBObject result = new BasicDBObject();
            result.put("token", token);
            result.put("agentEndpoint", AI_AGENT_URL);
            result.put("contextSource", contextSource);
            result.put("accountId", accountId);

            this.mcpToken = token;
            this.agentEndpoint = AI_AGENT_URL;
            this.response = result;

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.error("Error generating MCP token", e);
            addActionError("Failed to generate token: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    public String chatAndStoreConversation() {
        try {
            User sUser = getSUser();

            if (message == null || message.trim().isEmpty()) {
                addActionError("Message cannot be empty");
                return ERROR.toUpperCase();
            }

            int accountId = Context.accountId.get();
            String contextSource = servletRequest.getHeader("x-context-source");
            if (contextSource == null || contextSource.isEmpty()) contextSource = "API";

            String apiKey = getOrCreateApiKey(sUser.getLogin(), accountId);

            boolean isNewConversation = conversationId == null || conversationId.trim().isEmpty();
            String currentConversationId = isNewConversation ? UUID.randomUUID().toString() : conversationId;

            McpTokenGenerator tokenGenerator = McpTokenGenerator.fromEnvironment();
            String token = tokenGenerator.generateToken(accountId, contextSource, apiKey);

            String agentUrl = AI_AGENT_URL + "/chat";
            String aiResponse = callAiAgentApi(agentUrl, token, message, accountId, contextSource);

            JsonNode jsonNode = objectMapper.readTree(aiResponse);
            String responseText = jsonNode.get("response").asText();

            double costUsd = 0.0;
            long durationMs = 0;
            if (jsonNode.has("usage")) {
                JsonNode usage = jsonNode.get("usage");
                if (usage.has("cost_usd")) costUsd = usage.get("cost_usd").asDouble();
                if (usage.has("duration_ms")) durationMs = usage.get("duration_ms").asLong();
            }

            int currentTimestamp = (int) (System.currentTimeMillis() / 1000);
            String conversationTitle = null;

            if (isNewConversation) {
                conversationTitle = generateConversationTitle(token, message, accountId, contextSource);

                AgentConversation conversation = new AgentConversation();
                conversation.setId(currentConversationId);
                conversation.setTitle(conversationTitle);
                conversation.setCreatedAt(currentTimestamp);
                conversation.setLastUpdatedAt(currentTimestamp);
                conversation.setMessageCount(1);

                AgentConversationDao.instance.insertOne(conversation);
            } else {
                AgentConversation existingConv = AgentConversationDao.instance.findOne(
                    Filters.eq("_id", currentConversationId)
                );

                if (existingConv != null) {
                    conversationTitle = existingConv.getTitle();
                    AgentConversationDao.instance.updateOne(
                        Filters.eq("_id", currentConversationId),
                        Updates.combine(
                            Updates.set("lastUpdatedAt", currentTimestamp),
                            Updates.inc("messageCount", 1)
                        )
                    );
                }
            }

            AgentConversationResult conversationResult = new AgentConversationResult();
            conversationResult.setConversationId(currentConversationId);
            conversationResult.setPrompt(message);
            conversationResult.setResponse(responseText);
            conversationResult.setTimestamp(currentTimestamp);
            conversationResult.setConversation(new ArrayList<>());
            conversationResult.setValidation(false);

            AgentConversationResultDao.instance.insertOne(conversationResult);

            BasicDBObject result = new BasicDBObject();
            result.put("response", responseText);
            result.put("conversationId", currentConversationId);
            result.put("timestamp", conversationResult.getTimestamp());
            result.put("title", conversationTitle);
            result.put("usage", new BasicDBObject()
                .append("cost_usd", costUsd)
                .append("duration_ms", durationMs)
            );

            this.response = result;
            this.conversationId = currentConversationId;

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.error("Error in chatAndStoreConversation", e);
            addActionError("Failed to process chat: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    private String getOrCreateApiKey(String username, int accountId) {
        ApiToken existingToken = ApiTokensDao.instance.findOne(
            Filters.and(
                Filters.eq(ApiToken.USER_NAME, username),
                Filters.eq(ApiToken.ACCOUNT_ID, accountId),
                Filters.eq(ApiToken.UTILITY, ApiToken.Utility.EXTERNAL_API)
            )
        );

        if (existingToken != null) return existingToken.getKey();

        RandomString randomString = new RandomString(40);
        String apiKey = randomString.nextString();

        ApiToken mcpToken = new ApiToken(
            Context.now(), accountId, "mcp_agent", apiKey,
            Context.now(), username, ApiToken.Utility.EXTERNAL_API
        );

        ApiTokensDao.instance.insertOne(mcpToken);
        return apiKey;
    }

    public String fetchHistory() {
        try {
            int fetchLimit = limit > 0 ? limit : 50;

            List<AgentConversation> conversations = AgentConversationDao.instance.findAll(
                Filters.empty(),
                0,
                fetchLimit,
                Sorts.descending("lastUpdatedAt")
            );

            List<BasicDBObject> historyList = new ArrayList<>();
            for (AgentConversation conv : conversations) {
                BasicDBObject item = new BasicDBObject();
                item.put("conversationId", conv.getId());
                item.put("title", conv.getTitle() != null ? conv.getTitle() : "Untitled Conversation");
                item.put("timestamp", conv.getLastUpdatedAt());
                item.put("createdAt", conv.getCreatedAt());
                item.put("messageCount", conv.getMessageCount());
                historyList.add(item);
            }

            BasicDBObject result = new BasicDBObject();
            result.put("history", historyList);
            this.response = result;

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.error("Error fetching conversation history", e);
            addActionError("Failed to fetch history: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    public String fetchConversation() {
        try {
            if (conversationId == null || conversationId.trim().isEmpty()) {
                addActionError("Conversation ID is required");
                return ERROR.toUpperCase();
            }

            List<AgentConversationResult> conversations = AgentConversationResultDao.instance.findAll(
                Filters.eq("conversationId", conversationId),
                0,
                100,
                Sorts.ascending("timestamp")
            );

            List<BasicDBObject> messagesList = new ArrayList<>();
            for (AgentConversationResult conv : conversations) {
                BasicDBObject userMsg = new BasicDBObject();
                userMsg.put("type", "user");
                userMsg.put("text", conv.getPrompt());
                userMsg.put("timestamp", conv.getTimestamp());
                messagesList.add(userMsg);

                BasicDBObject assistantMsg = new BasicDBObject();
                assistantMsg.put("type", "assistant");
                assistantMsg.put("text", conv.getResponse());
                assistantMsg.put("timestamp", conv.getTimestamp());
                messagesList.add(assistantMsg);
            }

            BasicDBObject result = new BasicDBObject();
            result.put("messages", messagesList);
            result.put("conversationId", conversationId);
            this.response = result;

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.error("Error fetching conversation", e);
            addActionError("Failed to fetch conversation: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    private String generateConversationTitle(String authToken, String userMessage, int accountId, String contextSource) {
        try {
            String titlePrompt = "Generate a short, concise title (maximum 5-7 words) for this conversation based on the user's first message. Only return the title, nothing else. User's message: " + userMessage;

            String agentUrl = AI_AGENT_URL + "/chat";
            String titleResponse = callAiAgentApi(agentUrl, authToken, titlePrompt, accountId, contextSource);

            JsonNode titleNode = objectMapper.readTree(titleResponse);
            String title = titleNode.get("response").asText().trim();

            if (title.length() > 100) {
                title = title.substring(0, 97) + "...";
            }

            return title;
        } catch (Exception e) {
            logger.error("Error generating conversation title", e);
            return userMessage.length() > 50 ? userMessage.substring(0, 47) + "..." : userMessage;
        }
    }

    private String callAiAgentApi(String url, String authToken, String userMessage, int accountId, String contextSource) throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("message", userMessage);

        String bodyJson = objectMapper.writeValueAsString(requestBody);
        RequestBody requestBodyObj = RequestBody.create(bodyJson, MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url(url)
                .post(requestBodyObj)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + authToken)
                .addHeader("X-Account-Id", String.valueOf(accountId))
                .addHeader("X-Context-Source", contextSource)
                .build();

        try (Response response = aiAgentHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new Exception("AI Agent error " + response.code() + ": " + errorBody);
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            if (responseBody.isEmpty()) throw new Exception("Empty response from AI Agent");

            return responseBody;
        }
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public BasicDBObject getResponse() { return response; }
    public void setResponse(BasicDBObject response) { this.response = response; }
    public String getMcpToken() { return mcpToken; }
    public void setMcpToken(String mcpToken) { this.mcpToken = mcpToken; }
    public String getAgentEndpoint() { return agentEndpoint; }
    public void setAgentEndpoint(String agentEndpoint) { this.agentEndpoint = agentEndpoint; }
    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = limit; }

    @Override
    public void setServletRequest(HttpServletRequest request) {
        this.servletRequest = request;
    }
}
