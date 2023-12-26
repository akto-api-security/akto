package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.testing.DeleteRunsInfo;

public class DeleteRunsInfoDao extends AccountsContextDao<DeleteRunsInfo> {

    public static final DeleteRunsInfoDao instance = new DeleteRunsInfoDao();


    @Override
    public String getCollName() {
        return "delete_runs_info";
    }

    @Override
    public Class<DeleteRunsInfo> getClassT() {
        return DeleteRunsInfo.class;
    }
}
