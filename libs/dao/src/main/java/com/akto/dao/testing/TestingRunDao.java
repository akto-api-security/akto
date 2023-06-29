package com.akto.dao.testing;

import org.bson.conversions.Bson;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRun.State;
import com.mongodb.client.model.Filters;

public class TestingRunDao extends AccountsContextDao<TestingRun> {

    public static final TestingRunDao instance = new TestingRunDao();

    public Bson getTestRunningOrScheduledFilter(){
                return Filters.or(
            Filters.eq(TestingRun.STATE, State.SCHEDULED),
            Filters.eq(TestingRun.STATE, State.RUNNING)
        );
    }

    @Override
    public String getCollName() {
        return "testing_run";
    }

    @Override
    public Class<TestingRun> getClassT() {
        return TestingRun.class;
    }
}
