package com.akto.action.quick_start;

import com.akto.action.UserAction;
import com.akto.dao.AccountSettingsDao;
import com.akto.dto.AccountSettings;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;

import lombok.Getter;
import lombok.Setter;

public class AwsAction extends UserAction {

    @Getter
    @Setter
    private String awsAccountIds;

    public String addAwsAccountIdsForApiGatewayLogging() {

        AccountSettingsDao.instance.updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.set(AccountSettings.AWS_ACCOUNT_IDS_FOR_API_GATEWAY_LOGGING, awsAccountIds));

        return Action.SUCCESS.toUpperCase();
    }

    public String fetchAwsAccountIdsForApiGatewayLogging() {

        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        if (accountSettings != null && accountSettings.getAwsAccountIdsForApiGatewayLogging() != null) {
            awsAccountIds = accountSettings.getAwsAccountIdsForApiGatewayLogging();
        }

        return Action.SUCCESS.toUpperCase();
    }

}
