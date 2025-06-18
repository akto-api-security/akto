package com.akto.action.waf;

import org.bson.conversions.Bson;

import com.akto.action.UserAction;
import com.akto.action.threat_detection.ThreatActorAction;
import com.akto.dao.ConfigsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.akto.dto.Config.ConfigType;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import software.amazon.awssdk.services.wafv2.Wafv2Client;
import software.amazon.awssdk.services.wafv2.model.GetIpSetResponse;

import java.util.List;

public class AwsWafAction extends UserAction {
    
    private String awsAccessKey;
    private String awsSecretKey;
    private String region;
    private String ruleSetId;
    private String ruleSetName;
    Config.AwsWafConfig wafConfig;
    private List<String> severityLevels;

    public String addAwsWafIntegration() {

        if(awsAccessKey == null || awsSecretKey == null){
            addActionError("Please provide correct credentials for your account");
            return ERROR.toUpperCase();
        }

        if(region == null){
            addActionError("Region cannot be null");
            return ERROR.toUpperCase();
        }

        if(ruleSetId == null){
            addActionError("Specify appropriate Rule Set Id, cannot be null");
            return ERROR.toUpperCase();
        }

        if(ruleSetName == null){
            addActionError("Specify appropriate Rule Set name, cannot be null");
            return ERROR.toUpperCase();
        }

        int accId = Context.accountId.get();

        Wafv2Client client = ThreatActorAction.getAwsWafClient(awsAccessKey, awsSecretKey, region);
        GetIpSetResponse resp = ThreatActorAction.getIpSet(client, ruleSetName, ruleSetId);
        if (resp == null) {
            addActionError("Error trying to fetch information of your rule set");
        }

        Bson filters = Filters.and(
            Filters.eq("accountId", accId),
            Filters.eq("configId", ConfigType.AWS_WAF.name())
        );
        Config.AwsWafConfig existingConfig = (Config.AwsWafConfig) ConfigsDao.instance.findOne(filters);

        if (existingConfig != null) {
            Bson updates = Updates.combine(
                Updates.set("awsAccessKey", awsAccessKey),
                Updates.set("awsSecretKey", awsSecretKey),
                Updates.set("region", region),
                Updates.set("ruleSetName", ruleSetName),
                Updates.set("ruleSetId", ruleSetId),
                 Updates.set("severityLevels", severityLevels)
            );
            ConfigsDao.instance.updateOne(filters, updates);
        } else {
            Config.AwsWafConfig config = new Config.AwsWafConfig(awsAccessKey, awsSecretKey, region, ruleSetId, ruleSetName, accId,severityLevels);
            ConfigsDao.instance.insertOne(config);
        }

        return SUCCESS.toUpperCase();
    }

    public String fetchAwsWafIntegration() {

        int accId = Context.accountId.get();
        Bson filters = Filters.and(
            Filters.eq("accountId", accId),
            Filters.eq("configId", ConfigType.AWS_WAF.name())
        );
        wafConfig = (Config.AwsWafConfig) ConfigsDao.instance.findOne(filters);

        return SUCCESS.toUpperCase();
    }

    public String getAwsAccessKey() {
        return awsAccessKey;
    }


    public void setAwsAccessKey(String awsAccessKey) {
        this.awsAccessKey = awsAccessKey;
    }


    public String getAwsSecretKey() {
        return awsSecretKey;
    }


    public void setAwsSecretKey(String awsSecretKey) {
        this.awsSecretKey = awsSecretKey;
    }


    public String getRegion() {
        return region;
    }


    public void setRegion(String region) {
        this.region = region;
    }


    public String getRuleSetId() {
        return ruleSetId;
    }


    public void setRuleSetId(String ruleSetId) {
        this.ruleSetId = ruleSetId;
    }


    public String getRuleSetName() {
        return ruleSetName;
    }


    public void setRuleSetName(String ruleSetName) {
        this.ruleSetName = ruleSetName;
    }

    public Config.AwsWafConfig getWafConfig() {
        return wafConfig;
    }

    public void setWafConfig(Config.AwsWafConfig wafConfig) {
        this.wafConfig = wafConfig;
    }

    public List<String> getSeverityLevels() {
        return severityLevels;
    }

    public void setSeverityLevels(List<String> severityLevels) {
        this.severityLevels = severityLevels;
    }

}
