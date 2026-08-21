package com.akto.action;

import com.akto.data_actor.ClientActor;
import com.akto.gateway.Gateway;
import com.akto.log.LoggerMaker;
import com.akto.publisher.KafkaDataPublisher;
import com.akto.util.JSONUtils;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@lombok.Getter
@lombok.Setter
public class CopilotStudioWebhookAction extends ActionSupport {

    private static final LoggerMaker loggerMaker = new LoggerMaker(CopilotStudioWebhookAction.class, LoggerMaker.LogDb.DATA_INGESTION);
    private static final Gateway gateway = Gateway.getInstance();

    static {
        gateway.setDataPublisher(new KafkaDataPublisher());
    }

    private static final Pattern BOT_NAME_JUNK = Pattern.compile("[^\\p{L}\\p{N}-]+");
    private static final Pattern BOT_NAME_HYPHENS = Pattern.compile("-+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-zA-Z0-9]");
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

    @SuppressWarnings("unchecked")
    public String analyzeToolExecution() {
        try {
            Map<String, Object> body = parseJsonBody();

            Map<String, Object> plannerContext = (Map<String, Object>) body.getOrDefault("plannerContext", new HashMap<>());
            Map<String, Object> toolDefinition = (Map<String, Object>) body.getOrDefault("toolDefinition", new HashMap<>());
            Map<String, Object> inputValues = (Map<String, Object>) body.getOrDefault("inputValues", new HashMap<>());
            Map<String, Object> conversationMetadata = (Map<String, Object>) body.getOrDefault("conversationMetadata", new HashMap<>());
            Map<String, Object> agent = (Map<String, Object>) conversationMetadata.getOrDefault("agent", new HashMap<>());

            String conversationId = stringOrEmpty(conversationMetadata.get("conversationId"));

            Map<String, Object> userMsgCall = buildUserMessageRequestData(plannerContext, agent, conversationId);
            Map<String, Object> toolCall = buildToolInvocationRequestData(toolDefinition, inputValues, agent, conversationId);

            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                ExecutorCompletionService<LabeledResult> completionService = new ExecutorCompletionService<>(executor);
                completionService.submit(() -> callLabeled("userMessage", userMsgCall));
                completionService.submit(() -> callLabeled("toolInvocation", toolCall));

                for (int i = 0; i < 2; i++) {
                    Future<LabeledResult> future = completionService.take();
                    LabeledResult result = future.get();
                    if (result.error != null) {
                        loggerMaker.errorAndAddToDb("CopilotStudioWebhookAction - " + result.label
                            + " guardrails call failed, failing open: " + result.error.getMessage());
                        blockAction = false;
                        return Action.SUCCESS.toUpperCase();
                    }
                    if (blockedFrom(result.guardrailsResult)) {
                        blockAction = true;
                        reason = result.label + ": " + reasonFrom(result.guardrailsResult);
                        return Action.SUCCESS.toUpperCase();
                    }
                }
                blockAction = false;
                return Action.SUCCESS.toUpperCase();
            } finally {
                executor.shutdownNow();
            }

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("CopilotStudioWebhookAction - failing open: " + e.getMessage());
            blockAction = false;
            return Action.SUCCESS.toUpperCase();
        }
    }

    private static final class LabeledResult {
        final String label;
        final Map<String, Object> guardrailsResult;
        final Exception error;

        LabeledResult(String label, Map<String, Object> guardrailsResult, Exception error) {
            this.label = label;
            this.guardrailsResult = guardrailsResult;
            this.error = error;
        }
    }

    private LabeledResult callLabeled(String label, Map<String, Object> requestData) {
        try {
            return new LabeledResult(label, callGuardrails(requestData), null);
        } catch (Exception e) {
            return new LabeledResult(label, null, e);
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

    private Map<String, Object> buildUserMessageRequestData(Map<String, Object> plannerContext, Map<String, Object> agent, String conversationId) {
        String userMessage = stringOrEmpty(plannerContext.get("userMessage"));
        String agentName = agentDisplayName(agent);

        Map<String, Object> requestData = newBaseRequestData(agent, agentName, conversationId, false);
        requestData.put("path", "/copilot/conversation/messages/" + conversationId);
        requestData.put("requestPayload", JSONUtils.getString(Collections.singletonMap("prompt", userMessage)));
        requestData.put("tag", JSONUtils.getString(buildCopilotStudioTag(agentName, stringOrEmpty(agent.get("environmentId")), false)));
        return requestData;
    }

    private Map<String, Object> buildToolInvocationRequestData(Map<String, Object> toolDefinition, Map<String, Object> inputValues, Map<String, Object> agent, String conversationId) {
        boolean isMcp = MCP_TOOL_TYPE.equals(stringOrEmpty(toolDefinition.get("type")));
        String toolName = stringOrEmpty(toolDefinition.get("name"));
        String toolId = stringOrEmpty(toolDefinition.get("id"));
        String agentName = agentDisplayName(agent);

        Map<String, Object> requestData = newBaseRequestData(agent, agentName, conversationId, isMcp);
        if (isMcp) {
            requestData.put("path", "/copilot/mcp");
            String operationName = mcpOperationName(toolId, toolName);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("name", operationName);
            params.put("arguments", inputValues);
            Map<String, Object> rpc = new LinkedHashMap<>();
            rpc.put("jsonrpc", "2.0");
            rpc.put("id", 1);
            rpc.put("method", "tools/call");
            rpc.put("params", params);
            requestData.put("requestPayload", JSONUtils.getString(rpc));

            String mcpHost = copilotStudioMcpHost(agentName, mcpServerName(toolId, toolName));
            requestData.put("requestHeaders", buildRequestHeaders(mcpHost, conversationId));
        } else {
            requestData.put("path", "/copilot/tool/" + toolName.toLowerCase());
            requestData.put("requestPayload", JSONUtils.getString(inputValues));
        }
        requestData.put("tag", JSONUtils.getString(buildCopilotStudioTag(agentName, stringOrEmpty(agent.get("environmentId")), isMcp)));
        return requestData;
    }

    private Map<String, Object> newBaseRequestData(Map<String, Object> agent, String agentName, String conversationId, boolean isMcp) {
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
        String host = isMcp ? "" : copilotStudioHost(agentName, stringOrEmpty(agent.get("environmentId")));
        requestData.put("requestHeaders", buildRequestHeaders(host, conversationId));
        return requestData;
    }

    private static String resolveAccountId() {
        Integer accountId = ClientActor.getAbstractorAccountIdFromEnvOrNull();
        return accountId != null ? String.valueOf(accountId) : "1000000";
    }

    private static String mcpServerName(String toolId, String toolName) {
        int idx = toolId.indexOf('~');
        if (idx < 0) return toolName;
        String operation = toolId.substring(idx + 1);
        if (!operation.isEmpty() && toolName.endsWith("-" + operation)) {
            return toolName.substring(0, toolName.length() - operation.length() - 1);
        }
        return toolName;
    }

    private static String mcpOperationName(String toolId, String toolName) {
        int idx = toolId.indexOf('~');
        if (idx < 0 || idx + 1 >= toolId.length()) return toolName;
        return toolId.substring(idx + 1);
    }

    private static String sanitizeBotName(String name) {
        if (name == null || name.isEmpty()) return "";
        String sanitized = BOT_NAME_JUNK.matcher(name).replaceAll("-");
        sanitized = BOT_NAME_HYPHENS.matcher(sanitized).replaceAll("-");
        sanitized = sanitized.replaceAll("^-+|-+$", "");
        return sanitized.toLowerCase();
    }

    private static String sanitizeEnvironmentId(String environmentId) {
        if (environmentId == null) return "";
        String suffix = NON_ALPHANUMERIC.matcher(environmentId).replaceAll("");
        if (suffix.length() > 10) suffix = suffix.substring(0, 10);
        return suffix.toLowerCase();
    }

    private static String agentDisplayName(Map<String, Object> agent) {
        String name = sanitizeBotName(stringOrEmpty(agent.get("name")));
        if (!name.isEmpty()) return name;
        return sanitizeBotName(stringOrEmpty(agent.get("id")));
    }

    private static String copilotStudioHost(String agentName, String environmentId) {
        if (agentName == null || agentName.isEmpty()) return "";
        String host = agentName + ".copilot-studio";
        String envSuffix = sanitizeEnvironmentId(environmentId);
        if (!envSuffix.isEmpty()) {
            host += "-" + envSuffix;
        }
        return host + ".microsoft.com";
    }

    private static String copilotStudioMcpHost(String agentName, String mcpServerName) {
        if (agentName == null || agentName.isEmpty()) return "";
        String host = agentName + ".copilot-studio";
        String serverSuffix = sanitizeBotName(mcpServerName);
        if (!serverSuffix.isEmpty()) {
            host += "." + serverSuffix;
        }
        return host;
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

    private static Map<String, Object> buildCopilotStudioTag(String agentName, String environmentId, boolean isMcp) {
        Map<String, Object> tags = new LinkedHashMap<>();
        tags.put("source", "ENDPOINT");
        tags.put("bot-environment-id", environmentId);
        // tags.put("bot-name", agentName);
        tags.put("ai-agent", "copilot-studio");
        if (isMcp) {
            tags.put("mcp-server", "MCP Server");
            tags.put("mcp-client", "copilot-studio");
        } else {
            tags.put("gen-ai", "Gen AI");
            tags.put("ai-agent", "copilot-studio");
        }
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
