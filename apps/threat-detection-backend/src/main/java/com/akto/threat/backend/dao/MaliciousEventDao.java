package com.akto.threat.backend.dao;

import com.akto.dto.threat_detection_backend.MaliciousEventDto;
import com.akto.threat.backend.constants.MongoDBCollection;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.conversions.Bson;
import com.akto.threat.backend.utils.ThreatUtils;
import com.akto.util.enums.GlobalEnums.Severity;

import java.util.List;

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
