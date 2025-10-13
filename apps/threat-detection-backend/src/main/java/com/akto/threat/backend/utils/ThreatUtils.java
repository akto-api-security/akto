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
        
        // Indexes for fetchTopNData: top APIs aggregation (group by endpoint+method, filter by detectedAt)
        requiredIndexes.put("detectedAt_1_latestApiEndpoint_1_latestApiMethod_1", 
            Indexes.ascending("detectedAt", "latestApiEndpoint", "latestApiMethod"));
        
        // Indexes for fetchTopNData: top hosts aggregation (group by host, filter by detectedAt)
        requiredIndexes.put("detectedAt_1_host_1", 
            Indexes.compoundIndex(Indexes.ascending("detectedAt"), Indexes.ascending("host")));
        
        // Indexes for getDailyActorCounts: filter by filterId+detectedAt, group by actor+severity
        requiredIndexes.put("filterId_1_detectedAt_1_actor_1", 
            Indexes.ascending("filterId", "detectedAt", "actor"));
        
        requiredIndexes.put("filterId_1_detectedAt_1_severity_1", 
            Indexes.ascending("filterId", "detectedAt", "severity"));
        
        // Indexes for getSeverityWiseCount: filter by severity+detectedAt+filterId (uses countDocuments)
        requiredIndexes.put("severity_1_detectedAt_1_filterId_1", 
            Indexes.ascending("severity", "detectedAt", "filterId"));

        for (Map.Entry<String, Bson> entry : requiredIndexes.entrySet()) {
            if (!existingIndexes.contains(entry.getKey())) {
                coll.createIndex(entry.getValue(), new IndexOptions().name(entry.getKey()));
            }
        }
    }

}
