package com.akto.action.quick_start;

import java.util.ArrayList;
import java.util.List;

import com.akto.action.UserAction;
import com.akto.dao.AccountSettingsDao;
import com.akto.dto.AccountSettings;
import com.akto.dto.settings.ApiGatewayRoleRegions;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;

import lombok.Getter;
import lombok.Setter;

public class AwsAction extends UserAction {

    @Getter
    @Setter
    private String awsAccountIds;

    @Getter
    @Setter
    private List<ApiGatewayRoleRegions> awsApiGConnectorRoleRegions;

    public String addAwsAccountIdsForApiGatewayLogging() {

        if (awsApiGConnectorRoleRegions == null) {
            AccountSettingsDao.instance.updateOne(
                    AccountSettingsDao.generateFilter(),
                    Updates.set(AccountSettings.AWS_ACCOUNT_IDS_FOR_API_GATEWAY_LOGGING, awsAccountIds));
            return Action.SUCCESS.toUpperCase();
        }

        // Also keep the plain comma-separated role ARN list, which connectors
        // that predate regions still read.
        List<String> roleArns = new ArrayList<>();
        for (ApiGatewayRoleRegions roleRegions : awsApiGConnectorRoleRegions) {
            roleArns.add(roleRegions.getRoleArn());
        }

        AccountSettingsDao.instance.updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.combine(
                        Updates.set(AccountSettings.AWS_ACCOUNT_IDS_FOR_API_GATEWAY_LOGGING, String.join(",", roleArns)),
                        Updates.set(AccountSettings.AWS_API_G_CONNECTOR_ROLE_REGIONS, awsApiGConnectorRoleRegions)));

        return Action.SUCCESS.toUpperCase();
    }

    public String fetchAwsAccountIdsForApiGatewayLogging() {

        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        if (accountSettings != null && accountSettings.getAwsAccountIdsForApiGatewayLogging() != null) {
            awsAccountIds = accountSettings.getAwsAccountIdsForApiGatewayLogging();
        }
        if (accountSettings != null && accountSettings.getAwsApiGConnectorRoleRegions() != null) {
            awsApiGConnectorRoleRegions = accountSettings.getAwsApiGConnectorRoleRegions();
        }

        return Action.SUCCESS.toUpperCase();
    }

}
