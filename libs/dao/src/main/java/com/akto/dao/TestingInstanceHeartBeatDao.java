package com.akto.dao;

import java.util.List;

import org.bson.conversions.Bson;

import com.akto.dao.context.Context;
import com.akto.dto.TestingInstanceHeartBeat;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public class TestingInstanceHeartBeatDao extends CommonContextDao<TestingInstanceHeartBeat> {

    public static final TestingInstanceHeartBeatDao instance = new TestingInstanceHeartBeatDao();

    public void createIndexIfAbsent() {
        String[] fieldNames = {"instanceId"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);
    }

    public boolean isTestEligibleForInstance(String testRunId) {
        
        Bson instanceFilter = Filters.eq("testRunId", testRunId);
        TestingInstanceHeartBeat testingInstanceHeartBeat = instance.findOne(instanceFilter);
        if (testingInstanceHeartBeat == null) {
            return true;
        }
        
        if (testingInstanceHeartBeat.getTs() > Context.now() - 10 * 60) {
            return false;
        }
        return true;
    }

    public void setTestingRunId(String testingInstanceId, String testRunId) {
        Bson instanceFilter = Filters.eq("instanceId", testingInstanceId);
        instance.updateOne(instanceFilter, 
            Updates.combine(Updates.set("testRunId", testRunId)));
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
