package com.akto.dao;

import com.akto.dto.TestingRunWebhook;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.conversions.Bson;

/**
 * DAO for managing test run webhook tracking entries in the common database.
 * 
 * This DAO handles:
 * - Storing UUID mappings for test execution webhook callbacks
 * - Tracking when webhook URLs are hit
 * - Automatic cleanup via TTL index (7 days)
 * 
 * Collection: testing_run_webhook (in common database)
 */
public class TestingRunWebhookDao extends CommonContextDao<TestingRunWebhook> {
    
    public static final TestingRunWebhookDao instance = new TestingRunWebhookDao();
    
    private TestingRunWebhookDao() {}
    
    @Override
    public String getCollName() {
        return "testing_run_webhook";
    }
    
    @Override
    public Class<TestingRunWebhook> getClassT() {
        return TestingRunWebhook.class;
    }
    
    /**
     * Creates indexes for the collection:
     * 1. TTL index on expiresAt (7 days) - automatically deletes documents after expiration
     * 2. Unique index on uuid for fast lookups and ensuring UUID uniqueness
     * 
     * This method should be called during application initialization.
     * See DaoInit.createIndices() for integration.
     */
    public void createIndicesIfAbsent() {
        // TTL Index on expiresAt - auto-deletes documents when expiresAt date is reached
        // Setting expireAfter to 0 means MongoDB will delete documents when the expiresAt date field value is reached
        Bson ttlIndex = Indexes.ascending(TestingRunWebhook.EXPIRES_AT);
        IndexOptions ttlOptions = new IndexOptions()
                .name("expiresAt_ttl")
                .expireAfter(0L, java.util.concurrent.TimeUnit.SECONDS);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), ttlIndex, ttlOptions);
        
        // Unique index on uuid for fast lookups and ensuring UUID uniqueness
        Bson uuidIndex = Indexes.ascending(TestingRunWebhook.UUID);
        IndexOptions uuidIndexOptions = new IndexOptions()
                .name("uuid_1")
                .unique(true);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), uuidIndex, uuidIndexOptions);
    }
}
