package com.akto.utils;

import org.apache.commons.lang3.StringUtils;

public enum DashboardMode {
    LOCAL_DEPLOY, ON_PREM;

    public static DashboardMode getDashboardMode(){
        String dashboardMode = System.getenv("DASHBOARD_MODE");
        if(StringUtils.isEmpty(dashboardMode)){
<<<<<<< HEAD
            return ON_PREM;
        }
        if("local_deploy".equalsIgnoreCase(dashboardMode)){
            return LOCAL_DEPLOY;
        }
        return ON_PREM;
=======
            return LOCAL_DEPLOY;
        }
        if("on_prem".equalsIgnoreCase(dashboardMode)){
            return ON_PREM;
        }
        return LOCAL_DEPLOY;
>>>>>>> pub-repo/master
    }

    public static boolean isLocalDeployment(){
        DashboardMode dashboardMode = DashboardMode.getDashboardMode();
        return dashboardMode.equals(LOCAL_DEPLOY);
    }

    public static boolean isOnPremDeployment(){
        DashboardMode dashboardMode = DashboardMode.getDashboardMode();
        return dashboardMode.equals(ON_PREM);
    }

}
