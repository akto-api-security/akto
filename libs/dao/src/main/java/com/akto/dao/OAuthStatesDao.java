package com.akto.dao;

import com.akto.dto.OAuthState;
import com.mongodb.client.model.CreateCollectionOptions;

public class OAuthStatesDao extends CommonContextDao<OAuthState> {

    public static final OAuthStatesDao instance = new OAuthStatesDao();

    private OAuthStatesDao() {}

    @Override
    public String getCollName() {
        return "oauth_states";
    }

    @Override
    public Class<OAuthState> getClassT() {
        return OAuthState.class;
    }

    public void createIndicesIfAbsent() {
        createCollectionIfAbsent(getDBName(), getCollName(), new CreateCollectionOptions());
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{OAuthState.NONCE}, false);
    }
}
