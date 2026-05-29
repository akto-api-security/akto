package com.akto.dao.monitoring;


import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.monitoring.ModuleInfo;

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
}
