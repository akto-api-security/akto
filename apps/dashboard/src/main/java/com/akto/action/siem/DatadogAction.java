package com.akto.action.siem;

import com.akto.action.UserAction;
import com.akto.dao.ConfigsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.akto.dto.Config.ConfigType;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DatadogAction extends UserAction {

    private String apiKey;
    private String datadogSite;
    private boolean enabled;
    private Config.DatadogForwarderConfig datadogForwarderConfig;

    public String fetchDatadogIntegration() {
        int accId = Context.accountId.get();
        Bson filter = Filters.eq("_id", Config.DatadogForwarderConfig.CONFIG_ID + "_" + accId);
        datadogForwarderConfig = (Config.DatadogForwarderConfig) ConfigsDao.instance.findOne(filter);
        return SUCCESS.toUpperCase();
    }

    public String addDatadogIntegration() {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            addActionError("API key cannot be empty");
            return ERROR.toUpperCase();
        }
        if (datadogSite == null || datadogSite.trim().isEmpty()) {
            addActionError("Datadog site cannot be empty");
            return ERROR.toUpperCase();
        }

        int accId = Context.accountId.get();
        String configId = Config.DatadogForwarderConfig.CONFIG_ID + "_" + accId;
        Bson filter = Filters.eq("_id", configId);

        Config.DatadogForwarderConfig existing = (Config.DatadogForwarderConfig) ConfigsDao.instance.findOne(filter);
        if (existing != null) {
            Bson updates = Updates.combine(
                Updates.set(Config.DatadogForwarderConfig.API_KEY, apiKey.trim()),
                Updates.set(Config.DatadogForwarderConfig.DATADOG_SITE, datadogSite.trim()),
                Updates.set(Config.DatadogForwarderConfig.ENABLED, enabled)
            );
            ConfigsDao.instance.updateOne(filter, updates);
        } else {
            Config.DatadogForwarderConfig config = new Config.DatadogForwarderConfig(accId);
            config.setApiKey(apiKey.trim());
            config.setDatadogSite(datadogSite.trim());
            config.setEnabled(enabled);
            ConfigsDao.instance.insertOne(config);
        }

        return SUCCESS.toUpperCase();
    }

    public String deleteDatadogIntegration() {
        int accId = Context.accountId.get();
        Bson filter = Filters.eq("_id", Config.DatadogForwarderConfig.CONFIG_ID + "_" + accId);
        ConfigsDao.instance.deleteAll(filter);
        return SUCCESS.toUpperCase();
    }

    public String testDatadogIntegration() {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            addActionError("API key cannot be empty");
            return ERROR.toUpperCase();
        }
        if (datadogSite == null || datadogSite.trim().isEmpty()) {
            addActionError("Datadog site cannot be empty");
            return ERROR.toUpperCase();
        }

        String testPayload = "[{\"ddsource\":\"akto\",\"service\":\"akto-mini-runtime\",\"ddtags\":\"env:test\",\"message\":\"Akto Datadog integration test\"}]";
        String endpointUrl = "https://http-intake.logs." + datadogSite.trim() + "/v1/input";

        try {
            URL url = new URL(endpointUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("DD-API-KEY", apiKey.trim());
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(testPayload.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                return SUCCESS.toUpperCase();
            } else {
                addActionError("Datadog returned HTTP " + responseCode + ". Please verify your API key and site.");
                return ERROR.toUpperCase();
            }
        } catch (Exception e) {
            addActionError("Failed to connect to Datadog: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getDatadogSite() { return datadogSite; }
    public void setDatadogSite(String datadogSite) { this.datadogSite = datadogSite; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Config.DatadogForwarderConfig getDatadogForwarderConfig() { return datadogForwarderConfig; }
    public void setDatadogForwarderConfig(Config.DatadogForwarderConfig datadogForwarderConfig) { this.datadogForwarderConfig = datadogForwarderConfig; }
}
