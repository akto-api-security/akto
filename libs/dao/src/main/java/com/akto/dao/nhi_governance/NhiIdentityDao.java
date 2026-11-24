package com.akto.dao.nhi_governance;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.nhi_governance.NhiIdentity;

public class NhiIdentityDao extends AccountsContextDao<NhiIdentity> {

    public static NhiIdentityDao instance = new NhiIdentityDao();

    @Override
    public String getCollName() {
        return NhiIdentity.COLLECTION_NAME;
    }

    @Override
    public Class<NhiIdentity> getClassT() {
        return NhiIdentity.class;
    }

    public void createIndicesIfAbsent() {
        boolean exists = false;
        for (String col : clients[0].getDatabase(Context.accountId.get() + "").listCollectionNames()) {
            if (getCollName().equalsIgnoreCase(col)) {
                exists = true;
                break;
            }
        }

        if (!exists) {
            clients[0].getDatabase(Context.accountId.get() + "").createCollection(getCollName());
        }

        String[] fieldNames = {NhiIdentity.CONTEXT_SOURCE};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{NhiIdentity.STATUS};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{NhiIdentity.IDENTITY_NAME};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{NhiIdentity.RISK_LEVEL};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        // compound: contextSource + status — most common combined filter
        fieldNames = new String[]{NhiIdentity.CONTEXT_SOURCE, NhiIdentity.STATUS};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);
    

        // union with the indexes defined by the mini-runtime build; (deviceId, source, identityName) is its upsert key
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{NhiIdentity.DEVICE_ID, NhiIdentity.SOURCE, NhiIdentity.IDENTITY_NAME}, true);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{NhiIdentity.LAST_SEEN_AT}, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{NhiIdentity.AGENT_NAME}, false);
    }

    public static class BatchResult {
        public int upserted;
        public int skipped;
    }
}
