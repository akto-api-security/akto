package com.akto.action.user;

import com.akto.audit_logs_util.Audit;
import com.akto.dto.audit_logs.Operation;
import com.akto.dto.audit_logs.Resource;

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
import com.akto.dao.CustomRoleDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.akto.dto.Config.OktaConfig;
import com.akto.dto.CustomRole;
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
    private String managementApiToken;
    private Map<String, String> oktaGroupToAktoUserRoleMap;
    private boolean syncGroupsToUserTags;
    // null keeps the saved value (some saves, e.g. token edits, do not send it)
    private Boolean removeAccessWithoutGroup;
    private List<String> oktaGroupNames;

    private static boolean hasStoredOktaApiToken(OktaConfig c) {
        if (c == null) return false;
        String t = c.getManagementApiToken();
        return t != null && !t.isEmpty();
    }

    /** Client must not persist the dashboard mask string as the real token. */
    private static boolean isMaskedTokenSubmission(String s) {
        if (s == null) return false;
        String t = s.trim();
        return Constants.ASTERISK.equals(t) || t.contains("***");
    }

    public String addOktaSso() {
        if (SsoUtils.isAnySsoActive()) {
            addActionError("A SSO Integration already exists.");
            return ERROR.toUpperCase();
        }

        int accountId = Context.accountId.get();
        String incomingToken = this.managementApiToken;

        Config.OktaConfig oktaConfig = new Config.OktaConfig(accountId);
        oktaConfig.setClientId(clientId);
        oktaConfig.setClientSecret(clientSecret);
        oktaConfig.setAuthorisationServerId(authorisationServerId);
        oktaConfig.setOktaDomainUrl(oktaDomain);
        oktaConfig.setRedirectUri(redirectUri);
        oktaConfig.setAccountId(Context.accountId.get());
        if (incomingToken != null && !incomingToken.trim().isEmpty() && !isMaskedTokenSubmission(incomingToken)) {
            oktaConfig.setManagementApiToken(incomingToken.trim());
        }
        String userLogin = getSUser().getLogin();
        String domain = userLogin.split("@")[1];
        oktaConfig.setOrganizationDomain(domain);
        ConfigsDao.instance.insertOne(oktaConfig);

        this.managementApiToken = hasStoredOktaApiToken(oktaConfig) ? Constants.ASTERISK : null;

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
        if (oktaConfig.getManagementApiToken() == null || oktaConfig.getManagementApiToken().isEmpty()) {
            addActionError("Management API token is not configured. Configure it in Edit to fetch Okta groups.");
            return ERROR.toUpperCase();
        }
        this.oktaGroupNames = SignupAction.fetchAllOktaGroupNamesFromManagementApi(
                oktaConfig.getManagementBaseUrl(), oktaConfig.getManagementApiToken());
        return SUCCESS.toUpperCase();
    }

    // audit: the Okta group mapping before this request and what was asked for
    public String auditOktaMapping() {
        OktaConfig existing = (OktaConfig) ConfigsDao.instance.findOne(Constants.ID, OktaConfig.getOktaId(Context.accountId.get()));
        String before = existing == null ? "none" : existing.getOktaGroupToAktoUserRoleMap() + " removeAccessWithoutGroup=" + existing.isRemoveAccessWithoutGroup();
        return "before=" + before + " requested=" + oktaGroupToAktoUserRoleMap + " removeAccessWithoutGroup=" + removeAccessWithoutGroup;
    }

    @Audit(description = "User changed the Okta group to role mapping", resource = Resource.SSO_CONFIG, operation = Operation.UPDATE, metadataGenerators = {"auditOktaMapping"})
    public String saveOktaGroupRoleMapping() {
        int accountId = Context.accountId.get();
        OktaConfig oktaConfig = (OktaConfig) ConfigsDao.instance.findOne(Constants.ID, OktaConfig.getOktaId(accountId));
        if (oktaConfig == null) {
            addActionError("Okta SSO is not configured.");
            return ERROR.toUpperCase();
        }
        String incomingToken = this.managementApiToken;
        Map<String, String> activeMapping = oktaGroupToAktoUserRoleMap != null ? oktaGroupToAktoUserRoleMap : Collections.<String, String>emptyMap();
        String validationError = validateRoleMappingValues(activeMapping);
        if (validationError != null) {
            addActionError(validationError);
            return ERROR.toUpperCase();
        }

        // This flag only gates the periodic org-wide sync cron (device-tag writes happen there
        // exclusively, not at login) — the cron has no login session to read a JWT groups claim
        // from, so it can only run via the Management API. Reject enabling it without a token.
        boolean tokenPresentAfterSave;
        if (incomingToken == null) {
            tokenPresentAfterSave = hasStoredOktaApiToken(oktaConfig);
        } else if (incomingToken.trim().isEmpty()) {
            tokenPresentAfterSave = false;
        } else if (isMaskedTokenSubmission(incomingToken)) {
            tokenPresentAfterSave = hasStoredOktaApiToken(oktaConfig);
        } else {
            tokenPresentAfterSave = true;
        }
        if (syncGroupsToUserTags && !tokenPresentAfterSave) {
            addActionError("Set a Management API token before enabling group sync.");
            return ERROR.toUpperCase();
        }

        List<Bson> bsonUpdates = new ArrayList<>();
        bsonUpdates.add(Updates.set("oktaGroupToAktoUserRoleMap", activeMapping));
        bsonUpdates.add(Updates.unset("groupRoleMapping"));
        bsonUpdates.add(Updates.unset("oktaRoleMapping"));
        bsonUpdates.add(Updates.set(OktaConfig.SYNC_GROUPS_TO_USER_TAGS, syncGroupsToUserTags));
        if (Boolean.TRUE.equals(removeAccessWithoutGroup) && activeMapping.isEmpty()) {
            addActionError("Map at least one Okta group to a role before managing roles from Okta.");
            return ERROR.toUpperCase();
        }
        if (removeAccessWithoutGroup != null) {
            bsonUpdates.add(Updates.set(OktaConfig.REMOVE_ACCESS_WITHOUT_GROUP, removeAccessWithoutGroup));
        } else if (activeMapping.isEmpty()) {
            bsonUpdates.add(Updates.set(OktaConfig.REMOVE_ACCESS_WITHOUT_GROUP, false));
        }
        if (incomingToken != null) {
            if (incomingToken.trim().isEmpty()) {
                bsonUpdates.add(Updates.unset(OktaConfig.MANAGEMENT_API_TOKEN));
            } else if (!isMaskedTokenSubmission(incomingToken)) {
                bsonUpdates.add(Updates.set(OktaConfig.MANAGEMENT_API_TOKEN, incomingToken.trim()));
            }
        }
        ConfigsDao.instance.updateOne(
            Filters.eq(Constants.ID, OktaConfig.getOktaId(accountId)),
            Updates.combine(bsonUpdates.toArray(new Bson[0]))
        );
        OktaConfig refreshed = (OktaConfig) ConfigsDao.instance.findOne(Constants.ID, OktaConfig.getOktaId(accountId));
        this.managementApiToken = hasStoredOktaApiToken(refreshed) ? Constants.ASTERISK : null;
        return SUCCESS.toUpperCase();
    }

    private String validateRoleMappingValues(Map<String, String> mapping) {
        if (mapping == null) return null;
        Set<String> rolesSeen = new HashSet<>();
        for (Map.Entry<String, String> e : mapping.entrySet()) {
            String role = e.getValue();
            boolean isStandardRole = RBAC.Role.fromName(role) != null;
            if (!isStandardRole) {
                CustomRole customRole = CustomRoleDao.instance.findRoleByName(role);
                if (customRole == null) {
                    return "The role " + role + " doesn't exist anymore. Pick another role for " + e.getKey() + ".";
                }
            }
            if (!rolesSeen.add(role)) {
                return "Each Akto role can be mapped to only one Okta group. " + role + " is mapped more than once.";
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
            this.syncGroupsToUserTags = oktaConfig.isSyncGroupsToUserTags();
            this.removeAccessWithoutGroup = oktaConfig.isRemoveAccessWithoutGroup();
            this.managementApiToken = hasStoredOktaApiToken(oktaConfig) ? Constants.ASTERISK : null;
        } else {
            this.managementApiToken = null;
            this.syncGroupsToUserTags = false;
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

    public boolean isSyncGroupsToUserTags() {
        return syncGroupsToUserTags;
    }
    public void setSyncGroupsToUserTags(boolean syncGroupsToUserTags) {
        this.syncGroupsToUserTags = syncGroupsToUserTags;
    }

    public Boolean getRemoveAccessWithoutGroup() {
        return removeAccessWithoutGroup;
    }
    public void setRemoveAccessWithoutGroup(Boolean removeAccessWithoutGroup) {
        this.removeAccessWithoutGroup = removeAccessWithoutGroup;
    }

    public void setManagementApiToken(String managementApiToken) {
        this.managementApiToken = managementApiToken;
    }

    public String getManagementApiToken() {
        return managementApiToken;
    }

    public List<String> getOktaGroupNames() {
        return oktaGroupNames != null ? oktaGroupNames : Collections.<String>emptyList();
    }

}
