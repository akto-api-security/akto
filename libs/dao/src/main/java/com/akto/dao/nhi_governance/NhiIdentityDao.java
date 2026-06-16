package com.akto.dao.nhi_governance;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.nhi_governance.NhiIdentity;

public class NhiIdentityDao extends AccountsContextDao<NhiIdentity> {

    public static final NhiIdentityDao instance = new NhiIdentityDao();
    public static final String COLLECTION_NAME = "nhi_identities";

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<NhiIdentity> getClassT() {
        return NhiIdentity.class;
    }

    public void createIndicesIfAbsent() {
        // Natural-key index used for upserts: (deviceId, source, identityName)
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[]{NhiIdentity.DEVICE_ID, NhiIdentity.SOURCE, NhiIdentity.IDENTITY_NAME}, true);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{NhiIdentity.STATUS}, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{NhiIdentity.RISK_LEVEL}, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{NhiIdentity.LAST_SEEN_AT}, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{NhiIdentity.AGENT_NAME}, false);
    }
}
