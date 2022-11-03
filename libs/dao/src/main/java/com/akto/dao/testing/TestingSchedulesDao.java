package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.testing.TestingSchedule;

public class TestingSchedulesDao extends AccountsContextDao<TestingSchedule> {

    public static final TestingSchedulesDao instance = new TestingSchedulesDao();

    @Override
    public String getCollName() {
        return "testing_schedules";
    }
    
    @Override
    public Class<TestingSchedule> getClassT() {
        return TestingSchedule.class;
    }
}
