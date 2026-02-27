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
        SLACK, GOOGLE, WEBPUSH, PASSWORD, SALESFORCE, SENDGRID, AUTH0, GITHUB, STIGG, MIXPANEL, SLACK_ALERT, OKTA, AZURE, HYBRID_SAAS, SLACK_ALERT_USAGE, SLACK_ALERT_CYBORG, CYBORG_TOOLS_AUTH;
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
        private String githubUrl;
        private String githubApiUrl;
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

        public String getGithubUrl() {
            return githubUrl;
        }

        public void setGithubUrl(String githubUrl) {
            this.githubUrl = githubUrl;
        }

        public String getGithubApiUrl() {
            return githubApiUrl;
        }

        public void setGithubApiUrl(String githubApiUrl) {
            this.githubApiUrl = githubApiUrl;
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
    public static class StiggConfig extends Config {
        private String clientKey;
        private String serverKey;
        private String signingKey;
        private String saasFreePlanId;
        private String onPremFreePlanId;

        private String activeEndpointsLabel;

        private String testRunsLabel;

        private String customTestsLabel;

        private String activeAccountsLabel;
        public static final String CONFIG_ID = ConfigType.STIGG.name() + CONFIG_SALT;

        public StiggConfig() {
            this.configType = ConfigType.STIGG;
            this.id = CONFIG_ID;
        }

        public String getClientKey() {
            return clientKey;
        }

        public void setClientKey(String clientKey) {
            this.clientKey = clientKey;
        }

        public String getServerKey() {
            return serverKey;
        }

        public void setServerKey(String serverKey) {
            this.serverKey = serverKey;
        }

        public String getSigningKey() {
            return signingKey;
        }

        public void setSigningKey(String signingKey) {
            this.signingKey = signingKey;
        }

        public String getSaasFreePlanId() {
            return saasFreePlanId;
        }

        public void setSaasFreePlanId(String saasFreePlanId) {
            this.saasFreePlanId = saasFreePlanId;
        }

        public String getOnPremFreePlanId() {
            return onPremFreePlanId;
        }

        public void setOnPremFreePlanId(String onPremFreePlanId) {
            this.onPremFreePlanId = onPremFreePlanId;
        }

        public String getActiveEndpointsLabel() {
            return activeEndpointsLabel;
        }

        public void setActiveEndpointsLabel(String activeEndpointsLabel) {
            this.activeEndpointsLabel = activeEndpointsLabel;
        }

        public String getTestRunsLabel() {
            return testRunsLabel;
        }

        public void setTestRunsLabel(String testRunsLabel) {
            this.testRunsLabel = testRunsLabel;
        }

        public String getCustomTestsLabel() {
            return customTestsLabel;
        }

        public void setCustomTestsLabel(String customTestsLabel) {
            this.customTestsLabel = customTestsLabel;
        }

        public String getActiveAccountsLabel() {
            return activeAccountsLabel;
        }

        public void setActiveAccountsLabel(String activeAccountsLabel) {
            this.activeAccountsLabel = activeAccountsLabel;
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
    @BsonDiscriminator
    public static class MixpanelConfig extends Config {
        private String projectToken;

        public static final String CONFIG_ID = ConfigType.MIXPANEL.name() + CONFIG_SALT;

        public MixpanelConfig() {
            this.configType = ConfigType.MIXPANEL;
            this.id = CONFIG_ID;
        }

        public MixpanelConfig(String projectToken) {
            this.configType = ConfigType.MIXPANEL;
            this.id = configType.name();
            this.projectToken = projectToken;
        }

        public String getProjectToken() {
            return projectToken;
        }

        public void setProjectToken(String projectToken) {
            this.projectToken = projectToken;
        }
    }


    @BsonDiscriminator
    public static class SlackAlertConfig extends Config {
        private String slackWebhookUrl;

        public static final String CONFIG_ID = ConfigType.SLACK_ALERT.name() + CONFIG_SALT;

        public SlackAlertConfig() {
            this.configType = ConfigType.SLACK_ALERT;
            this.id = CONFIG_ID;
        }

        public SlackAlertConfig(String slackWebhookUrl) {
            this.configType = ConfigType.SLACK_ALERT;
            this.id = configType.name();
            this.slackWebhookUrl = slackWebhookUrl;
        }

        public String getSlackWebhookUrl() {
            return slackWebhookUrl;
        }

        public void setSlackWebhookUrl(String slackWebhookUrl) {
            this.slackWebhookUrl = slackWebhookUrl;
        }
    }

    @BsonDiscriminator
    public static class SlackAlertUsageConfig extends Config {
        private String slackWebhookUrl;

        public static final String CONFIG_ID = ConfigType.SLACK_ALERT_USAGE.name() + CONFIG_SALT;

        public SlackAlertUsageConfig() {
            this.configType = ConfigType.SLACK_ALERT_USAGE;
            this.id = CONFIG_ID;
        }

        public String getSlackWebhookUrl() {
            return slackWebhookUrl;
        }

        public void setSlackWebhookUrl(String slackWebhookUrl) {
            this.slackWebhookUrl = slackWebhookUrl;
        }
    }

    @BsonDiscriminator
    public static class SlackAlertCyborgConfig extends Config {
        private String slackWebhookUrl;

        public static final String CONFIG_ID = ConfigType.SLACK_ALERT_CYBORG.name() + CONFIG_SALT;

        public SlackAlertCyborgConfig() {
            this.configType = ConfigType.SLACK_ALERT_CYBORG;
            this.id = CONFIG_ID;
        }

        public String getSlackWebhookUrl() {
            return slackWebhookUrl;
        }

        public void setSlackWebhookUrl(String slackWebhookUrl) {
            this.slackWebhookUrl = slackWebhookUrl;
        }
    }

    @BsonDiscriminator
    public static class HybridSaasConfig extends Config {
        String privateKey;
        String publicKey;

        public HybridSaasConfig() {
            this.configType = ConfigType.HYBRID_SAAS;
            this.id = configType.name();
        }

        public HybridSaasConfig(String privateKey, String publicKey) {
            this.configType = ConfigType.HYBRID_SAAS;
            this.id = configType.name();
            this.privateKey = privateKey;
            this.publicKey = publicKey;
        }

        public String getPrivateKey() {
            return privateKey;
        }

        public void setPrivateKey(String privateKey) {
            this.privateKey = privateKey;
        }

        public String getPublicKey() {
            return publicKey;
        }

        public void setPublicKey(String publicKey) {
            this.publicKey = publicKey;
        }
    }

    @BsonDiscriminator
    public static class CyborgToolsAuthConfig extends Config {
        private String staticToken;

        public static final String CONFIG_ID = ConfigType.CYBORG_TOOLS_AUTH.name() + CONFIG_SALT;

        public CyborgToolsAuthConfig() {
            this.configType = ConfigType.CYBORG_TOOLS_AUTH;
            this.id = CONFIG_ID;
        }

        public CyborgToolsAuthConfig(String staticToken) {
            this.configType = ConfigType.CYBORG_TOOLS_AUTH;
            this.id = CONFIG_ID;
            this.staticToken = staticToken;
        }

        public String getStaticToken() {
            return staticToken;
        }

        public void setStaticToken(String staticToken) {
            this.staticToken = staticToken;
        }
    }

}
