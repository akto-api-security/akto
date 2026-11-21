package com.akto.dao;

import com.akto.dto.AuthToken;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class AuthTokensDao extends AccountsContextDao<AuthToken> {

    public static final AuthTokensDao instance = new AuthTokensDao();

    private AuthTokensDao() {}

    @Override
    public String getCollName() {
        return "auth_tokens";
    }

    @Override
    public Class<AuthToken> getClassT() {
        return AuthToken.class;
    }

    public void createIndicesIfAbsent() {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{ AuthToken.STATUS }, false);
        // Create the collection first, or the TTL index is silently skipped
        createCollectionIfAbsent(getDBName(), getCollName(), new CreateCollectionOptions());
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), Indexes.ascending(AuthToken.DELETE_AT),
                new IndexOptions().name("deleteAt_ttl").expireAfter(0L, TimeUnit.SECONDS));
    }

    // Ids of revoked tokens that have not expired yet; an expired token is rejected anyway.
    public Set<String> fetchRevokedIds(int now) {
        Set<String> ids = new HashSet<>();
        for (AuthToken token : findAll(
                Filters.and(
                        Filters.eq(AuthToken.STATUS, AuthToken.STATUS_REVOKED),
                        Filters.or(Filters.eq(AuthToken.EXPIRES_AT, 0), Filters.gt(AuthToken.EXPIRES_AT, now))
                ),
                Projections.include(AuthToken.ID))) {
            ids.add(token.getId());
        }
        return ids;
    }
}
