package com.akto.dao.nhi_governance;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.nhi_governance.NhiViolation;

public class NhiViolationDao extends AccountsContextDao<NhiViolation> {

    public static NhiViolationDao instance = new NhiViolationDao();

    @Override
    public String getCollName() {
        return NhiViolation.COLLECTION_NAME;
    }

    @Override
    public Class<NhiViolation> getClassT() {
        return NhiViolation.class;
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

        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{NhiViolation.CONTEXT_SOURCE}, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{NhiViolation.STATUS}, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{NhiViolation.POLICY_IDS}, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{NhiViolation.CONTEXT_SOURCE, NhiViolation.STATUS}, false);
    }
}
