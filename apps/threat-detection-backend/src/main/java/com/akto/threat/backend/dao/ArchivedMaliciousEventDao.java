package com.akto.threat.backend.dao;

import com.akto.threat.backend.constants.MongoDBCollection;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.WriteModel;
import org.bson.Document;

import java.util.List;

public class ArchivedMaliciousEventDao extends AccountBasedDao<Document> {

    public static final ArchivedMaliciousEventDao instance = new ArchivedMaliciousEventDao();

    private ArchivedMaliciousEventDao() {}

    @Override
    protected String getCollectionName() {
        return MongoDBCollection.ThreatDetection.ARCHIVED_MALICIOUS_EVENTS;
    }

    @Override
    protected Class<Document> getClassType() {
        return Document.class;
    }

    public MongoCollection<Document> getCollection(String accountId) {
        return super.getCollection(accountId);
    }

    /**
     * Bulk insert documents into the archived collection.
     * Used for archiving old malicious events.
     */
    public void bulkInsert(String accountId, List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        List<WriteModel<Document>> writes = new java.util.ArrayList<>(documents.size());
        for (Document doc : documents) {
            writes.add(new InsertOneModel<>(doc));
        }
        try {
            getCollection(accountId).bulkWrite(writes, new BulkWriteOptions().ordered(false));
        } catch (MongoBulkWriteException e) {
            throw new RuntimeException("Failed to bulk insert archived events for account " + accountId, e);
        }
    }
}

