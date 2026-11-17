package com.akto.utils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.akto.dao.context.Context;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dto.monitoring.ModuleInfo;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

/** Which mini-testing modules are alive: the ones whose heartbeat reached cyborg recently. */
public final class MiniTestingModules {

    static final int ACTIVE_WINDOW_SECONDS = 20 * 60;

    private MiniTestingModules() {}

    public static Set<String> activeNames() {
        List<ModuleInfo> moduleInfos = ModuleInfoDao.instance.findAll(Filters.and(
            Filters.eq(ModuleInfo.MODULE_TYPE, ModuleInfo.ModuleType.MINI_TESTING),
            Filters.gt(ModuleInfo.LAST_HEARTBEAT_RECEIVED, Context.now() - ACTIVE_WINDOW_SECONDS)),
            Projections.include(ModuleInfo.NAME));
        Set<String> names = new HashSet<>();
        for (ModuleInfo moduleInfo : moduleInfos) {
            if (moduleInfo.getName() != null) {
                names.add(moduleInfo.getName());
            }
        }
        return names;
    }
}
