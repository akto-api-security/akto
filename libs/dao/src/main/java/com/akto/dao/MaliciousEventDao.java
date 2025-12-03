package com.akto.dao;

import com.akto.dto.threat_detection_backend.MaliciousEventDto;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.List;

public class MaliciousEventDao extends AccountsContextDao<MaliciousEventDto> {

    public static final MaliciousEventDao instance = new MaliciousEventDao();

    private MaliciousEventDao() {}

    @Override
    public String getCollName() {
        return "malicious_events";
    }

    @Override
    public Class<MaliciousEventDto> getClassT() {
        return MaliciousEventDto.class;
    }

    public void insertOne(String accountId, MaliciousEventDto event) {
        getCollection(accountId).insertOne(event);
    }

    public MongoCollection<MaliciousEventDto> getCollection(String accountId) {
        return clients[0].getDatabase(accountId).getCollection(getCollName(), getClassT());
    }

    public AggregateIterable<Document> aggregateRaw(String accountId, List<Document> pipeline) {
        return clients[0].getDatabase(accountId)
            .getCollection(getCollName(), Document.class)
            .aggregate(pipeline);
    }

    public long countDocuments(String accountId, Bson filter) {
        return getCollection(accountId).countDocuments(filter);
    }
}
