package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.testing.WorkflowTestResult;

public class WorkflowTestResultsDao extends AccountsContextDao<WorkflowTestResult> {

    public static final WorkflowTestResultsDao instance = new WorkflowTestResultsDao();

    @Override
    public String getCollName() {
        return "workflow_test_results";
    }

    @Override
    public Class<WorkflowTestResult> getClassT() {
        return WorkflowTestResult.class;
    }
}
