package com.akto.threat.backend.dao;

import com.akto.dto.threat_detection_backend.MaliciousEventDto;
import com.akto.log.LoggerMaker;
import com.akto.threat.backend.constants.MongoDBCollection;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.CountOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import org.bson.BsonDocument;
import org.bson.BsonType;
import org.bson.BsonDocumentWriter;
import org.bson.BsonInt64;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.codecs.EncoderContext;
import org.bson.conversions.Bson;
import com.akto.threat.backend.utils.ThreatUtils;
import com.akto.util.enums.GlobalEnums.Severity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class MaliciousEventDao extends AccountBasedDao<MaliciousEventDto> {

    public static final MaliciousEventDao instance = new MaliciousEventDao();

    private MaliciousEventDao() {}

    @Override
    protected String getCollectionName() {
        return MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS;
    }

    @Override
    protected Class<MaliciousEventDto> getClassType() {
        return MaliciousEventDto.class;
    }

    public void insertOne(String accountId, MaliciousEventDto event) {
        getCollection(accountId).insertOne(event);
    }

    private static final Set<String> INSERT_ONLY_FIELDS = new HashSet<>(Arrays.asList(
        "_id", "refId", "status", "caseOwner", "caseNotes", "caseTimeline", "jiraTicketUrl",
        MaliciousEventDto.DEDUPE_KEY, MaliciousEventDto.FIRST_DETECTED_AT));
    private static final int UPSERT_ATTEMPTS = 2;
    private static final Set<String> DEDUPE_INDEX_ACCOUNTS = ConcurrentHashMap.newKeySet();
    private static final LoggerMaker logger = new LoggerMaker(MaliciousEventDao.class, LoggerMaker.LogDb.THREAT_DETECTION);

    private void ensureDedupeKeyIndex(String accountId, MongoCollection<MaliciousEventDto> collection) {
        if (!DEDUPE_INDEX_ACCOUNTS.add(accountId)) {
            return;
        }
        try {
            ThreatUtils.createDedupeKeyIndex(collection);
        } catch (MongoException e) {
            logger.errorAndAddToDb("Unique dedupeKey index could not be built for account " + accountId + ": " + e.getMessage());
        }
    }

    public void upsertByDedupeKey(String accountId, MaliciousEventDto event) {
        MongoCollection<MaliciousEventDto> collection = getCollection(accountId);
        ensureDedupeKeyIndex(accountId, collection);
        BsonDocument encoded = new BsonDocument();
        collection.getCodecRegistry().get(MaliciousEventDto.class)
            .encode(new BsonDocumentWriter(encoded), event, EncoderContext.builder().build());

        List<Bson> updates = new ArrayList<>();
        for (Map.Entry<String, BsonValue> field : encoded.entrySet()) {
            if (INSERT_ONLY_FIELDS.contains(field.getKey())) {
                if (!MaliciousEventDto.DEDUPE_KEY.equals(field.getKey()) && !MaliciousEventDto.FIRST_DETECTED_AT.equals(field.getKey())) {
                    updates.add(Updates.setOnInsert(field.getKey(), field.getValue()));
                }
            } else {
                updates.add(Updates.set(field.getKey(), field.getValue()));
            }
        }
        updates.add(Updates.setOnInsert(MaliciousEventDto.FIRST_DETECTED_AT, new BsonInt64(event.getDetectedAt())));

        Bson sameFinding = Filters.and(
            Filters.type(MaliciousEventDto.DEDUPE_KEY, BsonType.STRING),
            Filters.eq(MaliciousEventDto.DEDUPE_KEY, event.getDedupeKey()));
        Bson filter = Filters.and(sameFinding, Filters.lte("detectedAt", event.getDetectedAt()));
        Bson update = Updates.combine(updates);

        for (int attempt = 1; attempt <= UPSERT_ATTEMPTS; attempt++) {
            try {
                UpdateResult result = getDocumentCollection(accountId).updateOne(filter, update, new UpdateOptions().upsert(true));
                if (result.getUpsertedId() == null) {
                    getDocumentCollection(accountId).updateOne(
                        Filters.and(sameFinding, Filters.eq("status", MaliciousEventDto.Status.FIXED.name())),
                        Updates.set("status", MaliciousEventDto.Status.ACTIVE.name()));
                }
                return;
            } catch (MongoWriteException e) {
                if (e.getError().getCategory() != ErrorCategory.DUPLICATE_KEY) {
                    throw e;
                }
            }
        }
        logger.debug("Skipped config event older than the stored row, dedupeKey={} detectedAt={}", event.getDedupeKey(), event.getDetectedAt());
    }

    public MongoCollection<MaliciousEventDto> getCollection(String accountId) {
        return super.getCollection(accountId);
    }

    public AggregateIterable<Document> aggregateRaw(String accountId, List<Document> pipeline) {
        return getDatabase(accountId)
            .getCollection(getCollectionName(), Document.class)
            .aggregate(pipeline);
    }

    public MongoCollection<Document> getDocumentCollection(String accountId) {
        return getDatabase(accountId)
            .getCollection(getCollectionName(), Document.class);
    }

    public long countDocuments(String accountId, Bson filter) {
        return getCollection(accountId).countDocuments(filter);
    }

    public long countDocuments(String accountId, Bson filter, long maxTimeMs) {
        return getCollection(accountId).countDocuments(filter, new CountOptions().maxTime(maxTimeMs, TimeUnit.MILLISECONDS));
    }

    public long countDocuments(String accountId, Bson filter, long maxTimeMs, Bson hint) {
        return getCollection(accountId).countDocuments(filter, new CountOptions().maxTime(maxTimeMs, TimeUnit.MILLISECONDS).hint(hint));
    }

    public void createIndexIfAbsent(String accountId) {
        ThreatUtils.createIndexIfAbsent(accountId, this);
    }

    public static float getThreatScore(Severity severity) {
        switch (severity) {
            case CRITICAL:
                return 1.0f;
            case HIGH:
                return 0.75f;
            case MEDIUM:
                return 0.5f;
            case LOW:
                return 0.25f;
            default:
                return 0.0f;
        }
    }

    public static float getThreatScoreFromSeverities(List<String> severities) {
        float threatScore = 0.0f;
        for(String severity : severities) {
            threatScore = Math.max(threatScore, getThreatScore(Severity.valueOf(severity)));
        }
        return threatScore;
    }
}
