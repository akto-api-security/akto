package com.akto.threat.backend.utils;

import com.akto.threat.backend.dao.MaliciousEventDao;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.*;

public class ThreatUtils {

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
