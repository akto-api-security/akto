package com.akto.threat.backend.utils;

import com.akto.ProtoMessageUtils;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.Metadata;
import com.akto.threat.backend.dao.MaliciousEventDao;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.TextFormat;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ThreatUtils {

    private static final boolean USE_ACTOR_INFO_TABLE = Boolean.parseBoolean(
      System.getenv().getOrDefault("USE_ACTOR_INFO_TABLE", "false")
    );

    private static final List<String> ENDPOINT_POLICY_FILTER_IDS = Arrays.asList(
        "MCPGuardrails", "AuditPolicy", "MCPMaliciousComponent"
    );

    public static Document buildSimpleContextFilterNew(String contextSource, String accountId) {
        if (contextSource == null || contextSource.isEmpty()) {
            contextSource = CONTEXT_SOURCE.API.name();
        }

        if (USE_ACTOR_INFO_TABLE && "API".equalsIgnoreCase(contextSource)) {
            return new Document("contextSource", "API");
        }

        Document contextSourceFilter = new Document("contextSource", contextSource);

        // For legacy accounts, include a legacy filter for backward compatibility
        List<Integer> legacyAccounts = Arrays.asList(1700087964, 1726615470, 1728622642, 1737683011, 1745563576, 1760499072);
        try {
            if (accountId != null && legacyAccounts.contains(Integer.parseInt(accountId))) {
                Document legacyFilter = buildLegacyContextFilter(contextSource);
                return new Document("$or", Arrays.asList(contextSourceFilter, legacyFilter));
            }
        } catch (NumberFormatException ignored) {
        }

        return contextSourceFilter;
    }

    public static Document buildSimpleContextFilter(String contextSource, String accountId) {
        return buildSimpleContextFilterNew(contextSource, accountId);
    }

    private static Document buildLegacyContextFilter(String contextSource) {
        Document nullOrNotExistsCondition = new Document("$or", Arrays.asList(
            new Document("contextSource", null),
            new Document("contextSource", new Document("$exists", false))
        ));

        String contextSourceUpper = contextSource.toUpperCase();
        Document filterIdCondition;

        switch (contextSourceUpper) {
            case "AGENTIC":
                filterIdCondition = new Document("filterId", new Document("$in", ENDPOINT_POLICY_FILTER_IDS));
                break;
            case "ENDPOINT":
                filterIdCondition = new Document("filterId", new Document("$in", Collections.emptyList()));
                break;

            default:
                filterIdCondition = new Document("filterId", new Document("$nin", ENDPOINT_POLICY_FILTER_IDS));
                break;
        }

        return new Document("$and", Arrays.asList(nullOrNotExistsCondition, filterIdCondition));
    }

    private static final Pattern SKILLS_ENDPOINT_PATTERN = Pattern.compile("^/skills/");

    // Excludes /skills/<name> endpoint events (skill invocations) from dashboard-level violation
    // aggregations - same partition MaliciousEventService.listMaliciousRequests already applies
    // for the Active tab via skillEvalMode="exclude". ENDPOINT (Atlas) only, matching that
    // existing convention; a no-op Document for other contexts.
    public static Document excludeSkillEndpointFilter(String contextSource) {
        if (!"ENDPOINT".equalsIgnoreCase(contextSource)) {
            return new Document();
        }
        return new Document("latestApiEndpoint", new Document("$not", SKILLS_ENDPOINT_PATTERN));
    }

    public static boolean isAgenticOrEndpointContext(String contextSource) {
        if (contextSource == null || contextSource.isEmpty()) {
            return false;
        }
        String contextSourceUpper = contextSource.toUpperCase();
        return CONTEXT_SOURCE.AGENTIC.name().equals(contextSourceUpper)
                || CONTEXT_SOURCE.ENDPOINT.name().equals(contextSourceUpper);
    }

    public static String fetchMetadataString(String metadataStr) {
        if (metadataStr == null || metadataStr.isEmpty()) {
            return "";
        }

        Metadata.Builder metadataBuilder = Metadata.newBuilder();
        try {
            TextFormat.getParser().merge(metadataStr, metadataBuilder);
        } catch (Exception e) {
            return "";
        }
        Metadata metadataProto = metadataBuilder.build();
        metadataStr = ProtoMessageUtils.toString(metadataProto).orElse("");
        return metadataStr;
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Pattern CONFIG_CONTENT_TAIL = Pattern.compile("\"config_content\"\\s*:\\s*\"([\\s\\S]*)\"\\s*}\\s*$");
    private static final Pattern EVIDENCE_FIELD = Pattern.compile("\"evidence\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern MESSAGE_FIELD = Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    public static String repairConfigScanPayload(String requestPayload) {
        if (requestPayload == null || requestPayload.isEmpty()) {
            return requestPayload;
        }

        try {
            OBJECT_MAPPER.readTree(requestPayload);
            return requestPayload;
        } catch (Exception e) {
            // fall through to repair
        }

        Map<String, Object> repaired = new LinkedHashMap<>();
        tryExtractField(EVIDENCE_FIELD, requestPayload).ifPresent(v -> repaired.put("evidence", v));
        tryExtractField(MESSAGE_FIELD, requestPayload).ifPresent(v -> repaired.put("message", v));
        extractConfigContentTail(requestPayload).ifPresent(v -> repaired.put("config_content", v));

        if (repaired.isEmpty()) {
            return requestPayload;
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(repaired);
        } catch (Exception e) {
            return requestPayload;
        }
    }

    private static Optional<String> extractConfigContentTail(String text) {
        Matcher m = CONFIG_CONTENT_TAIL.matcher(text);
        if (!m.find()) {
            return Optional.empty();
        }
        String rawTail = m.group(1);
        Optional<String> exact = readFullJsonString(rawTail);
        if (exact.isPresent()) {
            return exact;
        }
        // rawTail may contain a bare (unescaped) " left behind by a redaction
        // marker. Escape every such quote so the whole tail reads as one
        // string again, then try the strict parse a second time.
        String reescaped = rawTail.replaceAll("(?<!\\\\)\"", "\\\\\"");
        return readFullJsonString(reescaped);
    }

    // Parses rawTail as a JSON string value, but only succeeds if rawTail is
    // ENTIRELY consumed by that one string. Plain readValue(json, String.class)
    // stops at the first closing quote and ignores anything after it, so a bare
    // quote mid-string makes it return a truncated prefix instead of failing.
    private static Optional<String> readFullJsonString(String rawTail) {
        try (JsonParser parser = OBJECT_MAPPER.getFactory().createParser("\"" + rawTail + "\"")) {
            if (parser.nextToken() != JsonToken.VALUE_STRING) {
                return Optional.empty();
            }
            String value = parser.getValueAsString();
            if (parser.nextToken() != null) {
                return Optional.empty(); // something remains after the string closed, reject instead of truncating
            }
            return Optional.ofNullable(value);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Optional<String> tryExtractField(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        if (!m.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(OBJECT_MAPPER.readValue("\"" + m.group(1) + "\"", String.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static String repairConfigScanEnvelope(String envelope) {
        if (envelope == null || envelope.isEmpty()) {
            return envelope;
        }

        Map<String, Object> fields;
        try {
            fields = OBJECT_MAPPER.readValue(envelope, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return envelope;   
        }

        Object requestPayload = fields.get("requestPayload");
        if (!(requestPayload instanceof String)) {
            return envelope;
        }

        String repairedRequestPayload = repairConfigScanPayload((String) requestPayload);
        if (repairedRequestPayload.equals(requestPayload)) {
            return envelope;
        }

        fields.put("requestPayload", repairedRequestPayload);
        try {
            return OBJECT_MAPPER.writeValueAsString(fields);
        } catch (Exception e) {
            return envelope;
        }
    }

    public static void createIndexIfAbsent(String accountId, MaliciousEventDao maliciousEventDao) {
        // Get the collection from DAO - this will create the collection if it doesn't exist
        MongoCollection<?> collection = maliciousEventDao.getCollection(accountId);

        Set<String> existingIndexes = new HashSet<>();
        try (MongoCursor<Document> cursor = collection.listIndexes().iterator()) {
            while (cursor.hasNext()) {
                Document index = cursor.next();
                existingIndexes.add(index.get("name", ""));
            }
        }

        Map<String, Bson> requiredIndexes = new HashMap<>();
        requiredIndexes.put("detectedAt_1", Indexes.ascending("detectedAt"));
        requiredIndexes.put("status_1", Indexes.ascending("status"));
        requiredIndexes.put("detectedAt_-1", Indexes.descending("detectedAt"));
        requiredIndexes.put("country_1_detectedAt_-1", Indexes.compoundIndex(Indexes.ascending("country"), Indexes.descending("detectedAt")));
        requiredIndexes.put("detectedAt_1_category_1_subCategory_1", Indexes.ascending("detectedAt", "category", "subCategory"));
        requiredIndexes.put("severity_1_detectedAt_1", Indexes.ascending("severity", "detectedAt"));
        requiredIndexes.put("detectedAt_-1_actor_1", Indexes.compoundIndex(Indexes.descending("detectedAt"), Indexes.ascending("actor")));
        requiredIndexes.put("actor_1_detectedAt_-1", Indexes.compoundIndex(Indexes.ascending("actor"), Indexes.descending("detectedAt")));
        requiredIndexes.put("filterId_1", Indexes.ascending("filterId"));
        requiredIndexes.put("contextSource_1_filterId_1_detectedAt_-1", Indexes.compoundIndex(Indexes.ascending("contextSource"), Indexes.ascending("filterId"), Indexes.descending("detectedAt")));
        requiredIndexes.put("contextSource_1_detectedAt_-1", Indexes.compoundIndex(Indexes.ascending("contextSource"), Indexes.descending("detectedAt")));
        requiredIndexes.put("idx_host", Indexes.ascending("host"));
        requiredIndexes.put("idx_detectedAt_host", Indexes.compoundIndex(Indexes.descending("detectedAt"), Indexes.ascending("host")));
        requiredIndexes.put("idx_detectedAt_latestApiEndpoint", Indexes.compoundIndex(Indexes.descending("detectedAt"), Indexes.ascending("latestApiEndpoint")));
        requiredIndexes.put("idx_context_detectedAt_severity", Indexes.compoundIndex(Indexes.ascending("contextSource"), Indexes.descending("detectedAt"), Indexes.ascending("severity")));
        requiredIndexes.put("idx_context_detectedAt_status", Indexes.compoundIndex(Indexes.ascending("contextSource"), Indexes.descending("detectedAt"), Indexes.ascending("status")));
        requiredIndexes.put("idx_detected_context_actor_country", Indexes.compoundIndex(Indexes.descending("detectedAt"), Indexes.ascending("contextSource"), Indexes.ascending("filterId"), Indexes.ascending("actor"), Indexes.ascending("country")));
        requiredIndexes.put("idx_refId", Indexes.ascending("refId"));
        requiredIndexes.put("idx_context_detectedAt_host", Indexes.compoundIndex(Indexes.ascending("contextSource"), Indexes.descending("detectedAt"), Indexes.ascending("host")));
        requiredIndexes.put("idx_context_detectedAt_latestApiEndpoint", Indexes.compoundIndex(Indexes.ascending("contextSource"), Indexes.descending("detectedAt"), Indexes.ascending("latestApiEndpoint")));
        requiredIndexes.put("idx_context_detectedAt_category_subCategory", Indexes.compoundIndex(Indexes.ascending("contextSource"), Indexes.descending("detectedAt"), Indexes.ascending("category"), Indexes.ascending("subCategory")));

        for (Map.Entry<String, Bson> entry : requiredIndexes.entrySet()) {
            if (!existingIndexes.contains(entry.getKey())) {
                collection.createIndex(entry.getValue(), new IndexOptions().name(entry.getKey()));
            }
        }
    }

}
