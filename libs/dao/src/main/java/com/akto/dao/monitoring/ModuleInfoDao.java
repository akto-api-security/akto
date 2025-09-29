package com.akto.dao.monitoring;


import com.akto.dao.AccountsContextDao;
import com.akto.dto.monitoring.ModuleInfo;
import com.mongodb.client.model.Filters;

public class ModuleInfoDao extends AccountsContextDao<ModuleInfo> {
    @Override
    public String getCollName() {
        return "module_info";
    }

    public static final ModuleInfoDao instance = new ModuleInfoDao();
    private ModuleInfoDao(){}

    @Override
    public Class<ModuleInfo> getClassT() {
        return ModuleInfo.class;
    }

    public boolean isValidForTimestamp(ModuleInfo.ModuleType moduleType, int startTs, int endTs, String version){
        return instance.count(Filters.and(
            Filters.eq(ModuleInfo.MODULE_TYPE, moduleType),
            Filters.gte(ModuleInfo.LAST_HEARTBEAT_RECEIVED, startTs),
            Filters.lte(ModuleInfo.LAST_HEARTBEAT_RECEIVED, endTs),
            Filters.regex("currentVersion", version)
        )) > 0;
    }
}
