package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.testing.SmartAgentProfile;

public class SmartAgentProfileDao extends AccountsContextDao<SmartAgentProfile> {

    public static final SmartAgentProfileDao instance = new SmartAgentProfileDao();

    @Override
    public String getCollName() {
        return "smart_agent_profiles";
    }

    @Override
    public Class<SmartAgentProfile> getClassT() {
        return SmartAgentProfile.class;
    }
}
