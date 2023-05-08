package com.akto.utils.platform;

public class MirroringStackDetails {

    public static String getStackName(){
        String dashboardStackName = DashboardStackDetails.getStackName();
        if(dashboardStackName != null){
            return dashboardStackName + "-mirroring";
        }
        return "akto-mirroring"; // keep this backward compatible
    }

    public static final String CREATE_MIRROR_SESSION_LAMBDA = "CreateMirrorSession";

    public static final String AKTO_CONTEXT_ANALYZER_AUTO_SCALING_GROUP = "AktoContextAnalyzerAutoScalingGroup";

    public static final String AKTO_AUTO_SCALING_GROUP = "AktoAutoScalingGroup";

    public static final String TRAFFIC_MIRROR_TARGET = "TrafficMirrorTarget";

    public static final String LB_TRAFFIC_MIRROR_FILTER = "LBTrafficMirrorFilter";

    public static final String LAMBDA_LOG_GROUP = "LambdaLogGroup";

    public static final String LAMBDA_BASIC_EXECUTION_ROLE = "LambdaBasicExecutionRole";

    public static final String GET_AKTO_SETUP_DETAILS_LAMBDA_BASIC_EXECUTION_ROLE = "GetAktoSetupDetailsLambdaBasicExecutionRole";


    public static final String AKTO_NLB = "AktoNLB";

    public static final String AKTO_CONTEXT_ANALYZER_UPDATE_LAMBDA = "AktoContextAnalyzerInstanceRefreshHandler";
    public static final String AKTO_DASHBOARD_UPDATE_LAMBDA = "DashboardInstanceRefreshHandler";
    public static final String AKTO_RUNTIME_UPDATE_LAMBDA = "TrafficMirroringInstanceRefreshHandler";

    public static final String AKTO_CONTEXT_ANALYSER_AUTO_SCALING_GROUP = "AktoContextAnalyzerAutoScalingGroup";
    public static final String AKTO_TRAFFIC_MIRRORING_AUTO_SCALING_GROUP = "AktoAutoScalingGroup";

    public static final String GET_AKTO_SETUP_DETAILS_LAMBDA = "GetAktoSetupDetails";

    public static final String LAMBDA_VPC_ACCESS_ROLE = "LambdaVPCAccessRole";

    public static final String LAMBDA_SECURITY_GROUP_VPC = "LambdaSecurityGroupVPC";

    public static final String SAVE_COLLECTION_NAMES_LAMBDA = "SaveCollectionNames";

    public static final String GET_VPC_DETAILS_LAMBDA_ROLE = "GetVpcDetailsLambdaRole";

    public static final String GET_VPC_DETAILS_LAMBDA = "GetVpcDetailsLambda";

    public static final String AKTO_CONTEXT_ANALYZER_SECURITY_GROUP = "AktoContextAnalyzerSecurityGroup";

    public static final String AKTO_CONTEXT_ANALYZER_INSTANCE_REFRESH_HANDLER_LAMBDA = "AktoContextAnalyzerInstanceRefreshHandler";

    public static final String REFRESH_HANDLER_LAMBDA_BASIC_EXECUTION_ROLE = "RefreshHandlerLambdaBasicExecutionRole";

    public static final String AKTO_SECURITY_GROUP = "AktoSecurityGroup";

    public static final String AKTO_TRAFFIC_MIRRORING_TARGET_GROUP = "AktoTrafficMirroringTargetGroup";

    public static final String AKTO_KAFKA_TARGET_GROUP = "AktoKafkaTargetGroup";

    public static final String DASHBOARD_INSTANCE_REFRESH_HANDLER_LAMBDA = "DashboardInstanceRefreshHandler";

    public static final String TRAFFIC_MIRRORING_INSTANCE_REFRESH_HANDLER_LAMBDA = "TrafficMirroringInstanceRefreshHandler";

    public static final String INSTANCE_REFRESH_HANDLER_LAMBDA_ROLE = "InstanceRefreshHandlerLambdaRole";

}
