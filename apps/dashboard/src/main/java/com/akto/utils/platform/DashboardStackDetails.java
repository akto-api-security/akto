package com.akto.utils.platform;

import org.apache.commons.lang3.StringUtils;

public class DashboardStackDetails {


    public static final String AKTO_DASHBOARD_STACK_NAME = "AKTO_DASHBOARD_STACK_NAME";
    public static String getStackName() {
        return System.getenv(AKTO_DASHBOARD_STACK_NAME);
    }

    //TODO all the pending items from CFT

    public static final String AKTO_DASHBOARD_ROLE = "AktoDashboardRole";

    public static final String AKTO_DASHBOARD_AUTO_SCALING_GROUP = "AktoDashboardAutoScalingGroup";

    public static final String AKTO_LB_DASHBOARD = "AktoLBDashboard";

    public static String getAktoDashboardRole(){
        String roleName = System.getenv("AKTO_DASHBOARD_ROLE_NAME");
        return StringUtils.isEmpty(roleName) ? AKTO_DASHBOARD_ROLE: roleName;
    }

}
