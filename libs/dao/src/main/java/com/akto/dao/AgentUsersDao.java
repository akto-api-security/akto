package com.akto.dao;

import com.akto.dto.AgenticUsers;

public class AgentUsersDao extends AccountsContextDao<AgenticUsers>{
    public static final AgentUsersDao instance = new AgentUsersDao();

    @Override
    public String getCollName() {
        return "agent_users";
    }

    @Override
    public Class<AgenticUsers> getClassT() {
        return AgenticUsers.class;
    }
}
