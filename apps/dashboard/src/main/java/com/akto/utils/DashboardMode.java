package com.akto.utils;

import org.apache.commons.lang3.StringUtils;

public enum DashboardMode {
    LOCAL_DEPLOY, ON_PREM, STAIRWAY, SAAS;

    public static DashboardMode getDashboardMode(){
        String dashboardMode = System.getenv("DASHBOARD_MODE");
        if(StringUtils.isEmpty(dashboardMode)){
            return ON_PREM;
        }
        if("on_prem".equalsIgnoreCase(dashboardMode)){
            return ON_PREM;
        }
        if("local_deploy".equalsIgnoreCase(dashboardMode)){
            return LOCAL_DEPLOY;
        }
        if("saas".equalsIgnoreCase(dashboardMode)){
            return SAAS;
        }
        if("stairway".equalsIgnoreCase(dashboardMode)){
            return STAIRWAY;
        }
        return ON_PREM;
    }

    public static boolean isLocalDeployment(){
        DashboardMode dashboardMode = DashboardMode.getDashboardMode();
        return dashboardMode.equals(LOCAL_DEPLOY);
    }

    public static boolean isOnPremDeployment(){
        DashboardMode dashboardMode = DashboardMode.getDashboardMode();
        return dashboardMode.equals(ON_PREM);
    }

    public static boolean isSaasDeployment(){
        DashboardMode dashboardMode = DashboardMode.getDashboardMode();
        return dashboardMode.equals(LOCAL_DEPLOY) && "true".equalsIgnoreCase(System.getenv("IS_SAAS"));
    }
}
