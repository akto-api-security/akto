package com.akto.threat.backend.utils;

import com.akto.ProtoMessageUtils;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.HostScope;
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

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ThreatUtils {

  /*
   * Events of the given hosts, matched the same way as the activity list: exact host, loose "device service"
   * key, or claude-config events by device id. Null when nothing is given.
   */
  public static Document hostAttributionMatch(List<String> hosts, List<String> looseHostKeys,
                                              List<String> claudeDeviceIds, boolean matchClaudeConfig) {
    List<Document> hostOrConditions = new ArrayList<>();
    if (!hosts.isEmpty()) {
      hostOrConditions.add(new Document("host", new Document("$in", hosts)));
    }
    if (!looseHostKeys.isEmpty()) {
      Document looseKeyExpr = new Document("$concat", Arrays.asList(
          new Document("$arrayElemAt", Arrays.asList(new Document("$split", Arrays.asList("$host", ".")), 0)),
          " ",
          new Document("$arrayElemAt", Arrays.asList(new Document("$split", Arrays.asList("$host", ".")), -1))
      ));
      hostOrConditions.add(new Document("$expr",
          new Document("$in", Arrays.asList(looseKeyExpr, looseHostKeys))));
    }
    Document claudeConfigHostMatch = new Document("host",
        Pattern.compile("^[^.]+\\.(claude-settings|claude)$", Pattern.CASE_INSENSITIVE));
    if (matchClaudeConfig) {
      hostOrConditions.add(claudeConfigHostMatch);
    } else if (!claudeDeviceIds.isEmpty()) {
      Document deviceIdExpr = new Document("$arrayElemAt", Arrays.asList(new Document("$split", Arrays.asList("$host", ".")), 0));
      hostOrConditions.add(new Document("$and", Arrays.asList(
          claudeConfigHostMatch,
          new Document("$expr", new Document("$in", Arrays.asList(deviceIdExpr, claudeDeviceIds)))
      )));
    }
    if (hostOrConditions.isEmpty()) {
      return null;
    }
    return hostOrConditions.size() == 1 ? hostOrConditions.get(0) : new Document("$or", hostOrConditions);
  }

  /** Match limiting an aggregation to the request's HostScope (some agents only); matches nothing when its lists are all empty. */
  public static Document hostScopeMatch(HostScope scope) {
    Document match = hostAttributionMatch(scope.getHostsList(), scope.getLooseHostKeysList(), scope.getClaudeDeviceIdsList(), false);
    return match != null ? match : new Document("host", new Document("$in", Collections.emptyList()));
  }

  /** Adds the host scope to a $match document, alongside any $and it already has. */
  public static void andHostScope(Document match, Document hostScopeMatch) {
    if (hostScopeMatch == null) {
      return;
    }
    List<Object> and = new ArrayList<>();
    Object existing = match.get("$and");
    if (existing instanceof List) {
      and.addAll((List<?>) existing);
    }
    and.add(hostScopeMatch);
    match.put("$and", and);
  }

  /** Adds the host scope as the first stage of an events aggregation (Mongo merges it with the following $match). */
  public static void addHostScope(List<Document> pipeline, Document hostScopeMatch) {
    if (hostScopeMatch != null) {
      pipeline.add(0, new Document("$match", hostScopeMatch));
    }
  }

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

    public static final Pattern SKILLS_ENDPOINT_PATTERN = Pattern.compile("^/skills/");
    public static final int SKILLS_ENDPOINT_PREFIX_LENGTH = "/skills/".length();

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

    public static final Pattern CONFIG_ENDPOINT_PATTERN = Pattern.compile("/config/");

    // Skills Evaluations / Misconfigured Settings partitions — Atlas (ENDPOINT) only. An event
    // belongs to Skills Evaluations iff latestApiEndpoint starts with "/skills/"; it belongs to
    // Misconfigured Settings iff latestApiEndpoint contains "/config/" (e.g.
    // "/codex/config/mcp_servers.computer-use.command" — the agent name prefix varies, so this
    // isn't anchored to the start like the skills pattern). Each mode independently narrows to
    // just its own partition ("only") or excludes it ("exclude") — Active sets both to "exclude"
    // so neither shows up there. Returns the conditions to AND onto a match; empty for other
    // contexts or when no mode is set.
    public static List<Document> evaluationModeConditions(String contextSource, String skillEvalMode, String configEvalMode) {
        List<Document> conditions = new ArrayList<>();
        if (!"ENDPOINT".equalsIgnoreCase(contextSource)) {
            return conditions;
        }
        if ("only".equalsIgnoreCase(skillEvalMode)) {
            conditions.add(new Document("latestApiEndpoint", SKILLS_ENDPOINT_PATTERN));
        } else if ("exclude".equalsIgnoreCase(skillEvalMode)) {
            conditions.add(new Document("latestApiEndpoint", new Document("$not", SKILLS_ENDPOINT_PATTERN)));
        }
        if ("only".equalsIgnoreCase(configEvalMode)) {
            conditions.add(new Document("latestApiEndpoint", CONFIG_ENDPOINT_PATTERN));
        } else if ("exclude".equalsIgnoreCase(configEvalMode)) {
            conditions.add(new Document("latestApiEndpoint", new Document("$not", CONFIG_ENDPOINT_PATTERN)));
        }
        return conditions;
    }

    private static Document configScanDedupeKey() {
        Document isConfigEvent = new Document("$regexMatch",
            new Document("input", new Document("$ifNull", Arrays.asList("$latestApiEndpoint", "")))
                .append("regex", "/config/"));

        return new Document("$cond", Arrays.asList(
            isConfigEvent,
            new Document("host", "$host").append("actor", "$actor").append("category", "$category")
                .append("latestApiEndpoint", "$latestApiEndpoint"),
            new Document("uniqueId", "$_id")));
    }

    public static List<Document> configScanDedupeStages(String contextSource) {
        if (!"ENDPOINT".equalsIgnoreCase(contextSource)) {
            return Collections.emptyList();
        }

        return Arrays.asList(
            new Document("$sort", new Document("detectedAt", -1)),
            new Document("$group",
                new Document("_id", configScanDedupeKey())
                    .append("host", new Document("$first", "$host"))
                    .append("category", new Document("$first", "$category"))
                    .append("subCategory", new Document("$first", "$subCategory"))
                    .append("status", new Document("$first", "$status"))
                    .append("severity", new Document("$first", "$severity"))
                    .append("detectedAt", new Document("$first", "$detectedAt"))
                    .append("successfulExploit", new Document("$first", "$successfulExploit"))));
    }

    public static List<Document> configScanLatestDocStages(String contextSource) {
        if (!"ENDPOINT".equalsIgnoreCase(contextSource)) {
            return Collections.emptyList();
        }

        return Arrays.asList(
            new Document("$sort", new Document("detectedAt", -1)),
            new Document("$group",
                new Document("_id", configScanDedupeKey()).append("doc", new Document("$first", "$$ROOT"))),
            new Document("$replaceRoot", new Document("newRoot", "$doc")),
            new Document("$sort", new Document("detectedAt", -1)));
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

    // Guardrail activities from one Bedrock caller repeating the same prompt (an agent re-sends it on
    // every step, tripping the same policies each time) within this gap of each other are one activity.
    public static final long ACTIVITY_GROUP_GAP_SECONDS = 20 * 60;
    public static final String ACTIVITY_GROUP_INDEX = "idx_groupKey_detectedAt";
    public static final Bson ACTIVITY_GROUP_INDEX_KEYS = Indexes.ascending("groupKey", "detectedAt");
    private static final String BEDROCK_IDENTITY_ARN_HEADER = "bedrock-identity-arn";
    private static final Set<String> GROUPED_ACTIVITY_ACCOUNTS = parseAccountList(
        System.getenv().getOrDefault("AKTO_GROUPED_ACTIVITY_ACCOUNTS", "1703087742"));

    private static Set<String> parseAccountList(String csv) {
        Set<String> accounts = new HashSet<>();
        for (String id : csv.split(",")) {
            if (!id.trim().isEmpty()) {
                accounts.add(id.trim());
            }
        }
        return accounts;
    }

    /**
     * Pipeline stages that turn matching events into activity rows. Events with the same groupKey whose
     * detectedAt is within ACTIVITY_GROUP_GAP_SECONDS of the previous one form one row: the latest event,
     * plus groupCount, groupFirstDetectedAt, groupEventIds (every event in it, so actions on the row reach
     * exactly the events the current filters grouped) and groupFilterIds (the policies they tripped).
     * Events without a groupKey stay one row each. The stored payload is dropped first; the list never
     * returns it and it is most of each event's size.
     */
    public static List<Document> activityGroupStages(Document query) {
        Document hasKey = new Document("$eq", Arrays.asList(new Document("$type", "$groupKey"), "string"));
        Document byKeyOldestFirst = new Document("partitionBy", "$groupKey").append("sortBy", new Document("detectedAt", 1));
        return Arrays.asList(
            new Document("$match", query),
            new Document("$unset", "latestApiOrig"),
            new Document("$setWindowFields", new Document(byKeyOldestFirst).append("output", new Document("prevDetectedAt",
                new Document("$shift", new Document("output", "$detectedAt").append("by", -1))))),
            new Document("$set", new Document("startsActivity", new Document("$cond", Arrays.asList(
                new Document("$or", Arrays.asList(
                    new Document("$not", Arrays.asList(hasKey)),
                    new Document("$eq", Arrays.asList("$prevDetectedAt", null)),
                    new Document("$gt", Arrays.asList(new Document("$subtract", Arrays.asList("$detectedAt", "$prevDetectedAt")),
                        ACTIVITY_GROUP_GAP_SECONDS)))),
                1, 0)))),
            new Document("$setWindowFields", new Document(byKeyOldestFirst).append("output", new Document("activityNo",
                new Document("$sum", "$startsActivity").append("window", new Document("documents", Arrays.asList("unbounded", "current")))))),
            new Document("$sort", new Document("detectedAt", -1)),
            new Document("$group", new Document("_id", new Document("$cond", Arrays.asList(hasKey,
                    new Document("groupKey", "$groupKey").append("activityNo", "$activityNo"), "$_id")))
                .append("doc", new Document("$first", "$$ROOT"))
                .append("groupCount", new Document("$sum", 1))
                .append("groupFirstDetectedAt", new Document("$min", "$detectedAt"))
                .append("groupEventIds", new Document("$push", "$_id"))
                .append("groupFilterIds", new Document("$addToSet", "$filterId"))),
            new Document("$replaceRoot", new Document("newRoot", new Document("$mergeObjects", Arrays.asList("$doc",
                new Document("groupCount", "$groupCount").append("groupFirstDetectedAt", "$groupFirstDetectedAt")
                    .append("groupEventIds", "$groupEventIds").append("groupFilterIds", "$groupFilterIds"))))),
            new Document("$unset", Arrays.asList("prevDetectedAt", "startsActivity", "activityNo"))
        );
    }

    /** Whether repeated Bedrock activities are grouped for this account (opt-in per account). */
    public static boolean groupsActivities(String accountId) {
        return accountId != null && GROUPED_ACTIVITY_ACCOUNTS.contains(accountId);
    }

    /**
     * Grouping key of a guardrail activity: the Bedrock caller ARN (bedrock-identity-arn request header)
     * plus the normalized prompt (requestPayload) of its stored envelope. Null when the envelope carries
     * no ARN, so only Bedrock activities are ever grouped.
     */
    public static String activityGroupKey(String envelope) {
        if (envelope == null || envelope.isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> fields = OBJECT_MAPPER.readValue(envelope, new TypeReference<Map<String, Object>>() {});
            Object prompt = fields.get("requestPayload");
            Object headers = fields.get("requestHeaders");
            if (!(prompt instanceof String) || ((String) prompt).isEmpty() || !(headers instanceof String)) {
                return null;
            }
            Map<String, Object> headerMap = OBJECT_MAPPER.readValue((String) headers, new TypeReference<Map<String, Object>>() {});
            Object arn = null;
            for (Map.Entry<String, Object> header : headerMap.entrySet()) {
                if (BEDROCK_IDENTITY_ARN_HEADER.equalsIgnoreCase(header.getKey())) {
                    arn = header.getValue();
                }
            }
            if (!(arn instanceof String) || ((String) arn).isEmpty()) {
                return null;
            }
            return UUID.nameUUIDFromBytes((arn + "\n" + normalizePrompt((String) prompt)).getBytes(StandardCharsets.UTF_8)).toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static final Pattern REDACTION_MARKER = Pattern.compile("\\[[a-z_]*redacted[a-z_]*\\]");
    private static final Pattern LONG_ID = Pattern.compile("\\b[0-9a-f]{8}(-?[0-9a-f]{4}){3}-?[0-9a-f]{12}\\b|\\b[0-9a-f]{12,}\\b");
    private static final Pattern NOT_LETTERS = Pattern.compile("[^a-z]+");
    private static final int PROMPT_WORDS_COMPARED = 40;

    // An agent's repeated prompt differs only in what it fills in on each step (turn numbers, ids,
    // redacted values, the data it pastes after its instructions), so only the words of its opening
    // are compared: same opening words in the same order is the same prompt.
    static String normalizePrompt(String prompt) {
        String text = prompt.toLowerCase(Locale.ROOT);
        text = REDACTION_MARKER.matcher(text).replaceAll(" ");
        text = LONG_ID.matcher(text).replaceAll(" ");
        String words = NOT_LETTERS.matcher(text).replaceAll(" ").trim();
        // No words left (only numbers or ids): compare the prompt as it is rather than as empty.
        if (words.isEmpty()) return prompt;
        String[] split = words.split(" ", PROMPT_WORDS_COMPARED + 1);
        return split.length <= PROMPT_WORDS_COMPARED ? words : String.join(" ", Arrays.copyOf(split, PROMPT_WORDS_COMPARED));
    }

    private static void createFilterFieldIndexIfAbsent(MongoCollection<?> collection, Set<String> existingIndexes, String field) {
        String name = "idx_context_status_" + field + "_detectedAt";
        if (existingIndexes.contains(name)) {
            return;
        }
        collection.createIndex(
            Indexes.compoundIndex(Indexes.ascending("contextSource"), Indexes.ascending("status"), Indexes.ascending(field), Indexes.descending("detectedAt")),
            new IndexOptions().name(name).partialFilterExpression(new Document(field, new Document("$exists", true))));
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

        // Only Endpoint/Agentic/MCP events carry these fields; partial so other contexts add nothing to the index.
        createFilterFieldIndexIfAbsent(collection, existingIndexes, "agenticType");
        createFilterFieldIndexIfAbsent(collection, existingIndexes, "behaviour");

        // Only accounts that group activities have keyed events; partial so it stays small.
        if (groupsActivities(accountId) && !existingIndexes.contains(ACTIVITY_GROUP_INDEX)) {
            collection.createIndex(ACTIVITY_GROUP_INDEX_KEYS, new IndexOptions().name(ACTIVITY_GROUP_INDEX)
                .partialFilterExpression(new Document("groupKey", new Document("$type", "string"))));
        }
    }

}
