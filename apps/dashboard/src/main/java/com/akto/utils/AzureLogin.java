package com.akto.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.akto.dao.ConfigsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.akto.dto.Config.AzureConfig;
import com.onelogin.saml2.settings.Saml2Settings;
import com.onelogin.saml2.settings.SettingsBuilder;;

public class AzureLogin {
    public static final int PROBE_PERIOD_IN_SECS = 60;
    private static AzureLogin instance = null;
    private AzureConfig azureConfig = null;
    private int lastProbeTs = 0;

    public static AzureLogin getInstance() {
        boolean shouldProbeAgain = true;
        if (instance != null) {
            shouldProbeAgain = Context.now() - instance.lastProbeTs >= PROBE_PERIOD_IN_SECS;
        }

        if (shouldProbeAgain) {
            AzureConfig azureConfig = (Config.AzureConfig) ConfigsDao.instance.findOne("_id", "AZURE-ankush");
            if (instance == null) {
                instance = new AzureLogin();
            }

            instance.azureConfig = azureConfig;
            instance.lastProbeTs = Context.now();
        }

        return instance;
    }

    public static Saml2Settings getSamlSettings () {
        AzureLogin azureLoginInstance = AzureLogin.getInstance();
        if(azureLoginInstance == null){
            return null;
        }

        Config.AzureConfig azureConfig = AzureLogin.getInstance().getAzureConfig();
        if(azureConfig == null){
            return null;
        }

        Map<String, Object> samlData = new HashMap<>();
        samlData.put("onelogin.saml2.sp.entityid",azureConfig.getApplicationIdentifier());
        samlData.put("onelogin.saml2.idp.single_sign_on_service.url", azureConfig.getLoginUrl());
        samlData.put("onelogin.saml2.idp.x509cert", azureConfig.getX509Certificate());
        samlData.put("onelogin.saml2.sp.assertion_consumer_service.url", azureConfig.getAcsUrl());
        samlData.put("onelogin.saml2.idp.entityid", azureConfig.getAzureEntityId());

        SettingsBuilder builder = new SettingsBuilder();
        Saml2Settings settings = builder.fromValues(samlData).build();
        return settings;
    }

    private AzureLogin(){} 
    
    public AzureConfig getAzureConfig() {
        return azureConfig;
    }

    public void setAzureConfig(AzureConfig azureConfig) {
        this.azureConfig = azureConfig;
    }
}
