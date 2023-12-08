package com.akto.dto;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator
public abstract class Config {

    public static final String CONFIG_SALT = "-ankush";

    public ConfigType getConfigType() {
        return configType;
    }

    public void setConfigType(ConfigType configType) {
        this.configType = configType;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    String id;

    public enum ConfigType {
        SLACK, GOOGLE, WEBPUSH, PASSWORD, SALESFORCE, SENDGRID, AUTH0, GITHUB, OKTA, AZURE;
    }

    ConfigType configType;

    @BsonDiscriminator
    public static class SlackConfig extends Config {
        String clientId;

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getRedirect_url() {
            return redirect_url;
        }

        public void setRedirect_url(String redirect_url) {
            this.redirect_url = redirect_url;
        }

        String clientSecret;

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        String redirect_url;

        public SlackConfig() {
            this.configType = ConfigType.SLACK;
            this.id = configType.name()+"-ankush";
        }
    }

    @BsonDiscriminator
    public static class SendgridConfig extends Config {
        String sendgridSecretKey;
        public static final String CONFIG_ID = ConfigType.SENDGRID.name() + CONFIG_SALT;

        public SendgridConfig() {
            this.configType = ConfigType.SENDGRID;
            this.id = CONFIG_ID;
        }

        public String getSendgridSecretKey() {return this.sendgridSecretKey;}

        public void setSendgridSecretKey(String sendgridSecretKey) {this.sendgridSecretKey = sendgridSecretKey;}
    }

    @BsonDiscriminator
    public static class GoogleConfig extends Config {

        String clientId, projectId, authURI, tokenURI, certURL, clientSecret, jsOrigins;

        public GoogleConfig() {
            this.configType = ConfigType.GOOGLE;
            this.id = configType.name()+"-ankush";
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getProjectId() {
            return projectId;
        }

        public void setProjectId(String projectId) {
            this.projectId = projectId;
        }

        public String getAuthURI() {
            return authURI;
        }

        public void setAuthURI(String authURI) {
            this.authURI = authURI;
        }

        public String getTokenURI() {
            return tokenURI;
        }

        public void setTokenURI(String tokenURI) {
            this.tokenURI = tokenURI;
        }

        public String getCertURL() {
            return certURL;
        }

        public void setCertURL(String certURL) {
            this.certURL = certURL;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getJsOrigins() {
            return jsOrigins;
        }

        public void setJsOrigins(String jsOrigins) {
            this.jsOrigins = jsOrigins;
        }
    }

    @BsonDiscriminator
    public static class WebpushConfig extends Config {

        String publicKey;
        String privateKey;

        public WebpushConfig() {
            this.configType = ConfigType.WEBPUSH;
            this.id = configType.name()+"-ankush";
        }

        public String getPublicKey() {
            return publicKey;
        }

        public void setPublicKey(String publicKey) {
            this.publicKey = publicKey;
        }

        public String getPrivateKey() {
            return privateKey;
        }

        public void setPrivateKey(String privateKey) {
            this.privateKey = privateKey;
        }


    }

    @BsonDiscriminator
    public static class SalesforceConfig extends Config {
        String consumer_key,consumer_secret, redirect_uri, response_type;

        public SalesforceConfig() {
            this.configType = ConfigType.SALESFORCE;
            this.id = configType.name() + "-ankush";
        }

        public SalesforceConfig(String consumer_key,String consumer_secret, String redirect_uri, String response_type) {
            this.configType = ConfigType.SALESFORCE;
            this.id = configType.name() + "-ankush";
            this.consumer_key = consumer_key;
            this.consumer_secret = consumer_secret;
            this.redirect_uri = redirect_uri;
            this.response_type = response_type;
        }

        public String getConsumer_key() {
            return consumer_key;
        }

        public void setConsumer_key(String consumer_key) {
            this.consumer_key = consumer_key;
        }

        public String getConsumer_secret() {
            return consumer_secret;
        }

        public void setConsumer_secret(String consumer_secret) {
            this.consumer_secret = consumer_secret;
        }

        public String getRedirect_uri() {
            return redirect_uri;
        }

        public void setRedirect_uri(String redirect_uri) {
            this.redirect_uri = redirect_uri;
        }

        public String getResponse_type() {
            return response_type;
        }

        public void setResponse_type(String response_type) {
            this.response_type = response_type;
        }
    }

    @BsonDiscriminator
    public static class Auth0Config extends Config {

        String clientId;
        String redirectUrl;
        String clientSecret;
        String domain;

        String apiToken;

        public Auth0Config() {
            this.configType = ConfigType.AUTH0;
            this.id = configType.name();
        }

        public Auth0Config(String clientId, String clientSecret, String domain, String redirectUrl) {
            this.configType = ConfigType.AUTH0;
            this.id = configType.name();
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.domain = domain;
            this.redirectUrl = redirectUrl;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getRedirectUrl() {
            return redirectUrl;
        }

        public void setRedirectUrl(String redirectUrl) {
            this.redirectUrl = redirectUrl;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getDomain() {
            return domain;
        }

        public void setDomain(String domain) {
            this.domain = domain;
        }

        public String getApiToken() {
            return apiToken;
        }

        public void setApiToken(String apiToken) {
            this.apiToken = apiToken;
        }
    }

    @BsonDiscriminator
    public static class GithubConfig extends Config {
        private String clientId;

        private String clientSecret;
        public static final String CONFIG_ID = ConfigType.GITHUB.name() + CONFIG_SALT;

        public GithubConfig() {
            this.configType = ConfigType.GITHUB;
            this.id = CONFIG_ID;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }
    }

    @BsonDiscriminator
    public static class OktaConfig extends Config {
        private String clientId;
        private String clientSecret;
        private String oktaDomainUrl;
        private String authorisationServerId;
        private String redirectUri;
        
        public static final String CONFIG_ID = ConfigType.OKTA.name() + CONFIG_SALT;

        public OktaConfig() {
            this.configType = ConfigType.OKTA;
            this.id = CONFIG_ID;
        }
        
        public String getClientId() {
            return clientId;
        }
        public void setClientId(String clientId) {
            this.clientId = clientId;
        }
        
        public String getClientSecret() {
            return clientSecret;
        }
        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }
        
        public String getOktaDomainUrl() {
            return oktaDomainUrl;
        }
        public void setOktaDomainUrl(String oktaDomainUrl) {
            this.oktaDomainUrl = oktaDomainUrl;
        }

        public String getAuthorisationServerId() {
            return authorisationServerId;
        }
        public void setAuthorisationServerId(String authorisationServerId) {
            this.authorisationServerId = authorisationServerId;
        }

        public String getRedirectUri() {
            return redirectUri;
        }

        public void setRedirectUri(String redirectUri) {
            this.redirectUri = redirectUri;
        }
    }

    @BsonDiscriminator
    public static class AzureConfig extends Config{
        
        private String x509Certificate ;
        private String azureEntityId ;
        private String loginUrl ;
        private String acsUrl ;
        private String applicationIdentifier;

        public static final String CONFIG_ID = ConfigType.AZURE.name() + CONFIG_SALT;

        public AzureConfig() {
            this.configType = ConfigType.AZURE;
            this.id = CONFIG_ID;
        }

        public String getX509Certificate() {
            return x509Certificate;
        }

        public void setX509Certificate(String x509Certificate) {
            this.x509Certificate = x509Certificate;
        }

        public String getAzureEntityId() {
            return azureEntityId;
        }

        public void setAzureEntityId(String azureEntityId) {
            this.azureEntityId = azureEntityId;
        }

        public String getLoginUrl() {
            return loginUrl;
        }

        public void setLoginUrl(String loginUrl) {
            this.loginUrl = loginUrl;
        }

        public String getAcsUrl() {
            return acsUrl;
        }

        public void setAcsUrl(String acsUrl) {
            this.acsUrl = acsUrl;
        }

        public String getApplicationIdentifier() {
            return applicationIdentifier;
        }

        public void setApplicationIdentifier(String applicationIdentifier) {
            this.applicationIdentifier = applicationIdentifier;
        }
    }
}
