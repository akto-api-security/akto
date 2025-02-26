package com.akto.util;

import com.akto.dao.SetupDao;
import com.akto.dao.context.Context;
import com.akto.dto.Setup;
import com.akto.onprem.Constants;
import com.mongodb.BasicDBObject;

public enum DashboardMode {
    LOCAL_DEPLOY, ON_PREM, STAIRWAY, SAAS;

    public static boolean isKubernetes() {
        String isKubernetes = System.getenv("IS_KUBERNETES");
        return isKubernetes != null && isKubernetes.equalsIgnoreCase("true");
    }

    public static DashboardMode getActualDashboardMode() {
        DashboardMode dashboardMode = getDashboardMode();
        if (isSaasDeployment()) {
            return SAAS;
        }
        return dashboardMode;
    }

    // modify this and remove getActualDashboardMode method
    public static DashboardMode getDashboardMode(){
        String dashboardMode = System.getenv("DASHBOARD_MODE");
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
        return Constants.DEFAULT_DASHBOARD_MODE;
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

    private static boolean isSaasDeploymentGlobal = false;
    private static int lastSaasFetched = 0;

    public static boolean isMetered() {

        if (lastSaasFetched == 0 || lastSaasFetched < Context.now() - 30 * 60) {
            boolean isSaasDeployment = isSaasDeployment();
            try {
                Setup setup = SetupDao.instance.findOne(new BasicDBObject());
                if (setup != null) {
                    String dashboardMode = setup.getDashboardMode();
                    isSaasDeployment = dashboardMode.equalsIgnoreCase(DashboardMode.SAAS.name());
                }
            } catch (Exception e) {
            }
            isSaasDeploymentGlobal = isSaasDeployment;
            lastSaasFetched = Context.now();
        }

        return isSaasDeploymentGlobal || isOnPremDeployment();
    }
}
