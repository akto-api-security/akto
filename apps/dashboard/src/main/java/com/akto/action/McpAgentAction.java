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
    /** Guardrail: very large context breaks MCP /chat or gateway limits and yields 422 (ERROR). */
    private static final int MAX_TEST_RESULT_CONTEXT_CHARS = 120_000;
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
                } else if(StringUtils.isNotEmpty(type) && type.equals("test_execution_result")) {
                    Object data = metaData.get("data");
                    if(data != null && data instanceof Map) {
                        Map<String, Object> dataMap = (Map<String, Object>) data;
                        StringBuilder sb = new StringBuilder("Test Execution Result Context:\n");
                        sb.append("Test Name: ").append(dataMap.getOrDefault("testName", "")).append("\n");
                        sb.append("Test Category: ").append(dataMap.getOrDefault("testCategory", "")).append("\n");
                        sb.append("Vulnerable: ").append(dataMap.getOrDefault("vulnerable", false)).append("\n");
                        sb.append("Severity: ").append(dataMap.getOrDefault("severity", "")).append("\n");
                        sb.append("URL: ").append(dataMap.getOrDefault("url", "")).append("\n");
                        Object originalMsg = dataMap.get("originalMessage");
                        if(originalMsg != null) {
                            sb.append("Original API Request+Response: ").append(originalMsg).append("\n");
                        }
                        Object attemptMsg = dataMap.get("attemptMessage");
                        if(attemptMsg != null) {
                            sb.append("Test Attempt Request+Response: ").append(attemptMsg).append("\n");
                        }
                        Object agenticCtx = dataMap.get("agenticConversationContext");
                        if(agenticCtx != null) {
                            String agenticStr = agenticCtx instanceof String ? (String) agenticCtx : String.valueOf(agenticCtx);
                            if(StringUtils.isNotEmpty(agenticStr)) {
                                sb.append("Agent / LLM Test Conversation:\n").append(agenticStr).append("\n");
                            }
                        }
                        contextString = sb.toString();
                        if(contextString.length() > MAX_TEST_RESULT_CONTEXT_CHARS) {
                            contextString = contextString.substring(0, MAX_TEST_RESULT_CONTEXT_CHARS)
                                + "\n\n[... truncated server-side ...]";
                        }
                        tokensLimit = 40000;
                    }
                } else if (StringUtils.isNotEmpty(type) && type.equals("agentic_observe")) {
                    Object data = metaData.get("data");
                    if (data != null && data instanceof Map) {
                        Map<String, Object> dataMap = (Map<String, Object>) data;
                        String scope = (String) dataMap.getOrDefault("scope", "");
                        StringBuilder sb = new StringBuilder();

                        if ("device".equals(scope)) {
                            sb.append("CONTEXT SCOPE: Single device. Answer questions about THIS device and its agents only.\n");
                            sb.append("You have the full agent inventory for this device below. Do NOT speculate about other devices.\n\n");
                            sb.append("--- Device Context ---\n");
                            appendIfPresent(sb, "Device endpoint", dataMap.get("deviceEndpoint"));
                            appendIfPresent(sb, "Device ID", dataMap.get("deviceId"));
                            appendIfPresent(sb, "Username", dataMap.get("username"));
                            appendIfPresent(sb, "Group/Team", dataMap.get("group"));
                            appendIfPresent(sb, "Role", dataMap.get("role"));
                            appendIfPresent(sb, "OS", dataMap.get("os"));
                            appendIfPresent(sb, "Risk score", dataMap.get("riskScore"));
                            appendIfPresent(sb, "Last traffic", dataMap.get("lastTraffic"));
                            Object violations = dataMap.get("violations");
                            if (violations != null) {
                                sb.append("Violations: ").append(violations).append("\n");
                            }
                            Object counts = dataMap.get("counts");
                            if (counts instanceof Map) {
                                Map<?, ?> c = (Map<?, ?>) counts;
                                sb.append("Agentic asset counts on this device: total=").append(c.get("totalAgents"))
                                  .append(", MCP servers=").append(c.get("mcpServers"))
                                  .append(", AI agents=").append(c.get("aiAgents"))
                                  .append(", high-risk agents=").append(c.get("maliciousAgents"))
                                  .append("\n");
                            }
                            Object agentList = dataMap.get("agentList");
                            if (agentList instanceof List) {
                                sb.append("Agentic assets on this device (name | type | riskScore | violations | skillCount):\n");
                                for (Object item : (List<?>) agentList) {
                                    if (item instanceof Map) {
                                        Map<?, ?> a = (Map<?, ?>) item;
                                        sb.append("  - ").append(a.get("name"))
                                          .append(" | ").append(a.get("type"))
                                          .append(" | risk=").append(a.get("riskScore"))
                                          .append(" | violations=").append(a.get("violations"))
                                          .append(" | skills=").append(a.get("skillCount"))
                                          .append("\n");
                                    }
                                }
                            }
                        } else if ("asset".equals(scope)) {
                            sb.append("CONTEXT SCOPE: Single agentic asset. Answer questions about THIS asset only.\n");
                            sb.append("You have the full device list and component list for this asset below. Do NOT speculate about other assets.\n\n");
                            sb.append("--- Asset Context ---\n");
                            appendIfPresent(sb, "Asset name", dataMap.get("assetName"));
                            appendIfPresent(sb, "Asset type", dataMap.get("assetType"));
                            appendIfPresent(sb, "Asset tag", dataMap.get("assetTagValue"));
                            appendIfPresent(sb, "Risk score", dataMap.get("riskScore"));
                            appendIfPresent(sb, "Last seen", dataMap.get("lastSeen"));
                            appendIfPresent(sb, "Device count", dataMap.get("deviceCount"));
                            appendIfPresent(sb, "Endpoint count", dataMap.get("endpointCount"));
                            appendIfPresent(sb, "Skill count", dataMap.get("skillCount"));
                            appendIfPresent(sb, "AI interactions (tokens)", dataMap.get("aiInteractions"));
                            Object violations = dataMap.get("violations");
                            if (violations != null) {
                                sb.append("Violations: ").append(violations).append("\n");
                            }
                            Object deviceList = dataMap.get("deviceList");
                            if (deviceList instanceof List) {
                                sb.append("Devices this asset is present on (deviceId | riskScore):\n");
                                for (Object item : (List<?>) deviceList) {
                                    if (item instanceof Map) {
                                        Map<?, ?> d = (Map<?, ?>) item;
                                        sb.append("  - ").append(d.get("deviceId"))
                                          .append(" | risk=").append(d.get("riskScore"))
                                          .append("\n");
                                    }
                                }
                            }
                            Object componentList = dataMap.get("componentList");
                            if (componentList instanceof List) {
                                sb.append("Linked components (name | type | riskScore):\n");
                                for (Object item : (List<?>) componentList) {
                                    if (item instanceof Map) {
                                        Map<?, ?> c = (Map<?, ?>) item;
                                        sb.append("  - ").append(c.get("name"))
                                          .append(" | ").append(c.get("type"))
                                          .append(" | risk=").append(c.get("riskScore"))
                                          .append("\n");
                                    }
                                }
                            }
                            Object groups = dataMap.get("groups");
                            if (groups != null) {
                                sb.append("Team groups using this asset: ").append(groups).append("\n");
                            }
                            Object mcpServers = dataMap.get("mcpServers");
                            if (mcpServers != null) {
                                sb.append("MCP servers connected: ").append(mcpServers).append("\n");
                            }
                        } else {
                            appendIfPresent(sb, "Device", dataMap.get("deviceEndpoint"));
                            appendIfPresent(sb, "Device ID", dataMap.get("deviceId"));
                            appendIfPresent(sb, "Agent", dataMap.get("agentEndpoint"));
                            appendIfPresent(sb, "Risk score", dataMap.get("riskScore"));
                            appendIfPresent(sb, "Skill", dataMap.get("skillName"));
                            appendIfPresent(sb, "Tool", dataMap.get("toolName"));
                            appendIfPresent(sb, "Resource", dataMap.get("resourceName"));
                            appendIfPresent(sb, "Prompt", dataMap.get("promptName"));
                            Object violations = dataMap.get("violations");
                            if (violations != null) {
                                sb.append("Violations summary: ").append(violations).append("\n");
                            }
                            Object counts = dataMap.get("counts");
                            if (counts != null) {
                                sb.append("Counts: ").append(counts).append("\n");
                            }
                        }
                        contextString = sb.toString();
                        tokensLimit = 40000;
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

    private static void appendIfPresent(StringBuilder sb, String label, Object value) {
        if (value == null) {
            return;
        }
        String text = value instanceof String ? (String) value : String.valueOf(value);
        if (StringUtils.isNotEmpty(text)) {
            sb.append(label).append(": ").append(text).append("\n");
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
