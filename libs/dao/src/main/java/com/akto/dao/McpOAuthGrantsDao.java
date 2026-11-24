package com.akto.dao;

import java.util.concurrent.TimeUnit;

import org.bson.conversions.Bson;

import com.akto.dto.McpOAuthGrant;
import com.mongodb.client.model.CreateCollectionOptions;
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
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                Indexes.ascending(McpOAuthGrant.TYPE, McpOAuthGrant.ACCOUNT_ID), new IndexOptions().name("type_1_accountId_1"));
    }
}
