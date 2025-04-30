package com.akto.dto.sso;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

import com.akto.dto.Config;

@BsonDiscriminator
public class SAMLConfig extends Config  {

    public static final String IDENTIFIER = "applicationIdentifier";
    private String applicationIdentifier;

    public static final String LOGIN_URL = "loginUrl";
    private String loginUrl;

    public static final String CERTIFICATE = "x509Certificate";
    private String x509Certificate;

    public static final String ACS_URL = "acsUrl";
    private String acsUrl;

    public static final String ENTITY_ID = "entityId";
    private String entityId;

    public static final String ORGANIZATION_DOMAIN = "organizationDomain";
    private String organizationDomain;

    public SAMLConfig(){}
    
    public SAMLConfig(ConfigType configType, int accountId) {
        this.setConfigType(configType);
        String CONFIG_ID = String.valueOf(accountId);
        this.setId(CONFIG_ID);
    }

    public static SAMLConfig convertAzureConfigToSAMLConfig(Config.AzureConfig azureConfig) {
        SAMLConfig samlConfig = new SAMLConfig();
        samlConfig.setApplicationIdentifier(azureConfig.getApplicationIdentifier());
        samlConfig.setX509Certificate(azureConfig.getX509Certificate());
        samlConfig.setLoginUrl(azureConfig.getLoginUrl());
        samlConfig.setAcsUrl(azureConfig.getAcsUrl());
        samlConfig.setEntityId(azureConfig.getAzureEntityId()); 
        samlConfig.setConfigType(ConfigType.AZURE);
        return samlConfig;
    }

    public String getApplicationIdentifier() {
        return applicationIdentifier;
    }

    public void setApplicationIdentifier(String applicationIdentifier) {
        this.applicationIdentifier = applicationIdentifier;
    }

    public String getLoginUrl() {
        return loginUrl;
    }

    public void setLoginUrl(String loginUrl) {
        this.loginUrl = loginUrl;
    }

    public String getX509Certificate() {
        return x509Certificate;
    }

    public void setX509Certificate(String x509Certificate) {
        this.x509Certificate = x509Certificate;
    }

    public String getAcsUrl() {
        return acsUrl;
    }

    public void setAcsUrl(String acsUrl) {
        this.acsUrl = acsUrl;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getOrganizationDomain() {
        return organizationDomain;
    }

    public void setOrganizationDomain(String organizationDomain) {
        this.organizationDomain = organizationDomain;
    }

}
