package com.akto.utils;

import java.util.HashMap;
import java.util.Map;


import com.akto.dao.ConfigsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.akto.dto.Config.OktaConfig;
import com.akto.utils.sso.SsoUtils;

public class OktaLogin {
    public static final int PROBE_PERIOD_IN_SECS = 60;
    private static OktaLogin instance = null;
    private OktaConfig oktaConfig = null;
    private int lastProbeTs = 0;

    public static OktaLogin getInstance() {
        boolean shouldProbeAgain = true;
        if (instance != null) {
            shouldProbeAgain = Context.now() - instance.lastProbeTs >= PROBE_PERIOD_IN_SECS;
        }

        if (shouldProbeAgain) {
            OktaConfig oktaConfig = (Config.OktaConfig) ConfigsDao.instance.findOne("_id", "OKTA-ankush");
            if (instance == null) {
                instance = new OktaLogin();
            }

            instance.oktaConfig = oktaConfig;
            instance.lastProbeTs = Context.now();
        }

        return instance;
    }

    public static String getAuthorisationUrl() {
        if (getInstance() == null) return null;

        OktaConfig oktaConfig = getInstance().getOktaConfig();
        if (oktaConfig == null) return null;

        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("client_id", oktaConfig.getClientId());
        paramMap.put("redirect_uri",oktaConfig.getRedirectUri());
        paramMap.put("response_type", "code");
        paramMap.put("scope", "openid%20email%20profile");
        paramMap.put("state", "login");

        String queryString = SsoUtils.getQueryString(paramMap);

        String authUrl = "https://" + oktaConfig.getOktaDomainUrl() + "/oauth2/" + oktaConfig.getAuthorisationServerId() + "/v1/authorize?" + queryString;
        return authUrl;
    }

    public static String getAuthorisationUrl(String email) {
        OktaConfig oktaConfig = Config.getOktaConfig(email);

        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("client_id", oktaConfig.getClientId());
        paramMap.put("redirect_uri",oktaConfig.getRedirectUri());
        paramMap.put("response_type", "code");
        paramMap.put("scope", "openid%20email%20profile");
        paramMap.put("state", String.valueOf(oktaConfig.getAccountId()));

        String queryString = SsoUtils.getQueryString(paramMap);

        String authUrl = "https://" + oktaConfig.getOktaDomainUrl() + "/oauth2/" + oktaConfig.getAuthorisationServerId() + "/v1/authorize?" + queryString;
        return authUrl;
    }

    private OktaLogin() {
    }

    public OktaConfig getOktaConfig() {
        return this.oktaConfig;
    }

    public int getLastProbeTs() {
        return lastProbeTs;
    }
}
