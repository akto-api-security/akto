package com.akto.action.user;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import org.bson.conversions.Bson;

import com.akto.action.UserAction;
import com.akto.dao.ConfigsDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.akto.dto.Config.OktaConfig;
import com.akto.dto.RBAC;
import com.akto.dto.User;
import com.akto.util.Constants;
import com.akto.utils.sso.SsoUtils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;

public class OktaSsoAction extends UserAction {

    private String clientId;
    private String clientSecret;
    private String authorisationServerId;
    private String oktaDomain;
    private String redirectUri;
    /** Optional: Okta API token (SSWS) to read user group membership when groups are not in the access token. */
    private String managementApiToken;
    private boolean oktaApiTokenConfigured;
    private Map<String, String> groupRoleMapping;

    public String addOktaSso() {
        if (SsoUtils.isAnySsoActive()) {
            addActionError("A SSO Integration already exists.");
            return ERROR.toUpperCase();
        }

        int accountId = Context.accountId.get();

        Config.OktaConfig oktaConfig = new Config.OktaConfig(accountId);
        oktaConfig.setClientId(clientId);
        oktaConfig.setClientSecret(clientSecret);
        oktaConfig.setAuthorisationServerId(authorisationServerId);
        oktaConfig.setOktaDomainUrl(oktaDomain);
        oktaConfig.setRedirectUri(redirectUri);
        oktaConfig.setAccountId(Context.accountId.get());
        if (managementApiToken != null && !managementApiToken.trim().isEmpty()) {
            oktaConfig.setApiToken(managementApiToken.trim());
        }
        String userLogin = getSUser().getLogin();
        String domain = userLogin.split("@")[1];
        oktaConfig.setOrganizationDomain(domain);
        ConfigsDao.instance.insertOne(oktaConfig);

        return SUCCESS.toUpperCase();
    }

    public String saveOktaManagementApiToken() {
        int accountId = Context.accountId.get();
        OktaConfig oktaConfig = (OktaConfig) ConfigsDao.instance.findOne(Constants.ID, OktaConfig.getOktaId(accountId));
        if (oktaConfig == null) {
            addActionError("Okta SSO is not configured.");
            return ERROR.toUpperCase();
        }
        if (managementApiToken == null || managementApiToken.trim().isEmpty()) {
            addActionError("API token is required. Create in Okta: Security → API → Tokens.");
            return ERROR.toUpperCase();
        }
        ConfigsDao.instance.updateOne(
                Filters.eq(Constants.ID, OktaConfig.getOktaId(accountId)),
                Updates.set("apiToken", managementApiToken.trim()));
        oktaApiTokenConfigured = true;
        return SUCCESS.toUpperCase();
    }

    public String deleteOktaSso() {
        int accountId = Context.accountId.get();
        Bson idFilter = Filters.eq(Constants.ID, OktaConfig.getOktaId(accountId));
        DeleteResult result = ConfigsDao.instance.deleteAll(idFilter);

        if (result.getDeletedCount() > 0) {
            for (Object obj : UsersDao.instance.getAllUsersInfoForTheAccount(Context.accountId.get())) {
                BasicDBObject detailsObj = (BasicDBObject) obj;
                UsersDao.instance.updateOne("login", detailsObj.getString(User.LOGIN), Updates.set("refreshTokens", new ArrayList<>()));
                UsersDao.instance.updateOne("login", detailsObj.getString(User.LOGIN), Updates.unset("signupInfoMap.OKTA"));
            }
        }

        return SUCCESS.toUpperCase();
    }

    public String saveOktaGroupRoleMapping() {
        int accountId = Context.accountId.get();
        OktaConfig oktaConfig = (OktaConfig) ConfigsDao.instance.findOne(Constants.ID, OktaConfig.getOktaId(accountId));
        if (oktaConfig == null) {
            addActionError("Okta SSO is not configured.");
            return ERROR.toUpperCase();
        }
        Map<String, String> activeMapping = groupRoleMapping != null ? groupRoleMapping : Collections.<String, String>emptyMap();
        String validationError = validateRoleMappingValues(activeMapping);
        if (validationError != null) {
            addActionError(validationError);
            return ERROR.toUpperCase();
        }
        ConfigsDao.instance.updateOne(
            Filters.eq(Constants.ID, OktaConfig.getOktaId(accountId)),
            Updates.combine(
                Updates.set("mappingType", OktaConfig.MappingType.GROUP.name()),
                Updates.set("groupRoleMapping", activeMapping),
                Updates.set("oktaRoleMapping", null)
            )
        );
        return SUCCESS.toUpperCase();
    }

    private String validateRoleMappingValues(Map<String, String> mapping) {
        if (mapping == null) return null;
        for (String role : mapping.values()) {
            try {
                RBAC.Role.valueOf(role);
            } catch (IllegalArgumentException e) {
                return "Invalid Akto role: " + role + ". Valid values are ADMIN, MEMBER, DEVELOPER, GUEST.";
            }
        }
        return null;
    }

    @Override
    public String execute() throws Exception {
        int accountId = Context.accountId.get();
        Config.OktaConfig oktaConfig = (Config.OktaConfig) ConfigsDao.instance.findOne(Constants.ID, OktaConfig.getOktaId(accountId));

        if (SsoUtils.isAnySsoActive() && oktaConfig == null) {
            addActionError("A different SSO Integration already exists.");
            return ERROR.toUpperCase();
        }

        if (oktaConfig != null) {
            this.clientId = oktaConfig.getClientId();
            this.oktaDomain = oktaConfig.getOktaDomainUrl();
            this.authorisationServerId = oktaConfig.getAuthorisationServerId();
            this.redirectUri = oktaConfig.getRedirectUri();
            this.groupRoleMapping = oktaConfig.getGroupRoleMapping();
            String tok = oktaConfig.getApiToken();
            this.oktaApiTokenConfigured = tok != null && !tok.isEmpty();
        }

        return SUCCESS.toUpperCase();
    }

    public String getOktaDomain() {
        return oktaDomain;
    }

    public void setOktaDomain(String oktaDomain) {
        this.oktaDomain = oktaDomain;
    }

    public String getAuthorisationServerId() {
        return authorisationServerId;
    }
    public void setAuthorisationServerId(String authorisationServerId) {
        this.authorisationServerId = authorisationServerId;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getClientId() {
        return clientId;
    }
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getRedirectUri() {
        return redirectUri;
    }
    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public Map<String, String> getGroupRoleMapping() {
        return groupRoleMapping;
    }
    public void setGroupRoleMapping(Map<String, String> groupRoleMapping) {
        this.groupRoleMapping = groupRoleMapping;
    }

    public void setManagementApiToken(String managementApiToken) {
        this.managementApiToken = managementApiToken;
    }

    public boolean isOktaApiTokenConfigured() {
        return oktaApiTokenConfigured;
    }

}
