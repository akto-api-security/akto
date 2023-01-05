package com.akto.utils.platform;

public class MirroringStackDetails {

    public static String getStackName(){
        String dashboardStackName = DashboardStackDetails.getStackName();
        if(dashboardStackName != null){
            return dashboardStackName + "-mirroring";
        }
        return "akto-mirroring"; // keep this backward compatible
    }

    public static final String GET_VPC_DETAILS_LAMBDA_ROLE = "GetVpcDetailsLambdaRole";
    public static final String GET_VPC_DETAILS_LAMBDA = "GetVpcDetailsLambda";

    public static final String CREATE_MIRROR_SESSION_LAMBDA = "CreateMirrorSession";

    //TODO all the pending items from CFT



    public static final String AKTO_NLB = "AktoNLB";

    public static final String AKTO_CONTEXT_ANALYZER_UPDATE_LAMBDA = "AktoContextAnalyzerInstanceRefreshHandler";
    public static final String AKTO_DASHBOARD_UPDATE_LAMBDA = "DashboardInstanceRefreshHandler";
    public static final String AKTO_RUNTIME_UPDATE_LAMBDA = "TrafficMirroringInstanceRefreshHandler";
}
