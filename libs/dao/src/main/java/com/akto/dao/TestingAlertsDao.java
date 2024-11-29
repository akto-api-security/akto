package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.TestingAlerts;

public class TestingAlertsDao extends CommonContextDao<TestingAlerts>{
    public static final TestingAlertsDao instance = new TestingAlertsDao();

    public void createIndicesIfAbsent() {

        boolean exists = false;
        for (String col: clients[0].getDatabase(Context.accountId.get()+"").listCollectionNames()){
            if (getCollName().equalsIgnoreCase(col)){
                exists = true;
                break;
            }
        };

        if (!exists) {
            clients[0].getDatabase(Context.accountId.get()+"").createCollection(getCollName());
        }

        String[] fieldNames = {"updatedTs"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

        fieldNames = new String[] {"testRunId"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);
    }


    @Override
    public String getCollName() {
        return "testing_alerts";
    }

    @Override
    public Class<TestingAlerts> getClassT() {
        return TestingAlerts.class;
    }
}

