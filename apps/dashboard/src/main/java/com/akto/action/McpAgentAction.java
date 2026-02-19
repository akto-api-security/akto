package com.akto.action;

import com.akto.agent.AgentClient;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.AgentConversationDao;
import com.akto.dto.testing.GenericAgentConversation;
import com.akto.dto.testing.GenericAgentConversation.ConversationType;
import com.akto.util.Constants;
import com.akto.util.McpTokenGenerator;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BsonField;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.conversions.Bson;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class McpAgentAction extends UserAction {

    private static final Logger logger = LoggerFactory.getLogger(McpAgentAction.class);
    private String message;
    private String conversationId;
    private BasicDBObject response;
    private String mcpToken;
    private String agentEndpoint;
    private int limit;
    private String conversationType;

    private Map<String, Object> metaData;

    public String chatAndStoreConversation() {
        try {

            String userId = getSUser().getLogin();
            if (userId != null && userId.startsWith("akash+") && userId.endsWith("@akto.io")) {
                addActionError("You are not allowed to use this feature");
                return ERROR.toUpperCase();
            }

            // check for conversation type
            if(conversationType == null) {
                addActionError("Conversation type is required");
                return ERROR.toUpperCase();
            }
            ConversationType conversationTypeEnum = null;
            try {
                conversationTypeEnum = ConversationType.valueOf(this.conversationType);
            } catch (Exception e) {
                addActionError("Invalid conversation type: " + this.conversationType);
                return ERROR.toUpperCase();
            }
            int timeNow = Context.now();

            String accessTokenForRequest = McpTokenGenerator.generateToken(getSUser().getLogin());

            boolean isFirstRequest = true;
            String storedTitle = null;
            if(StringUtils.isNotEmpty(conversationId)) {
                GenericAgentConversation conversation = AgentConversationDao.instance.findOne(Filters.eq("conversationId", conversationId));
                if(conversation != null) {
                    isFirstRequest = false;
                    storedTitle = conversation.getTitle();
                }
            }

            if(isFirstRequest) {
                this.conversationId = UUID.randomUUID().toString();
            }
            AgentClient agentClient = new AgentClient(Constants.AKTO_MCP_SERVER_URL);
            String contextString = "";
            int tokensLimit = 20000;

            if(metaData != null) {
                String type = (String) metaData.get("type");
                if(StringUtils.isNotEmpty(type) && type.equals("sample_request")) {
                    // get sample data from metaData
                    // it will be data -> apiCollectionId, url, method
                    Object data = metaData.get("data");
                    if(data != null && data instanceof Map) {
                        Map<String, Object> dataMap = (Map<String, Object>) data;
                        Integer apiCollectionId = (Integer) dataMap.get("apiCollectionId");
                        String url = (String) dataMap.get("url");
                        String method = (String) dataMap.get("method");
                        String latestSampleData = SampleDataDao.getLatestSampleData(apiCollectionId, url, method);
                        if(StringUtils.isNotEmpty(latestSampleData)) {
                            contextString = "Current context: " + latestSampleData;
                        }
                    }
                } else if(StringUtils.isNotEmpty(type) && type.equals("dashboard_collections")) {
                    // Dashboard collections data sent from UI
                    Object data = metaData.get("data");
                    if(data != null && data instanceof List) {
                        List<Map<String, Object>> collections = (List<Map<String, Object>>) data;
                        contextString = "Dashboard API Collections Data:\n" +
                            "Total collections analyzed: " + collections.size() + "\n" +
                            "Collections with their metrics (endpoints count and risk scores):\n" +
                            collections.toString();
                        // Increase timeout for large data
                        tokensLimit = 60000; // 60 seconds
                    }
                }
            }

            String userEmail = getSUser() != null ? getSUser().getLogin() : null;
            String contextSource = Context.contextSource.get() != null ? Context.contextSource.get().toString() : null;
            GenericAgentConversation responseFromMcpServer = agentClient.getResponseFromMcpServer(message, conversationId, tokensLimit, storedTitle, conversationTypeEnum, accessTokenForRequest, contextString, userEmail, contextSource);
            if(responseFromMcpServer != null) {
                responseFromMcpServer.setCreatedAt(timeNow);
                AgentConversationDao.instance.insertOne(responseFromMcpServer);
            }
            this.response = new BasicDBObject();
            this.response.put("response", responseFromMcpServer.getResponse());
            this.response.put("conversationId", responseFromMcpServer.getConversationId());
            this.response.put("finalSentPrompt", responseFromMcpServer.getFinalSentPrompt());
            this.response.put("tokensUsed", responseFromMcpServer.getTokensUsed());
            this.response.put("title", responseFromMcpServer.getTitle());
        }catch(Exception e) {
            logger.error("Error chatting and storing conversation", e);
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    public String fetchHistory() {
        try {
            int fetchLimit = limit > 0 ? limit : 5;

            List<Bson> pipeline = new ArrayList<>();
            
            pipeline.add(Aggregates.sort(Sorts.descending("lastUpdatedAt")));
            BasicDBObject groupedId = new BasicDBObject("_id", "$conversationId");
            List<BsonField> groupAccumulators = new ArrayList<>();
            groupAccumulators.add(Accumulators.first("lastUpdatedAt", "$lastUpdatedAt"));
            groupAccumulators.add(Accumulators.last("title", "$title"));
            groupAccumulators.add(Accumulators.sum("tokensUsed", "$tokensUsed"));
            groupAccumulators.add(Accumulators.push("messages", new BasicDBObject()
                .append("prompt", "$prompt")
                .append("response", "$response")
            ));
            
            pipeline.add(Aggregates.group(groupedId, groupAccumulators.toArray(new BsonField[0])));
            pipeline.add(Aggregates.limit(fetchLimit));
            MongoCursor<BasicDBObject> cursor = AgentConversationDao.instance.getMCollection()
                .aggregate(pipeline, BasicDBObject.class)
                .cursor();
            
            List<BasicDBObject> conversations = new ArrayList<>();
            while (cursor.hasNext()) {
                BasicDBObject doc = cursor.next();
                conversations.add(doc);
            }

            BasicDBObject result = new BasicDBObject();
            result.put("history", conversations);
            this.response = result;

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.error("Error fetching conversation history", e);
            addActionError("Failed to fetch history: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    public String deleteConversationHistory() {
        if(conversationId == null || conversationId.isEmpty()) {
            addActionError("Conversation ID is required");
            return ERROR.toUpperCase();
        }
        try {
            AgentConversationDao.instance.deleteAll(Filters.eq("conversationId", conversationId));
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.error("Error deleting conversation history", e);            addActionError("Failed to delete conversation history: " + e.getMessage());
            return ERROR.toUpperCase();
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
    public String getConversationType() { return conversationType; }
    public void setConversationType(String conversationType) { this.conversationType = conversationType; }
    public Map<String, Object> getMetaData() { return metaData; }
    public void setMetaData(Map<String, Object> metaData) {
        this.metaData = metaData;
    }

}
