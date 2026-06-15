package com.akto.dao.monitoring;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.monitoring.ModuleInfo.ModuleType;
import com.mongodb.client.model.Filters;

public class ModuleInfoDao extends AccountsContextDao<ModuleInfo> {
    @Override
    public String getCollName() {
        return "module_info";
    }

    public static final ModuleInfoDao instance = new ModuleInfoDao();

    private ModuleInfoDao() {
    }

    private static final int INACTIVE_THRESHOLD = 5 * 60; // 5 minutes

    public boolean checkIsModuleActiveUsingName(ModuleType moduleType, String moduleName) {
        ModuleInfo moduleInfo = instance.findOne(Filters.and(
                Filters.eq(ModuleInfo.MODULE_TYPE, moduleType),
                Filters.eq(ModuleInfo.NAME, moduleName),
                Filters.gt(ModuleInfo.LAST_HEARTBEAT_RECEIVED, Context.now() - INACTIVE_THRESHOLD)));
        return moduleInfo != null;
    }

    public void createIndicesIfAbsent() {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { ModuleInfo.MODULE_TYPE, ModuleInfo._REBOOT, ModuleInfo.MINI_RUNTIME_NAME }, false);
    }

    @Override
    public Class<ModuleInfo> getClassT() {
        return ModuleInfo.class;
    }
}
