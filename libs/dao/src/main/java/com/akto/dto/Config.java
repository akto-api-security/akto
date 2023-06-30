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
        SLACK, GOOGLE, WEBPUSH, PASSWORD, SALESFORCE, SENDGRID;
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
}
