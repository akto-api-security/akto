package com.akto.dto;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.Getter;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

import com.akto.dao.ConfigsDao;
import com.mongodb.client.model.Filters;

@BsonDiscriminator
public abstract class Config {

    public static final String CONFIG_SALT = "-ankush";
    private static final Set<ConfigType> ssoConfigTypes = new HashSet(Arrays.asList( ConfigType.OKTA, ConfigType.AZURE, ConfigType.GOOGLE_SAML));

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

    public String id;

    public enum ConfigType {
        SLACK, GOOGLE, WEBPUSH, PASSWORD, SALESFORCE, SENDGRID, AUTH0, GITHUB, STIGG, MIXPANEL, SLACK_ALERT, OKTA, AZURE, HYBRID_SAAS, SLACK_ALERT_USAGE, GOOGLE_SAML, AWS_WAF, SPLUNK_SIEM, AKTO_DASHBOARD_HOST_URL, CLOUDFLARE_WAF, RSA_KP, MCP_REGISTRY;
    }

    public ConfigType configType;

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
        public static final String ORGANIZATION_DOMAIN = "organizationDomain";
        private String organizationDomain;
        public static final String ACCOUNT_ID = "accountId";
        private int accountId;

        public static final String CONFIG_ID = ConfigType.OKTA.name() + CONFIG_SALT;

        public OktaConfig(){
            this.configType = ConfigType.OKTA;
        }

        public static String getOktaId(int accountId){
            return CONFIG_ID + "_" + accountId;
        }

        public OktaConfig(int id) {
            this.configType = ConfigType.OKTA;
            this.id = CONFIG_ID + "_" + id;
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

        public String getOrganizationDomain() {
            return organizationDomain;
        }
        public void setOrganizationDomain(String organizationDomain) {
            this.organizationDomain = organizationDomain;
        }

        public int getAccountId() {
            return accountId;
        }
        public void setAccountId(int accountId) {
            this.accountId = accountId;
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
        private String aiAssetsLabel;
        private String mcpAssetsLabel;

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

        public String getAiAssetsLabel() {
            return aiAssetsLabel;
        }

        public void setAiAssetsLabel(String aiAssetsLabel) {
            this.aiAssetsLabel = aiAssetsLabel;
        }

        public String getMcpAssetsLabel() {
            return mcpAssetsLabel;
        }

        public void setMcpAssetsLabel(String mcpAssetsLabel) {
            this.mcpAssetsLabel = mcpAssetsLabel;
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
    public static class CloudflareWafConfig extends Config {
        public static final String API_KEY = "apiKey";
        private String apiKey;
        public static final String EMAIL = "email";
        private String email;
        public static final String INTEGRATION_TYPE = "integrationType";
        private String integrationType;
        public static final String ACCOUNT_OR_ZONE_ID = "accountOrZoneId";
        private String accountOrZoneId;
        public static final String ACCOUNT_ID = "accountId";
        private int accountId;
        private List<String> severityLevels;
        public static final String SEVERITY_LEVELS = "severityLevels";

        public static final String _CONFIG_ID = "configId";
        public static final String CONFIG_ID = ConfigType.CLOUDFLARE_WAF.name();

        public CloudflareWafConfig() {
            this.configType = ConfigType.CLOUDFLARE_WAF;
            this.id = CONFIG_ID;
        }

        public CloudflareWafConfig(String apiKey, String email, String integrationType, String accountOrZoneId, int accountId,List<String> severityLevels) {
            this.apiKey = apiKey;
            this.email = email;
            this.integrationType = integrationType;
            this.accountOrZoneId = accountOrZoneId;
            this.accountId = accountId;
            this.id = accountId + "_" + CONFIG_ID;
            this.severityLevels = severityLevels;
        }

        public String getApiKey() {
            return apiKey;
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

        public String getIntegrationType() {
            return integrationType;
        }

        public void setIntegrationType(String integrationType) {
            this.integrationType = integrationType;
        }

        public String getAccountOrZoneId() {
            return accountOrZoneId;
        }

        public void setAccountOrZoneId(String accountOrZoneId) {
            this.accountOrZoneId = accountOrZoneId;
        }

        public int getAccountId() {
            return accountId;
        }

        public void setAccountId(int accountId) {
            this.accountId = accountId;
        }

        public static String getConfigId() {
            return CONFIG_ID;
        }

        public List<String> getSeverityLevels() {
            return severityLevels;
        }

        public void setSeverityLevels(List<String> severityLevels) {
            this.severityLevels = severityLevels;
        }
    }

    @BsonDiscriminator
    public static class AwsWafConfig extends Config {
        private String awsAccessKey;
        private String awsSecretKey;
        private String region;
        private String ruleSetId;
        private String ruleSetName;
        private int accountId;
        private List<String> severityLevels;

        public static final String CONFIG_ID = ConfigType.AWS_WAF.name();

        public AwsWafConfig() {
            this.configType = ConfigType.AWS_WAF;
            this.id = CONFIG_ID;
        }

        public AwsWafConfig(String awsAccessKey, String awsSecretKey, String region, String ruleSetId,
                String ruleSetName, int accountId,List<String> severityLevels) {
            this.awsAccessKey = awsAccessKey;
            this.awsSecretKey = awsSecretKey;
            this.region = region;
            this.ruleSetId = ruleSetId;
            this.ruleSetName = ruleSetName;
            this.accountId = accountId;
            this.id = accountId + "_" + CONFIG_ID;
        }

        public String getAwsAccessKey() {
            return awsAccessKey;
        }

        public void setAwsAccessKey(String awsAccessKey) {
            this.awsAccessKey = awsAccessKey;
        }

        public String getAwsSecretKey() {
            return awsSecretKey;
        }

        public void setAwsSecretKey(String awsSecretKey) {
            this.awsSecretKey = awsSecretKey;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getRuleSetId() {
            return ruleSetId;
        }

        public void setRuleSetId(String ruleSetId) {
            this.ruleSetId = ruleSetId;
        }

        public String getRuleSetName() {
            return ruleSetName;
        }

        public void setRuleSetName(String ruleSetName) {
            this.ruleSetName = ruleSetName;
        }

        public static String getConfigId() {
            return CONFIG_ID;
        }

        public int getAccountId() {
            return accountId;
        }

        public void setAccountId(int accountId) {
            this.accountId = accountId;
        }

        public List<String> getSeverityLevels() {
            return severityLevels;
        }

        public void setSeverityLevels(List<String> severityLevels) {
            this.severityLevels = severityLevels;
        }
       
    }

    @BsonDiscriminator
    public static class SplunkSiemConfig extends Config {
        private String splunkUrl;
        private String splunkToken;

        public static final String CONFIG_ID = ConfigType.SPLUNK_SIEM.name();

        public SplunkSiemConfig() {
            this.configType = ConfigType.SPLUNK_SIEM;
            this.id = CONFIG_ID;
        }

        public SplunkSiemConfig(String splunkUrl, String splunkToken, int accountId) {
            this.splunkUrl = splunkUrl;
            this.splunkToken = splunkToken;
            this.id = accountId + "_" + CONFIG_ID;
        }

        public String getSplunkUrl() {
            return splunkUrl;
        }

        public void setSplunkUrl(String splunkUrl) {
            this.splunkUrl = splunkUrl;
        }

        public String getSplunkToken() {
            return splunkToken;
        }

        public void setSplunkToken(String splunkToken) {
            this.splunkToken = splunkToken;
        }
       
    }

    @Getter
    @Setter
    @BsonDiscriminator
    public static class AktoHostUrlConfig extends Config {

        public static final String HOST_URL = "hostUrl";
        public static final String LAST_SYNCED_AT = "lastSyncedAt";

        private String hostUrl;
        private int lastSyncedAt;

        public AktoHostUrlConfig() {
            this.configType = ConfigType.AKTO_DASHBOARD_HOST_URL;
            this.id = ConfigType.AKTO_DASHBOARD_HOST_URL.name();
        }
    }

    @Getter
    @Setter
    @BsonDiscriminator
    public static class RSAKeyPairConfig extends Config {

        public static final String PRIVATE_KEY = "privateKey";
        public static final String PUBLIC_KEY = "publicKey";
        public static final String CREATED_AT = "createdAt";

        private String privateKey;
        private String publicKey;
        private int createdAt;

        public RSAKeyPairConfig() {
            this.configType = ConfigType.RSA_KP;
            this.id = ConfigType.RSA_KP.name() + CONFIG_SALT;
        }

        public RSAKeyPairConfig(String privateKey, String publicKey) {
            this.configType = ConfigType.RSA_KP;
            this.id = ConfigType.RSA_KP.name() + CONFIG_SALT;
            this.privateKey = privateKey;
            this.publicKey = publicKey;
        }

        public RSAKeyPairConfig(String privateKey, String publicKey, int createdAt) {
            this.configType = ConfigType.RSA_KP;
            this.id = ConfigType.RSA_KP.name() + CONFIG_SALT;
            this.privateKey = privateKey;
            this.publicKey = publicKey;
            this.createdAt = createdAt;
        }
    }

    public static boolean isConfigSSOType(ConfigType configType){
        if(configType == null){
            return false;
        }
        return ssoConfigTypes.contains(configType);
    }

    public static OktaConfig getOktaConfig(int accountId) {
        String id =  OktaConfig.getOktaId(accountId);
        OktaConfig config = (OktaConfig) ConfigsDao.instance.findOne(
                Filters.and(
                    Filters.eq("_id", id),
                    Filters.eq(OktaConfig.ACCOUNT_ID, accountId)
                )
        );
        return config;
    }

    public static OktaConfig getOktaConfig(String userEmail){
        if (userEmail == null || userEmail.trim().isEmpty()) {
            return null;
        }
        String[] companyKeyArr = userEmail.split("@");
        if(companyKeyArr == null || companyKeyArr.length < 2){
            return null;
        }

        String domain = companyKeyArr[1];
        OktaConfig config = (OktaConfig) ConfigsDao.instance.findOne(
                Filters.eq(OktaConfig.ORGANIZATION_DOMAIN, domain)
        );
        return config;
    }

    @Getter
    @Setter
    @BsonDiscriminator
    public static class McpRegistryConfig extends Config {

        public static final String REGISTRIES = "registries";
        public static final String CONFIG_ID = ConfigType.MCP_REGISTRY.name();

        private List<McpRegistry> registries;

        public McpRegistryConfig() {
            this.configType = ConfigType.MCP_REGISTRY;
            this.id = CONFIG_ID;
        }

        public McpRegistryConfig(List<McpRegistry> registries, int accountId) {
            this.registries = registries;
            this.id = accountId + "_" + CONFIG_ID;
            this.configType = ConfigType.MCP_REGISTRY;
        }

        @Getter
        @Setter
        public static class McpRegistry {
            public static final String ID = "id";
            public static final String NAME = "name";
            public static final String URL = "url";
            public static final String IS_DEFAULT = "isDefault";

            private String id;
            private String name;
            private String url;
            private boolean isDefault;

            public McpRegistry() {}

            public McpRegistry(String id, String name, String url, boolean isDefault) {
                this.id = id;
                this.name = name;
                this.url = url;
                this.isDefault = isDefault;
            }
        }
    }
}
