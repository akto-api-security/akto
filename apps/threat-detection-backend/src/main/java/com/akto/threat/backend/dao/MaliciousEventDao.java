package com.akto.threat.backend.dao;

import com.akto.dto.threat_detection_backend.MaliciousEventDto;
import com.akto.threat.backend.constants.MongoDBCollection;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.conversions.Bson;

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

    public long countDocuments(String accountId, Bson filter) {
        return getCollection(accountId).countDocuments(filter);
    }
}
