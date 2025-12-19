package com.akto.utils;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.akto.dao.ConfigsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.akto.dto.Config.OktaConfig;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.akto.utils.sso.SsoUtils;
import com.mongodb.BasicDBObject;

public class OktaLogin {
    private static final LoggerMaker logger = new LoggerMaker(OktaLogin.class, LogDb.DASHBOARD);
    public static final int PROBE_PERIOD_IN_SECS = 60;
    private static OktaLogin instance = null;
    private OktaConfig oktaConfig = null;
    private int lastProbeTs = 0;

    public static OktaLogin getInstance() {
        logger.info("[OktaLogin.getInstance] Called");
        boolean shouldProbeAgain = true;
        if (instance != null) {
            shouldProbeAgain = Context.now() - instance.lastProbeTs >= PROBE_PERIOD_IN_SECS;
            logger.info("[OktaLogin.getInstance] Existing instance found, shouldProbeAgain: " + shouldProbeAgain + " (lastProbeTs: " + instance.lastProbeTs + ", now: " + Context.now() + ")");
        } else {
            logger.info("[OktaLogin.getInstance] No existing instance, will create new one");
        }

        if (shouldProbeAgain) {
            int accountId = Context.accountId.get() != null ? Context.accountId.get() : 1_000_000;
            logger.infoAndAddToDb("[OktaLogin.getInstance] Probing for OktaConfig with accountId: " + accountId);
            String oktaConfigId = OktaConfig.getOktaId(accountId);
            logger.info("[OktaLogin.getInstance] Looking up OktaConfig with ID: " + oktaConfigId);
            OktaConfig oktaConfig = (Config.OktaConfig) ConfigsDao.instance.findOne(Constants.ID, oktaConfigId);
            logger.infoAndAddToDb("[OktaLogin.getInstance] OktaConfig lookup result: " + (oktaConfig != null ? "FOUND (clientId: " + (oktaConfig.getClientId() != null ? "present" : "null") + ", domain: " + oktaConfig.getOktaDomainUrl() + ", organizationDomain: " + oktaConfig.getOrganizationDomain() + ", accountId: " + oktaConfig.getAccountId() + ")" : "NOT FOUND"));

            if (instance == null) {
                logger.info("[OktaLogin.getInstance] Creating new OktaLogin instance");
                instance = new OktaLogin();
            }

            instance.oktaConfig = oktaConfig;
            instance.lastProbeTs = Context.now();
            logger.info("[OktaLogin.getInstance] Updated instance with oktaConfig: " + (oktaConfig != null ? "present" : "null") + ", lastProbeTs: " + instance.lastProbeTs);
        }

        logger.info("[OktaLogin.getInstance] Returning instance: " + (instance != null ? "present" : "null") + ", oktaConfig: " + (instance != null && instance.oktaConfig != null ? "present" : "null"));
        return instance;
    }

    public static String getAuthorisationUrl() {
        logger.info("[OktaLogin.getAuthorisationUrl] Called (no email parameter)");
        OktaLogin instance = getInstance();
        if (instance == null) {
            logger.error("[OktaLogin.getAuthorisationUrl] getInstance() returned null, cannot generate authorization URL");
            return null;
        }

        OktaConfig oktaConfig = instance.getOktaConfig();
        if (oktaConfig == null) {
            logger.error("[OktaLogin.getAuthorisationUrl] OktaConfig is null, cannot generate authorization URL");
            return null;
        }

        logger.infoAndAddToDb("[OktaLogin.getAuthorisationUrl] Building authorization URL with OktaConfig: " +
            "domain=" + oktaConfig.getOktaDomainUrl() +
            ", accountId=" + oktaConfig.getAccountId() +
            ", organizationDomain=" + oktaConfig.getOrganizationDomain());

        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("client_id", oktaConfig.getClientId());
        paramMap.put("redirect_uri",oktaConfig.getRedirectUri());
        paramMap.put("response_type", "code");
        paramMap.put("scope", "openid%20email%20profile");
        int accountId = 1000000;
        if(oktaConfig.getAccountId() != 0){
            accountId = oktaConfig.getAccountId();
        }
        logger.info("[OktaLogin.getAuthorisationUrl] Using accountId for state parameter: " + accountId);
        paramMap.put("state", String.valueOf(accountId));

        String queryString = SsoUtils.getQueryString(paramMap);

        String authUrl = "https://" + oktaConfig.getOktaDomainUrl() + "/oauth2/";
        if(!oktaConfig.getAuthorisationServerId().isEmpty()){
            authUrl += oktaConfig.getAuthorisationServerId();
        }
        authUrl += "/v1/authorize?" + queryString;
        logger.infoAndAddToDb("[OktaLogin.getAuthorisationUrl] Generated authorization URL: " + authUrl);
        return authUrl;
    }

    public static String getAuthorisationUrl(String email, String signUpEmailId, String signupInvitationCode) {
        logger.infoAndAddToDb("[OktaLogin.getAuthorisationUrl] Called with email: " + email +
            ", signUpEmailId: " + (signUpEmailId != null ? signUpEmailId : "null") +
            ", signupInvitationCode: " + (signupInvitationCode != null ? "present" : "null"));

        logger.info("[OktaLogin.getAuthorisationUrl] Calling Config.getOktaConfig for email: " + email);
        OktaConfig oktaConfig = Config.getOktaConfig(email);

        if(oktaConfig == null) {
            logger.errorAndAddToDb("[OktaLogin.getAuthorisationUrl] CRITICAL: Config.getOktaConfig returned NULL for email: " + email +
                ". This means no OktaConfig found matching the user's email domain. New user signup will FAIL.", LogDb.DASHBOARD);
            return null;
        }

        logger.infoAndAddToDb("[OktaLogin.getAuthorisationUrl] Successfully retrieved OktaConfig for email: " + email +
            " -> domain: " + oktaConfig.getOktaDomainUrl() +
            ", accountId: " + oktaConfig.getAccountId() +
            ", organizationDomain: " + oktaConfig.getOrganizationDomain() +
            ", clientId: " + (oktaConfig.getClientId() != null ? "present" : "null") +
            ", redirectUri: " + oktaConfig.getRedirectUri());

        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("client_id", oktaConfig.getClientId());
        paramMap.put("redirect_uri",oktaConfig.getRedirectUri());
        paramMap.put("response_type", "code");
        paramMap.put("scope", "openid%20email%20profile");
        BasicDBObject stateObj = new BasicDBObject("accountId", String.valueOf(oktaConfig.getAccountId()));
        String stateVal = String.valueOf(oktaConfig.getAccountId());

        logger.info("[OktaLogin.getAuthorisationUrl] Initial state object: accountId=" + oktaConfig.getAccountId());

        if(!StringUtils.isEmpty(signupInvitationCode) && !StringUtils.isEmpty(signUpEmailId)) {
            logger.info("[OktaLogin.getAuthorisationUrl] Adding invitation parameters to state: " +
                "signupInvitationCode=" + signupInvitationCode + ", signUpEmailId=" + signUpEmailId);
            stateObj.append("signupInvitationCode", signupInvitationCode)
                    .append("signupEmailId", signUpEmailId);
        } else {
            logger.info("[OktaLogin.getAuthorisationUrl] No invitation code/email, state will only contain accountId");
        }

        stateVal = stateObj.toJson();
        logger.info("[OktaLogin.getAuthorisationUrl] State JSON before Base64 encoding: " + stateVal);
        String encodedState = Base64.getEncoder().encodeToString(stateVal.getBytes());
        logger.info("[OktaLogin.getAuthorisationUrl] State after Base64 encoding: " + encodedState);
        paramMap.put("state", encodedState);

        String queryString = SsoUtils.getQueryString(paramMap);
        logger.info("[OktaLogin.getAuthorisationUrl] Generated query string parameters");

        String authUrl = "https://" + oktaConfig.getOktaDomainUrl() + "/oauth2/";
        if(!oktaConfig.getAuthorisationServerId().isEmpty()){
            logger.info("[OktaLogin.getAuthorisationUrl] Using authorization server ID: " + oktaConfig.getAuthorisationServerId());
            authUrl += oktaConfig.getAuthorisationServerId();
        } else {
            logger.info("[OktaLogin.getAuthorisationUrl] No authorization server ID configured, using default");
        }
        authUrl += "/v1/authorize?" + queryString;
        logger.infoAndAddToDb("[OktaLogin.getAuthorisationUrl] Final authorization URL generated successfully: " + authUrl);
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
