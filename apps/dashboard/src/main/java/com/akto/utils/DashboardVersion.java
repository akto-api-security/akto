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

    public static int[] parseSemanticVersionString(String semanticVersionString) {
        int[] semanticVersionStringPartsInt = null;

        if (DashboardVersion.isSemanticVersionString(semanticVersionString)) {
            semanticVersionStringPartsInt = new int[3];
            String[] semanticVersionStringParts = semanticVersionString.split("\\.", 3);

            for(int i=0;i<3;i++) {
                semanticVersionStringPartsInt[i] = Integer.parseInt(semanticVersionStringParts[i]);
            }

            return semanticVersionStringPartsInt;
        }
        

        return semanticVersionStringPartsInt;
    }

    public static int compareVersions(String semVer1, String semVer2) {
        /* 
         * Return -1 if semVer1 < semVer2 
         * Return 0 if semVer1 = semVer2 
         * Return 1 if semVer1 > semVer2
         */ 

        int[] semVer1parts = parseSemanticVersionString(semVer1);
        int[] semVer2parts = parseSemanticVersionString(semVer2);

        if (semVer1parts != null && semVer2parts != null) {
            for(int i=0;i<3;i++) {
                if (semVer1parts[i] != semVer2parts[i]) {
                    if (semVer1parts[i] > semVer2parts[i]) {
                        return 1;
                    } else if (semVer1parts[i] < semVer2parts[i]) {
                        return -1;
                    }
                }
            }    
        }
        
        return 0;
    }

    public static String getDashboardVersion() {
        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(new BasicDBObject());
        String dashboardVersionDb = accountSettings.getDashboardVersion();

        if (dashboardVersionDb == null) {
            return "latest";
        }

        String aktoImageTag = dashboardVersionDb.split(" - ", 2)[0];

        if (aktoImageTag == null) {
            return "latest";
        }

        String dashboardVersion = null;

        if(isSemanticVersionString(aktoImageTag)) {
            dashboardVersion = aktoImageTag;
        } else {
            dashboardVersion = "latest";
        }

        return dashboardVersion;
    }

}
