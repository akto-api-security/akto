package com.akto.action.siem;

import org.bson.conversions.Bson;

import com.akto.action.UserAction;
import com.akto.action.threat_detection.ThreatActorAction;
import com.akto.dao.ConfigsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.akto.dto.Config.ConfigType;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public class SplunkAction extends UserAction {

    private String splunkUrl;
    private String splunkToken;
    private Config.SplunkSiemConfig splunkSiemConfig;

    public String addSplunkIntegration() {
        if(splunkUrl == null){
            addActionError("Splunk Url cannot be null");
            return ERROR.toUpperCase();
        }

        if(splunkToken == null){
            addActionError("Specify appropriate token, cannot be null");
            return ERROR.toUpperCase();
        }

        int accId = Context.accountId.get();

        Bson filters = Filters.and(
            Filters.eq("_id", accId + "_" + ConfigType.SPLUNK_SIEM.name())
        );
        Config.SplunkSiemConfig existingConfig = (Config.SplunkSiemConfig) ConfigsDao.instance.findOne(filters);

        ThreatActorAction threatActorAction = new ThreatActorAction();
        threatActorAction.setSplunkToken(splunkToken);
        threatActorAction.setSplunkUrl(splunkUrl);
        String resp = threatActorAction.sendIntegrationDataToThreatBackend();
        if (resp == ERROR.toUpperCase()) {
            addActionError("Error saving integration");
            return ERROR.toUpperCase();
        }
        if (existingConfig != null) {
            Bson updates = Updates.combine(
                Updates.set("splunkUrl", splunkUrl),
                Updates.set("splunkToken", splunkToken)
            );
            ConfigsDao.instance.updateOne(filters, updates);
        } else {
            Config.SplunkSiemConfig config = new Config.SplunkSiemConfig(splunkUrl, splunkToken, accId);
            ConfigsDao.instance.insertOne(config);
        }

        return SUCCESS.toUpperCase();
    }

    public String fetchSplunkIntegration() {

        int accId = Context.accountId.get();
        Bson filters = Filters.and(
            Filters.eq("_id", accId + "_" + ConfigType.SPLUNK_SIEM.name())
        );
        splunkSiemConfig = (Config.SplunkSiemConfig) ConfigsDao.instance.findOne(filters);

        return SUCCESS.toUpperCase();
    }
    
    public String getSplunkUrl() {
        return splunkUrl;
    }
    public void setSplunkUrl(String splunkUrl) {
        this.splunkUrl = splunkUrl;
    }
    public String getSplunkToken() {
        return splunkToken;
    }
    public void setSplunkToken(String splunkToken) {
        this.splunkToken = splunkToken;
    }

    public Config.SplunkSiemConfig getSplunkSiemConfig() {
        return splunkSiemConfig;
    }

    public void setSplunkSiemConfig(Config.SplunkSiemConfig splunkSiemConfig) {
        this.splunkSiemConfig = splunkSiemConfig;
    }
    
}
