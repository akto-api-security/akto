package com.akto.dao.monitoring;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.monitoring.ModuleInfo;

public class ModuleInfoDao extends AccountsContextDao<ModuleInfo> {
    @Override
    public String getCollName() {
        return "module_info";
    }

    @Override
    public Class<ModuleInfo> getClassT() {
        return ModuleInfo.class;
    }
}
