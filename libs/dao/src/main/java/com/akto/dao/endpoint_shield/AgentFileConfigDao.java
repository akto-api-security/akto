package com.akto.dao.endpoint_shield;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.endpoint_shield.AgentFileConfig;

public class AgentFileConfigDao extends AccountsContextDao<AgentFileConfig> {

    public static final AgentFileConfigDao instance = new AgentFileConfigDao();
    public static final String COLLECTION_NAME = "agent_file_config";

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<AgentFileConfig> getClassT() {
        return AgentFileConfig.class;
    }

    public void createIndicesIfAbsent() {
        MCollection.createUniqueIndex(getDBName(), getCollName(), new String[]{AgentFileConfig.AGENT, AgentFileConfig.OS, AgentFileConfig.ROLE, AgentFileConfig.PATH}, true);
    }
}
