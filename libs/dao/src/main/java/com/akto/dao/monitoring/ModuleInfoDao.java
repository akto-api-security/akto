package com.akto.dao.monitoring;


import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.monitoring.ModuleInfo;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import java.util.List;

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

    public void createIndicesIfAbsent() {
        // moduleType is the most common single-field filter
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[]{ ModuleInfo.MODULE_TYPE }, false);

        // moduleType + lastHeartbeatReceived — used in heartbeat threshold queries
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[]{ ModuleInfo.MODULE_TYPE, ModuleInfo.LAST_HEARTBEAT_RECEIVED }, false);
    }

    private static final int HEARTBEAT_THRESHOLD_SECONDS = 5 * 60;
    private static final int REBOOT_THRESHOLD_SECONDS = 2 * 60;

    public List<ModuleInfo> fetchLiveModulesByType(ModuleInfo.ModuleType moduleType) {
        return instance.findAll(Filters.and(
            Filters.eq(ModuleInfo.MODULE_TYPE, moduleType.toString()),
            Filters.gte(ModuleInfo.LAST_HEARTBEAT_RECEIVED, Context.now() - HEARTBEAT_THRESHOLD_SECONDS)
        ));
    }

    public void triggerReboot(String moduleId, ModuleInfo.ModuleType moduleType) {
        instance.updateMany(
            Filters.and(
                Filters.eq(ID, moduleId),
                Filters.gte(ModuleInfo.LAST_HEARTBEAT_RECEIVED, Context.now() - REBOOT_THRESHOLD_SECONDS),
                Filters.eq(ModuleInfo.MODULE_TYPE, moduleType.toString())
            ),
            Updates.set(ModuleInfo._REBOOT, true)
        );
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
