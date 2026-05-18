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

        String[] fieldNames = {NhiViolation.CONTEXT_SOURCE};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{NhiViolation.STATUS};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{NhiViolation.POLICY_IDS};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{NhiViolation.CONTEXT_SOURCE, NhiViolation.STATUS};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);
    }
}
