package com.akto.tracing.n8n;

import com.akto.dto.ApiCollection.ServiceGraphEdgeInfo;
import com.akto.dto.tracing.model.Trace;
import com.akto.dto.tracing.model.Span;
import com.akto.dto.tracing.constants.TracingConstants;
import com.akto.tracing.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

public class N8nTraceParser implements TraceParser {

    private static final N8nTraceParser INSTANCE = new N8nTraceParser();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SOURCE_TYPE = "n8n";

    // Helper classes for optimized data collection
    private static class WorkflowMetadata {
        final Map<String, String> nodeTypeMap;
        final String agentName;
        final String workflowName;

        WorkflowMetadata(Map<String, String> nodeTypeMap, String agentName, String workflowName) {
            this.nodeTypeMap = nodeTypeMap;
            this.agentName = agentName;
            this.workflowName = workflowName;
        }
    }

    private static class ExecutionData {
        final List<Span> spans;
        final Map<String, String> nodeToSpanIdMap;
        final Map<String, JsonNode> nodeToExecutionMap;
        final String rootSpanId;
        final JsonNode firstExecution;
        final JsonNode lastExecution;

        ExecutionData(List<Span> spans, Map<String, String> nodeToSpanIdMap,
                     Map<String, JsonNode> nodeToExecutionMap, String rootSpanId,
                     JsonNode firstExecution, JsonNode lastExecution) {
            this.spans = spans;
            this.nodeToSpanIdMap = nodeToSpanIdMap;
            this.nodeToExecutionMap = nodeToExecutionMap;
            this.rootSpanId = rootSpanId;
            this.firstExecution = firstExecution;
            this.lastExecution = lastExecution;
        }
    }

    // N8N field names
    private static final String FIELD_ID = "id";
    private static final String FIELD_WORKFLOW_ID = "workflowId";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_STARTED_AT = "startedAt";
    private static final String FIELD_STOPPED_AT = "stoppedAt";
    private static final String FIELD_DATA = "data";
    private static final String FIELD_RESULT_DATA = "resultData";
    private static final String FIELD_RUN_DATA = "runData";
    private static final String FIELD_WORKFLOW_DATA = "workflowData";
    private static final String FIELD_NODES = "nodes";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_START_TIME = "startTime";
    private static final String FIELD_EXECUTION_TIME = "executionTime";
    private static final String FIELD_EXECUTION_STATUS = "executionStatus";
    private static final String FIELD_SOURCE = "source";
    private static final String FIELD_PREVIOUS_NODE = "previousNode";
    private static final String FIELD_INPUT_OVERRIDE = "inputOverride";

    // Agent node suffix
    private static final String AGENT_SUFFIX = ".agent";

    public static N8nTraceParser getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean canParse(Object input) {
        if (input == null) return false;

        try {
            String jsonStr = input instanceof String ? (String) input : OBJECT_MAPPER.writeValueAsString(input);
            JsonNode root = OBJECT_MAPPER.readTree(jsonStr);

            // Check for required N8N fields
            return root.has(FIELD_ID) &&
                   root.has(FIELD_WORKFLOW_ID) &&
                   root.has(FIELD_DATA) &&
                   root.path(FIELD_DATA).has(FIELD_RESULT_DATA);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public TraceParseResult parse(Object input) throws Exception {
        try {
            String jsonStr = input instanceof String ? (String) input : OBJECT_MAPPER.writeValueAsString(input);
            JsonNode root = OBJECT_MAPPER.readTree(jsonStr);

            if(!canParse(input)) {
                throw new Exception("Invalid N8N trace: " + jsonStr);
            }

            // Extract basic execution info
            String executionId = root.get(FIELD_ID).asText();
            String workflowId = root.get(FIELD_WORKFLOW_ID).asText();
            String status = root.get(FIELD_STATUS).asText();
            long startTimeMillis = parseTimestamp(root.path(FIELD_STARTED_AT).asText());
            long endTimeMillis = parseTimestamp(root.path(FIELD_STOPPED_AT).asText());

            // Single pass over workflowData to build node type map AND extract agent name
            WorkflowMetadata workflowMetadata = extractWorkflowMetadata(root.path(FIELD_WORKFLOW_DATA));

            // Parse runData into spans - Single pass to collect all necessary data
            JsonNode runData = root.path(FIELD_DATA).path(FIELD_RESULT_DATA).path(FIELD_RUN_DATA);
            String lastNodeName = root.path(FIELD_DATA).path(FIELD_RESULT_DATA).path("lastNodeExecuted").asText();

            // Parse all execution data in a single comprehensive pass
            ExecutionData executionData = parseExecutionData(runData, workflowMetadata.nodeTypeMap, lastNodeName, executionId);

            // Set parent relationships using cached execution data
            for (Span span : executionData.spans) {
                String nodeName = span.getName();
                JsonNode nodeExecution = executionData.nodeToExecutionMap.get(nodeName);

                if (nodeExecution != null && nodeExecution.has(FIELD_SOURCE)) {
                    JsonNode sources = nodeExecution.get(FIELD_SOURCE);
                    if (sources.isArray() && sources.size() > 0) {
                        String previousNodeName = sources.get(0).get(FIELD_PREVIOUS_NODE).asText();
                        String parentSpanId = executionData.nodeToSpanIdMap.get(previousNodeName);
                        span.setParentSpanId(parentSpanId);
                    }
                }
            }

            // Calculate depths
            calculateSpanDepths(executionData.spans, executionData.rootSpanId, executionData.nodeToSpanIdMap);

            // Extract root input and output using cached data
            Map<String, Object> rootInput = extractRootInputFromExecution(executionData.firstExecution);
            Map<String, Object> rootOutput = extractRootOutputFromExecution(executionData.lastExecution);

            // Build Trace object
            List<String> spanIds = executionData.spans.stream().map(Span::getTraceId).collect(Collectors.toList());

            Trace trace = Trace.builder()
                .id(executionId)
                .rootSpanId(executionData.rootSpanId)
                .aiAgentName(workflowMetadata.agentName)
                .name(workflowMetadata.workflowName)
                .startTimeMillis(startTimeMillis)
                .endTimeMillis(endTimeMillis)
                .status(mapStatus(status))
                .totalSpans(executionData.spans.size())
                .totalTokens(0)
                .totalInputTokens(0)
                .totalOutputTokens(0)
                .rootInput(rootInput)
                .rootOutput(rootOutput)
                .spanIds(spanIds)
                .metadata(buildTraceMetadata(root))
                .apiCollectionId(ServiceGraphBuilder.getInstance().getApiCollectionIdFromWorkflowId(workflowId))
                .build();

            return TraceParseResult.builder()
                .trace(trace)
                .spans(executionData.spans)
                .workflowId(workflowId)
                .sourceIdentifier(executionId)
                .metadata(Collections.singletonMap("executionId", executionId))
                .build();

        } catch (Exception e) {
            throw new Exception("Failed to parse N8N trace: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts workflow metadata (node types, agent name, and workflow name) in a single pass.
     * Optimized to avoid multiple iterations over workflowData.
     */
    private WorkflowMetadata extractWorkflowMetadata(JsonNode workflowData) {
        Map<String, String> nodeTypeMap = new HashMap<>();
        String agentName = null;
        String workflowName = workflowData.path(FIELD_NAME).asText("N8N Workflow");
        JsonNode nodes = workflowData.path(FIELD_NODES);

        if (nodes.isArray()) {
            for (JsonNode node : nodes) {
                if (node.has(FIELD_NAME) && node.has(FIELD_TYPE)) {
                    String name = node.get(FIELD_NAME).asText();
                    String type = node.get(FIELD_TYPE).asText();

                    // Store node type (stripped of common prefixes)
                    String strippedType = stripN8nPrefixes(type);
                    nodeTypeMap.put(name, strippedType);

                    // Check if this is the agent node
                    if (agentName == null && type.contains(".agent")) {
                        agentName = name;
                    }
                }
            }
        }

        // Fallback: if no agent found, look in nodeTypeMap
        if (agentName == null) {
            for (Map.Entry<String, String> entry : nodeTypeMap.entrySet()) {
                if (entry.getValue() != null && entry.getValue().toLowerCase().contains("agent")) {
                    agentName = entry.getKey();
                    break;
                }
            }
        }

        // Final fallback: use workflow name
        if (agentName == null) {
            agentName = workflowName;
        }

        return new WorkflowMetadata(nodeTypeMap, agentName, workflowName);
    }

    /**
     * Parses execution data in a single optimized pass.
     * Collects spans, mappings, and root/last execution data together.
     */
    private ExecutionData parseExecutionData(JsonNode runData, Map<String, String> nodeTypeMap, String lastNodeName, String executionId) {
        List<Span> spans = new ArrayList<>();
        Map<String, String> nodeToSpanIdMap = new HashMap<>();
        Map<String, JsonNode> nodeToExecutionMap = new HashMap<>();
        String rootSpanId = null;
        JsonNode firstExecution = null;
        JsonNode lastExecution = null;

        Iterator<Map.Entry<String, JsonNode>> nodeIterator = runData.fields();
        while (nodeIterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = nodeIterator.next();
            String nodeName = entry.getKey();
            JsonNode executions = entry.getValue();

            if (executions.isArray() && executions.size() > 0) {
                JsonNode execution = executions.get(0);
                nodeToExecutionMap.put(nodeName, execution);

                String spanId = UUID.randomUUID().toString();
                nodeToSpanIdMap.put(nodeName, spanId);

                Span span = buildSpan(
                    spanId,
                    executionId,
                    nodeName,
                    nodeTypeMap.get(nodeName),
                    execution,
                    null
                );

                spans.add(span);

                // Check if this is the root node
                boolean isRootNode = !execution.has(FIELD_SOURCE) || execution.get(FIELD_SOURCE).size() == 0;
                if (isRootNode) {
                    rootSpanId = spanId;
                    firstExecution = execution;
                }

                // Check if this is the last node
                if (nodeName.equals(lastNodeName)) {
                    lastExecution = execution;
                }
            }
        }

        return new ExecutionData(spans, nodeToSpanIdMap, nodeToExecutionMap, rootSpanId, firstExecution, lastExecution);
    }

    @Override
    public Map<String, ServiceGraphEdgeInfo> extractServiceGraph(Object input) throws Exception {
        try {
            String jsonStr = input instanceof String ? (String) input : OBJECT_MAPPER.writeValueAsString(input);
            JsonNode root = OBJECT_MAPPER.readTree(jsonStr);

            Map<String, ServiceGraphEdgeInfo> edges = new HashMap<>();

            // Single pass to get workflow metadata
            WorkflowMetadata workflowMetadata = extractWorkflowMetadata(root.path(FIELD_WORKFLOW_DATA));

            // Extract edges from runData - reusing nodeTypeMap
            JsonNode runData = root.path(FIELD_DATA).path(FIELD_RESULT_DATA).path(FIELD_RUN_DATA);

            Iterator<Map.Entry<String, JsonNode>> nodeIterator = runData.fields();
            while (nodeIterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = nodeIterator.next();
                String nodeName = entry.getKey();
                String nodeType = workflowMetadata.nodeTypeMap.get(nodeName);
                JsonNode executions = entry.getValue();

                if (executions.isArray() && executions.size() > 0) {
                    JsonNode execution = executions.get(0);

                    if (execution.has(FIELD_SOURCE)) {
                        JsonNode sources = execution.get(FIELD_SOURCE);
                        if (sources.isArray() && sources.size() > 0) {
                            String previousNodeName = sources.get(0).get(FIELD_PREVIOUS_NODE).asText();
                            String previousNodeType = workflowMetadata.nodeTypeMap.get(previousNodeName);

                            // Determine if source is agent node
                            boolean isAgentSource = previousNodeName.endsWith(AGENT_SUFFIX) ||
                                                   (previousNodeType != null && previousNodeType.toLowerCase().contains("agent"));

                            if (isAgentSource) {
                                // Create edge: agent -> target service
                                String edgeKey = nodeName;
                                Map<String, Object> metadata = new HashMap<>();
                                metadata.put("type", nodeType != null ? nodeType : "unknown");

                                ServiceGraphEdgeInfo edge = new ServiceGraphEdgeInfo(previousNodeName, nodeName, metadata);
                                edges.put(edgeKey, edge);
                            }
                        }
                    }
                }
            }

            return edges;

        } catch (Exception e) {
            throw new Exception("Failed to extract service graph: " + e.getMessage(), e);
        }
    }

    @Override
    public String getSourceType() {
        return SOURCE_TYPE;
    }

    private String stripN8nPrefixes(String type) {
        if (type == null) return null;

        // Common N8N node type prefixes to strip
        String[] prefixes = {
            "@n8n/n8n-nodes-langchain.",
            "n8n-nodes-base.",
            "@n8n/"
        };

        for (String prefix : prefixes) {
            if (type.startsWith(prefix)) {
                return type.substring(prefix.length());
            }
        }

        return type;
    }

    private Span buildSpan(String spanId, String traceId, String nodeName, String nodeType,
                          JsonNode execution, String parentSpanId) {
        long startTime = execution.has(FIELD_START_TIME) ? execution.get(FIELD_START_TIME).asLong() : 0;
        long executionTime = execution.has(FIELD_EXECUTION_TIME) ? execution.get(FIELD_EXECUTION_TIME).asLong() : 0;
        long endTime = startTime + executionTime;

        String status = execution.has(FIELD_EXECUTION_STATUS) ?
            execution.get(FIELD_EXECUTION_STATUS).asText() : "unknown";

        return Span.builder()
            .id(spanId)
            .traceId(traceId)
            .parentSpanId(parentSpanId)
            .spanKind(determineSpanKind(nodeType, nodeName))
            .name(nodeName)
            .startTimeMillis(startTime)
            .endTimeMillis(endTime)
            .status(mapStatus(status))
            .input(extractInput(execution))
            .output(extractOutput(execution))
            .metadata(extractMetadata(execution, nodeType))
            .depth(0) // Will be set later
            .tags(Arrays.asList(SOURCE_TYPE, nodeType != null ? nodeType : "unknown"))
            .build();
    }

    private String determineSpanKind(String nodeType, String nodeName) {
        if (nodeName.endsWith(AGENT_SUFFIX)) {
            return TracingConstants.SpanKind.AGENT;
        }
        if (nodeType != null) {
            String lowerType = nodeType.toLowerCase();
            if (lowerType.contains("agent")) return TracingConstants.SpanKind.AGENT;
            if (lowerType.contains("chat") || lowerType.contains("llm")) return TracingConstants.SpanKind.LLM;
            if (lowerType.contains("tool")) return TracingConstants.SpanKind.TOOL;
            if (lowerType.contains("mcp")) return TracingConstants.SpanKind.MCP_SERVER;
            if (lowerType.contains("webhook")) return TracingConstants.SpanKind.HTTP;
        }
        return TracingConstants.SpanKind.TASK;
    }

    private void calculateSpanDepths(List<Span> spans, String rootSpanId, Map<String, String> nodeToSpanIdMap) {
        Map<String, Span> spanMap = new HashMap<>();
        for (Span span : spans) {
            spanMap.put(span.getTraceId(), span);
        }

        // BFS to set depths
        Queue<String> queue = new LinkedList<>();
        if (rootSpanId != null && spanMap.containsKey(rootSpanId)) {
            queue.offer(rootSpanId);
            spanMap.get(rootSpanId).setDepth(0);

            while (!queue.isEmpty()) {
                String currentId = queue.poll();
                Span current = spanMap.get(currentId);
                int currentDepth = current.getDepth();

                // Find children
                for (Span span : spans) {
                    if (currentId.equals(span.getParentSpanId()) && span.getDepth() == 0 && !span.getTraceId().equals(rootSpanId)) {
                        span.setDepth(currentDepth + 1);
                        queue.offer(span.getTraceId());
                    }
                }
            }
        }
    }

    private long parseTimestamp(String timestamp) {
        try {
            return java.time.Instant.parse(timestamp).toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private String mapStatus(String n8nStatus) {
        if ("success".equalsIgnoreCase(n8nStatus)) return "success";
        if ("error".equalsIgnoreCase(n8nStatus)) return "error";
        if ("running".equalsIgnoreCase(n8nStatus)) return "running";
        return "unknown";
    }

    private Map<String, Object> extractInput(JsonNode execution) {
        Map<String, Object> input = new HashMap<>();
        if (execution.has(FIELD_INPUT_OVERRIDE)) {
            try {
                JsonNode inputNode = execution.get(FIELD_INPUT_OVERRIDE);
                input.put("data", OBJECT_MAPPER.writeValueAsString(inputNode));
            } catch (Exception e) {
                input.put("data", execution.get(FIELD_INPUT_OVERRIDE).toString());
            }
        }
        return input;
    }

    private Map<String, Object> extractOutput(JsonNode execution) {
        Map<String, Object> output = new HashMap<>();
        if (execution.has("data")) {
            try {
                JsonNode outputNode = execution.get("data");
                output.put("result", OBJECT_MAPPER.writeValueAsString(outputNode));
            } catch (Exception e) {
                output.put("result", execution.get("data").toString());
            }
        }
        return output;
    }

    private Map<String, Object> extractMetadata(JsonNode execution, String nodeType) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("nodeType", nodeType != null ? nodeType : "unknown");
        metadata.put("sourceType", SOURCE_TYPE);
        if (execution.has("metadata")) {
            try {
                metadata.put("n8nMetadata", OBJECT_MAPPER.writeValueAsString(execution.get("metadata")));
            } catch (Exception e) {
                metadata.put("n8nMetadata", execution.get("metadata").toString());
            }
        }
        return metadata;
    }

    private Map<String, Object> buildTraceMetadata(JsonNode root) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sourceType", SOURCE_TYPE);
        metadata.put("executionId", root.get(FIELD_ID).asText());
        metadata.put("workflowId", root.get(FIELD_WORKFLOW_ID).asText());
        metadata.put("mode", root.path("mode").asText("unknown"));
        if (root.has("finished")) {
            metadata.put("finished", root.get("finished").asBoolean());
        }
        return metadata;
    }

    /**
     * Extracts input from the first node's execution data.
     * Optimized version that works directly with cached execution data.
     */
    private Map<String, Object> extractRootInputFromExecution(JsonNode execution) {
        Map<String, Object> rootInput = new HashMap<>();

        if (execution == null) {
            return rootInput;
        }

        try {
            // Extract input from the first node's data
            if (execution.has("data")) {
                JsonNode dataNode = execution.get("data");
                if (dataNode.has("main") && dataNode.get("main").isArray() && dataNode.get("main").size() > 0) {
                    JsonNode mainArray = dataNode.get("main").get(0);
                    if (mainArray.isArray() && mainArray.size() > 0) {
                        JsonNode firstItem = mainArray.get(0);
                        if (firstItem.has("json")) {
                            rootInput.put("data", OBJECT_MAPPER.writeValueAsString(firstItem.get("json")));
                        }
                    }
                }
            }
        } catch (Exception e) {
            // If extraction fails, return empty map
            rootInput.put("error", "Failed to extract root input: " + e.getMessage());
        }

        return rootInput;
    }

    /**
     * Extracts output from the last node's execution data.
     * Optimized version that works directly with cached execution data.
     */
    private Map<String, Object> extractRootOutputFromExecution(JsonNode execution) {
        Map<String, Object> rootOutput = new HashMap<>();

        if (execution == null) {
            return rootOutput;
        }

        try {
            // Extract output from the last node's data
            if (execution.has("data")) {
                JsonNode dataNode = execution.get("data");
                if (dataNode.has("main") && dataNode.get("main").isArray() && dataNode.get("main").size() > 0) {
                    JsonNode mainArray = dataNode.get("main").get(0);
                    if (mainArray.isArray() && mainArray.size() > 0) {
                        JsonNode firstItem = mainArray.get(0);
                        if (firstItem.has("json")) {
                            rootOutput.put("output", OBJECT_MAPPER.writeValueAsString(firstItem.get("json")));
                        }
                    }
                }
            }
        } catch (Exception e) {
            // If extraction fails, return empty map
            rootOutput.put("error", "Failed to extract root output: " + e.getMessage());
        }

        return rootOutput;
    }
}
