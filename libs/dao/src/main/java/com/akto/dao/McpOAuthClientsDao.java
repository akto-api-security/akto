package com.akto.dao;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.bson.conversions.Bson;

import com.akto.dto.McpOAuthClient;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;

public class McpOAuthClientsDao extends CommonContextDao<McpOAuthClient> {

    public static final McpOAuthClientsDao instance = new McpOAuthClientsDao();

    private static final long UNUSED_CLIENT_TTL_DAYS = 90L;

    private McpOAuthClientsDao() {}

    @Override
    public String getCollName() {
        return "mcp_oauth_clients";
    }

    @Override
    public Class<McpOAuthClient> getClassT() {
        return McpOAuthClient.class;
    }

    // Unused registrations clean themselves up
    public void createIndicesIfAbsent() {
        // Create the collection first, or the TTL index is silently skipped
        createCollectionIfAbsent(getDBName(), getCollName(), new CreateCollectionOptions());
        Bson ttlIndex = Indexes.ascending(McpOAuthClient.LAST_USED_AT);
        IndexOptions ttlOptions = new IndexOptions()
                .name("lastUsedAt_ttl")
                .expireAfter(UNUSED_CLIENT_TTL_DAYS, TimeUnit.DAYS);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), ttlIndex, ttlOptions);
    }

    public McpOAuthClient findById(String clientId) {
        return findOne(Filters.eq("_id", clientId));
    }

    public void touch(String clientId) {
        updateOne(Filters.eq("_id", clientId), Updates.set(McpOAuthClient.LAST_USED_AT, new Date()));
    }
}
