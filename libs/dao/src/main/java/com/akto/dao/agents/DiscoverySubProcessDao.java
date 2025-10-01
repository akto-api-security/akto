package com.akto.dao.agents;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.agents.DiscoverySubProcess;

public class DiscoverySubProcessDao extends AccountsContextDao<DiscoverySubProcess> {

    public static final DiscoverySubProcessDao instance = new DiscoverySubProcessDao();

    @Override
    public String getCollName() {
        return "discovery_sub_processes";
    }

    @Override
    public Class<DiscoverySubProcess> getClassT() {
        return DiscoverySubProcess.class;
    }
}
