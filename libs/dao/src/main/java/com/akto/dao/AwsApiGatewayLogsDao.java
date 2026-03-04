package com.akto.dao;

public class AwsApiGatewayLogsDao extends LogsDao {
    public static final AwsApiGatewayLogsDao instance = new AwsApiGatewayLogsDao();

    @Override
    public String getCollName() {
        return "logs_aws_api_gateway";
    }
}
