package com.akto.action.user;

import java.util.ArrayList;
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
import com.akto.util.http_request.CustomHttpRequest;
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
    private Map<String, String> groupRoleMapping;
    private Map<String, String> oktaRoleMapping;
    private List<String> oktaGroups;
    private String mappingType;

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
        OktaConfig.MappingType type;
        try {
            type = OktaConfig.MappingType.valueOf(mappingType);
        } catch (IllegalArgumentException e) {
            addActionError("Invalid mappingType: " + mappingType + ". Valid values are GROUP, ROLE.");
            return ERROR.toUpperCase();
        }
        boolean isGroupBased = type == OktaConfig.MappingType.GROUP;
        Map<String, String> activeMapping = isGroupBased ? groupRoleMapping : oktaRoleMapping;
        String validationError = validateRoleMappingValues(activeMapping);
        if (validationError != null) {
            addActionError(validationError);
            return ERROR.toUpperCase();
        }
        ConfigsDao.instance.updateOne(
            Filters.eq(Constants.ID, OktaConfig.getOktaId(accountId)),
            Updates.combine(
                Updates.set("mappingType", type.name()),
                Updates.set("groupRoleMapping", isGroupBased ? activeMapping : null),
                Updates.set("oktaRoleMapping", isGroupBased ? null : activeMapping)
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

    @SuppressWarnings("unchecked")
    public String fetchOktaGroups() {
        int accountId = Context.accountId.get();
        OktaConfig oktaConfig = (OktaConfig) ConfigsDao.instance.findOne(Constants.ID, OktaConfig.getOktaId(accountId));
        if (oktaConfig == null) {
            addActionError("Okta SSO is not configured.");
            return ERROR.toUpperCase();
        }
        if (oktaConfig.getApiToken() == null || oktaConfig.getApiToken().isEmpty()) {
            addActionError("Okta API token is not configured. Set apiToken in the Okta config to fetch groups.");
            return ERROR.toUpperCase();
        }
        try {
            String url = "https://" + oktaConfig.getOktaDomainUrl() + "/api/v1/groups";
            String auth = "SSWS " + oktaConfig.getApiToken();
            List<Map<String, Object>> groupsList = CustomHttpRequest.getRequestAsList(url, auth);
            this.oktaGroups = new ArrayList<>();
            if (groupsList != null) {
                for (Map<String, Object> group : groupsList) {
                    Map<String, Object> profile = (Map<String, Object>) group.get("profile");
                    if (profile != null && profile.get("name") != null) {
                        this.oktaGroups.add(String.valueOf(profile.get("name")));
                    }
                }
            }
        } catch (Exception e) {
            addActionError("Failed to fetch groups from Okta: " + e.getMessage());
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
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
            this.oktaRoleMapping = oktaConfig.getOktaRoleMapping();
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

    public Map<String, String> getOktaRoleMapping() {
        return oktaRoleMapping;
    }
    public void setOktaRoleMapping(Map<String, String> oktaRoleMapping) {
        this.oktaRoleMapping = oktaRoleMapping;
    }

    public List<String> getOktaGroups() {
        return oktaGroups;
    }

    public String getMappingType() {
        return mappingType;
    }
    public void setMappingType(String mappingType) {
        this.mappingType = mappingType;
    }

}
