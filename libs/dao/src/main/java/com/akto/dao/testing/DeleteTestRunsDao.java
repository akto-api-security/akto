package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.testing.DeleteTestRuns;

public class DeleteTestRunsDao extends AccountsContextDao<DeleteTestRuns> {

    public static final DeleteTestRunsDao instance = new DeleteTestRunsDao();

    @Override
    public String getCollName() {
        return "delete_test_runs_info";
    }

    @Override
    public Class<DeleteTestRuns> getClassT() {
        return DeleteTestRuns.class;
    }
}
