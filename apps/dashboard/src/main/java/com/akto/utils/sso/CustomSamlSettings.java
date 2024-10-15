package com.akto.utils.sso;

import java.util.HashMap;
import java.util.Map;

import com.akto.dao.context.Context;
import com.akto.dto.Config.ConfigType;
import com.akto.dto.sso.SAMLConfig;
import com.onelogin.saml2.settings.Saml2Settings;
import com.onelogin.saml2.settings.SettingsBuilder;

public class CustomSamlSettings {
    private static final int PROBE_PERIOD_IN_SECS = 60;
    private static Map<ConfigType, CustomSamlSettings> instances = new HashMap<>();
    private SAMLConfig samlConfig;
    private int lastProbeTs;

    private CustomSamlSettings() {}

    public static CustomSamlSettings getInstance(ConfigType configType, int accountId) {
        CustomSamlSettings instance = instances.get(configType);
        SAMLConfig samlConfig = SsoUtils.findSAMLConfig(configType);
        instance.samlConfig = samlConfig;
        return instance;
    }

    public static CustomSamlSettings getInstance(ConfigType configType) {
        CustomSamlSettings instance = instances.get(configType);
        boolean shouldProbeAgain = true;
        if (instance != null) {
            shouldProbeAgain = Context.now() - instance.lastProbeTs >= PROBE_PERIOD_IN_SECS;
        }

        if (shouldProbeAgain) {
            SAMLConfig samlConfig = SsoUtils.findSAMLConfig(configType);
            if (instance == null) {
                instance = new CustomSamlSettings();
                instances.put(configType, instance);
            }
            instance.samlConfig = samlConfig;
            instance.lastProbeTs = Context.now();
        }

        return instance;
    }

    public SAMLConfig getSamlConfig() {
        return samlConfig;
    }

    private static Saml2Settings buildSamlSettingsMap (SAMLConfig samlConfig){
        Map<String, Object> samlData = new HashMap<>();
        samlData.put("onelogin.saml2.sp.entityid", samlConfig.getApplicationIdentifier());
        samlData.put("onelogin.saml2.idp.single_sign_on_service.url", samlConfig.getLoginUrl());
        samlData.put("onelogin.saml2.idp.x509cert", samlConfig.getX509Certificate());
        samlData.put("onelogin.saml2.sp.assertion_consumer_service.url", samlConfig.getAcsUrl());
        samlData.put("onelogin.saml2.idp.entityid", samlConfig.getEntityId());

        SettingsBuilder builder = new SettingsBuilder();
        Saml2Settings settings = builder.fromValues(samlData).build();
        return settings;
    }

    public static Saml2Settings getSamlSettings(ConfigType configType) {
        CustomSamlSettings CustomSamlSettingsInstance = CustomSamlSettings.getInstance(configType);
        if (CustomSamlSettingsInstance == null || CustomSamlSettingsInstance.getSamlConfig() == null) {
            return null;
        }

        SAMLConfig samlConfig = CustomSamlSettingsInstance.getSamlConfig();

        
        return buildSamlSettingsMap(samlConfig);
    }

    public static Saml2Settings getSamlSettings(ConfigType configType, int accountId){
        CustomSamlSettings CustomSamlSettingsInstance = CustomSamlSettings.getInstance(configType, accountId);
        if (CustomSamlSettingsInstance == null || CustomSamlSettingsInstance.getSamlConfig() == null) {
            return null;
        }
        SAMLConfig samlConfig = CustomSamlSettingsInstance.getSamlConfig();
        return buildSamlSettingsMap(samlConfig);
    }
}

