package com.akto.utils;

import com.akto.dao.AccountSettingsDao;
import com.akto.dto.AccountSettings;
import com.mongodb.BasicDBObject;

public class DashboardVersion {

    public static Boolean isSemanticVersionString(String semanticVersionString) {
        if (semanticVersionString.matches("^\\d+.\\d+.\\d+$")) {
            return true;
        } 

        return false;
    }

    public static String getDashboardVersion() {
        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(new BasicDBObject());
        String dashboardVersionDb = accountSettings.getDashboardVersion();

        String aktoImageTag = dashboardVersionDb.split(" - ", 2)[0];
        System.out.println(aktoImageTag);

        String dashboardVersion = null;

        if(isSemanticVersionString(aktoImageTag)) {
            dashboardVersion = aktoImageTag;
        } else {
            dashboardVersion = "latest";
        }

        return dashboardVersion;
    }

}
