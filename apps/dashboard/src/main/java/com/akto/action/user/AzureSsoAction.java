package com.akto.action.user;

import com.akto.audit_logs_util.Audit;
import com.akto.dto.audit_logs.Operation;
import com.akto.dto.audit_logs.Resource;

import com.opensymphony.xwork2.Action;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.akto.action.UserAction;
import com.akto.dao.CustomRoleDao;
import com.akto.dao.SSOConfigsDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.RBAC.Role;
import com.akto.dto.User;
import com.akto.dto.Config.ConfigType;
import com.akto.dto.sso.SAMLConfig;
import com.akto.util.Constants;
import com.akto.utils.sso.SsoUtils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import org.bson.conversions.Bson;

public class AzureSsoAction extends UserAction{
    
    private String x509Certificate ;
    private String ssoEntityId ;
    private String loginUrl ;
    private String acsUrl ;
    private String applicationIdentifier;
    private ConfigType configType;
    private Map<String, String> groupRoleMapping;
    // null (e.g. from an older page) keeps the saved value
    private Boolean removeAccessWithoutGroup;

    private SAMLConfig getConfig(ConfigType configType, String domain){
        SAMLConfig config = new SAMLConfig(configType,Context.accountId.get());
        config.setX509Certificate(x509Certificate);
        config.setEntityId(ssoEntityId);
        config.setAcsUrl(acsUrl);
        config.setLoginUrl(loginUrl);
        config.setApplicationIdentifier(applicationIdentifier);
        config.setOrganizationDomain(domain);
        return config;
    }

    public String addSamlSsoInfo(){
        String userLogin = getSUser().getLogin();
        String domain = userLogin.split("@")[1];
        if (SsoUtils.isAnySsoActive()) {
            addActionError("A SSO Integration already exists.");
            return ERROR.toUpperCase();
        }
        
        SAMLConfig samlConfig = getConfig(this.configType, domain);       
        SSOConfigsDao.instance.insertOne(samlConfig);

        return Action.SUCCESS.toUpperCase();
    }

    private void deleteSAMLSettings(ConfigType configType){
        int accountId = Context.accountId.get();
        DeleteResult result = SSOConfigsDao.instance.deleteAll(Filters.eq(Constants.ID, String.valueOf(accountId)));

        if (result.getDeletedCount() > 0) {
            for (Object obj : UsersDao.instance.getAllUsersInfoForTheAccount(Context.accountId.get())) {
                BasicDBObject detailsObj = (BasicDBObject) obj;
                UsersDao.instance.updateOne("login", detailsObj.getString(User.LOGIN), Updates.set("refreshTokens", new ArrayList<>()));
                UsersDao.instance.updateOne("login", detailsObj.getString(User.LOGIN), Updates.unset("signupInfoMap." + this.configType.name()));
            }
        }
    }

    public String deleteSamlSso(){
        deleteSAMLSettings(this.configType);
        return Action.SUCCESS.toUpperCase();
    }

    private SAMLConfig findSamlConfig() {
        return SSOConfigsDao.instance.findOne(
            Filters.and(
                Filters.eq(Constants.ID, String.valueOf(Context.accountId.get())),
                Filters.eq("configType", configType.name())
            )
        );
    }

    // audit: the SSO group mapping before this request and what was asked for
    public String auditSsoMapping() {
        SAMLConfig existing = findSamlConfig();
        String before = existing == null ? "none" : existing.getGroupRoleMapping() + " removeAccessWithoutGroup=" + existing.isRemoveAccessWithoutGroup();
        return "before=" + before + " requested=" + groupRoleMapping + " removeAccessWithoutGroup=" + removeAccessWithoutGroup;
    }

    @Audit(description = "User changed the SSO group to role mapping", resource = Resource.SSO_CONFIG, operation = Operation.UPDATE, metadataGenerators = {"auditSsoMapping"})
    public String saveSamlGroupRoleMapping() {
        if (this.groupRoleMapping != null) {
            for (Map.Entry<String, String> entry : this.groupRoleMapping.entrySet()) {
                String group = entry.getKey();
                String role = entry.getValue();
                // Mongo map keys cannot contain '.' or start with '$'
                if (group == null || group.trim().isEmpty()) {
                    addActionError("Enter a group name or ID.");
                    return ERROR.toUpperCase();
                }
                if (group.contains(".") || group.startsWith("$")) {
                    addActionError("Group names can't contain '.' or start with '$': " + group);
                    return ERROR.toUpperCase();
                }
                boolean validRole = Role.fromName(role) != null ? Role.fromName(role) != Role.NO_ACCESS
                        : role != null && CustomRoleDao.instance.findRoleByName(role) != null;
                if (!validRole) {
                    addActionError("The role " + role + " doesn't exist anymore. Pick another role for " + group + ".");
                    return ERROR.toUpperCase();
                }
            }
        }

        if (findSamlConfig() == null) {
            addActionError("Set up SSO first, then map groups to roles.");
            return ERROR.toUpperCase();
        }
        boolean hasMapping = this.groupRoleMapping != null && !this.groupRoleMapping.isEmpty();
        if (Boolean.TRUE.equals(this.removeAccessWithoutGroup) && !hasMapping) {
            addActionError("Map at least one group to a role before removing access for users in no group.");
            return ERROR.toUpperCase();
        }

        List<Bson> updates = new ArrayList<>();
        updates.add(Updates.set(SAMLConfig.GROUP_ROLE_MAPPING, this.groupRoleMapping));
        if (this.removeAccessWithoutGroup != null) {
            updates.add(Updates.set(SAMLConfig.REMOVE_ACCESS_WITHOUT_GROUP, this.removeAccessWithoutGroup && hasMapping));
        } else if (!hasMapping) {
            // with no mapping left there is no group to keep users in
            updates.add(Updates.set(SAMLConfig.REMOVE_ACCESS_WITHOUT_GROUP, false));
        }
        SSOConfigsDao.instance.updateOne(
            Filters.eq(Constants.ID, String.valueOf(Context.accountId.get())),
            Updates.combine(updates)
        );
        return SUCCESS.toUpperCase();
    }

    @Override
    public String execute() throws Exception {
        String idString = String.valueOf(Context.accountId.get());
        SAMLConfig samlConfig = (SAMLConfig) SSOConfigsDao.instance.findOne(
            Filters.and(
                Filters.eq(Constants.ID, idString),
                Filters.eq("configType", configType.name())
            )
        );
        if (SsoUtils.isAnySsoActive() && samlConfig == null) {
            addActionError("A different SSO Integration already exists.");
            return ERROR.toUpperCase();
        }

        if (samlConfig != null) {
            this.loginUrl = samlConfig.getLoginUrl();
            this.ssoEntityId = samlConfig.getEntityId();
            this.groupRoleMapping = samlConfig.getGroupRoleMapping();
            this.removeAccessWithoutGroup = samlConfig.isRemoveAccessWithoutGroup();
        }

        return SUCCESS.toUpperCase();
    }

    public void setX509Certificate(String x509Certificate) {
        this.x509Certificate = x509Certificate;
    }

    public String getSsoEntityId() {
        return ssoEntityId;
    }

    public void setSsoEntityId(String ssoEntityId) {
        this.ssoEntityId = ssoEntityId;
    }

    public String getLoginUrl() {
        return loginUrl;
    }

    public void setLoginUrl(String loginUrl) {
        this.loginUrl = loginUrl;
    }
    
    public void setAcsUrl(String acsUrl) {
        this.acsUrl = acsUrl;
    }

    public void setApplicationIdentifier(String applicationIdentifier) {
        this.applicationIdentifier = applicationIdentifier;
    }

    public void setConfigType(ConfigType configType) {
        this.configType = configType;
    }

    public Map<String, String> getGroupRoleMapping() {
        return groupRoleMapping;
    }

    public void setGroupRoleMapping(Map<String, String> groupRoleMapping) {
        this.groupRoleMapping = groupRoleMapping;
    }

    public Boolean getRemoveAccessWithoutGroup() {
        return removeAccessWithoutGroup;
    }

    public void setRemoveAccessWithoutGroup(Boolean removeAccessWithoutGroup) {
        this.removeAccessWithoutGroup = removeAccessWithoutGroup;
    }
}
