package com.akto.action;

import com.akto.agent.AgentClient;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.AgentConversationDao;
import com.akto.dto.testing.GenericAgentConversation;
import com.akto.dto.testing.GenericAgentConversation.ConversationType;
import com.akto.dao.UsersDao;
import com.akto.dto.User;
import com.akto.util.Constants;
import com.akto.util.McpTokenGenerator;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BsonField;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.bson.conversions.Bson;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

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
    private int skip;
    private String conversationType;
    private String searchQuery;
    private boolean includeMessages = true;
    // Optional user filter, and whether to also return the users that have conversations (to fill the filter)
    private List<Integer> userIds;
    private boolean includeUsers;

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
                    // Pass only the minimal asset/device identity (assetName/assetType/collectionIds
                    // or deviceId). The MCP agent fetches what it needs on demand via the focused
                    // akto_agentic_* tools (collections_search / users_search / skills / audit_data)
                    // — see the agentic_observe system prompt in test-editor-services.
                    Object data = metaData.get("data");
                    if (data != null && data instanceof Map) {
                        Map<String, Object> dataMap = (Map<String, Object>) data;
                        String scope = (String) dataMap.getOrDefault("scope", "");
                        String scopeLabel = "device".equals(scope) ? "Single device" : "asset".equals(scope) ? "Single agentic asset" : "Agentic context";
                        try {
                            ObjectMapper mapper = new ObjectMapper();
                            contextString = "CONTEXT SCOPE: " + scopeLabel
                                + ". This is only the asset/device identity — use the akto_agentic_* tools "
                                + "(with the collectionIds, assetName, or deviceId below) to fetch its data before answering.\n\n"
                                + mapper.writeValueAsString(dataMap);
                        } catch (Exception e) {
                            contextString = "Agentic Observe Context: " + dataMap.toString();
                        }
                        tokensLimit = 20000;
                    }
                } else if (StringUtils.isNotEmpty(type) && type.equals("insight_result")) {
                    // Insights detail view's Ask Akto chat. The frontend sends the exact
                    // InsightResult fields it's already rendering on screen (not just an id to
                    // re-fetch) so the AI is grounded in precisely what the user is looking at,
                    // never a possibly-drifted server-side recompute.
                    Object data = metaData.get("data");
                    if (data != null && data instanceof Map) {
                        Map<String, Object> dataMap = (Map<String, Object>) data;
                        StringBuilder sb = new StringBuilder("Insight Context:\n");
                        appendIfPresent(sb, "Insight", dataMap.get("title"));
                        appendIfPresent(sb, "Status", dataMap.get("status"));
                        appendIfPresent(sb, "Severity", dataMap.get("severity"));
                        appendIfPresent(sb, "Headline", dataMap.get("headline"));

                        Object metricsObj = dataMap.get("metrics");
                        if (metricsObj instanceof List && !((List<?>) metricsObj).isEmpty()) {
                            sb.append("Metrics:\n");
                            for (Object m : (List<?>) metricsObj) {
                                if (m instanceof Map) {
                                    Map<?, ?> metric = (Map<?, ?>) m;
                                    sb.append("- ").append(metric.get("label")).append(": ").append(metric.get("formatted")).append("\n");
                                }
                            }
                        }

                        appendIfPresent(sb, "Concern", dataMap.get("concern"));
                        appendIfPresent(sb, "Impact", dataMap.get("impact"));
                        appendIfPresent(sb, "Remediation", dataMap.get("remediation"));
                        appendIfPresent(sb, "AI Summary", dataMap.get("markdown"));

                        Object evidenceObj = dataMap.get("evidence");
                        if (evidenceObj instanceof List && !((List<?>) evidenceObj).isEmpty()) {
                            sb.append("Evidence:\n");
                            for (Object e : (List<?>) evidenceObj) {
                                if (!(e instanceof Map)) continue;
                                Map<?, ?> table = (Map<?, ?>) e;
                                Object rowsObj = table.get("rows");
                                int rowCount = rowsObj instanceof List ? ((List<?>) rowsObj).size() : 0;
                                sb.append(table.get("title")).append(" (").append(rowCount).append(" of ")
                                        .append(table.get("totalRowCount")).append(" shown):\n");
                                if (rowsObj instanceof List) {
                                    for (Object row : (List<?>) rowsObj) {
                                        sb.append("  ").append(row).append("\n");
                                    }
                                }
                            }
                        }

                        Object caveatsObj = dataMap.get("caveats");
                        if (caveatsObj instanceof List && !((List<?>) caveatsObj).isEmpty()) {
                            sb.append("Caveats:\n");
                            for (Object c : (List<?>) caveatsObj) sb.append("- ").append(c).append("\n");
                        }

                        Object dataGapsObj = dataMap.get("dataGaps");
                        if (dataGapsObj instanceof List && !((List<?>) dataGapsObj).isEmpty()) {
                            sb.append("Data gaps:\n");
                            for (Object g : (List<?>) dataGapsObj) {
                                if (g instanceof Map) {
                                    Map<?, ?> gap = (Map<?, ?>) g;
                                    sb.append("- ").append(gap.get("source")).append("/").append(gap.get("reason"))
                                            .append(": ").append(gap.get("impact")).append("\n");
                                }
                            }
                        }

                        contextString = sb.toString();
                        if (contextString.length() > MAX_TEST_RESULT_CONTEXT_CHARS) {
                            contextString = contextString.substring(0, MAX_TEST_RESULT_CONTEXT_CHARS)
                                + "\n\n[... truncated server-side ...]";
                        }
                        tokensLimit = 40000;
                    }
                }
            }

            // A follow-up message (or a chat reopened from history) often arrives without metaData; reuse the context
            // stored with the conversation instead of answering without it.
            boolean contextSuppliedThisTurn = StringUtils.isNotEmpty(contextString);
            if (!contextSuppliedThisTurn && !isFirstRequest) {
                // Read only the two fields needed: a stored turn also holds the full response and prompt
                GenericAgentConversation stored = AgentConversationDao.instance.getMCollection()
                    .find(Filters.and(
                        Filters.eq(GenericAgentConversation._CONVERSATION_ID, conversationId),
                        Filters.exists(GenericAgentConversation._CONTEXT_STRING, true),
                        Filters.ne(GenericAgentConversation._CONTEXT_STRING, "")))
                    .projection(Projections.include(GenericAgentConversation._CONTEXT_STRING, "tokensLimit"))
                    .sort(Sorts.descending("createdAt"))
                    .first();
                if (stored != null) {
                    contextString = stored.getContextString();
                    tokensLimit = Math.max(tokensLimit, stored.getTokensLimit());
                }
            }

            String userEmail = getSUser() != null ? getSUser().getLogin() : null;
            String contextSource = Context.contextSource.get() != null ? Context.contextSource.get().toString() : null;
            GenericAgentConversation responseFromMcpServer = agentClient.getResponseFromMcpServer(message, conversationId, tokensLimit, storedTitle, conversationTypeEnum, accessTokenForRequest, contextString, userEmail, contextSource);
            if(responseFromMcpServer != null) {
                responseFromMcpServer.setCreatedAt(timeNow);
                responseFromMcpServer.setUserId(getSUser().getId());
                // Store only context that came with this turn; reused context already sits on an earlier turn
                if (contextSuppliedThisTurn) {
                    responseFromMcpServer.setContextString(contextString);
                }
                // Later turns reuse the first turn's title, so never store the agent's placeholder title
                if (isFirstRequest && isPlaceholderTitle(responseFromMcpServer.getTitle())) {
                    responseFromMcpServer.setTitle(titleFromPrompt(message));
                }
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
            boolean singleConversation = StringUtils.isNotEmpty(conversationId);

            List<Bson> matchFilters = new ArrayList<>();
            matchFilters.add(AgentConversationDao.instance.getContextSourceFilter());
            if (singleConversation) {
                matchFilters.add(Filters.eq(GenericAgentConversation._CONVERSATION_ID, conversationId));
            } else if (StringUtils.isNotEmpty(searchQuery)) {
                matchFilters.add(Filters.regex("title", Pattern.compile(Pattern.quote(searchQuery), Pattern.CASE_INSENSITIVE)));
            }
            if (!singleConversation && userIds != null && !userIds.isEmpty()) {
                matchFilters.add(Filters.in(GenericAgentConversation.USER_ID, userIds));
            }

            List<Bson> pipeline = new ArrayList<>();

            pipeline.add(Aggregates.match(Filters.and(matchFilters)));
            // One document per turn: oldest first for a single conversation (chat order), newest first for the list
            pipeline.add(Aggregates.sort(singleConversation
                ? Sorts.ascending("createdAt", "lastUpdatedAt")
                : Sorts.descending("lastUpdatedAt")));
            BasicDBObject groupedId = new BasicDBObject("_id", "$conversationId");
            List<BsonField> groupAccumulators = new ArrayList<>();
            groupAccumulators.add(Accumulators.max("lastUpdatedAt", "$lastUpdatedAt"));
            groupAccumulators.add(singleConversation
                ? Accumulators.first("title", "$title")
                : Accumulators.last("title", "$title"));
            groupAccumulators.add(Accumulators.sum("tokensUsed", "$tokensUsed"));
            groupAccumulators.add(Accumulators.sum("turns", 1));
            // Stored titles are cut to ~50 chars; the list is sorted newest-first, so last() is the opening prompt
            groupAccumulators.add(singleConversation
                ? Accumulators.first("firstPrompt", "$prompt")
                : Accumulators.last("firstPrompt", "$prompt"));
            // $max skips documents without the field, so conversations stored before userId existed still resolve
            groupAccumulators.add(Accumulators.max("userId", "$" + GenericAgentConversation.USER_ID));
            groupAccumulators.add(Accumulators.first("conversationType", "$conversationType"));
            if (singleConversation || includeMessages) {
                groupAccumulators.add(Accumulators.push("messages", new BasicDBObject()
                    .append("prompt", "$prompt")
                    .append("response", "$response")
                    .append("createdAt", "$createdAt")
                ));
            }

            pipeline.add(Aggregates.group(groupedId, groupAccumulators.toArray(new BsonField[0])));
            // $group doesn't preserve order, so re-sort before limiting to keep the latest conversations
            pipeline.add(Aggregates.sort(Sorts.descending("lastUpdatedAt")));
            if (!singleConversation) {
                if (skip > 0) {
                    pipeline.add(Aggregates.skip(skip));
                }
                pipeline.add(Aggregates.limit(fetchLimit));
            }
            MongoCursor<BasicDBObject> cursor = AgentConversationDao.instance.getMCollection()
                .aggregate(pipeline, BasicDBObject.class)
                .cursor();
            
            List<BasicDBObject> conversations = new ArrayList<>();
            while (cursor.hasNext()) {
                BasicDBObject doc = cursor.next();
                conversations.add(doc);
            }

            attachUserEmails(conversations);

            BasicDBObject result = new BasicDBObject();
            result.put("history", conversations);
            if (!singleConversation) {
                // A non-empty page shorter than the limit is the last one, so the total is known without another query
                boolean lastPage = !conversations.isEmpty() && conversations.size() < fetchLimit;
                result.put("total", lastPage
                    ? skip + conversations.size()
                    : countConversations(Filters.and(matchFilters)));
            }
            if (includeUsers) {
                result.put("users", fetchConversationUsers());
            }
            this.response = result;

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.error("Error fetching conversation history", e);
            addActionError("Failed to fetch history: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    /** Replaces each row's stored user id with that user's login (as userEmail). */
    private static void attachUserEmails(List<BasicDBObject> conversations) {
        Set<Integer> userIds = new HashSet<>();
        for (BasicDBObject conversation : conversations) {
            Object id = conversation.get("userId");
            if (id instanceof Number) {
                userIds.add(((Number) id).intValue());
            }
        }
        Map<Integer, String> loginById = loginsById(userIds);
        for (BasicDBObject conversation : conversations) {
            Object id = conversation.remove("userId");
            String login = id instanceof Number ? loginById.get(((Number) id).intValue()) : null;
            if (login != null) {
                conversation.put("userEmail", login);
            }
        }
    }

    /** Users who have conversations in the current context, as {id, email}, for the user filter. */
    private static List<BasicDBObject> fetchConversationUsers() {
        Set<Integer> ids = AgentConversationDao.instance.getMCollection()
            .distinct(GenericAgentConversation.USER_ID, AgentConversationDao.instance.getContextSourceFilter(), Integer.class)
            .into(new HashSet<>());
        List<BasicDBObject> users = new ArrayList<>();
        loginsById(ids).forEach((id, login) -> users.add(new BasicDBObject("id", id).append("email", login)));
        users.sort(Comparator.comparing(user -> user.getString("email")));
        return users;
    }

    private static Map<Integer, String> loginsById(Set<Integer> userIds) {
        Map<Integer, String> loginById = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (User user : UsersDao.instance.findAll(Filters.in(User.ID, userIds), Projections.include(User.LOGIN))) {
                loginById.put(user.getId(), user.getLogin());
            }
        }
        return loginById;
    }

    /** Number of matching conversations (not turns). */
    private static int countConversations(Bson matchFilter) {
        BasicDBObject countDoc = AgentConversationDao.instance.getMCollection()
            .aggregate(Arrays.asList(
                Aggregates.match(matchFilter),
                Aggregates.group("$conversationId"),
                Aggregates.count("total")), BasicDBObject.class)
            .first();
        return countDoc != null ? countDoc.getInt("total") : 0;
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

    private static final int MAX_TITLE_LENGTH = 60;

    // Blank, "Untitled", or the "null" text AgentClient produces for a JSON null title
    private static boolean isPlaceholderTitle(String title) {
        return StringUtils.isBlank(title) || "Untitled".equalsIgnoreCase(title.trim())
            || "null".equalsIgnoreCase(title.trim());
    }

    private static String titleFromPrompt(String prompt) {
        String title = prompt == null ? "" : prompt.replaceAll("\\s+", " ").trim();
        if (title.isEmpty()) {
            return "Untitled";
        }
        return title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH - 3).trim() + "..." : title;
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
    public int getSkip() { return skip; }
    public void setSkip(int skip) { this.skip = skip; }
    public String getConversationType() { return conversationType; }
    public void setConversationType(String conversationType) { this.conversationType = conversationType; }
    public String getSearchQuery() { return searchQuery; }
    public void setSearchQuery(String searchQuery) { this.searchQuery = searchQuery; }
    public List<Integer> getUserIds() { return userIds; }
    public void setUserIds(List<Integer> userIds) { this.userIds = userIds; }
    public boolean isIncludeUsers() { return includeUsers; }
    public void setIncludeUsers(boolean includeUsers) { this.includeUsers = includeUsers; }
    public boolean isIncludeMessages() { return includeMessages; }
    public void setIncludeMessages(boolean includeMessages) { this.includeMessages = includeMessages; }
    public Map<String, Object> getMetaData() { return metaData; }
    public void setMetaData(Map<String, Object> metaData) {
        this.metaData = metaData;
    }

}
