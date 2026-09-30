package com.akto.action.user;

import com.opensymphony.xwork2.Action;

import java.util.ArrayList;
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

public class AzureSsoAction extends UserAction{
    
    private String x509Certificate ;
    private String ssoEntityId ;
    private String loginUrl ;
    private String acsUrl ;
    private String applicationIdentifier;
    private ConfigType configType;
    private Map<String, String> groupRoleMapping;

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

    public String saveSamlGroupRoleMapping() {
        if (this.groupRoleMapping != null) {
            for (Map.Entry<String, String> entry : this.groupRoleMapping.entrySet()) {
                String group = entry.getKey();
                String role = entry.getValue();
                // Mongo map keys cannot contain '.' or start with '$'
                if (group == null || group.trim().isEmpty() || group.contains(".") || group.startsWith("$")) {
                    addActionError("Invalid group name: " + group);
                    return ERROR.toUpperCase();
                }
                boolean validRole = false;
                try {
                    validRole = Role.valueOf(role) != Role.NO_ACCESS;
                } catch (Exception e) {
                    validRole = role != null && CustomRoleDao.instance.findRoleByName(role) != null;
                }
                if (!validRole) {
                    addActionError("Invalid role: " + role);
                    return ERROR.toUpperCase();
                }
            }
        }

        if (findSamlConfig() == null) {
            addActionError("SSO is not set up.");
            return ERROR.toUpperCase();
        }

        SSOConfigsDao.instance.updateOne(
            Filters.eq(Constants.ID, String.valueOf(Context.accountId.get())),
            Updates.set(SAMLConfig.GROUP_ROLE_MAPPING, this.groupRoleMapping)
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
}
