package com.akto.util;

public class StairwayUtils {



    public static final String AKTO_DASHBOARD_ROLE_NAME = "AKTO_DASHBOARD_ROLE_NAME";

    public static final String AKTO_DASHBOARD_STACK_NAME = "AKTO_DASHBOARD_STACK_NAME";

    public static String getDashboardStackName(){
        return System.getenv(AKTO_DASHBOARD_STACK_NAME);
    }

    public static String getMirroringStackName(){
        String dashboardStackName = getDashboardStackName();
        if(dashboardStackName != null){
            return dashboardStackName + "-mirroring";
        }
        return "akto-mirroring"; // keep this backward compatible
    }

    public static String getAktoDashboardRoleName(){
        return System.getenv(AKTO_DASHBOARD_ROLE_NAME);
    }
}
