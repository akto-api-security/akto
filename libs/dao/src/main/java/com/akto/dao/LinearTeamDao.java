package com.akto.dao;

import com.akto.dto.linear_integration.LinearTeam;

public class LinearTeamDao extends AccountsContextDao<LinearTeam> {
    public static final LinearTeamDao instance = new LinearTeamDao();

    @Override
    public String getCollName() {
        return "linear_teams";
    }

    @Override
    public Class<LinearTeam> getClassT() {
        return LinearTeam.class;
    }
}
