package com.akto.dao.monitoring;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.monitoring.ModuleInfo.ModuleType;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.conversions.Bson;

public class ModuleInfoDao extends AccountsContextDao<ModuleInfo> {
    @Override
    public String getCollName() {
        return "module_info";
    }

    public static final ModuleInfoDao instance = new ModuleInfoDao();

    private ModuleInfoDao() {
    }

    private static final int INACTIVE_THRESHOLD = 5 * 60; // 5 minutes
    
    public static final long MODULE_INFO_TTL_MS = 2 * 24 * 60 * 60 * 1000;

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
        
        Bson ttlIndex = Indexes.ascending(ModuleInfo.EXPIRES_AT);
        IndexOptions ttlOptions = new IndexOptions()
                .name("expiresAt_ttl")
                .expireAfter(0L, java.util.concurrent.TimeUnit.SECONDS);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), ttlIndex, ttlOptions);
    }

    @Override
    public Class<ModuleInfo> getClassT() {
        return ModuleInfo.class;
    }
}
