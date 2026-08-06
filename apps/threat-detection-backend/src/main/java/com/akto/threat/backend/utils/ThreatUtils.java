package com.akto.threat.backend.utils;

import com.akto.log.LoggerMaker;
import com.akto.threat.backend.dao.MaliciousEventDao;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final LoggerMaker logger = new LoggerMaker(ThreatUtils.class, LoggerMaker.LogDb.THREAT_DETECTION);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern CONFIG_CONTENT_TAIL = Pattern.compile("\"config_content\"\\s*:\\s*\"([\\s\\S]*)\"\\s*}\\s*$");
    private static final Pattern EVIDENCE_FIELD = Pattern.compile("\"evidence\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern MESSAGE_FIELD = Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    // Repairs a settings-scanner requestPayload broken by PII anonymization scrubbing text
    // adjacent to a quote (e.g. a redaction marker landing right after a `"`). Recovers
    // whichever of evidence/message/config_content can still be extracted; returns the
    // original string unchanged if it's already valid or nothing is recoverable.
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

    // config_content is always the last field, so a greedy match to end-of-string
    // captures its full value even if a redaction marker corrupted the escaping.
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
        // A bare (unescaped) " left behind by a redaction marker breaks the string early -
        // escape every such quote and retry once before giving up.
        String reescaped = rawTail.replaceAll("(?<!\\\\)\"", "\\\\\"");
        return readFullJsonString(reescaped);
    }

    // Parses rawTail as a JSON string, but only succeeds if the ENTIRE input is consumed -
    // plain readValue(json, String.class) stops at the first closing quote and silently
    // discards the rest, truncating a repaired value instead of failing loudly.
    private static Optional<String> readFullJsonString(String rawTail) {
        try (JsonParser parser = OBJECT_MAPPER.getFactory().createParser("\"" + rawTail + "\"")) {
            if (parser.nextToken() != JsonToken.VALUE_STRING) {
                return Optional.empty();
            }
            String value = parser.getValueAsString();
            if (parser.nextToken() != null) {
                return Optional.empty();
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

    // Repairs the nested requestPayload field inside a full malicious_events envelope
    // (latestApiOrig). Returns the envelope unchanged if there's nothing to repair.
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

    // One-time-per-run backfill: repairs already-corrupted latestApiOrig for existing
    // settings-scanner events in this account. Self-limiting - already-valid documents are
    // returned unchanged by repairConfigScanEnvelope and skipped, so re-running is a no-op.
    public static void repairConfigScanHistoricalData(String accountId, MaliciousEventDao maliciousEventDao) {
        MongoCollection<Document> collection = maliciousEventDao.getRawCollection(accountId);
        int repaired = 0;
        try (MongoCursor<Document> cursor = collection.find(new Document("actor", "settings-scanner")).iterator()) {
            while (cursor.hasNext()) {
                Document doc = cursor.next();
                String orig = doc.getString("latestApiOrig");
                String repairedOrig = repairConfigScanEnvelope(orig);
                if (repairedOrig == null || repairedOrig.equals(orig)) {
                    continue;
                }
                collection.updateOne(
                    new Document("_id", doc.get("_id")),
                    new Document("$set", new Document("latestApiOrig", repairedOrig)));
                repaired++;
            }
        }
        if (repaired > 0) {
            logger.info("repaired " + repaired + " settings-scanner events for account " + accountId);
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
        requiredIndexes.put("detectedAt_-1", Indexes.descending("detectedAt"));
        requiredIndexes.put("country_1_detectedAt_-1", Indexes.compoundIndex(Indexes.ascending("country"), Indexes.descending("detectedAt")));
        requiredIndexes.put("detectedAt_1_category_1_subCategory_1", Indexes.ascending("detectedAt", "category", "subCategory"));
        requiredIndexes.put("severity_1_detectedAt_1", Indexes.ascending("severity", "detectedAt"));
        requiredIndexes.put("detectedAt_-1_actor_1", Indexes.compoundIndex(Indexes.descending("detectedAt"), Indexes.ascending("actor")));
        requiredIndexes.put("actor_1_detectedAt_-1", Indexes.compoundIndex(Indexes.ascending("actor"), Indexes.descending("detectedAt")));
        requiredIndexes.put("filterId_1", Indexes.ascending("filterId"));

        for (Map.Entry<String, Bson> entry : requiredIndexes.entrySet()) {
            if (!existingIndexes.contains(entry.getKey())) {
                collection.createIndex(entry.getValue(), new IndexOptions().name(entry.getKey()));
            }
        }
    }

}
