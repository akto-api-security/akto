package com.akto.action.waf;

import com.akto.action.UserAction;
import com.akto.action.threat_detection.ThreatActorAction;
import com.akto.dao.ConfigsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;

import java.util.List;

public class CloudflareWafAction extends UserAction {

    private String apiKey;
    private String email;
    private String integrationType;
    private String accountOrZoneId;
    private List<String> severityLevels;

    private Config.CloudflareWafConfig cloudflareWafConfig;

    public String addCloudflareWafIntegration() {
        if(email == null || integrationType == null || accountOrZoneId == null) {
            addActionError("Please provide valid parameters.");
        }

        int accId = Context.accountId.get();


        Bson filters = Filters.and(
                Filters.eq(Config.CloudflareWafConfig.ACCOUNT_ID, accId),
                Filters.eq(Config.CloudflareWafConfig._CONFIG_ID, Config.ConfigType.CLOUDFLARE_WAF.name())
        );
        Config.CloudflareWafConfig existingConfig = (Config.CloudflareWafConfig) ConfigsDao.instance.findOne(filters);
        if(existingConfig == null && (apiKey == null || apiKey.isEmpty())) {
            addActionError("Please provide a valid API Key.");
            return ERROR.toUpperCase();
        }


        if(existingConfig != null) {
            setApiKey(existingConfig.getApiKey());
        }
        Config.CloudflareWafConfig config = new Config.CloudflareWafConfig(apiKey, email, integrationType, accountOrZoneId, accId,severityLevels);
        String cloudFlareIPAccessRuleByActorIP = ThreatActorAction.getCloudFlareIPAccessRuleByActorIP("", config);
        if(cloudFlareIPAccessRuleByActorIP == null) {
            addActionError("Invalid cloudflare credentials.");
            return ERROR.toUpperCase();
        }

        if (existingConfig != null) {
            Bson updates = Updates.combine(
                    Updates.set(Config.CloudflareWafConfig.ACCOUNT_OR_ZONE_ID, accountOrZoneId),
                    Updates.set(Config.CloudflareWafConfig.INTEGRATION_TYPE, integrationType),
                    Updates.set(Config.CloudflareWafConfig.EMAIL, email),
                    Updates.set(Config.CloudflareWafConfig.SEVERITY_LEVELS, severityLevels)
            );
            ConfigsDao.instance.updateOne(filters, updates);
        } else {
            ConfigsDao.instance.insertOne(config);
        }

        return SUCCESS.toUpperCase();
    }

    public String deleteCloudflareWafIntegration() {
        Bson filters = Filters.and(
                Filters.eq(Config.CloudflareWafConfig.ACCOUNT_ID, Context.accountId.get()),
                Filters.eq(Config.CloudflareWafConfig._CONFIG_ID, Config.ConfigType.CLOUDFLARE_WAF.name())
        );
        ConfigsDao.instance.getMCollection().deleteOne(filters);
        return SUCCESS.toUpperCase();
    }

    public String fetchCloudflareWafIntegration() {
        int accountId = Context.accountId.get();

        cloudflareWafConfig = (Config.CloudflareWafConfig) ConfigsDao.instance.findOne(Filters.and(
                Filters.eq(Config.CloudflareWafConfig.ACCOUNT_ID, accountId),
                Filters.eq(Config.CloudflareWafConfig._CONFIG_ID, Config.ConfigType.CLOUDFLARE_WAF.name())
        ));

        return SUCCESS.toUpperCase();
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAccountOrZoneId() {
        return accountOrZoneId;
    }

    public void setAccountOrZoneId(String accountOrZoneId) {
        this.accountOrZoneId = accountOrZoneId;
    }

    public Config.CloudflareWafConfig getCloudflareWafConfig() {
        return cloudflareWafConfig;
    }

    public String getIntegrationType() {
        return integrationType;
    }

    public void setIntegrationType(String integrationType) {
        this.integrationType = integrationType;
    }

    public List<String> getSeverityLevels() {
        return severityLevels;
    }

    public void setSeverityLevels(List<String> severityLevels) {
        this.severityLevels = severityLevels;
    }
}
