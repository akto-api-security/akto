package com.akto.tracing.snowflake;

import com.akto.dto.HttpResponseParams;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared helpers for {@link SnowflakeTraceParser}: JSON ↔ maps, payload graph arrays, row merge / ids, and
 * small string utilities.
 */
public final class SnowflakeTraceParserUtils {

    private SnowflakeTraceParserUtils() {
    }

    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static final String FIELD_SNOWFLAKE_TRACE_METADATA = "snowflakeTraceMetadata";
    static final String META_RECORD_ATTRIBUTES = "record_attributes";
    static final String AKTO_EVENT_TS = "_akto.observability.event_timestamp_unix";
    static final String AKTO_ROW_TS = "_akto.observability.timestamp";

    private static final DateTimeFormatter SNOWFLAKE_OBS_CSV_TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS");

    private static final String KEY_RECORD_ID = "ai.observability.record_id";
    private static final String KEY_AGENT_REQUEST_ID = "snow.ai.observability.agent.request_id";
    private static final String KEY_REQUEST_ID = "request_id";
    private static final String KEY_AGENT_DURATION = "snow.ai.observability.agent.duration";

    public static JsonNode readSnowflakeTraceMetadataArrayFromPayload(HttpResponseParams params) {
        String payload = params.getPayload();
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(payload);
            JsonNode meta = root.get(FIELD_SNOWFLAKE_TRACE_METADATA);
            return resolveSnowflakeTraceMetadataArray(meta);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * In-tree JSON array, or JSON string containing serialized array. Any other shape yields null
     * (parse failures on string also yield null).
     */
    public static JsonNode resolveSnowflakeTraceMetadataArray(JsonNode meta) {
        if (meta == null || meta.isNull()) {
            return null;
        }
        if (meta.isArray()) {
            return meta;
        }
        if (meta.isTextual()) {
            try {
                JsonNode parsed = OBJECT_MAPPER.readTree(meta.asText());
                return parsed != null && parsed.isArray() ? parsed : null;
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Each element matches {@code observability.csv}: envelope {@code TIMESTAMP} + {@code RECORD_ATTRIBUTES}
     * (object or JSON string), or a flat object row. Keys matched case-insensitively where noted.
     */
    public static void appendSnowflakeTraceMetadataArray(JsonNode array, List<Map<String, Object>> rows) {
        for (JsonNode node : array) {
            if (node == null || !node.isObject()) {
                continue;
            }
            JsonNode ra = getChildIgnoreCase(node, "RECORD_ATTRIBUTES", META_RECORD_ATTRIBUTES);
            Map<String, Object> m;
            if (ra != null) {
                if (ra.isObject()) {
                    m = jsonObjectToMap(ra);
                } else if (ra.isTextual()) {
                    String raw = ra.asText().trim();
                    if (raw.isEmpty()) {
                        continue;
                    }
                    try {
                        JsonNode inner = OBJECT_MAPPER.readTree(raw);
                        if (inner == null || !inner.isObject()) {
                            continue;
                        }
                        m = jsonObjectToMap(inner);
                    } catch (Exception e) {
                        continue;
                    }
                } else {
                    continue;
                }
                mergeEnvelopeTimestampIntoRow(node, m);
                if (node.hasNonNull("event_timestamp_unix") && node.get("event_timestamp_unix").isNumber()) {
                    m.put(AKTO_EVENT_TS, node.get("event_timestamp_unix").longValue());
                }
                rows.add(m);
            } else {
                m = jsonObjectToMap(node);
                mergeEnvelopeTimestampIntoRow(node, m);
                if (node.hasNonNull("event_timestamp_unix") && node.get("event_timestamp_unix").isNumber()) {
                    m.put(AKTO_EVENT_TS, node.get("event_timestamp_unix").longValue());
                }
                rows.add(m);
            }
        }
    }

    static JsonNode getChildIgnoreCase(JsonNode obj, String... names) {
        if (obj == null || !obj.isObject() || names == null) {
            return null;
        }
        for (String n : names) {
            if (n != null && obj.has(n)) {
                return obj.get(n);
            }
        }
        Iterator<String> it = obj.fieldNames();
        while (it.hasNext()) {
            String f = it.next();
            for (String n : names) {
                if (n != null && f.equalsIgnoreCase(n)) {
                    return obj.get(f);
                }
            }
        }
        return null;
    }

    /**
     * Copies envelope {@code TIMESTAMP} / numeric epoch into {@link #AKTO_ROW_TS} / {@link #AKTO_EVENT_TS}
     * for {@link #rowObsTimestamp} ordering when building spans.
     */
    public static void mergeEnvelopeTimestampIntoRow(JsonNode envelope, Map<String, Object> row) {
        if (envelope == null || row == null) {
            return;
        }
        JsonNode ts = getChildIgnoreCase(envelope, "TIMESTAMP", "timestamp");
        if (ts == null || ts.isNull()) {
            return;
        }
        if (ts.isIntegralNumber()) {
            row.put(AKTO_EVENT_TS, ts.longValue());
            return;
        }
        if (ts.isFloatingPointNumber()) {
            row.put(AKTO_EVENT_TS, ts.longValue());
            return;
        }
        if (ts.isTextual()) {
            String s = ts.asText().trim();
            if (s.isEmpty()) {
                return;
            }
            try {
                row.put(AKTO_ROW_TS, Instant.parse(s).toEpochMilli());
                return;
            } catch (Exception ignored) {
            }
            try {
                LocalDateTime ldt = LocalDateTime.parse(s, SNOWFLAKE_OBS_CSV_TIMESTAMP);
                row.put(AKTO_ROW_TS, ldt.toInstant(ZoneOffset.UTC).toEpochMilli());
                return;
            } catch (Exception ignored) {
            }
            try {
                row.put(AKTO_ROW_TS, Long.parseLong(s));
            } catch (Exception ignored) {
            }
        }
    }

    public static Map<String, Object> jsonObjectToMap(JsonNode obj) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (obj == null || !obj.isObject()) {
            return m;
        }
        obj.fields().forEachRemaining(e -> m.put(e.getKey(), jsonNodeToJava(e.getValue())));
        return m;
    }

    public static Object jsonNodeToJava(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isBoolean()) {
            return n.booleanValue();
        }
        if (n.isInt()) {
            return n.intValue();
        }
        if (n.isLong()) {
            return n.longValue();
        }
        if (n.isDouble() || n.isFloat()) {
            return n.doubleValue();
        }
        if (n.isTextual()) {
            return n.asText();
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(n);
        } catch (Exception e) {
            return n.toString();
        }
    }

    public static String stringValue(Object o) {
        if (o == null) {
            return null;
        }
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    public static long longValue(Object o, long defaultVal) {
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        if (o != null) {
            try {
                return Long.parseLong(o.toString().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultVal;
    }

    @SafeVarargs
    public static String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String c : candidates) {
            if (c != null && !c.trim().isEmpty()) {
                return c.trim();
            }
        }
        return null;
    }

    /**
     * Merges row maps. When rows are <b>newest first</b> (index 0 = latest), merges from last index toward
     * first so duplicate keys keep the <b>newest</b> row’s value without sorting.
     */
    public static Map<String, Object> mergeMapsInOrder(List<Map<String, Object>> rows) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (rows == null) {
            return out;
        }
        for (int i = rows.size() - 1; i >= 0; i--) {
            out.putAll(rows.get(i));
        }
        return out;
    }

    public static String resolvePrimaryRecordId(List<Map<String, Object>> rows) {
        Map<String, Integer> counts = new HashMap<>();
        for (Map<String, Object> r : rows) {
            String id = stringValue(r.get(KEY_RECORD_ID));
            if (id != null) {
                counts.merge(id, 1, Integer::sum);
            }
        }
        if (counts.isEmpty()) {
            for (Map<String, Object> r : rows) {
                String id = firstNonBlank(stringValue(r.get(KEY_AGENT_REQUEST_ID)), stringValue(r.get(KEY_REQUEST_ID)));
                if (id != null) {
                    return id;
                }
            }
            return null;
        }
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    public static long rowObsTimestamp(Map<String, Object> row) {
        if (row == null) {
            return 0L;
        }
        long t = longValue(row.get(AKTO_ROW_TS), 0L);
        if (t > 0) {
            return t;
        }
        return longValue(row.get(AKTO_EVENT_TS), 0L);
    }

    public static int parseStepNumber(Object o) {
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        if (o != null) {
            try {
                return Integer.parseInt(o.toString().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    /** Snowflake often encodes repeated tool columns as JSON string arrays; unwrap the first element when possible. */
    public static String unwrapJsonStringArrayFirstOrString(Object v) {
        String s = stringValue(v);
        if (s == null) {
            return null;
        }
        s = s.trim();
        if (s.length() >= 2 && s.charAt(0) == '[' && s.charAt(s.length() - 1) == ']') {
            try {
                JsonNode arr = OBJECT_MAPPER.readTree(s);
                if (arr != null && arr.isArray() && arr.size() > 0) {
                    JsonNode first = arr.get(0);
                    if (first != null && first.isTextual()) {
                        return first.asText();
                    }
                    if (first != null && first.isNumber()) {
                        return first.asText();
                    }
                    return first != null ? first.toString() : null;
                }
            } catch (Exception ignored) {
            }
        }
        return s;
    }

    public static boolean isNoiseMetadataKey(String fullKey) {
        if (fullKey == null) {
            return false;
        }
        if (fullKey.endsWith(".duration") || KEY_AGENT_DURATION.equals(fullKey)) {
            return true;
        }
        if (fullKey.endsWith(".status.code")) {
            return true;
        }
        if (fullKey.endsWith(".status") && !fullKey.endsWith(".status.code")) {
            return true;
        }
        if (fullKey.contains(".token_count.") || fullKey.endsWith(".token_count")) {
            return true;
        }
        if (fullKey.endsWith(".instruction") || fullKey.contains(".instruction.")) {
            return true;
        }
        if (fullKey.endsWith(".messages") || fullKey.contains(".messages.")) {
            return true;
        }
        if (fullKey.contains(".tool.description")) {
            return true;
        }
        if (fullKey.contains(".tool.parameters")) {
            return true;
        }
        return false;
    }
}
