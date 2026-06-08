package com.akto.dao.nhi_governance;

import com.akto.dao.AccountsContextDaoWithContextSource;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.nhi_governance.NhiPolicy;

public class NhiPolicyDao extends AccountsContextDaoWithContextSource<NhiPolicy> {

    public static NhiPolicyDao instance = new NhiPolicyDao();

    @Override
    public String getCollName() {
        return NhiPolicy.COLLECTION_NAME;
    }

    @Override
    public Class<NhiPolicy> getClassT() {
        return NhiPolicy.class;
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

        String[] fieldNames = {NhiPolicy.CONTEXT_SOURCE};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{NhiPolicy.STATUS};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{NhiPolicy.POLICY_NAME};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{NhiPolicy.CONTEXT_SOURCE, NhiPolicy.STATUS};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);
    }
}
