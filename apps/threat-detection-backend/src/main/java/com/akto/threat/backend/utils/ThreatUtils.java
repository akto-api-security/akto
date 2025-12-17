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

    // Policy filterIds that belong to Endpoint context
    private static final List<String> ENDPOINT_POLICY_FILTER_IDS = Arrays.asList(
        "MCPGuardrails", "AuditPolicy", "MCPMaliciousComponent"
    );

    /**
     * Builds a context-aware filter condition for MongoDB queries.
     *
     * This method handles both new records (with contextSource field) and legacy records (without contextSource field):
     * - For records WITH contextSource: filters by matching contextSource from header
     * - For records WITHOUT contextSource: applies legacy filtering rules based on context
     *
     * @param contextSource The context source from x-context-source header (required)
     * @param latestAttackList Optional list of filterIds from UI (can be null or empty)
     * @return A Document representing the MongoDB query filter
     * @throws IllegalArgumentException if contextSource is null or empty
     */
    public static Document buildContextAwareFilter(String contextSource, List<String> latestAttackList) {
        if (contextSource == null || contextSource.isEmpty()) {
            throw new IllegalArgumentException("contextSource is required. Header x-context-source must be provided.");
        }

        // Build the filter for records WITH contextSource field (not null and matches)
        Document hasContextSourceFilter = new Document("$and", Arrays.asList(
            new Document("contextSource", new Document("$exists", true)),
            new Document("contextSource", new Document("$ne", null)),
            new Document("contextSource", contextSource)
        ));
        if (latestAttackList != null && !latestAttackList.isEmpty()) {
            hasContextSourceFilter.append("filterId", new Document("$in", latestAttackList));
        }

        // Build the filter for legacy records WITHOUT contextSource field
        Document legacyFilter = buildLegacyContextFilter(contextSource, latestAttackList);

        // Combine both filters using $or
        return new Document("$or", Arrays.asList(hasContextSourceFilter, legacyFilter));
    }

    /**
     * Builds filter for legacy records (records without contextSource field).
     *
     * Rules:
     * - API context: filterId NOT IN {MCPGuardrails, AuditPolicy, MCPMaliciousComponent}
     * - AGENTIC context: filterId IN {} (empty, so no legacy results)
     * - ENDPOINT context: filterId IN {MCPGuardrails, AuditPolicy, MCPMaliciousComponent}
     * - Other contexts (MCP, GEN_AI, DAST): filterId NOT IN {MCPGuardrails, AuditPolicy, MCPMaliciousComponent}
     */
    private static Document buildLegacyContextFilter(String contextSource, List<String> latestAttackList) {
        // Legacy records: contextSource is either null or doesn't exist
        Document nullOrNotExistsCondition = new Document("$or", Arrays.asList(
            new Document("contextSource", null),
            new Document("contextSource", new Document("$exists", false))
        ));

        String contextSourceUpper = contextSource.toUpperCase();
        Document filterIdCondition;

        switch (contextSourceUpper) {
            case "AGENTIC":
                // AGENTIC context: empty filterId list, so no legacy results
                // Add impossible condition
                filterIdCondition = new Document("filterId", new Document("$in", Collections.emptyList()));
                break;

            case "ENDPOINT":
                // ENDPOINT context: filterId IN {MCPGuardrails, AuditPolicy, MCPMaliciousComponent}
                List<String> endpointFilterIds = new ArrayList<>(ENDPOINT_POLICY_FILTER_IDS);
                if (latestAttackList != null && !latestAttackList.isEmpty()) {
                    // Intersection of latestAttackList and ENDPOINT_POLICY_FILTER_IDS
                    endpointFilterIds.retainAll(latestAttackList);
                }
                filterIdCondition = new Document("filterId", new Document("$in", endpointFilterIds));
                break;

            case "API":
            case "MCP":
            case "GEN_AI":
            case "DAST":
            default:
                // API and other contexts: filterId NOT IN {MCPGuardrails, AuditPolicy, MCPMaliciousComponent}
                if (latestAttackList != null && !latestAttackList.isEmpty()) {
                    // filterId must be in latestAttackList AND not in ENDPOINT_POLICY_FILTER_IDS
                    filterIdCondition = new Document("$and", Arrays.asList(
                        new Document("filterId", new Document("$in", latestAttackList)),
                        new Document("filterId", new Document("$nin", ENDPOINT_POLICY_FILTER_IDS))
                    ));
                } else {
                    filterIdCondition = new Document("filterId", new Document("$nin", ENDPOINT_POLICY_FILTER_IDS));
                }
                break;
        }

        // Combine both conditions: (contextSource is null or doesn't exist) AND (filterId condition)
        return new Document("$and", Arrays.asList(nullOrNotExistsCondition, filterIdCondition));
    }

    /**
     * Merges context-aware filter with existing MongoDB query conditions.
     *
     * This method takes an existing query Document and adds context-aware filtering to it.
     * Useful for both find() queries and aggregation pipeline $match stages.
     *
     * @param existingQuery The existing MongoDB query document (can be empty but not null)
     * @param contextSource The context source from x-context-source header (required)
     * @param latestAttackList Optional list of filterIds from UI (can be null or empty)
     * @return A Document with context-aware filter merged with existing conditions
     * @throws IllegalArgumentException if contextSource is null or empty
     */
    public static Document mergeContextFilter(Document existingQuery, String contextSource, List<String> latestAttackList) {
        Document contextFilter = buildContextAwareFilter(contextSource, latestAttackList);

        if (existingQuery == null || existingQuery.isEmpty()) {
            return contextFilter;
        }

        // Merge using $and to combine existing query with context filter
        return new Document("$and", Arrays.asList(existingQuery, contextFilter));
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
