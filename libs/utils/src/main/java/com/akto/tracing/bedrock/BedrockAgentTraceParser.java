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

    public static BedrockAgentTraceParser getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean canParse(Object input) {
        if (input == null) return false;

        try {
            String jsonStr = input instanceof String ? (String) input : OBJECT_MAPPER.writeValueAsString(input);
            JsonNode root = OBJECT_MAPPER.readTree(jsonStr);

            // Check for awsMetadata field
            JsonNode awsMetadata = root.path("awsMetadata");
            if (awsMetadata.isMissingNode()) {
                return false;
            }

            // Check for either harness or bedrock execution role
            boolean hasHarnessRole = awsMetadata.has("harness-execution-role");
            boolean hasBedrockRole = awsMetadata.has("bedrock-execution-role");

            if (!hasHarnessRole && !hasBedrockRole) {
                return false;
            }

            // Check for model and traceData
            return awsMetadata.has("model") && awsMetadata.has("traceData");

        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public TraceParseResult parse(Object input) throws Exception {
        try {
            String jsonStr = input instanceof String ? (String) input : OBJECT_MAPPER.writeValueAsString(input);
            JsonNode root = OBJECT_MAPPER.readTree(jsonStr);

            if (!canParse(input)) {
                throw new Exception("Invalid Bedrock Agent trace: " + jsonStr);
            }

            JsonNode awsMetadata = root.path("awsMetadata");

            // Extract agent/harness info
            String botName = extractBotName(awsMetadata);
            String model = awsMetadata.path("model").asText("unknown");
            String harnessRole = awsMetadata.path("harness-execution-role").asText("");
            String agentType = (!harnessRole.isEmpty()) ? "AGENTCORE" : "BEDROCK";

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
        try {
            String jsonStr = input instanceof String ? (String) input : OBJECT_MAPPER.writeValueAsString(input);
            JsonNode root = OBJECT_MAPPER.readTree(jsonStr);

            if (!canParse(input)) {
                throw new Exception("Invalid Bedrock Agent trace for service graph extraction");
            }

            JsonNode awsMetadata = root.path("awsMetadata");
            String harnessRole = awsMetadata.path("harness-execution-role").asText("");
            String agentType = (!harnessRole.isEmpty()) ? "AGENTCORE" : "BEDROCK";

            Map<String, ServiceGraphEdgeInfo> edges = new HashMap<>();

            // Extract model edge
            String model = awsMetadata.path("model").asText("unknown");
            String executionRole = agentType.equals("AGENTCORE") ?
                awsMetadata.path("harness-execution-role").asText("unknown") :
                awsMetadata.path("bedrock-execution-role").asText("unknown");

            String sourceService = "BEDROCK_AGENT (Role - " + executionRole + ")";

            // LLM Call edge
            Map<String, Object> llmMetadata = new HashMap<>();
            llmMetadata.put("type", "llmCall");
            llmMetadata.put("edgeParam", "Call to model");
            edges.put(model, new ServiceGraphEdgeInfo(sourceService, model, llmMetadata));

            // Add tools edge for AgentCore
            if (agentType.equals("AGENTCORE")) {
                String tools = awsMetadata.path("harness-configured-tools").asText("unknown");
                if (!tools.isEmpty() && !tools.equals("unknown")) {
                    Map<String, Object> toolsMetadata = new HashMap<>();
                    toolsMetadata.put("type", "tool");
                    toolsMetadata.put("edgeParam", "configured tools");
                    edges.put(tools, new ServiceGraphEdgeInfo(sourceService, tools, toolsMetadata));
                }

                // Add skills edge
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

    private String extractBotName(JsonNode awsMetadata) {
        // Extract bot name from first step of executionFlow
        JsonNode executionFlow = awsMetadata.path("traceData").path("executionFlow");
        if (executionFlow.isArray() && executionFlow.size() > 0) {
            JsonNode firstStep = executionFlow.get(0);
            String name = firstStep.path("name").asText(null);
            if (name != null && !name.isEmpty()) {
                return name;
            }
        }

        // Fallback: use from toolsSummary
        JsonNode toolsSummary = awsMetadata.path("traceData").path("toolsSummary");
        String orchestrator = toolsSummary.path("agentOrchestrator").asText(null);
        if (orchestrator != null && !orchestrator.isEmpty()) {
            return orchestrator;
        }

        return "bedrock-agent";
    }

    private List<Span> buildSpansFromExecutionFlow(String traceId, String rootSpanId, JsonNode executionFlow) {
        List<Span> spans = new ArrayList<>();
        String parentSpanId = rootSpanId;

        if (!executionFlow.isArray()) {
            return spans;
        }

        for (JsonNode step : executionFlow) {
            String spanId = UUID.randomUUID().toString();
            String stepType = step.path("type").asText("unknown");
            String spanKind = mapStepTypeToSpanKind(stepType, step);
            String name = step.path("name").asText(step.path("tool").asText("unknown"));

            Map<String, Object> input = new HashMap<>();
            input.put("type", stepType);

            Map<String, Object> output = new HashMap<>();
            output.put("completed", true);

            Span span = Span.builder()
                .id(spanId)
                .traceId(traceId)
                .parentSpanId(parentSpanId)
                .spanKind(spanKind)
                .name(name)
                .startTimeMillis(System.currentTimeMillis())
                .endTimeMillis(System.currentTimeMillis())
                .status("success")
                .input(input)
                .output(output)
                .metadata(extractStepMetadata(step, stepType))
                .depth(0)
                .tags(Arrays.asList(SOURCE_TYPE, stepType))
                .build();

            spans.add(span);
            parentSpanId = spanId; // Set as parent for next span
        }

        // Set depths based on hierarchy
        if (!spans.isEmpty()) {
            spans.get(0).setDepth(0);
            for (int i = 1; i < spans.size(); i++) {
                spans.get(i).setDepth(i);
            }
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
        if (agentType.equals("AGENTCORE")) {
            metadata.put("harnessExecutionRole", awsMetadata.path("harness-execution-role").asText(""));
            metadata.put("configuredTools", awsMetadata.path("harness-configured-tools").asText(""));
            metadata.put("configuredSkills", awsMetadata.path("harness-configured-skills").asText(""));
        } else {
            metadata.put("bedrockExecutionRole", awsMetadata.path("bedrock-execution-role").asText(""));
        }

        // Add execution summary
        if (!toolsSummary.isMissingNode()) {
            metadata.put("toolCount", toolsSummary.path("totalToolCalls").asInt(0));
            metadata.put("executionPattern", toolsSummary.path("executionPattern").asText(""));
        }

        return metadata;
    }
}
