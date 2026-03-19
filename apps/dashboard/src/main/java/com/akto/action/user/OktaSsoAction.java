package com.akto.action.user;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.conversions.Bson;

import com.akto.action.SignupAction;
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
    /** True when a non-empty Management API token (SSWS) is stored. */
    private boolean managementApiTokenStatus;
    private Map<String, String> oktaGroupToAktoUserRoleMap;
    /** Fetched from Okta Management API (for autosuggest in dashboard). */
    private List<String> oktaGroupNames;

    private static boolean managementApiTokenStatusFrom(OktaConfig c) {
        if (c == null) return false;
        String t = c.getApiToken();
        return t != null && !t.isEmpty();
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

    /**
     * Fetches Okta group names for autosuggest when adding mappings from the dashboard.
     * Uses all-groups API (no user ID). Requires API token to be configured.
     */
    public String fetchOktaGroups() {
        int accountId = Context.accountId.get();
        OktaConfig oktaConfig = (OktaConfig) ConfigsDao.instance.findOne(Constants.ID, OktaConfig.getOktaId(accountId));
        if (oktaConfig == null) {
            addActionError("Okta SSO is not configured.");
            return ERROR.toUpperCase();
        }
        if (oktaConfig.getApiToken() == null || oktaConfig.getApiToken().isEmpty()) {
            addActionError("Management API token is not configured. Configure it in Edit to fetch Okta groups.");
            return ERROR.toUpperCase();
        }
        this.oktaGroupNames = SignupAction.fetchAllOktaGroupNamesFromManagementApi(
                oktaConfig.getManagementBaseUrl(), oktaConfig.getApiToken());
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
        if (managementApiToken != null) {
            if (managementApiToken.trim().isEmpty()) {
                bsonUpdates.add(Updates.unset("apiToken"));
            } else {
                bsonUpdates.add(Updates.set("apiToken", managementApiToken.trim()));
            }
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
        Set<String> rolesSeen = new HashSet<>();
        for (Map.Entry<String, String> e : mapping.entrySet()) {
            String role = e.getValue();
            try {
                RBAC.Role.valueOf(role);
            } catch (IllegalArgumentException ex) {
                return "Invalid Akto role: " + role + ". Valid values are ADMIN, MEMBER, DEVELOPER, GUEST.";
            }
            if (!rolesSeen.add(role)) {
                return "One-to-one mapping required: each Akto role can be assigned to only one Okta group. Role " + role + " is mapped more than once.";
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

    public boolean isManagementApiTokenStatus() {
        return managementApiTokenStatus;
    }

    public List<String> getOktaGroupNames() {
        return oktaGroupNames != null ? oktaGroupNames : Collections.<String>emptyList();
    }

}
