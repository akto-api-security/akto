package com.akto.dao.monitoring;


import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
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
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[]{ ModuleInfo._REBOOT, ModuleInfo.MODULE_TYPE, ModuleInfo.MINI_RUNTIME_NAME }, true);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[]{ ModuleInfo.LAST_HEARTBEAT_RECEIVED }, true);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[]{ ModuleInfo.MODULE_TYPE, ModuleInfo.NAME }, true);
    

        // union of the module_info indexes defined by every build; the existing ones above are kept as-is
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{ ModuleInfo.MODULE_TYPE }, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{ ModuleInfo.MODULE_TYPE, ModuleInfo.LAST_HEARTBEAT_RECEIVED }, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{ ModuleInfo.MODULE_TYPE, ModuleInfo._REBOOT, ModuleInfo.MINI_RUNTIME_NAME }, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), Indexes.ascending(ModuleInfo.TOKEN_ID),
            new IndexOptions().name("tokenId_1").sparse(true));
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), Indexes.ascending(ModuleInfo.EXPIRES_AT),
            new IndexOptions().name("expiresAt_ttl").expireAfter(0L, java.util.concurrent.TimeUnit.SECONDS));
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
