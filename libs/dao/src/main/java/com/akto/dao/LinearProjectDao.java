package com.akto.dao;

import com.akto.dto.linear_integration.LinearProject;

public class LinearProjectDao extends AccountsContextDao<LinearProject> {
    public static final LinearProjectDao instance = new LinearProjectDao();

    @Override
    public String getCollName() {
        return "linear_projects";
    }

    @Override
    public Class<LinearProject> getClassT() {
        return LinearProject.class;
    }
}
