package com.akto.dto;

import com.akto.dto.Config.ConfigType;
import com.mongodb.BasicDBObject;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

import java.util.Base64;
import java.util.HashMap;

@BsonDiscriminator
public abstract class SignupInfo {
    String key;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Config.ConfigType getConfigType() {
        return configType;
    }

    public void setConfigType(Config.ConfigType configType) {
        this.configType = configType;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    Config.ConfigType configType;
    int timestamp = (int) (System.currentTimeMillis()/1000l);

    @BsonDiscriminator
    public static class GoogleSignupInfo extends SignupInfo {
        long expiresInSeconds;

        public GoogleSignupInfo() {}

        String accessToken;
        String refreshToken;

        public GoogleSignupInfo(String key, String accessToken, String refreshToken, long expiresInSeconds) {
            this.key = key;
            this.configType = Config.ConfigType.GOOGLE;
            this.expiresInSeconds = expiresInSeconds;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }

        public long getExpiresInSeconds() {
            return expiresInSeconds;
        }

        public void setExpiresInSeconds(long expiresInSeconds) {
            this.expiresInSeconds = expiresInSeconds;
        }
    }

    @BsonDiscriminator
    public static class SlackSignupInfo extends SignupInfo {

        public SlackSignupInfo() {}

        public SlackSignupInfo(String key, boolean ok, String appId, String authedUserAccessToken, String authedUserId, String authedUserScope,
                               String authedUserTokenType, String scope, String tokenType, String accessToken, String botUserId,
                               String teamName, String teamId, String enterpriseName, String enterpriseId, boolean isEnterpriseInstall) {
            this.key = key;
            this.ok = ok;
            this.appId = appId;
            this.authedUserAccessToken = authedUserAccessToken;
            this.authedUserId = authedUserId;
            this.authedUserScope = authedUserScope;
            this.authedUserTokenType = authedUserTokenType;
            this.scope = scope;
            this.tokenType = tokenType;
            this.accessToken = accessToken;
            this.botUserId = botUserId;
            this.teamName = teamName;
            this.teamId = teamId;
            this.enterpriseName = enterpriseName;
            this.enterpriseId = enterpriseId;
            this.isEnterpriseInstall = isEnterpriseInstall;
            this.configType = Config.ConfigType.SLACK;
        }

        private boolean ok;
        private String appId;
        private String authedUserAccessToken;
        private String authedUserId;
        private String authedUserScope;
        private String authedUserTokenType;
        private String scope;
        private String tokenType;
        private String accessToken;
        private String botUserId;
        private String teamName;
        private String teamId;
        private String enterpriseName;
        private String enterpriseId;
        private boolean isEnterpriseInstall;

        public boolean isOk() {
            return ok;
        }

        public void setOk(boolean ok) {
            this.ok = ok;
        }

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getAuthedUserAccessToken() {
            return authedUserAccessToken;
        }

        public void setAuthedUserAccessToken(String authedUserAccessToken) {
            this.authedUserAccessToken = authedUserAccessToken;
        }

        public String getAuthedUserId() {
            return authedUserId;
        }

        public void setAuthedUserId(String authedUserId) {
            this.authedUserId = authedUserId;
        }

        public String getAuthedUserScope() {
            return authedUserScope;
        }

        public void setAuthedUserScope(String authedUserScope) {
            this.authedUserScope = authedUserScope;
        }

        public String getAuthedUserTokenType() {
            return authedUserTokenType;
        }

        public void setAuthedUserTokenType(String authedUserTokenType) {
            this.authedUserTokenType = authedUserTokenType;
        }

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }

        public String getTokenType() {
            return tokenType;
        }

        public void setTokenType(String tokenType) {
            this.tokenType = tokenType;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getBotUserId() {
            return botUserId;
        }

        public void setBotUserId(String botUserId) {
            this.botUserId = botUserId;
        }

        public String getTeamName() {
            return teamName;
        }

        public void setTeamName(String teamName) {
            this.teamName = teamName;
        }

        public String getTeamId() {
            return teamId;
        }

        public void setTeamId(String teamId) {
            this.teamId = teamId;
        }

        public String getEnterpriseName() {
            return enterpriseName;
        }

        public void setEnterpriseName(String enterpriseName) {
            this.enterpriseName = enterpriseName;
        }

        public String getEnterpriseId() {
            return enterpriseId;
        }

        public void setEnterpriseId(String enterpriseId) {
            this.enterpriseId = enterpriseId;
        }

        public boolean isEnterpriseInstall() {
            return isEnterpriseInstall;
        }

        public void setEnterpriseInstall(boolean enterpriseInstall) {
            isEnterpriseInstall = enterpriseInstall;
        }
    }

    @BsonDiscriminator
    public static class WebpushSubscriptionInfo extends SignupInfo {

        public WebpushSubscriptionInfo() {}

        public WebpushSubscriptionInfo(BasicDBObject obj) {
            this.endpoint = obj.getString("endpoint");
            HashMap<String, String> keys = (HashMap<String, String>) obj.get("keys");
            this.auth = keys.get("auth");
            this.authKey = keys.get("p256dh");
            this.configType = Config.ConfigType.WEBPUSH;
            this.key = configType+"-ankush";
        }


        private String auth;
        private String authKey;
        private String endpoint;

        public String getAuthKey() {
            return authKey;
        }

        public void setAuthKey(String authKey) {
            this.authKey = authKey;
        }

        public void setAuth(String auth) {
            this.auth = auth;
        }

        public String getAuth() {
            return auth;
        }

        /**
         * Returns the base64 encoded auth string as a byte[]
         */
        public byte[] authAsBytes() {
            return Base64.getUrlDecoder().decode(getAuth());
        }

        /**
         * Returns the base64 encoded public key string as a byte[]
         */
        public byte[] keyAsBytes() {
            return Base64.getUrlDecoder().decode(getAuthKey());
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getEndpoint() {
            return endpoint;
        }
    }

    @BsonDiscriminator
    public static class PasswordHashInfo extends SignupInfo {

        String passhash, salt;

        public PasswordHashInfo() {}

        public PasswordHashInfo(String passhash, String salt) {
            this.passhash = passhash;
            this.salt = salt;
            this.configType = Config.ConfigType.PASSWORD;
            this.key = configType+"-ankush";
        }

        public String getPasshash() {
            return passhash;
        }

        public void setPasshash(String passhash) {
            this.passhash = passhash;
        }

        public String getSalt() {
            return salt;
        }

        public void setSalt(String salt) {
            this.salt = salt;
        }

    }

    @BsonDiscriminator
    public static class Auth0SignupInfo extends SignupInfo {
        String accessToken;
        String refreshToken;
        String name;
        String email;


        public Auth0SignupInfo(){

        }
        public Auth0SignupInfo(String accessToken, String refreshToken, String name, String email) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.name = name;
            this.email = email;
            this.configType = Config.ConfigType.AUTH0;
            this.key = this.configType.name();
        }

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    public static class GithubSignupInfo extends SignupInfo {
        private String accessToken;
        private String refreshToken;
        private int refreshTokenExpiry;
        private String email;
        private String username;

        public GithubSignupInfo() {

        }

        public GithubSignupInfo(String accessToken, String refreshToken, int refreshTokenExpiry, String username, String email) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.refreshTokenExpiry = refreshTokenExpiry;
            this.email = email;
            this.username = username;
            this.configType = Config.ConfigType.GITHUB;
            this.key = this.configType.name();
        }

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }

        public int getRefreshTokenExpiry() {
            return refreshTokenExpiry;
        }

        public void setRefreshTokenExpiry(int refreshTokenExpiry) {
            this.refreshTokenExpiry = refreshTokenExpiry;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    public static class OktaSignupInfo extends SignupInfo {
        private String accessToken;
        private String username;

        public OktaSignupInfo() {

        }

        public OktaSignupInfo(String accessToken, String username) {
            this.accessToken = accessToken;
            this.username = username;
            this.configType = Config.ConfigType.OKTA;
            this.key = this.configType.name();
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }
        
        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }
    }

    public static class SamlSsoSignupInfo extends SignupInfo {
        private String username;
        private String email;

        public SamlSsoSignupInfo (){}

        public SamlSsoSignupInfo (String username, String email, ConfigType configType) {
            this.username = username ;
            this.email = email;
            this.configType = configType;
            this.key = configType.name();
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }
    }
}
