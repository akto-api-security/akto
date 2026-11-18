package com.akto.dao;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                Indexes.ascending(McpOAuthGrant.TYPE, McpOAuthGrant.ACCOUNT_ID), new IndexOptions().name("type_1_accountId_1"));
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

    // Clients that hold a refresh token in this account
    public List<String> refreshClientIds(int accountId) {
        return getMCollection().distinct(McpOAuthGrant.CLIENT_ID, refreshFilter(accountId), String.class).into(new ArrayList<>());
    }

    public long deleteRefreshForClients(int accountId, List<String> clientIds) {
        return getMCollection().deleteMany(Filters.and(refreshFilter(accountId), Filters.in(McpOAuthGrant.CLIENT_ID, clientIds))).getDeletedCount();
    }

    private static Bson refreshFilter(int accountId) {
        return Filters.and(Filters.eq(McpOAuthGrant.TYPE, McpOAuthGrant.TYPE_REFRESH), Filters.eq(McpOAuthGrant.ACCOUNT_ID, accountId));
    }
}
