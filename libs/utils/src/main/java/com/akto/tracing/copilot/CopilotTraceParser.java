package com.akto.tracing.copilot;

import com.akto.dto.ApiCollection.ServiceGraphEdgeInfo;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.tracing.Span;
import com.akto.dto.tracing.Trace;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.tracing.TraceParseResult;
import com.akto.tracing.TraceParser;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class CopilotTraceParser implements TraceParser {

    private static final LoggerMaker loggerMaker = new LoggerMaker(CopilotTraceParser.class, LogDb.RUNTIME);
    private static final CopilotTraceParser INSTANCE = new CopilotTraceParser();
    private static final Gson GSON = new Gson();
    private static final String SOURCE_TYPE = "copilot";


    public static final String INPUT_KEY_TRACE = "trace";

    public static final String INPUT_KEY_TOOL_NAME = "toolName";


    private static final String NODE_USER = "User";
    private static final String NODE_VSCODE = "VSCode";
    private static final String NODE_GUARDRAIL_SERVICE = "Guardrail Service";
    private static final String NODE_LLM_CALL = "LLM call";
    /** VSCode -> LLM call edge (always present by default). Uses distinct target key so it does not overwrite Guardrail -> LLM call. */
    private static final String NODE_LLM_CALL_FROM_VSCODE = "LLM call (VSCode)";
    private static final String NODE_TOOL_PREFIX = "Tool: ";

    public static CopilotTraceParser getInstance() {
        return INSTANCE;
    }


    public static String extractToolNameFromRequest(HttpResponseParams httpResponseParam) {
        if (httpResponseParam == null) {
            return null;
        }
        if (httpResponseParam.getRequestParams() == null) {
            return null;
        }
        HttpRequestParams request = httpResponseParam.getRequestParams();
        String path = request.getURL();
        if (path != null) {
            String pathLower = path.toLowerCase();
            int idx = pathLower.indexOf("/copilot/tool/");
            if (idx >= 0) {
                int start = idx + "/copilot/tool/".length();
                int end = path.indexOf('?', start);
                if (end < 0) end = path.length();
                if (start < end) {
                    String toolFromPath = path.substring(start, end).trim();
                    if (!toolFromPath.isEmpty()) {
                        return toolFromPath;
                    }
                }
            }
        }
        String payload = request.getPayload();

        if (payload != null && !payload.isEmpty()) {
            try {
                Map<String, Object> outer = GSON.fromJson(payload, new TypeToken<Map<String, Object>>() {}.getType());
                if (outer == null) {
                    return null;
                }
                // Request payload may be wrapped: {"body": "{\"toolName\": \"...\", \"toolArgs\": \"...\"}"}
                String innerJson = null;
                if (outer.containsKey("body")) {
                    Object bodyVal = outer.get("body");
                    if (bodyVal instanceof String) {
                        innerJson = (String) bodyVal;
                    }
                }
                if (innerJson == null) {
                    innerJson = payload;
                }
                Map<String, Object> body = GSON.fromJson(innerJson, new TypeToken<Map<String, Object>>() {}.getType());
                if (body != null && body.containsKey("toolName")) {
                    Object tn = body.get("toolName");
                    String toolName = tn != null ? tn.toString().trim() : null;

                    if (toolName != null && !toolName.isEmpty()) {
                        return toolName;
                    }
                } else {
                    loggerMaker.info("extractToolNameFromRequest: inner body null or no toolName key", LogDb.RUNTIME);
                }
            } catch (Exception e) {
                loggerMaker.info("extractToolNameFromRequest: parse exception " + e.getMessage(), LogDb.RUNTIME);
            }
        } else {
            loggerMaker.info("extractToolNameFromRequest: requestPayload null or empty", LogDb.RUNTIME);
        }
        return null;
    }

    private String getTraceJson(Object input) {
        if (input instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) input;
            Object trace = map.get(INPUT_KEY_TRACE);
            return trace != null ? trace.toString() : null;
        }
        return input instanceof String ? (String) input : null;
    }

    private String getToolName(Object input) {
        if (input instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) input;
            Object tn = map.get(INPUT_KEY_TOOL_NAME);
            return tn != null ? tn.toString().trim() : null;
        }
        return null;
    }

    @Override
    public boolean canParse(Object input) {
        String traceJson = getTraceJson(input);
        if (traceJson == null) {
            return false;
        }
        return canParseTraceJson(traceJson);
    }

    private boolean canParseTraceJson(String copilotTraceJson) {
        if (copilotTraceJson == null || copilotTraceJson.trim().isEmpty()) {
            return false;
        }
        try {
            Map<String, Object> map = GSON.fromJson(copilotTraceJson, new TypeToken<Map<String, Object>>() {}.getType());
            return map != null && !map.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public TraceParseResult parse(Object input) throws Exception {
        String traceJson = getTraceJson(input);
        if (traceJson == null) {
            throw new IllegalArgumentException("Copilot trace input must be String or Map with key '" + INPUT_KEY_TRACE + "'");
        }
        return parseTraceJson(traceJson);
    }

    private TraceParseResult parseTraceJson(String copilotTraceJson) {
        String workflowId = "copilot";
        try {
            Map<String, Object> map = GSON.fromJson(copilotTraceJson, new TypeToken<Map<String, Object>>() {}.getType());
            if (map != null && map.containsKey("id")) {
                Object id = map.get("id");
                if (id != null) {
                    workflowId = id.toString();
                }
            }
        } catch (Exception ignored) {
            // use default workflowId
        }
        String traceId = "copilot-" + System.currentTimeMillis();
        Trace trace = Trace.builder()
                .id(traceId)
                .rootSpanId(traceId)
                .name("Copilot")
                .startTimeMillis(System.currentTimeMillis())
                .endTimeMillis(System.currentTimeMillis())
                .status("ok")
                .totalSpans(0)
                .metadata(new HashMap<>())
                .build();
        List<Span> spans = new ArrayList<>();
        return TraceParseResult.builder()
                .trace(trace)
                .spans(spans)
                .workflowId(workflowId)
                .build();
    }

    @Override
    public Map<String, ServiceGraphEdgeInfo> extractServiceGraph(Object input) throws Exception {
        String traceJson = getTraceJson(input);
        String toolName = getToolName(input);
        if (traceJson == null) {
            traceJson = input instanceof String ? (String) input : "";
        }
        return buildServiceGraphEdges(traceJson, toolName);
    }


    private Map<String, ServiceGraphEdgeInfo> buildServiceGraphEdges(String copilotTraceJson, String toolName) {
        Map<String, ServiceGraphEdgeInfo> edges = new HashMap<>();

        Map<String, Object> metaVscode = new HashMap<>();
        metaVscode.put("type", "client");
        edges.put(NODE_VSCODE, new ServiceGraphEdgeInfo(NODE_USER, NODE_VSCODE, metaVscode));

        Map<String, Object> metaGuardrail = new HashMap<>();
        metaGuardrail.put("type", "guardrail");
        edges.put(NODE_GUARDRAIL_SERVICE, new ServiceGraphEdgeInfo(NODE_VSCODE, NODE_GUARDRAIL_SERVICE, metaGuardrail));

        // VSCode -> LLM call: always present by default
        Map<String, Object> metaVscodeToLlm = new HashMap<>();
        metaVscodeToLlm.put("type", "llm");
        edges.put(NODE_LLM_CALL_FROM_VSCODE, new ServiceGraphEdgeInfo(NODE_VSCODE, NODE_LLM_CALL_FROM_VSCODE, metaVscodeToLlm));

        String lastNode = (toolName != null && !toolName.isEmpty())
                ? NODE_TOOL_PREFIX
                : NODE_LLM_CALL;
        Map<String, Object> metaLast = new HashMap<>();
        metaLast.put("type", toolName != null && !toolName.isEmpty() ? "tool" : "llm");
        if (toolName != null && !toolName.isEmpty()) {
            metaLast.put("toolName", toolName);
        }
        edges.put(lastNode, new ServiceGraphEdgeInfo(NODE_VSCODE, lastNode, metaLast));

        return edges;
    }

    @Override
    public String getSourceType() {
        return SOURCE_TYPE;
    }
}
