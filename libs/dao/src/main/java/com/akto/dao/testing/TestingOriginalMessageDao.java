package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.testing.TestingOriginalMessage;

public class TestingOriginalMessageDao extends AccountsContextDao<TestingOriginalMessage> {

    public static final TestingOriginalMessageDao instance = new TestingOriginalMessageDao();

    @Override
    public String getCollName() {
        return "testing_original_message";
    }

    @Override
    public Class<TestingOriginalMessage> getClassT() {
        return TestingOriginalMessage.class;
    }
}
