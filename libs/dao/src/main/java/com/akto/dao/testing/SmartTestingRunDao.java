package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.testing.SmartTestingRun;

public class SmartTestingRunDao extends AccountsContextDao<SmartTestingRun> {

    public static final SmartTestingRunDao instance = new SmartTestingRunDao();

    @Override
    public String getCollName() {
        return "smart_testing_runs";
    }

    @Override
    public Class<SmartTestingRun> getClassT() {
        return SmartTestingRun.class;
    }

    public void createIndicesIfAbsent() {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{SmartTestingRun.RUN_KEY}, true);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{SmartTestingRun.TESTING_RUN_RESULT_SUMMARY_ID, SmartTestingRun.STARTED_TS}, false);
    }
}
