package com.akto.dao;

import com.akto.dto.Setup;

public class SetupDao extends CommonContextDao<Setup> {

    public static final SetupDao instance = new SetupDao();

    @Override
    public String getCollName() {
        return "setup";
    }

    @Override
    public Class<Setup> getClassT() {
        return Setup.class;
    }

}
