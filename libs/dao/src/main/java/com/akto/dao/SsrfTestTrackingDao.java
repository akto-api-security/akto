package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.SsrfTestTracking;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.conversions.Bson;

/**
 * DAO for managing SSRF test tracking entries in the common database.
 * 
 * This DAO handles:
 * - Storing UUID mappings for SSRF test execution
 * - Tracking when SSRF URLs are hit
 * - Automatic cleanup via TTL index (7 days)
 * 
 * Collection: ssrf_test_tracking (in common database)
 */
public class SsrfTestTrackingDao extends CommonContextDao<SsrfTestTracking> {
    
    public static final SsrfTestTrackingDao instance = new SsrfTestTrackingDao();
    
    private SsrfTestTrackingDao() {}
    
    @Override
    public String getCollName() {
        return "ssrf_test_tracking";
    }
    
    @Override
    public Class<SsrfTestTracking> getClassT() {
        return SsrfTestTracking.class;
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
        Bson ttlIndex = Indexes.ascending(SsrfTestTracking.EXPIRES_AT);
        IndexOptions ttlOptions = new IndexOptions()
                .name("expiresAt_ttl")
                .expireAfter(0L, java.util.concurrent.TimeUnit.SECONDS);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), ttlIndex, ttlOptions);
        
        // Unique index on uuid for fast lookups and ensuring UUID uniqueness
        Bson uuidIndex = Indexes.ascending(SsrfTestTracking.UUID);
        IndexOptions uuidIndexOptions = new IndexOptions()
                .name("uuid_1")
                .unique(true);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), uuidIndex, uuidIndexOptions);
    }
    
    /**
     * Finds an SSRF test tracking entry by UUID.
     * 
     * @param uuid The UUID to search for
     * @return SsrfTestTracking entry if found, null otherwise
     */
    public SsrfTestTracking findByUuid(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return null;
        }
        return instance.findOne(Filters.eq(SsrfTestTracking.UUID, uuid));
    }
    
    /**
     * Marks a URL as hit for the given UUID.
     * Updates the urlHit flag and updatedAt timestamp.
     * 
     * @param uuid The UUID of the SSRF test tracking entry to update
     * @return true if the entry was found and updated, false otherwise
     */
    public boolean markUrlHit(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return false;
        }
        
        SsrfTestTracking mapping = findByUuid(uuid);
        if (mapping != null) {
            mapping.setUrlHit(true);
            mapping.setUpdatedAt(Context.now());
            instance.replaceOne(Filters.eq(SsrfTestTracking.UUID, uuid), mapping);
            return true;
        }
        return false;
    }
}
