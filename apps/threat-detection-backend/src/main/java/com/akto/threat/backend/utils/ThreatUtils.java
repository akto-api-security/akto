package com.akto.threat.backend.utils;

import com.akto.threat.backend.constants.MongoDBCollection;
import com.mongodb.client.*;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.*;

public class ThreatUtils {

    public static void createIndexIfAbsent(String accountId, MongoClient mongoClient) {
        MongoDatabase database = mongoClient.getDatabase(accountId);

        MongoCursor<String> stringMongoCursor = database.listCollectionNames().cursor();
        boolean maliciousEventCollectionExists = false;

        while (stringMongoCursor.hasNext()) {
            String collectionName = stringMongoCursor.next();
            if(collectionName.equalsIgnoreCase(MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS)) {
                maliciousEventCollectionExists = true;
                break;
            }
        }

        if (!maliciousEventCollectionExists) {
            database.createCollection(MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS);
        }

        MongoCollection<Document> coll = database.getCollection(MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS, Document.class);

        Set<String> existingIndexes = new HashSet<>();
        try (MongoCursor<Document> cursor = coll.listIndexes().iterator()) {
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
                coll.createIndex(entry.getValue(), new IndexOptions().name(entry.getKey()));
            }
        }
    }

}
