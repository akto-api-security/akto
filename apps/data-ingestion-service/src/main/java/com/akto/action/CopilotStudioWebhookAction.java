package com.akto.action;

import com.akto.data_actor.ClientActor;
import com.akto.dto.AgenticUsers;
import com.akto.gateway.Gateway;
import com.akto.jobs.executors.AIAgentConnectorConstants;
import com.akto.jobs.executors.copilotstudio.CopilotStudioAgentUsersCron;
import com.akto.jobs.executors.copilotstudio.CopilotStudioInventoryPublisher;
import com.akto.log.LoggerMaker;
import com.akto.publisher.KafkaDataPublisher;
import com.akto.util.Constants;
import com.akto.util.JSONUtils;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.json.annotations.JSON;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@lombok.Getter
@lombok.Setter
public class CopilotStudioWebhookAction extends ActionSupport {

    private static final LoggerMaker loggerMaker = new LoggerMaker(CopilotStudioWebhookAction.class, LoggerMaker.LogDb.DATA_INGESTION);
    private static final Gateway gateway = Gateway.getInstance();

    static {
        gateway.setDataPublisher(new KafkaDataPublisher());
    }

    private static final String MCP_TOOL_TYPE = "DynamicServerToolDefinition";

    private boolean isSuccessful;
    private String status;

    private boolean blockAction;
    private String reason;
    private String diagnostics;

    public String checkReady() {
        isSuccessful = true;
        status = "OK";
        return Action.SUCCESS.toUpperCase();
    }

    /**
     * Explicit getter (Lombok would otherwise generate this) forced to serialize as "isSuccessful"
     * via @JSON(name=...): the JavaBean convention strips a leading "is" off a boolean getter to
     * derive its property name, so a plain isSuccessful() getter serializes as "successful" — which
     * never matches struts.xml's includeProperties pattern and silently drops the field. Microsoft's
     * ValidationResponse contract requires the literal key "isSuccessful".
     */
    @JSON(name = "isSuccessful")
    public boolean isSuccessful() {
        return isSuccessful;
    }

    @SuppressWarnings("unchecked")
    public String analyzeToolExecution() {
        try {
            Map<String, Object> body = parseJsonBody();

            Map<String, Object> plannerContext = (Map<String, Object>) body.getOrDefault("plannerContext", new HashMap<>());
            Map<String, Object> toolDefinition = (Map<String, Object>) body.getOrDefault("toolDefinition", new HashMap<>());
            Map<String, Object> inputValues = (Map<String, Object>) body.getOrDefault("inputValues", new HashMap<>());
            Map<String, Object> conversationMetadata = (Map<String, Object>) body.getOrDefault("conversationMetadata", new HashMap<>());
            Map<String, Object> agent = (Map<String, Object>) conversationMetadata.getOrDefault("agent", new HashMap<>());
            Map<String, Object> userContext = (Map<String, Object>) conversationMetadata.getOrDefault("user", new HashMap<>());

            String conversationId = stringOrEmpty(conversationMetadata.get("conversationId"));
            String hostUserId = resolveHostUserId(stringOrEmpty(userContext.get("id")));

            Map<String, Object> requestData = buildEvaluationRequestData(
                plannerContext, toolDefinition, inputValues, agent, hostUserId, conversationId);

            Map<String, Object> guardrailsResult = callGuardrails(requestData);
            if (blockedFrom(guardrailsResult)) {
                blockAction = true;
                reason = reasonFrom(guardrailsResult);
            } else {
                blockAction = false;
            }
            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("CopilotStudioWebhookAction - failing open: " + e.getMessage());
            blockAction = false;
            return Action.SUCCESS.toUpperCase();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callGuardrails(Map<String, Object> requestData) throws Exception {
        Map<String, Object> result = gateway.processHttpProxy(requestData);
        if (result == null || !Boolean.TRUE.equals(result.get("success"))) {
            Object message = result != null ? result.get("message") : "gateway returned null";
            throw new RuntimeException("guardrails call did not succeed: " + message);
        }
        Object guardrailsResult = result.get("guardrailsResult");
        return guardrailsResult instanceof Map ? (Map<String, Object>) guardrailsResult : null;
    }

    private boolean blockedFrom(Map<String, Object> guardrailsResult) {
        if (guardrailsResult == null) return false;
        Object allowed = guardrailsResult.get("Allowed");
        if (allowed instanceof Boolean) return !((Boolean) allowed);
        if (allowed != null) return !Boolean.parseBoolean(allowed.toString());
        return false;
    }

    private String reasonFrom(Map<String, Object> guardrailsResult) {
        if (guardrailsResult == null) return "";
        Object r = guardrailsResult.get("Reason");
        return r != null ? r.toString() : "";
    }

    /**
     * One evaluation call covering both the user's message and the tool/MCP call in a single
     * request — Microsoft's webhook already hands us both in one call to analyzeToolExecution,
     * so there's no need for two separate guardrails+ingestion round trips (and two separate
     * traffic samples/paths) to cover them.
     */
    private Map<String, Object> buildEvaluationRequestData(Map<String, Object> plannerContext, Map<String, Object> toolDefinition,
            Map<String, Object> inputValues, Map<String, Object> agent, String hostUserId, String conversationId) {
        boolean isMcp = MCP_TOOL_TYPE.equals(stringOrEmpty(toolDefinition.get("type")));
        String toolId = stringOrEmpty(toolDefinition.get("id"));
        String rawToolName = stringOrEmpty(toolDefinition.get("name"));
        String toolName = isMcp ? mcpOperationName(toolId, rawToolName) : rawToolName;
        String agentName = agentDisplayName(agent);

        Map<String, Object> requestData = newBaseRequestData(agent, agentName, hostUserId, conversationId);
        String path = !toolName.isEmpty()
            ? "/copilot/conversation/tool/" + toolName.toLowerCase()
            : "/copilot/conversation/messages/" + conversationId;
        requestData.put("path", path);

        Map<String, Object> evaluationPayload = new LinkedHashMap<>();
        evaluationPayload.put("userMessage", stringOrEmpty(plannerContext.get("userMessage")));
        evaluationPayload.put("toolName", toolName);
        evaluationPayload.put("toolInput", inputValues != null ? inputValues : new HashMap<>());
        requestData.put("requestPayload", JSONUtils.getString(evaluationPayload));

        requestData.put("tag", JSONUtils.getString(buildCopilotStudioTag(agentName, stringOrEmpty(agent.get("environmentId")))));
        return requestData;
    }

    private Map<String, Object> newBaseRequestData(Map<String, Object> agent, String agentName, String hostUserId, String conversationId) {
        Map<String, Object> requestData = new LinkedHashMap<>();
        requestData.put("guardrails", "true");
        requestData.put("ingest_data", "true");
        requestData.put("method", "POST");
        requestData.put("contextSource", "ENDPOINT");
        requestData.put("source", "copilot-studio");
        requestData.put("akto_account_id", resolveAccountId());
        requestData.put("ip", clientIpFromHeaders());
        requestData.put("responsePayload", "{}");
        requestData.put("responseHeaders", "{}");
        requestData.put("requestHeaders", buildRequestHeaders(aiAgentHost(hostUserId, agentName), conversationId));
        return requestData;
    }

    private static String resolveAccountId() {
        return String.valueOf(ClientActor.getAccountId());
    }

    /**
     * Resolves the Copilot Studio conversation's AAD user id (conversationMetadata.user.id) to
     * the sanitized host-segment string cached by CopilotStudioAgentUsersCron (see
     * CopilotStudioUserResolver.buildUserId); falls back to a fixed sentinel when unresolved
     * (missing from the payload, or no cache hit yet).
     */
    private static String resolveHostUserId(String aadUserId) {
        if (aadUserId == null || aadUserId.isEmpty()) {
            return AIAgentConnectorConstants.UNRESOLVED_AGENT_USER_ID;
        }
        AgenticUsers cachedUser = CopilotStudioAgentUsersCron.getCachedUser(aadUserId);
        String userName = cachedUser != null ? cachedUser.getUserName() : null;
        return (userName != null && !userName.isEmpty()) ? userName : AIAgentConnectorConstants.UNRESOLVED_AGENT_USER_ID;
    }

    private static String mcpOperationName(String toolId, String toolName) {
        int idx = toolId.indexOf('~');
        if (idx < 0 || idx + 1 >= toolId.length()) return toolName;
        return toolId.substring(idx + 1);
    }

    private static String agentDisplayName(Map<String, Object> agent) {
        String name = CopilotStudioInventoryPublisher.sanitizeBotName(stringOrEmpty(agent.get("name")));
        if (!name.isEmpty()) return name;
        return CopilotStudioInventoryPublisher.sanitizeBotName(stringOrEmpty(agent.get("id")));
    }

    /** {hostUserId}.ai-agent.{agentName} — same shape CopilotStudioInventoryPublisher already uses, so transcript and inventory data for the same (user, agent) pair land in the same collection. */
    private static String aiAgentHost(String hostUserId, String agentName) {
        if (agentName == null || agentName.isEmpty()) return "";
        return hostUserId + AIAgentConnectorConstants.AI_AGENT_HOST_INFIX + agentName;
    }

    private static Map<String, String> flattenHeaders() {
        Map<String, String> flat = new LinkedHashMap<>();
        HttpServletRequest request = ServletActionContext.getRequest();
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) return flat;
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if ("Authorization".equalsIgnoreCase(name)) {
                flat.put(name, "REDACTED");
                continue;
            }
            flat.put(name, request.getHeader(name));
        }
        return flat;
    }

    private static String clientIpFromHeaders() {
        HttpServletRequest request = ServletActionContext.getRequest();
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            String first = xff.split(",")[0].trim();
            if (!first.isEmpty()) return normalizeIp(first);
        }
        return normalizeIp(request.getHeader("X-Real-Ip"));
    }

    private static String normalizeIp(String ip) {
        if (ip == null) return null;
        if (ip.toLowerCase().startsWith("::ffff:")) {
            return ip.substring(7);
        }
        return ip;
    }

    private static Map<String, Object> buildCopilotStudioTag(String agentName, String environmentId) {
        Map<String, Object> tags = new LinkedHashMap<>();
        tags.put(Constants.AKTO_ENDPOINT_SOURCE_TAG, "ENDPOINT");
        tags.put("bot-environment-id", environmentId);
        tags.put(Constants.AKTO_GEN_AI_TAG, AIAgentConnectorConstants.DATA_TAG_GEN_AI);
        tags.put(Constants.AKTO_AI_AGENT_TAG, agentName);
        tags.put(Constants.AKTO_SAAS_AGENT_TAG, Constants.COPILOT_STUDIO_AI_AGENT_NAME);
        return tags;
    }

    private static String buildRequestHeaders(String host, String conversationId) {
        Map<String, String> headers = flattenHeaders();
        if (host != null && !host.isEmpty()) {
            headers.put("Host", host);
        }
        if (conversationId != null && !conversationId.isEmpty()) {
            headers.put("X-Conversation-Id", conversationId);
        }
        return JSONUtils.getString(headers);
    }

    private Map<String, Object> parseJsonBody() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        BufferedReader reader = new BufferedReader(new InputStreamReader(request.getInputStream()));
        String jsonBody = reader.lines().collect(Collectors.joining("\n"));
        loggerMaker.info("CopilotStudioWebhookAction - incoming request headers: {}, body: {}",
            JSONUtils.getString(flattenHeaders()), jsonBody);
        if (jsonBody == null || jsonBody.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, Object> parsed = JSONUtils.getMap(jsonBody);
        return parsed != null ? parsed : new HashMap<>();
    }

    private static String stringOrEmpty(Object value) {
        return value != null ? value.toString() : "";
    }
}
