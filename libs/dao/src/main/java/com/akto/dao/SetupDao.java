package com.akto.dao;

import com.akto.dto.Setup;
import com.mongodb.BasicDBObject;

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

    private final static String SAAS = "saas";
    public boolean isMetered() {
        Setup setup = SetupDao.instance.findOne(new BasicDBObject());
        boolean isSaas = false;
        if (setup != null) {
            String dashboardMode = setup.getDashboardMode();
            if (dashboardMode != null) {
                isSaas = dashboardMode.equalsIgnoreCase(SAAS);
            }
        }
        return isSaas;
    }

}
