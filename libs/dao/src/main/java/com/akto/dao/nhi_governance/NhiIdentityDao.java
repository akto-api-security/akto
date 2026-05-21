package com.akto.dao.nhi_governance;

import com.akto.dao.AccountsContextDaoWithContextSource;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.nhi_governance.NhiIdentity;

public class NhiIdentityDao extends AccountsContextDaoWithContextSource<NhiIdentity> {

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
    }
}
