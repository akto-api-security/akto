package com.akto.dto.sso;
import com.akto.dto.Config;

public class SAMLConfig extends Config  {

    private String applicationIdentifier;
    private String loginUrl;
    private String x509Certificate;
    private String acsUrl;
    private String entityId;
    private String organizationKey;

    public SAMLConfig(){}
    
    public SAMLConfig(ConfigType configType, int accountId) {
        this.setConfigType(configType);
        String CONFIG_ID = String.valueOf(accountId);
        this.setId(CONFIG_ID);
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

    public String getOrganizationKey() {
        return organizationKey;
    }

    public void setOrganizationKey(String organizationKey) {
        this.organizationKey = organizationKey;
    }

}
