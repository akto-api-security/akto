package com.akto.dao;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.bson.conversions.Bson;

import com.akto.dto.McpOAuthGrant;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

public class McpOAuthGrantsDao extends CommonContextDao<McpOAuthGrant> {

    public static final McpOAuthGrantsDao instance = new McpOAuthGrantsDao();

    private McpOAuthGrantsDao() {}

    @Override
    public String getCollName() {
        return "mcp_oauth_grants";
    }

    @Override
    public Class<McpOAuthGrant> getClassT() {
        return McpOAuthGrant.class;
    }

    public void createIndicesIfAbsent() {
        // Create the collection first, or the TTL index is silently skipped
        createCollectionIfAbsent(getDBName(), getCollName(), new CreateCollectionOptions());
        Bson ttlIndex = Indexes.ascending(McpOAuthGrant.EXPIRES_AT);
        IndexOptions ttlOptions = new IndexOptions()
                .name("expiresAt_ttl")
                .expireAfter(0L, TimeUnit.SECONDS);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), ttlIndex, ttlOptions);
    }

    public void insert(McpOAuthGrant grant) {
        insertOne(grant);
    }

    // Atomic single-use; expiry is checked here because the TTL sweeper lags
    public McpOAuthGrant consume(String secretHash, String type) {
        return getMCollection().findOneAndDelete(Filters.and(
                Filters.eq(McpOAuthGrant.ID, secretHash),
                Filters.eq(McpOAuthGrant.TYPE, type),
                Filters.gt(McpOAuthGrant.EXPIRES_AT, new Date())));
    }

    // Non-destructive read for the consent page
    public McpOAuthGrant find(String secretHash, String type) {
        return findOne(Filters.and(
                Filters.eq(McpOAuthGrant.ID, secretHash),
                Filters.eq(McpOAuthGrant.TYPE, type),
                Filters.gt(McpOAuthGrant.EXPIRES_AT, new Date())));
    }
}
