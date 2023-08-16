package com.akto.utils;

import com.akto.dao.ConfigsDao;
import com.akto.dto.Config;
import com.auth0.AuthenticationController;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;


public abstract class Auth0 {

    private static volatile Config.Auth0Config aktoAuth0Config;

    public static String getDomain() {
        return aktoAuth0Config.getDomain();
    }

    public static String getClientId() {
        return aktoAuth0Config.getClientId();
    }

    public static String getClientSecret() {
        return aktoAuth0Config.getClientSecret();
    }

    public static String getRedirectUrl() {
        return aktoAuth0Config.getRedirectUrl();
    }

    public static AuthenticationController getInstance() throws Exception {
        if (aktoAuth0Config == null) {
            synchronized (Auth0.class) {
                if(aktoAuth0Config == null) {
                    Config config = ConfigsDao.instance.findOne("_id", Config.ConfigType.AUTH0.name());
                    if(config == null){
                        throw new Exception("Auth0 cannot work");
                    } else {
                        aktoAuth0Config = (Config.Auth0Config) config;
                    }
                    if (aktoAuth0Config.getDomain() == null || aktoAuth0Config.getClientId() == null
                            || aktoAuth0Config.getClientSecret() == null) {
                        throw new Exception("Auth0 not supported exception");
                    }
                }
            }

        }
        // JwkProvider required for RS256 tokens. If using HS256, do not use.
        JwkProvider jwkProvider = new JwkProviderBuilder(aktoAuth0Config.getDomain()).build();
        return AuthenticationController
                .newBuilder(aktoAuth0Config.getDomain(), aktoAuth0Config.getClientId(),
                        aktoAuth0Config.getClientSecret())
                .withJwkProvider(jwkProvider)

                .build();
    }

    public static String getApiToken() {
        return aktoAuth0Config.getApiToken();
    }
}
