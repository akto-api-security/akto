package com.akto.dao;

import com.akto.dto.Team;

public class TeamsDao extends AccountsContextDao<Team> {

    public static final TeamsDao instance = new TeamsDao();

    private TeamsDao() {}

    @Override
    public String getCollName() {
        return "teams";
    }

    @Override
    public Class<Team> getClassT() {
        return Team.class;
    }
}
