package com.akto.dao;

import com.akto.dto.AuthToken;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

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
}
