package com.akto.tracing.snowflake;

import com.akto.dto.ApiCollection.ServiceGraphEdgeInfo;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.tracing.Span;
import com.akto.dto.tracing.Trace;
import com.akto.dto.tracing.TracingConstants;
import com.akto.tracing.TraceParseResult;
import com.akto.tracing.TraceParser;
import com.akto.util.Constants;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Builds one {@link Trace} per Snowflake agent request from observability rows.
 *
 * <p>Span hierarchy is derived from the dot-depth of each key's namespace:
 * {@code entityDepth = entity.split(".").length - DEPTH_OFFSET} where {@code entity} is the key
 * minus its last segment and {@code DEPTH_OFFSET = 3} (snow + ai + observability).
 *
 * <p>Planning steps are named {@code planner-1}, {@code planner-2}, … (1-indexed). All other
 * entity spans are created on demand from the key namespace using a shared stack. Dedicated tool
 * rows ({@code agent.tool.*}) are always parented to the active planning span.
 */
public class SnowflakeTraceParser implements TraceParser {

    private static final SnowflakeTraceParser INSTANCE = new SnowflakeTraceParser();
    private static final String SOURCE_TYPE = "snowflake";
    private static final String DEFAULT_AGENT_NAME = "snowflake-agent";
    public static final String TAG_SNOWFLAKE_OBSERVABILITY = "snowflakeObservability";
    private static final String TAG_AGENT = "agent";

    // Namespace constants
    private static final String PREFIX_AI_OBS   = "ai.observability.";
    private static final String PREFIX_SNOW_OBS  = "snow.ai.observability.";
    private static final String PREFIX_AGENT     = "snow.ai.observability.agent.";
    private static final int    DEPTH_OFFSET     = 3; // snow, ai, observability

    private static final String ENTITY_AGENT         = "snow.ai.observability.agent";
    private static final String ENTITY_PLANNING      = PREFIX_AGENT + "planning";
    private static final String ENTITY_PLANNING_TOOL = PREFIX_AGENT + "planning.tool";
    // "agent.tool" namespace holds dedicated (correlated) tool rows
    private static final String ENTITY_DEDICATED_TOOL = PREFIX_AGENT + "tool";

    // Well-known correlation / root-level keys
    private static final String KEY_RECORD_ID      = "ai.observability.record_id";
    private static final String KEY_INPUT_ID       = "ai.observability.input_id";
    private static final String KEY_RECORD_ROOT_IN = "ai.observability.record_root.input";
    private static final String KEY_RECORD_ROOT_OUT = "ai.observability.record_root.output";
    private static final String KEY_STEP_NUMBER    = PREFIX_AGENT + "planning.step_number";
    private static final String KEY_OBJECT_NAME    = PREFIX_SNOW_OBS + "object.name";
    private static final String KEY_AGENT_DURATION = PREFIX_AGENT + "duration";
    private static final String KEY_AGENT_STATUS   = PREFIX_AGENT + "status";
    private static final String KEY_TOOL_CHOICE_TS = PREFIX_AGENT + "tool_choice.input_timestamp";

    /** Leaf segments whose entity path should not create a span — they are metadata on the parent. */
    private static final Set<String> NON_ENTITY_LEAVES = new HashSet<>(Arrays.asList(
            "model", "request_id", "thread_id", "message_id", "parent_message_id",
            "duration", "status", "step_number", "response", "messages", "instruction",
            "first_message_in_thread", "research_mode", "version", "type",
            "thinking_response", "query", "span_kind", "text", "think",
            "input_timestamp", "is_client_side", "code", "description", "semantic_model",
            // argument containers (argument.name / argument.value) are always metadata
            "argument"
    ));

    // ── Backwards-compatible static API ──────────────────────────────────

    /**
     * Logical span position for Snowflake telemetry. Covers all entity types observed in
     * production observability data; new Snowflake entity types added later that don't map
     * to any enum value should be resolved via {@link #resolveSpanKindFromEntityPath(String)}.
     */
    public enum SnowflakeSpanRole {
        ROOT,              // top-level agent request
        PLANNING,          // LLM planning step (agent.planning.*)
        TOOL_EXECUTION,    // tool call within a planning step (planning.tool_execution.*)
        DEDICATED_TOOL,    // integrated Snowflake tool row (agent.tool.*)
        SKILL,             // skill / sub-agent workflow (planning.skill.*)
        TOOL_SELECTION,    // tool routing / selection step
        RETRIEVAL,         // Cortex Search or other retrieval
        SQL_EXECUTION,     // SQL generation or execution
        RESPONSE_SYNTHESIS,// response-synthesis / summarisation
        CHART_GENERATION,  // Vega-Lite chart generation
    }

    /**
     * Resolves {@link TracingConstants.SpanKind} for a known Snowflake span role.
     * When {@code role} is null or an unrecognised future value, falls back to
     * {@link #resolveSpanKindFromEntityPath(String)} with {@code snowflakeTypeOrPlanningModel}
     * as the entity path, so callers never receive a hard failure.
     */
    public static String resolveSpanKind(SnowflakeSpanRole role, String snowflakeTypeOrPlanningModel) {
        if (role == null) return resolveSpanKindFromEntityPath(snowflakeTypeOrPlanningModel);
        switch (role) {
            case ROOT:
                return TracingConstants.SpanKind.AGENT;
            case PLANNING:
                return looksLikeLlmModelId(snowflakeTypeOrPlanningModel)
                        ? TracingConstants.SpanKind.LLM : TracingConstants.SpanKind.PLANNING;
            case TOOL_EXECUTION:
            case DEDICATED_TOOL:
                return spanKindForToolType(snowflakeTypeOrPlanningModel);
            case SKILL:
            case TOOL_SELECTION:
                return TracingConstants.SpanKind.WORKFLOW;
            case RETRIEVAL:
                return TracingConstants.SpanKind.RAG;
            case SQL_EXECUTION:
                return TracingConstants.SpanKind.DATABASE;
            case RESPONSE_SYNTHESIS:
                return TracingConstants.SpanKind.LLM;
            case CHART_GENERATION:
                return TracingConstants.SpanKind.CUSTOM;
            default:
                return resolveSpanKindFromEntityPath(snowflakeTypeOrPlanningModel);
        }
    }

    /**
     * Heuristic span-kind resolution for entity paths not covered by {@link SnowflakeSpanRole}.
     * Applies substring matching so future Snowflake entity types degrade gracefully without
     * requiring parser changes. Ordered most-specific → least-specific.
     */
    public static String resolveSpanKindFromEntityPath(String entityPath) {
        if (entityPath == null || entityPath.isEmpty()) return TracingConstants.SpanKind.CUSTOM;
        String e       = entityPath.toLowerCase(Locale.ROOT);
        String lastSeg = e.substring(e.lastIndexOf('.') + 1);
        // Exact last-segment matches (highest confidence)
        if ("cortex_analyst".equals(lastSeg) || "custom_tool".equals(lastSeg))
            return TracingConstants.SpanKind.TOOL;
        if ("chart_generation".equals(lastSeg))
            return TracingConstants.SpanKind.CUSTOM;
        if ("sql_execution".equals(lastSeg))
            return TracingConstants.SpanKind.DATABASE;
        // Substring heuristics
        if (e.contains("cortex_search") || e.contains("cortexsearch") || e.contains("retrieval"))
            return TracingConstants.SpanKind.RAG;
        if (e.contains("sql") || e.contains("database") || e.contains("query_id"))
            return TracingConstants.SpanKind.DATABASE;
        if (e.contains("skill") || e.contains("tool_selection") || e.contains("results"))
            return TracingConstants.SpanKind.WORKFLOW;
        if (e.contains("tool_execution") || e.contains("tool"))
            return TracingConstants.SpanKind.TOOL;
        if (e.contains("response") || e.contains("synthesis") || e.contains("llm"))
            return TracingConstants.SpanKind.LLM;
        if (e.contains("search") || e.contains("retriev") || e.contains("embed"))
            return TracingConstants.SpanKind.RAG;
        return TracingConstants.SpanKind.CUSTOM;
    }

    /** @deprecated Use {@link #resolveSpanKind(SnowflakeSpanRole, String)}. */
    @Deprecated
    public static String determineSnowflakeSpanKind(boolean isRoot, boolean isPlanning,
                                                     boolean isTool, String modelOrToolType) {
        if (isRoot)     return resolveSpanKind(SnowflakeSpanRole.ROOT, null);
        if (isPlanning) return resolveSpanKind(SnowflakeSpanRole.PLANNING, modelOrToolType);
        if (isTool)     return resolveSpanKind(SnowflakeSpanRole.TOOL_EXECUTION, modelOrToolType);
        return TracingConstants.SpanKind.TASK;
    }

    private static String spanKindForToolType(String raw) {
        String t = raw != null ? raw.toLowerCase(Locale.ROOT) : "";
        if ("generic".equals(t)) return TracingConstants.SpanKind.CUSTOM;
        if (t.contains("mcp"))   return TracingConstants.SpanKind.MCP_SERVER;
        return TracingConstants.SpanKind.TOOL;
    }

    private static boolean looksLikeLlmModelId(String model) {
        if (model == null || model.isEmpty()) return false;
        String m = model.toLowerCase(Locale.ROOT);
        return m.contains("claude") || m.contains("gpt") || m.contains("gemini")
                || m.contains("llama") || m.contains("mistral") || m.contains("grok")
                || m.contains("cohere") || m.contains("command-r") || m.contains("bedrock")
                || m.contains("anthropic") || m.contains("openai")
                || m.contains("sonnet") || m.contains("opus") || m.contains("haiku");
    }

    // ── Singleton ─────────────────────────────────────────────────────────

    public static SnowflakeTraceParser getInstance() { return INSTANCE; }

    // ── TraceParser interface ──────────────────────────────────────────────

    @Override
    public boolean canParse(Object input) {
        if (!(input instanceof HttpResponseParams)) return false;
        HttpResponseParams p = (HttpResponseParams) input;
        String tagsJson = p.getTags();
        if (tagsJson == null || tagsJson.isEmpty()) return false;
        try {
            JsonNode tags = SnowflakeTraceParserUtils.OBJECT_MAPPER.readTree(tagsJson);
            return Constants.AI_AGENT_SOURCE_SNOWFLAKE.equalsIgnoreCase(
                    tags.path(Constants.AI_AGENT_TAG_SOURCE).asText(""));
        } catch (Exception e) { return false; }
    }

    @Override
    public String getSourceType() { return SOURCE_TYPE; }

    @Override
    public Map<String, ServiceGraphEdgeInfo> extractServiceGraph(Object input) throws Exception {
        if (!canParse(input)) return Collections.emptyMap();
        HttpResponseParams params = (HttpResponseParams) input;
        JsonNode tags = SnowflakeTraceParserUtils.OBJECT_MAPPER.readTree(params.getTags());
        List<Map<String, Object>> rows = collectRows(tags, params);
        Map<String, Object> obs = rows.isEmpty()
                ? new HashMap<>() : SnowflakeTraceParserUtils.mergeMapsInOrder(rows);
        String agentName = resolveAgentName(tags, obs);
        String edgeKey = SnowflakeTraceParserUtils.stringValue(obs.get(KEY_OBJECT_NAME));
        if (edgeKey == null || edgeKey.isEmpty()) edgeKey = agentName;
        Map<String, Object> meta = new HashMap<>();
        meta.put("type", TracingConstants.SpanKind.AGENT);
        meta.put("sourceType", SOURCE_TYPE);
        meta.put("path", params.getRequestParams() != null ? params.getRequestParams().getURL() : null);
        meta.put("statusCode", params.getStatusCode());
        Map<String, ServiceGraphEdgeInfo> edges = new HashMap<>();
        edges.put(edgeKey, new ServiceGraphEdgeInfo("AGENT", edgeKey, meta));
        return edges;
    }

    @Override
    public TraceParseResult parse(Object input) throws Exception {
        if (!canParse(input)) throw new Exception("Not a parseable Snowflake HttpResponseParams");
        HttpResponseParams params = (HttpResponseParams) input;
        JsonNode tags = SnowflakeTraceParserUtils.OBJECT_MAPPER.readTree(params.getTags());

        // 1. Collect, filter, and sort rows oldest-first
        List<Map<String, Object>> rows = collectRows(tags, params);
        String traceId = SnowflakeTraceParserUtils.resolvePrimaryRecordId(rows);
        if (traceId == null) traceId = UUID.randomUUID().toString();
        rows = filterToTrace(rows, traceId);
        rows = sortOldestFirst(rows);

        Map<String, Object> merged = SnowflakeTraceParserUtils.mergeMapsInOrder(rows);
        String agentName   = resolveAgentName(tags, merged);
        long   rootStart   = resolveStartMillis(params, merged);
        String rootStatus  = resolveStatus(params, merged);

        String rootSpanId = SnowflakeTraceParserUtils.firstNonBlank(
                SnowflakeTraceParserUtils.stringValue(merged.get(KEY_INPUT_ID)),
                UUID.randomUUID().toString());

        Map<String, Object> rootInput = new HashMap<>();
        String userText = resolveUserText(params, merged);
        if (userText != null) rootInput.put("data", userText);

        Map<String, Object> rootOutput = new HashMap<>();
        String respText = resolveResponseText(params, merged);
        if (respText != null) rootOutput.put("result", respText);

        long rootDuration = SnowflakeTraceParserUtils.longValue(merged.get(KEY_AGENT_DURATION), 0L);
        long rootEnd = rootDuration > 0 ? rootStart + rootDuration : rootStart;

        Map<String, Object> rootMeta = baseMetadata(params, tags, merged);
        rootMeta.put("spanRole", "record_root");

        Span rootSpan = Span.builder()
                .id(rootSpanId).traceId(traceId)
                .parentSpanId(null)
                .spanKind(TracingConstants.SpanKind.AGENT)
                .name("REQUEST_RECEIVED")
                .startTimeMillis(rootStart).endTimeMillis(rootEnd)
                .status(rootStatus)
                .input(rootInput).output(rootOutput)
                .metadata(rootMeta).depth(0)
                .tags(Arrays.asList(SOURCE_TYPE, agentName))
                .build();

        List<Span> spans = new ArrayList<>();
        spans.add(rootSpan);

        // 2. Walk rows with a shared depth-stack
        Map<Integer, Span> planningSpans = new LinkedHashMap<>();
        Map<String, Span>  entitySpanMap = new LinkedHashMap<>();
        Deque<Span> stack = new ArrayDeque<>();
        stack.push(rootSpan);
        Integer lastStep = null;

        for (Map<String, Object> row : rows) {
            Integer step = hasStepNumber(row)
                    ? SnowflakeTraceParserUtils.parseStepNumber(row.get(KEY_STEP_NUMBER)) : null;

            if (step != null) {
                lastStep = step;
                Span ps = getOrCreatePlannerSpan(step, row, traceId, rootSpanId, rootStart,
                        rootStatus, params, tags, merged, planningSpans, spans);
                stack.clear();
                stack.push(rootSpan);
                stack.push(ps);
            } else if (hasKeyPrefix(row, ENTITY_DEDICATED_TOOL) && lastStep != null
                    && planningSpans.containsKey(lastStep)) {
                // Dedicated tool row: reset stack so tool entities sit under the active planner
                stack.clear();
                stack.push(rootSpan);
                stack.push(planningSpans.get(lastStep));
            }

            processRowKeys(row, stack, traceId, rootStart, rootStatus, lastStep,
                    planningSpans, entitySpanMap, spans, params, tags, merged, rootSpan);
        }

        // 3. Assign depths from parent chain and compute time bounds
        assignDepths(spans);

        long endTime = spans.stream()
                .mapToLong(s -> s.getEndTimeMillis() != null ? s.getEndTimeMillis() : rootEnd)
                .max().orElse(rootEnd);
        List<String> spanIds = spans.stream().map(Span::getId).collect(Collectors.toList());
        String traceName = SnowflakeTraceParserUtils.stringValue(merged.get(KEY_OBJECT_NAME));

        // Sum planning.token_count.* across all rows (one entry per planning step)
        long[] tok = sumTokens(rows);
        int totalTokens       = (int) Math.min(tok[0], Integer.MAX_VALUE);
        int totalInputTokens  = (int) Math.min(tok[1], Integer.MAX_VALUE);
        int totalOutputTokens = (int) Math.min(tok[2], Integer.MAX_VALUE);

        // Environment: prefer object type, then database/schema context
        String environment = SnowflakeTraceParserUtils.firstNonBlank(
                SnowflakeTraceParserUtils.stringValue(merged.get(PREFIX_SNOW_OBS + "object.type")),
                SnowflakeTraceParserUtils.stringValue(merged.get(PREFIX_SNOW_OBS + "database.name")));

        Map<String, Object> traceMeta = baseMetadata(params, tags, merged);
        if (traceName != null)   traceMeta.put("snowflakeObjectName", traceName);
        String dbName = SnowflakeTraceParserUtils.stringValue(merged.get(PREFIX_SNOW_OBS + "database.name"));
        if (dbName != null)      traceMeta.put("snowflakeDatabaseName", dbName);
        String schemaName = SnowflakeTraceParserUtils.stringValue(merged.get(PREFIX_SNOW_OBS + "schema.name"));
        if (schemaName != null)  traceMeta.put("snowflakeSchemaName", schemaName);

        Trace trace = Trace.builder()
                .id(traceId).rootSpanId(rootSpanId)
                .aiAgentName(agentName)
                .environment(environment)
                .name(traceName != null ? traceName : agentName)
                .startTimeMillis(rootStart).endTimeMillis(endTime)
                .status(rootStatus).totalSpans(spans.size())
                .totalTokens(totalTokens)
                .totalInputTokens(totalInputTokens)
                .totalOutputTokens(totalOutputTokens)
                .rootInput(rootInput).rootOutput(rootOutput)
                .metadata(traceMeta)
                .spanIds(spanIds)
                .build();

        return TraceParseResult.builder()
                .trace(trace).spans(spans)
                .workflowId(traceName)
                .sourceIdentifier(traceId)
                .metadata(Collections.singletonMap("agentName", agentName))
                .build();
    }

    // ── Core: two-pass key processing per row ────────────────────────────

    private void processRowKeys(Map<String, Object> row, Deque<Span> stack,
                                 String traceId, long rootStart, String rootStatus, Integer lastStep,
                                 Map<Integer, Span> planningSpans, Map<String, Span> entitySpanMap,
                                 List<Span> spans, HttpResponseParams params, JsonNode tags,
                                 Map<String, Object> merged, Span rootSpan) {
        List<String> entityKeys = new ArrayList<>();
        List<String> metaKeys   = new ArrayList<>();
        for (String key : row.keySet()) {
            if (key == null) continue;
            if (isEntityCreatingKey(key)) entityKeys.add(key);
            else                           metaKeys.add(key);
        }

        // Pass 1: shallowest entity first → build / reuse span, push onto stack
        entityKeys.sort(Comparator.comparingInt(k -> entityOf(k).split("\\.", -1).length));

        for (String key : entityKeys) {
            String entity      = entityOf(key);
            boolean isDedicated = isDedicatedToolEntity(entity);
            int entityDepth;
            Span parent;

            if (isDedicated) {
                // Always child of the active planning span (or root if none seen yet)
                Span ps = lastStep != null ? planningSpans.get(lastStep) : null;
                parent      = ps != null ? ps : rootSpan;
                entityDepth = parent.getDepth() + 1;
            } else {
                entityDepth = entity.split("\\.", -1).length - DEPTH_OFFSET;
                while (stack.size() > 1 && stack.peek().getDepth() >= entityDepth) stack.pop();
                parent = stack.peek();
            }

            String mapKey   = lastStep + "|" + entity;
            Span entitySpan = entitySpanMap.get(mapKey);
            if (entitySpan == null) {
                long ts = SnowflakeTraceParserUtils.rowObsTimestamp(row);
                if (ts <= 0) ts = rootStart;
                KindAndName kn     = resolveKindAndName(entity, row);
                String      spanId = UUID.nameUUIDFromBytes(
                        (traceId + "|" + mapKey).getBytes(StandardCharsets.UTF_8)).toString();
                entitySpan = Span.builder()
                        .id(spanId).traceId(traceId)
                        .parentSpanId(parent.getId())
                        .spanKind(kn.kind).name(kn.name)
                        .startTimeMillis(ts).endTimeMillis(ts)
                        .status(rootStatus)
                        .metadata(baseMetadata(params, tags, merged))
                        .depth(entityDepth)
                        .tags(Arrays.asList(SOURCE_TYPE, entity))
                        .build();
                entitySpanMap.put(mapKey, entitySpan);
                spans.add(entitySpan);
            } else {
                updateSpanTimes(entitySpan, row, rootStart);
            }

            // Dedicated tool spans are siblings; don't push them onto the planning stack
            if (!isDedicated) stack.push(entitySpan);
            attachMeta(entitySpan, key, row.get(key));
        }

        // Pass 2: metadata keys → attach to the closest ancestor span
        for (String key : metaKeys) {
            attachMeta(findOwner(key, lastStep, planningSpans, stack.peek(), rootSpan),
                    key, row.get(key));
        }
    }

    // ── Planning span creation ─────────────────────────────────────────────

    private Span getOrCreatePlannerSpan(int step, Map<String, Object> row, String traceId,
                                         String rootSpanId, long rootStart, String rootStatus,
                                         HttpResponseParams params, JsonNode tags,
                                         Map<String, Object> merged, Map<Integer, Span> planningSpans,
                                         List<Span> spans) {
        if (planningSpans.containsKey(step)) {
            updateSpanTimes(planningSpans.get(step), row, rootStart);
            return planningSpans.get(step);
        }
        long ts = SnowflakeTraceParserUtils.rowObsTimestamp(row);
        if (ts <= 0) ts = rootStart;
        String spanId = UUID.nameUUIDFromBytes(
                (traceId + "|planning|" + step).getBytes(StandardCharsets.UTF_8)).toString();
        Map<String, Object> meta = baseMetadata(params, tags, merged);
        meta.put("planningStepNumber", step);
        Span ps = Span.builder()
                .id(spanId).traceId(traceId)
                .parentSpanId(rootSpanId)
                .spanKind(TracingConstants.SpanKind.WORKFLOW)
                .name("planner-" + (step + 1))
                .startTimeMillis(ts).endTimeMillis(ts)
                .status(rootStatus)
                .metadata(meta).depth(2)
                .tags(Arrays.asList(SOURCE_TYPE, "planning", String.valueOf(step)))
                .build();
        planningSpans.put(step, ps);
        spans.add(ps);
        return ps;
    }

    // ── Entity / noise classification ──────────────────────────────────────

    /**
     * A key creates an entity span when:
     *  - It lives under the agent prefix
     *  - Its entity (key minus last segment) is not a direct agent child (depth < 2)
     *  - Its entity is not the planning entity itself (planning spans are pre-created)
     *  - Its entity is not the available-tools list ({@code planning.tool} / {@code planning.tool.*})
     *  - The key is not classified as a noise key by {@link SnowflakeTraceParserUtils#isNoiseMetadataKey}
     *  - Its leaf segment is not in {@link #NON_ENTITY_LEAVES}
     */
    private static boolean isEntityCreatingKey(String key) {
        if (!key.startsWith(PREFIX_AGENT)) return false;
        String entity = entityOf(key);
        if (ENTITY_AGENT.equals(entity))    return false; // depth 1 – direct agent child
        if (ENTITY_PLANNING.equals(entity)) return false; // planning spans created separately
        // Available-tools list: planning.tool and planning.tool.<anything> → metadata only
        if (entity.equals(ENTITY_PLANNING_TOOL) || entity.startsWith(ENTITY_PLANNING_TOOL + "."))
            return false;
        if (SnowflakeTraceParserUtils.isNoiseMetadataKey(key)) return false;
        int depth = entity.split("\\.", -1).length - DEPTH_OFFSET;
        if (depth < 2) return false;
        String leaf = entity.substring(entity.lastIndexOf('.') + 1);
        return !NON_ENTITY_LEAVES.contains(leaf);
    }

    /**
     * {@code agent.tool} and {@code agent.tool.<sub>} are "dedicated tool" rows that are
     * linked to planning steps via correlation ids rather than by namespace depth.
     * Must use exact-or-dot-prefix match to avoid catching {@code tool_execution}, {@code tool_selection}.
     */
    private static boolean isDedicatedToolEntity(String entity) {
        return entity.equals(ENTITY_DEDICATED_TOOL)
                || entity.startsWith(ENTITY_DEDICATED_TOOL + ".");
    }

    private static String entityOf(String key) {
        int dot = key.lastIndexOf('.');
        return dot > 0 ? key.substring(0, dot) : key;
    }

    /**
     * Metadata key owner selection:
     *  - Non-agent keys → root span
     *  - Direct agent children (depth 1) → root span (they describe the agent, not a step)
     *  - ENTITY_PLANNING keys → active planning span (or root if none)
     *  - Everything else → current stack top (deepest active entity span)
     */
    private static Span findOwner(String key, Integer lastStep, Map<Integer, Span> planningSpans,
                                   Span current, Span rootSpan) {
        if (!key.startsWith(PREFIX_AGENT)) return rootSpan;
        String entity = entityOf(key);
        int depth = entity.split("\\.", -1).length - DEPTH_OFFSET;
        if (depth <= 1) return rootSpan;
        if (ENTITY_PLANNING.equals(entity) && lastStep != null && planningSpans.containsKey(lastStep)) {
            return planningSpans.get(lastStep);
        }
        return current;
    }

    // ── Kind + display-name resolution ────────────────────────────────────

    private static final class KindAndName {
        final String kind, name;
        KindAndName(String kind, String name) { this.kind = kind; this.name = name; }
    }

    private KindAndName resolveKindAndName(String entity, Map<String, Object> row) {
        String ent     = entity.toLowerCase(Locale.ROOT);
        String lastSeg = ent.substring(ent.lastIndexOf('.') + 1);

        if ("cortex_analyst".equals(lastSeg))
            return new KindAndName(TracingConstants.SpanKind.TOOL, "Cortex Analyst");
        if ("sql_execution".equals(lastSeg))
            return new KindAndName(TracingConstants.SpanKind.DATABASE, "SQL Execution");
        if ("chart_generation".equals(lastSeg))
            return new KindAndName(TracingConstants.SpanKind.CUSTOM, "Chart Generation");
        if (ent.contains("cortex_search") || ent.contains("cortexsearch") || ent.endsWith("retrieval"))
            return new KindAndName(TracingConstants.SpanKind.RAG, "RETRIEVAL");
        if ("custom_tool".equals(lastSeg)) {
            String name = SnowflakeTraceParserUtils.unwrapJsonStringArrayFirstOrString(row.get(entity + ".name"));
            return new KindAndName(TracingConstants.SpanKind.TOOL, name != null ? name : "Custom Tool");
        }
        if (ent.contains("sql"))
            return new KindAndName(TracingConstants.SpanKind.DATABASE, "SQL_GENERATION");
        if ("query_id".equals(lastSeg) || ent.contains("statementhandle"))
            return new KindAndName(TracingConstants.SpanKind.DATABASE, "SQL_EXECUTION");
        // skill branch — but not tool_execution / tool_selection that happen to live under a skill namespace
        if (ent.contains("skill") && !lastSeg.startsWith("tool_")) {
            String name = SnowflakeTraceParserUtils.unwrapJsonStringArrayFirstOrString(row.get(entity + ".name"));
            return new KindAndName(TracingConstants.SpanKind.WORKFLOW, name != null ? "Skill: " + name : "SKILL");
        }
        if (ent.contains("results"))
            return new KindAndName(TracingConstants.SpanKind.WORKFLOW, "RESULT_AGGREGATION");
        if (ent.contains("tool_selection")) {
            String name = SnowflakeTraceParserUtils.unwrapJsonStringArrayFirstOrString(row.get(entity + ".name"));
            return new KindAndName(TracingConstants.SpanKind.WORKFLOW, name != null ? name : "TOOL_SELECTION");
        }
        if (ent.contains("tool_execution")) {
            String name = SnowflakeTraceParserUtils.unwrapJsonStringArrayFirstOrString(row.get(entity + ".name"));
            return new KindAndName(TracingConstants.SpanKind.TOOL, name != null ? name : "TOOL_EXECUTION");
        }
        if (ent.contains("response"))
            return new KindAndName(TracingConstants.SpanKind.LLM, "RESPONSE_SYNTHESIS");
        // Unknown entity type: derive kind from path heuristics so future types degrade gracefully
        String kind    = resolveSpanKindFromEntityPath(entity);
        String seg     = entity.substring(entity.lastIndexOf('.') + 1);
        String display = seg.isEmpty() ? "CUSTOM"
                : seg.substring(0, 1).toUpperCase(Locale.ROOT) + seg.substring(1).replace('_', ' ');
        return new KindAndName(kind, display);
    }

    // ── Row collection ────────────────────────────────────────────────────

    private List<Map<String, Object>> collectRows(JsonNode tags, HttpResponseParams params) {
        JsonNode arr = SnowflakeTraceParserUtils.readSnowflakeTraceMetadataArrayFromPayload(params);
        if (arr != null && arr.isArray() && arr.size() > 0) {
            List<Map<String, Object>> rows = new ArrayList<>();
            SnowflakeTraceParserUtils.appendSnowflakeTraceMetadataArray(arr, rows);
            return rows;
        }
        return collectFromTags(tags);
    }

    private List<Map<String, Object>> collectFromTags(JsonNode tags) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (tags == null || tags.isMissingNode()) return rows;
        JsonNode obs = tags.get(TAG_SNOWFLAKE_OBSERVABILITY);
        if (obs != null && obs.isArray()) {
            for (JsonNode el : obs) {
                if (el != null && el.isObject())
                    rows.add(SnowflakeTraceParserUtils.jsonObjectToMap(el));
            }
        } else if (obs != null && obs.isObject()) {
            rows.add(SnowflakeTraceParserUtils.jsonObjectToMap(obs));
        } else {
            Map<String, Object> flat = new LinkedHashMap<>();
            if (tags != null) {
                tags.fields().forEachRemaining(e -> {
                    String k = e.getKey();
                    if (k.startsWith(PREFIX_AI_OBS) || k.startsWith(PREFIX_SNOW_OBS))
                        flat.put(k, SnowflakeTraceParserUtils.jsonNodeToJava(e.getValue()));
                });
            }
            if (!flat.isEmpty()) rows.add(flat);
        }
        return rows;
    }

    private List<Map<String, Object>> filterToTrace(List<Map<String, Object>> rows, String traceId) {
        List<Map<String, Object>> filtered = rows.stream()
                .filter(r -> traceId.equals(SnowflakeTraceParserUtils.stringValue(r.get(KEY_RECORD_ID)))
                        || traceId.equals(SnowflakeTraceParserUtils.stringValue(r.get(PREFIX_AGENT + "request_id")))
                        || traceId.equals(SnowflakeTraceParserUtils.stringValue(r.get("request_id"))))
                .collect(Collectors.toList());
        return filtered.isEmpty() ? new ArrayList<>(rows) : filtered;
    }

    /**
     * Sort oldest-first. When timestamps tie (or are absent), higher original index = older —
     * this handles newest-first ingestion sources where index 0 is the most recent row.
     */
    private List<Map<String, Object>> sortOldestFirst(List<Map<String, Object>> rows) {
        List<Integer> indices = IntStream.range(0, rows.size()).boxed().collect(Collectors.toList());
        indices.sort((i, j) -> {
            long ti = SnowflakeTraceParserUtils.rowObsTimestamp(rows.get(i));
            long tj = SnowflakeTraceParserUtils.rowObsTimestamp(rows.get(j));
            if (ti != tj) return Long.compare(ti, tj);
            return Integer.compare(j, i); // higher original index → treat as older
        });
        return indices.stream().map(rows::get).collect(Collectors.toList());
    }

    // ── Small utilities ───────────────────────────────────────────────────

    /**
     * Sums {@code planning.token_count.*} values across all rows.
     * Returns {@code long[]{total, input, output}}.
     */
    private static long[] sumTokens(List<Map<String, Object>> rows) {
        long total = 0, input = 0, output = 0;
        String keyTotal  = PREFIX_AGENT + "planning.token_count.total";
        String keyInput  = PREFIX_AGENT + "planning.token_count.input";
        String keyOutput = PREFIX_AGENT + "planning.token_count.output";
        for (Map<String, Object> row : rows) {
            total  += SnowflakeTraceParserUtils.longValue(row.get(keyTotal),  0L);
            input  += SnowflakeTraceParserUtils.longValue(row.get(keyInput),  0L);
            output += SnowflakeTraceParserUtils.longValue(row.get(keyOutput), 0L);
        }
        return new long[]{ total, input, output };
    }

    private static boolean hasStepNumber(Map<String, Object> row) {
        Object v = row.get(KEY_STEP_NUMBER);
        return v != null && SnowflakeTraceParserUtils.stringValue(v) != null;
    }

    private static boolean hasKeyPrefix(Map<String, Object> row, String prefix) {
        for (String k : row.keySet()) {
            if (k != null && (k.startsWith(prefix + ".") || k.equals(prefix))) return true;
        }
        return false;
    }

    private static void attachMeta(Span span, String key, Object value) {
        if (span == null || key == null) return;
        Map<String, Object> m = span.getMetadata();
        if (m == null) { m = new LinkedHashMap<>(); span.setMetadata(m); }
        m.put(key, value);
    }

    private static void updateSpanTimes(Span span, Map<String, Object> row, long fallback) {
        long ts = SnowflakeTraceParserUtils.rowObsTimestamp(row);
        if (ts <= 0) ts = fallback;
        Long st = span.getStartTimeMillis();
        Long en = span.getEndTimeMillis();
        if (st == null || ts < st) span.setStartTimeMillis(ts);
        if (en == null || ts > en) span.setEndTimeMillis(ts);
    }

    private static void assignDepths(List<Span> spans) {
        Map<String, Span> byId = new HashMap<>();
        for (Span s : spans) byId.put(s.getId(), s);
        for (Span s : spans) {
            int d = 0;
            String pid = s.getParentSpanId();
            Set<String> seen = new HashSet<>();
            while (pid != null && seen.add(pid)) {
                d++;
                Span p = byId.get(pid);
                if (p == null) break;
                pid = p.getParentSpanId();
            }
            s.setDepth(d);
        }
    }

    // ── Context resolution ────────────────────────────────────────────────

    private long resolveStartMillis(HttpResponseParams params, Map<String, Object> merged) {
        String iso = SnowflakeTraceParserUtils.stringValue(merged.get(KEY_TOOL_CHOICE_TS));
        if (iso != null) {
            try { return Instant.parse(iso).toEpochMilli(); } catch (Exception ignored) {}
        }
        int t = params.getTime();
        if (t == 0) t = params.getTimeOrNow();
        return (long) t * 1000L;
    }

    private String resolveStatus(HttpResponseParams params, Map<String, Object> merged) {
        String snow = SnowflakeTraceParserUtils.stringValue(merged.get(KEY_AGENT_STATUS));
        if (snow != null) {
            switch (snow.trim().toUpperCase(Locale.ROOT)) {
                case "SUCCESS": case "OK":               return "success";
                case "FAILURE": case "FAILED": case "ERROR": return "error";
                case "RUNNING":                           return "running";
                default: return snow.toLowerCase(Locale.ROOT);
            }
        }
        int code = params.getStatusCode();
        if (code >= 200 && code < 300) return "success";
        if (code >= 400)               return "error";
        return "unknown";
    }

    private String resolveAgentName(JsonNode tags, Map<String, Object> merged) {
        String fromObs = SnowflakeTraceParserUtils.stringValue(merged.get(KEY_OBJECT_NAME));
        if (fromObs != null) return fromObs;
        String agent = tagText(tags, TAG_AGENT);
        if (agent != null) return agent;
        String bot = tagText(tags, Constants.AI_AGENT_TAG_BOT_NAME);
        if (bot != null) return bot;
        return DEFAULT_AGENT_NAME;
    }

    private String resolveUserText(HttpResponseParams params, Map<String, Object> merged) {
        String fromObs = SnowflakeTraceParserUtils.firstNonBlank(
                SnowflakeTraceParserUtils.stringValue(merged.get(KEY_RECORD_ROOT_IN)),
                SnowflakeTraceParserUtils.stringValue(merged.get(PREFIX_AGENT + "first_message_in_thread")),
                SnowflakeTraceParserUtils.stringValue(merged.get(PREFIX_AGENT + "messages")));
        if (fromObs != null) return fromObs;
        if (params.getRequestParams() != null)
            return extractUserMessageFromPayload(params.getRequestParams().getPayload());
        return null;
    }

    private String resolveResponseText(HttpResponseParams params, Map<String, Object> merged) {
        String fromObs = SnowflakeTraceParserUtils.firstNonBlank(
                SnowflakeTraceParserUtils.stringValue(merged.get(KEY_RECORD_ROOT_OUT)),
                SnowflakeTraceParserUtils.stringValue(merged.get(PREFIX_AGENT + "response")));
        if (fromObs != null) return fromObs;
        return extractResponseFromPayload(params.getPayload());
    }

    private String extractUserMessageFromPayload(String payload) {
        if (payload == null || payload.isEmpty()) return null;
        try {
            JsonNode root = SnowflakeTraceParserUtils.OBJECT_MAPPER.readTree(payload);
            JsonNode messages = root.path("messages");
            if (!messages.isArray()) return payload;
            for (JsonNode msg : messages) {
                if (!"user".equalsIgnoreCase(msg.path("role").asText(""))) continue;
                JsonNode content = msg.path("content");
                if (content.isTextual()) return content.asText();
                if (content.isArray()) {
                    StringBuilder sb = new StringBuilder();
                    for (JsonNode item : content) {
                        String text = item.path("text").asText("");
                        if (!text.isEmpty()) { if (sb.length() > 0) sb.append('\n'); sb.append(text); }
                    }
                    if (sb.length() > 0) return sb.toString();
                }
            }
        } catch (Exception ignored) {}
        return payload;
    }

    private String extractResponseFromPayload(String payload) {
        if (payload == null || payload.isEmpty()) return null;
        try {
            JsonNode root = SnowflakeTraceParserUtils.OBJECT_MAPPER.readTree(payload);
            JsonNode resp = root.path("response");
            if (resp.isTextual()) return resp.asText();
            if (!resp.isMissingNode())
                return SnowflakeTraceParserUtils.OBJECT_MAPPER.writeValueAsString(resp);
        } catch (Exception ignored) {}
        return payload;
    }

    private Map<String, Object> baseMetadata(HttpResponseParams params, JsonNode tags,
                                              Map<String, Object> merged) {
        Map<String, Object> m = new HashMap<>();
        m.put("sourceType", SOURCE_TYPE);
        m.put("statusCode", params.getStatusCode());
        if (params.getRequestParams() != null) {
            m.put("path", params.getRequestParams().getURL());
            m.put("method", params.getRequestParams().getMethod());
            m.put("apiCollectionId", params.getRequestParams().getApiCollectionId());
        }
        String bot = tagText(tags, Constants.AI_AGENT_TAG_BOT_NAME);
        if (bot != null) m.put("botName", bot);
        if (tags != null && tags.hasNonNull("gen-ai")) m.put("gen-ai", tags.get("gen-ai").asText());
        return m;
    }

    private static String tagText(JsonNode tags, String field) {
        if (tags == null || !tags.has(field)) return null;
        String v = tags.get(field).asText("");
        return v.isEmpty() ? null : v;
    }
}
