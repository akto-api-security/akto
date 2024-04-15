package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.testing.AccessMatrixTaskInfo;

public class AccessMatrixTaskInfosDao extends AccountsContextDao<AccessMatrixTaskInfo> {

    public final static AccessMatrixTaskInfosDao instance = new AccessMatrixTaskInfosDao();

    private AccessMatrixTaskInfosDao() {}

    @Override
    public String getCollName() {
        return "access_control_task_info";
    }

    @Override
    public Class<AccessMatrixTaskInfo> getClassT() {
        return AccessMatrixTaskInfo.class;
    }

}
