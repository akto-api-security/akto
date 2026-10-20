package com.akto.dao.monitoring;


import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.monitoring.ModuleInfo;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

import java.util.concurrent.TimeUnit;

public class ModuleInfoDao extends AccountsContextDao<ModuleInfo> {
    @Override
    public String getCollName() {
        return "module_info";
    }

    public static final ModuleInfoDao instance = new ModuleInfoDao();
    private ModuleInfoDao(){}

    /** TTL index on expiresAt: docs are deleted once their expiresAt date passes. */
    public void createTtlIndexIfAbsent() {
        IndexOptions ttlOptions = new IndexOptions()
                .name("expiresAt_ttl")
                .expireAfter(0L, TimeUnit.SECONDS);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), Indexes.ascending(ModuleInfo.EXPIRES_AT), ttlOptions);
    }

    @Override
    public Class<ModuleInfo> getClassT() {
        return ModuleInfo.class;
    }
}
