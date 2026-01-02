package com.akto.threat.backend.utils;

import com.akto.threat.backend.dao.MaliciousEventDao;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.*;

public class ThreatUtils {

    private static final List<String> ENDPOINT_POLICY_FILTER_IDS = Arrays.asList(
        "MCPGuardrails", "AuditPolicy", "MCPMaliciousComponent"
    );

    public static Document buildSimpleContextFilter(String contextSource) {
        if (contextSource == null || contextSource.isEmpty()) {
            contextSource = CONTEXT_SOURCE.API.name();
        }

        Document contextSourceFilter = new Document("contextSource", contextSource);

        Document legacyFilter = buildLegacyContextFilter(contextSource);

        return new Document("$or", Arrays.asList(contextSourceFilter, legacyFilter));
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
        requiredIndexes.put("detectedAt_1_contextSource_1_filterId_1", Indexes.compoundIndex(Indexes.ascending("detectedAt"), Indexes.ascending("contextSource"), Indexes.ascending("filterId")));
        
        // Simple indices for distinct operations to avoid COLLSCAN
        requiredIndexes.put("idx_host", Indexes.ascending("host"));
        requiredIndexes.put("idx_latestApiEndpoint", Indexes.ascending("latestApiEndpoint"));
        
        // Compound indices for time-range aggregations with contextSource filter and additional fields
        // Note: All queries filter by detectedAt range + contextSource, so these indices are optimized for that pattern
        requiredIndexes.put("detectedAt_1_contextSource_1_successfulExploit_1", Indexes.compoundIndex(Indexes.ascending("detectedAt"), Indexes.ascending("contextSource"), Indexes.ascending("successfulExploit")));
        requiredIndexes.put("detectedAt_1_contextSource_1_severity_1", Indexes.compoundIndex(Indexes.ascending("detectedAt"), Indexes.ascending("contextSource"), Indexes.ascending("severity")));
        requiredIndexes.put("detectedAt_1_contextSource_1_subCategory_1", Indexes.compoundIndex(Indexes.ascending("detectedAt"), Indexes.ascending("contextSource"), Indexes.ascending("subCategory")));
        requiredIndexes.put("detectedAt_1_contextSource_1_status_1_label_1", Indexes.compoundIndex(Indexes.ascending("detectedAt"), Indexes.ascending("contextSource"), Indexes.ascending("status"), Indexes.ascending("label")));
        requiredIndexes.put("detectedAt_1_contextSource_1_category_1_subCategory_1", Indexes.compoundIndex(Indexes.ascending("detectedAt"), Indexes.ascending("contextSource"), Indexes.ascending("category"), Indexes.ascending("subCategory")));
        
        requiredIndexes.put("idx_detected_context_actor_country", Indexes.compoundIndex(Indexes.descending("detectedAt"), Indexes.ascending("contextSource"), Indexes.ascending("filterId"), Indexes.ascending("actor"), Indexes.ascending("country")));

        for (Map.Entry<String, Bson> entry : requiredIndexes.entrySet()) {
            if (!existingIndexes.contains(entry.getKey())) {
                collection.createIndex(entry.getValue(), new IndexOptions().name(entry.getKey()));
            }
        }
    }

}
