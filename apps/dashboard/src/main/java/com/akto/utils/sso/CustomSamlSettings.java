package com.akto.utils.sso;

import java.util.HashMap;
import java.util.Map;

import com.akto.dto.Config.ConfigType;
import com.akto.dto.sso.SAMLConfig;
import com.onelogin.saml2.settings.Saml2Settings;
import com.onelogin.saml2.settings.SettingsBuilder;

public class CustomSamlSettings {
    private static Map<ConfigType, CustomSamlSettings> instances = new HashMap<>();
    private SAMLConfig samlConfig;

    private CustomSamlSettings() {}

    public static CustomSamlSettings getInstance(ConfigType configType, int accountId) {
        CustomSamlSettings instance = instances.getOrDefault(configType, new CustomSamlSettings());
        if(instance.samlConfig == null) {
            SAMLConfig samlConfig = SsoUtils.findSAMLConfig(configType, accountId);;
            instance.samlConfig = samlConfig;
        }

        return instance;
    }

    public SAMLConfig getSamlConfig() {
        return samlConfig;
    }

    public void setSamlConfig(SAMLConfig samlConfig) {
        this.samlConfig = samlConfig;
    }

    public static Saml2Settings buildSamlSettingsMap (SAMLConfig samlConfig){
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

    public static Saml2Settings getSamlSettings(SAMLConfig samlConfig) {
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

