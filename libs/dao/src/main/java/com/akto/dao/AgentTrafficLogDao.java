package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.AgentTrafficLog;
import com.akto.util.DbMode;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Projections;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DAO for AgentTrafficLog collection.
 * Stores raw agent traffic data for training purposes.
 */
public class AgentTrafficLogDao extends AccountsContextDao<AgentTrafficLog> {

    public static final String COLLECTION_NAME = "agent_traffic_logs";
    public static final AgentTrafficLogDao instance = new AgentTrafficLogDao();

    public static final int maxDocuments = 100_000;
    public static final int sizeInBytes = 100_000_000; // 100MB - standard size for log collections

    private AgentTrafficLogDao() {}

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<AgentTrafficLog> getClassT() {
        return AgentTrafficLog.class;
    }

    /**
     * Creates indexes for the collection:
     * 1. TTL index on expiresAt (7 days)
     * 2. Compound index on (apiCollectionId, timestamp, isBlocked) for all query patterns
     * 
     * Index order optimized for cardinality: high (apiCollectionId) -> high (timestamp) -> low (isBlocked)
     */
    public void createIndicesIfAbsent() {
        boolean exists = false;
        String dbName = Context.accountId.get() + "";
        MongoDatabase db = clients[0].getDatabase(dbName);
        
        for (String col : db.listCollectionNames()) {
            if (getCollName().equalsIgnoreCase(col)) {
                exists = true;
                break;
            }
        }

        if (!exists) {
            if (DbMode.allowCappedCollections()) {
                db.createCollection(getCollName(), new CreateCollectionOptions().capped(true).maxDocuments(maxDocuments).sizeInBytes(sizeInBytes));
            } else {
                db.createCollection(getCollName());
            }
        }

        // TTL Index on expiresAt - auto-deletes documents when expiresAt date is reached
        // Setting expireAfter to 0 means MongoDB will delete documents when the expiresAt date field value is reached
        Bson ttlIndex = Indexes.ascending(AgentTrafficLog.EXPIRES_AT);
        IndexOptions ttlOptions = new IndexOptions()
                .name("expiresAt_ttl")
                .expireAfter(0L, java.util.concurrent.TimeUnit.SECONDS);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), ttlIndex, ttlOptions);

        // Compound index optimized for cardinality: high -> high -> low
        // Order: apiCollectionId (high cardinality) -> timestamp (high cardinality) -> isBlocked (low cardinality)
        String[] fieldNames = new String[]{
                AgentTrafficLog.API_COLLECTION_ID,
                AgentTrafficLog.TIMESTAMP,
                AgentTrafficLog.IS_BLOCKED
        };
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);
    }

    /**
     * Get unique endpoints (url + method combinations) that have logs with request payloads for a collection.
     * 
     * @param apiCollectionId The API collection ID
     * @return Set of endpoint pairs as arrays: [url, method]
     */
    public Set<String[]> getUniqueEndpoints(int apiCollectionId) {
        Set<String[]> endpoints = new HashSet<>();
        
        try {
            List<Bson> pipeline = new ArrayList<>();
            pipeline.add(Aggregates.match(Filters.and(
                Filters.eq(AgentTrafficLog.API_COLLECTION_ID, apiCollectionId),
                Filters.exists(AgentTrafficLog.REQUEST_PAYLOAD),
                Filters.ne(AgentTrafficLog.REQUEST_PAYLOAD, null),
                Filters.ne(AgentTrafficLog.REQUEST_PAYLOAD, ""),
                Filters.exists(AgentTrafficLog.URL),
                Filters.exists(AgentTrafficLog.METHOD)
            )));
            pipeline.add(Aggregates.group(
                new BasicDBObject("url", "$" + AgentTrafficLog.URL)
                    .append("method", "$" + AgentTrafficLog.METHOD)
            ));
            
            MongoCursor<BasicDBObject> cursor = getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
            while (cursor.hasNext()) {
                BasicDBObject group = cursor.next();
                BasicDBObject id = (BasicDBObject) group.get("_id");
                String url = id.getString("url");
                String method = id.getString("method");
                if (url != null && method != null) {
                    endpoints.add(new String[]{url, method});
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching unique endpoints for collection " + apiCollectionId + ": " + e.getMessage());
            e.printStackTrace();
        }
        
        return endpoints;
    }

    /**
     * Fetch AgentTrafficLog records for a specific collection and endpoint (url + method).
     * 
     * @param apiCollectionId The API collection ID
     * @param url The URL of the endpoint
     * @param method The HTTP method of the endpoint
     * @return List of AgentTrafficLog records for the specified collection and endpoint
     */
    public List<AgentTrafficLog> fetchLogsByCollectionAndEndpoint(int apiCollectionId, String url, String method) {
        List<AgentTrafficLog> logs = new ArrayList<>();
        
        try {
            Bson filter = Filters.and(
                Filters.eq(AgentTrafficLog.API_COLLECTION_ID, apiCollectionId),
                Filters.eq(AgentTrafficLog.URL, url),
                Filters.eq(AgentTrafficLog.METHOD, method),
                Filters.exists(AgentTrafficLog.REQUEST_PAYLOAD),
                Filters.ne(AgentTrafficLog.REQUEST_PAYLOAD, null),
                Filters.ne(AgentTrafficLog.REQUEST_PAYLOAD, "")
            );
            
            logs = findAll(filter, 0, 0, null, 
                Projections.include(AgentTrafficLog.REQUEST_PAYLOAD, AgentTrafficLog.URL, AgentTrafficLog.METHOD));
            
        } catch (Exception e) {
            // Log error but return empty list
            System.err.println("Error fetching logs for collection " + apiCollectionId + 
                ", endpoint " + url + " " + method + ": " + e.getMessage());
            e.printStackTrace();
        }
        
        return logs;
    }
}
