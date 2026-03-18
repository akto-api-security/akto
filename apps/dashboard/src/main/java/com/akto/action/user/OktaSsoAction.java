package com.akto.action.user;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private boolean clearManagementApiToken;
    /** CONFIGURED | NOT_SET — from stored apiToken only. */
    private String managementApiTokenStatus = "NOT_SET";
    private Map<String, String> oktaGroupToAktoUserRoleMap;

    private static String managementApiTokenStatusFrom(OktaConfig c) {
        if (c == null) return "NOT_SET";
        String t = c.getApiToken();
        return (t != null && !t.isEmpty()) ? "CONFIGURED" : "NOT_SET";
    }

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
        Map<String, String> activeMapping = oktaGroupToAktoUserRoleMap != null ? oktaGroupToAktoUserRoleMap : Collections.<String, String>emptyMap();
        String validationError = validateRoleMappingValues(activeMapping);
        if (validationError != null) {
            addActionError(validationError);
            return ERROR.toUpperCase();
        }
        List<Bson> bsonUpdates = new ArrayList<>();
        bsonUpdates.add(Updates.set("oktaGroupToAktoUserRoleMap", activeMapping));
        bsonUpdates.add(Updates.unset("groupRoleMapping"));
        bsonUpdates.add(Updates.unset("oktaRoleMapping"));
        if (clearManagementApiToken) {
            bsonUpdates.add(Updates.unset("apiToken"));
        } else if (managementApiToken != null && !managementApiToken.trim().isEmpty()) {
            String tok = managementApiToken.trim();
            if (tok.length() < 20) {
                addActionError("API token is too short. Paste the full SSWS token from Okta (Security → API → Tokens).");
                return ERROR.toUpperCase();
            }
            bsonUpdates.add(Updates.set("apiToken", tok));
        }
        ConfigsDao.instance.updateOne(
            Filters.eq(Constants.ID, OktaConfig.getOktaId(accountId)),
            Updates.combine(bsonUpdates.toArray(new Bson[0]))
        );
        OktaConfig refreshed = (OktaConfig) ConfigsDao.instance.findOne(Constants.ID, OktaConfig.getOktaId(accountId));
        this.managementApiTokenStatus = managementApiTokenStatusFrom(refreshed);
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
            this.oktaGroupToAktoUserRoleMap = oktaConfig.getOktaGroupToAktoUserRoleMap();
            this.managementApiTokenStatus = managementApiTokenStatusFrom(oktaConfig);
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

    public Map<String, String> getOktaGroupToAktoUserRoleMap() {
        return oktaGroupToAktoUserRoleMap;
    }
    public void setOktaGroupToAktoUserRoleMap(Map<String, String> oktaGroupToAktoUserRoleMap) {
        this.oktaGroupToAktoUserRoleMap = oktaGroupToAktoUserRoleMap;
    }

    public void setManagementApiToken(String managementApiToken) {
        this.managementApiToken = managementApiToken;
    }

    public void setClearManagementApiToken(boolean clearManagementApiToken) {
        this.clearManagementApiToken = clearManagementApiToken;
    }

    public String getManagementApiTokenStatus() {
        return managementApiTokenStatus;
    }

}
