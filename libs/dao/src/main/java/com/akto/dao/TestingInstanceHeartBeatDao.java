package com.akto.dao;

import java.util.List;

import org.bson.conversions.Bson;

import com.akto.dao.context.Context;
import com.akto.dto.TestingInstanceHeartBeat;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

public class TestingInstanceHeartBeatDao extends CommonContextDao<TestingInstanceHeartBeat> {

    public static final TestingInstanceHeartBeatDao instance = new TestingInstanceHeartBeatDao();

    public void createIndexIfAbsent() {
        String[] fieldNames = {"instanceId", "ts"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);
    }

    public boolean isTestEligibleForInstance(String testingInstanceId) {

        // check for old tests
        if (testingInstanceId == null || testingInstanceId.length() == 0) {
            return true;
        }
        
        Bson instanceFilter = Filters.eq("instanceId", testingInstanceId);
        Bson timeFilter = Filters.gte("ts", Context.now() - 10 * 60);
        Bson combinedFilter = Filters.and(instanceFilter, timeFilter);

        TestingInstanceHeartBeat testingInstanceHeartBeat = instance.findOne(combinedFilter);
        return testingInstanceHeartBeat == null;
    }

    @Override
    public String getCollName() {
        return "testing_instance_heart_beat";
    }

    @Override
    public Class<TestingInstanceHeartBeat> getClassT() {
        return TestingInstanceHeartBeat.class;
    }

    public List<TestingInstanceHeartBeat> getAllAccounts() {
        return findAll(new BasicDBObject());
    }
    
}
