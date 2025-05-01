package com.akto.action.waf;

import com.akto.action.UserAction;
import com.akto.dao.ConfigsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;

public class CloudflareWafAction extends UserAction {

    private String apiKey;
    private String email;
    private String accountOrZoneId;

    private Config.CloudflareWafConfig cloudflareWafConfig;

    public String addCloudflareWafIntegration() {
        if(email == null || accountOrZoneId == null) {
            addActionError("Please provide valid parameters.");
        }

        int accId = Context.accountId.get();


        Bson filters = Filters.and(
                Filters.eq(Config.CloudflareWafConfig.ACCOUNT_ID, accId),
                Filters.eq(Config.CloudflareWafConfig._CONFIG_ID, Config.ConfigType.CLOUDFLARE_WAF.name())
        );
        Config configObj = ConfigsDao.instance.findOne(filters);
        Config.CloudflareWafConfig existingConfig;
        if(configObj instanceof Config.CloudflareWafConfig) {
            existingConfig = (Config.CloudflareWafConfig) configObj;

            if(apiKey == null) {
                addActionError("Please provide a valid API Key.");
                return ERROR.toUpperCase();
            }
        } else {
            existingConfig = null;
        }

        if (existingConfig != null) {
            Bson updates = Updates.combine(
                    Updates.set(Config.CloudflareWafConfig.ACCOUNT_OR_ZONE_ID, accountOrZoneId),
                    Updates.set(Config.CloudflareWafConfig.EMAIL, email)
            );
            ConfigsDao.instance.updateOne(filters, updates);
        } else {
            Config.CloudflareWafConfig config = new Config.CloudflareWafConfig(apiKey, email, accountOrZoneId, accId);
            ConfigsDao.instance.insertOne(config);
        }

        return SUCCESS.toUpperCase();
    }

    public String deleteCloudflareWafIntegration() {
        return SUCCESS.toUpperCase();
    }

    public String fetchCloudflareWafIntegration() {
        int accountId = Context.accountId.get();

        Config config = ConfigsDao.instance.findOne(Filters.eq(Config.CloudflareWafConfig.ACCOUNT_ID, accountId));

        if(config instanceof Config.CloudflareWafConfig) {
            cloudflareWafConfig = (Config.CloudflareWafConfig) config;
        }

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
}
