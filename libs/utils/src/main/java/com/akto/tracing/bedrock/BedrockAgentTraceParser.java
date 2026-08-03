package com.akto.tracing.bedrock;

import com.akto.dto.ApiCollection.ServiceGraphEdgeInfo;
import com.akto.dto.tracing.Trace;
import com.akto.dto.tracing.Span;
import com.akto.dto.tracing.TracingConstants;
import com.akto.tracing.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

public class BedrockAgentTraceParser implements TraceParser {

    private static final BedrockAgentTraceParser INSTANCE = new BedrockAgentTraceParser();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SOURCE_TYPE = "bedrock-agent";
    private static final String AGENT_TYPE_AGENTCORE = "AGENTCORE";
    private static final String AGENT_TYPE_BEDROCK = "BEDROCK";

    // AWS surfaces the execution role under different keys depending on how the
    // agent was deployed: Harness-based AgentCore, native AgentCore Runtime, or a
    // bare Bedrock model invoke with no agent harness at all.
    private static final String FIELD_HARNESS_ROLE = "harness-execution-role";
    private static final String FIELD_RUNTIME_ROLE = "runtime-execution-role";
    private static final String FIELD_BEDROCK_ROLE = "bedrock-execution-role";

    // CloudWatch's trace data has no structured success/failure field for a tool
    // result — only free-text. These are the phrasings actually observed across real
    // tool failures (validation errors, missing-session lookups, etc.); this is a
    // heuristic, not a guarantee, since a tool can fail in wording we haven't seen yet.
    private static final String[] FAILURE_INDICATORS = {
        "error", "not found", "failed", "invalid", "denied", "exception", "unable to"
    };

    private boolean looksLikeFailure(String result) {
        if (result == null || result.isEmpty()) {
            return false;
        }
        String lower = result.toLowerCase();
        for (String indicator : FAILURE_INDICATORS) {
            if (lower.contains(indicator)) {
                return true;
            }
        }
        return false;
    }

    public static BedrockAgentTraceParser getInstance() {
        return INSTANCE;
    }

    /**
     * The caller (HttpCallParser) already extracts and unwraps the "awsMetadata"
     * field from the response payload before handing input here, so the parsed
     * root IS the awsMetadata content — not a wrapper around it.
     */
    private JsonNode parseToJsonNode(Object input) throws Exception {
        String jsonStr = input instanceof String ? (String) input : OBJECT_MAPPER.writeValueAsString(input);
        return OBJECT_MAPPER.readTree(jsonStr);
    }

    private boolean isValidBedrockTrace(JsonNode awsMetadata) {
        boolean hasExecutionRole = awsMetadata.has(FIELD_HARNESS_ROLE)
            || awsMetadata.has(FIELD_RUNTIME_ROLE)
            || awsMetadata.has(FIELD_BEDROCK_ROLE);
        if (!hasExecutionRole) {
            return false;
        }
        return awsMetadata.has("model") && awsMetadata.has("traceData");
    }

    // Harness-based AgentCore and native AgentCore Runtime traces both drive tool
    // calls through an agent; a bare "bedrock-execution-role" means a direct,
    // un-orchestrated model invoke with no agent involved.
    private String resolveAgentType(JsonNode awsMetadata) {
        if (awsMetadata.has(FIELD_HARNESS_ROLE) || awsMetadata.has(FIELD_RUNTIME_ROLE)) {
            return AGENT_TYPE_AGENTCORE;
        }
        return AGENT_TYPE_BEDROCK;
    }

    private String extractExecutionRoleValue(JsonNode awsMetadata) {
        String field = awsMetadata.has(FIELD_HARNESS_ROLE) ? FIELD_HARNESS_ROLE
            : awsMetadata.has(FIELD_RUNTIME_ROLE) ? FIELD_RUNTIME_ROLE
            : FIELD_BEDROCK_ROLE;
        String value = awsMetadata.path(field).asText("");
        return value.isEmpty() ? "unknown" : value;
    }

    @Override
    public boolean canParse(Object input) {
        if (input == null) return false;

        try {
            return isValidBedrockTrace(parseToJsonNode(input));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public TraceParseResult parse(Object input) throws Exception {
        return parse(input, null);
    }

    /** @param botName the HTTP-level "bot-name" tag; this class has no other source for it. */
    public TraceParseResult parse(Object input, String botName) throws Exception {
        try {
            JsonNode awsMetadata = parseToJsonNode(input);

            if (!isValidBedrockTrace(awsMetadata)) {
                throw new Exception("Invalid Bedrock Agent trace: " + awsMetadata);
            }

            // Extract agent/harness info
            botName = extractBotName(botName);
            String model = awsMetadata.path("model").asText("unknown");
            String agentType = resolveAgentType(awsMetadata);

            // Generate IDs
            String traceId = UUID.randomUUID().toString();
            String rootSpanId = UUID.randomUUID().toString();

            // Parse trace data from executionFlow
            JsonNode traceData = awsMetadata.path("traceData");
            JsonNode executionFlow = traceData.path("executionFlow");
            JsonNode toolsSummary = traceData.path("toolsSummary");

            // Build spans from execution flow
            List<Span> spans = buildSpansFromExecutionFlow(traceId, rootSpanId, executionFlow);

            // Build trace metadata
            Map<String, Object> metadata = buildTraceMetadata(awsMetadata, agentType, toolsSummary);

            // Create input/output maps
            Map<String, Object> rootInput = new HashMap<>();
            rootInput.put("model", model);
            rootInput.put("agent", botName);

            Map<String, Object> rootOutput = new HashMap<>();
            rootOutput.put("status", "processed");

            // Create Trace object
            long currentTimeMillis = System.currentTimeMillis();
            Trace trace = Trace.builder()
                .id(traceId)
                .rootSpanId(rootSpanId)
                .aiAgentName(botName)
                .name(botName + " - " + model)
                .startTimeMillis(currentTimeMillis)
                .endTimeMillis(currentTimeMillis)
                .status("success")
                .totalSpans(spans.size())
                .totalTokens(0)
                .totalInputTokens(0)
                .totalOutputTokens(0)
                .rootInput(rootInput)
                .rootOutput(rootOutput)
                .spanIds(spans.stream().map(Span::getId).collect(Collectors.toList()))
                .metadata(metadata)
                .build();

            Map<String, Object> resultMetadata = new HashMap<>();
            resultMetadata.put("botName", botName);
            resultMetadata.put("model", model);
            resultMetadata.put("agentType", agentType);

            return TraceParseResult.builder()
                .trace(trace)
                .spans(spans)
                .workflowId(botName)
                .sourceIdentifier(traceId)
                .metadata(resultMetadata)
                .build();

        } catch (Exception e) {
            throw new Exception("Failed to parse Bedrock Agent trace: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, ServiceGraphEdgeInfo> extractServiceGraph(Object input) throws Exception {
        return extractServiceGraph(input, null);
    }

    /** @param botName see {@link #parse(Object, String)}. */
    public Map<String, ServiceGraphEdgeInfo> extractServiceGraph(Object input, String botName) throws Exception {
        try {
            JsonNode awsMetadata = parseToJsonNode(input);

            if (!isValidBedrockTrace(awsMetadata)) {
                throw new Exception("Invalid Bedrock Agent trace for service graph extraction");
            }

            String agentType = resolveAgentType(awsMetadata);

            Map<String, ServiceGraphEdgeInfo> edges = new HashMap<>();

            // Extract model edge
            String model = awsMetadata.path("model").asText("unknown");
            String executionRole = extractExecutionRoleValue(awsMetadata);

            String sourceService = extractBotName(botName);

            // The agent node is only ever a source below, never a target — without an
            // edge that targets it, the UI has no "type" for it and defaults to
            // "Internal Service" (see buildServiceGraphFromSpans in HttpCallParser for
            // the same pattern used by Copilot/Snowflake).
            Map<String, Object> agentMetadata = new HashMap<>();
            agentMetadata.put("type", TracingConstants.SpanKind.AGENT);
            agentMetadata.put("edgeParam", "AI Agent");
            agentMetadata.put("role", executionRole);
            edges.put(sourceService, new ServiceGraphEdgeInfo("User", sourceService, agentMetadata));

            // LLM Call edge
            Map<String, Object> llmMetadata = new HashMap<>();
            llmMetadata.put("type", "llmCall");
            llmMetadata.put("edgeParam", "Call to model");
            edges.put(model, new ServiceGraphEdgeInfo(sourceService, model, llmMetadata));

            // Add tools edge for AgentCore
            if (agentType.equals(AGENT_TYPE_AGENTCORE)) {
                JsonNode toolsSummary = awsMetadata.path("traceData").path("toolsSummary");
                JsonNode toolsActuallyUsed = toolsSummary.path("tools");

                if (toolsActuallyUsed.isArray() && toolsActuallyUsed.size() > 0) {
                    // Reflects what this specific request actually called, not just what
                    // the harness has configured — one edge per distinct tool used.
                    int totalToolCalls = toolsSummary.path("totalToolCalls").asInt(0);
                    for (JsonNode toolNameNode : toolsActuallyUsed) {
                        String toolName = toolNameNode.asText("unknown");
                        Map<String, Object> toolsMetadata = new HashMap<>();
                        toolsMetadata.put("type", "tool");
                        toolsMetadata.put("edgeParam", "tool call");
                        toolsMetadata.put("totalToolCalls", totalToolCalls);
                        edges.put(toolName, new ServiceGraphEdgeInfo(sourceService, toolName, toolsMetadata));
                    }
                } else {
                    // No tool was actually called in this request — fall back to the
                    // harness's static configuration so the graph still shows what it can do.
                    String tools = awsMetadata.path("harness-configured-tools").asText("unknown");
                    if (!tools.isEmpty() && !tools.equals("unknown")) {
                        Map<String, Object> toolsMetadata = new HashMap<>();
                        toolsMetadata.put("type", "tool");
                        toolsMetadata.put("edgeParam", "configured tools");
                        edges.put(tools, new ServiceGraphEdgeInfo(sourceService, tools, toolsMetadata));
                    }
                }

                // Add skills edge (always static config — we don't yet extract per-call skill usage)
                String skills = awsMetadata.path("harness-configured-skills").asText("unknown");
                if (!skills.isEmpty() && !skills.equals("unknown")) {
                    Map<String, Object> skillsMetadata = new HashMap<>();
                    skillsMetadata.put("type", "skill");
                    skillsMetadata.put("edgeParam", "configured skills");
                    edges.put(skills, new ServiceGraphEdgeInfo(sourceService, skills, skillsMetadata));
                }
            }

            return edges;

        } catch (Exception e) {
            throw new Exception("Failed to extract Bedrock Agent service graph: " + e.getMessage(), e);
        }
    }

    @Override
    public String getSourceType() {
        return SOURCE_TYPE;
    }

    // The HTTP-level "bot-name" tag (also used to name the collection itself) is the
    // agent's identity — awsMetadata's own traceData can be empty (e.g. a plain
    // Converse call), so it's not a reliable source to derive a name from.
    private String extractBotName(String botName) {
        return (botName != null && !botName.isEmpty()) ? botName : "Bedrock Agent";
    }

    private List<Span> buildSpansFromExecutionFlow(String traceId, String rootSpanId, JsonNode executionFlow) {
        List<Span> spans = new ArrayList<>();
        String parentSpanId = rootSpanId;

        if (!executionFlow.isArray()) {
            return spans;
        }

        // executionFlow steps form a linear chain (each step's parent is the previous
        // step), so depth == position in the chain.
        int depth = 0;
        for (JsonNode step : executionFlow) {
            String spanId = UUID.randomUUID().toString();
            String stepType = step.path("type").asText("unknown");
            String spanKind = mapStepTypeToSpanKind(stepType, step);
            String name = step.path("name").asText(step.path("tool").asText("unknown"));

            Map<String, Object> input = new HashMap<>();
            input.put("type", stepType);
            if ("tool-call".equalsIgnoreCase(stepType)) {
                input.put("tool", step.path("tool").asText("unknown"));
                input.put("action", step.path("action").asText("unknown"));
            }

            // The tool's actual result/error text (empty for non-tool-call steps, e.g. the
            // root "agent" step). See looksLikeFailure() for why this is a heuristic.
            String result = step.path("result").asText("");
            boolean failed = looksLikeFailure(result);

            Map<String, Object> output = new HashMap<>();
            output.put("completed", !failed);
            if (!result.isEmpty()) {
                output.put("result", result);
            }

            long stepTimeMillis = System.currentTimeMillis();
            Span span = Span.builder()
                .id(spanId)
                .traceId(traceId)
                .parentSpanId(parentSpanId)
                .spanKind(spanKind)
                .name(name)
                .startTimeMillis(stepTimeMillis)
                .endTimeMillis(stepTimeMillis)
                .status(failed ? "error" : "success")
                .input(input)
                .output(output)
                .metadata(extractStepMetadata(step, stepType))
                .depth(depth++)
                .tags(Arrays.asList(SOURCE_TYPE, stepType))
                .build();

            spans.add(span);
            parentSpanId = spanId; // Set as parent for next span
        }

        return spans;
    }

    private String mapStepTypeToSpanKind(String stepType, JsonNode step) {
        if ("agent".equalsIgnoreCase(stepType)) {
            return TracingConstants.SpanKind.AGENT;
        }
        if ("tool-call".equalsIgnoreCase(stepType)) {
            return TracingConstants.SpanKind.TOOL;
        }
        if ("llm-call".equalsIgnoreCase(stepType)) {
            return TracingConstants.SpanKind.LLM;
        }
        return TracingConstants.SpanKind.TASK;
    }

    private Map<String, Object> extractStepMetadata(JsonNode step, String stepType) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", stepType);
        metadata.put("sourceType", SOURCE_TYPE);

        if ("tool-call".equalsIgnoreCase(stepType)) {
            metadata.put("tool", step.path("tool").asText("unknown"));
            metadata.put("action", step.path("action").asText("unknown"));
            metadata.put("toolUseId", step.path("toolUseId").asText(""));
            metadata.put("result", step.path("result").asText(""));
        }

        if (step.has("description")) {
            metadata.put("description", step.path("description").asText(""));
        }

        return metadata;
    }

    private Map<String, Object> buildTraceMetadata(JsonNode awsMetadata, String agentType, JsonNode toolsSummary) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sourceType", SOURCE_TYPE);
        metadata.put("agentType", agentType);
        metadata.put("model", awsMetadata.path("model").asText("unknown"));

        // Add role information
        if (agentType.equals(AGENT_TYPE_AGENTCORE)) {
            metadata.put("harnessExecutionRole", awsMetadata.path(FIELD_HARNESS_ROLE).asText(""));
            metadata.put("runtimeExecutionRole", awsMetadata.path(FIELD_RUNTIME_ROLE).asText(""));
            metadata.put("configuredTools", awsMetadata.path("harness-configured-tools").asText(""));
            metadata.put("configuredSkills", awsMetadata.path("harness-configured-skills").asText(""));
        } else {
            metadata.put("bedrockExecutionRole", awsMetadata.path(FIELD_BEDROCK_ROLE).asText(""));
        }

        // Add execution summary
        if (!toolsSummary.isMissingNode()) {
            metadata.put("toolCount", toolsSummary.path("totalToolCalls").asInt(0));
            metadata.put("executionPattern", toolsSummary.path("executionPattern").asText(""));
        }

        return metadata;
    }
}
