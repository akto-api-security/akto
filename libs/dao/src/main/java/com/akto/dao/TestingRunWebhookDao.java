package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.TestingRunWebhook;
import com.mongodb.client.model.Filters;
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
    
    /**
     * Finds a test run webhook tracking entry by UUID.
     * 
     * @param uuid The UUID to search for
     * @return TestingRunWebhook entry if found, null otherwise
     */
    public TestingRunWebhook findByUuid(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return null;
        }
        return instance.findOne(Filters.eq(TestingRunWebhook.UUID, uuid));
    }
    
    /**
     * Marks a URL as hit for the given UUID.
     * Updates the urlHit flag and updatedAt timestamp.
     * 
     * @param uuid The UUID of the test run webhook tracking entry to update
     * @return true if the entry was found and updated, false otherwise
     */
    public boolean markUrlHit(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return false;
        }
        
        TestingRunWebhook mapping = findByUuid(uuid);
        if (mapping != null) {
            mapping.setUrlHit(true);
            mapping.setUpdatedAt(Context.now());
            instance.replaceOne(Filters.eq(TestingRunWebhook.UUID, uuid), mapping);
            return true;
        }
        return false;
    }
}
