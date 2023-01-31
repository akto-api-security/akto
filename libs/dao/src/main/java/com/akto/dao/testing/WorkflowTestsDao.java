package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.testing.WorkflowTest;

public class WorkflowTestsDao extends AccountsContextDao<WorkflowTest> {

    public static final WorkflowTestsDao instance = new WorkflowTestsDao();

    @Override
    public String getCollName() {
        return "workflow_tests";
    }

    @Override
    public Class<WorkflowTest> getClassT() {
        return WorkflowTest.class;
    }
    
}
