package com.akto.action.user;

import com.opensymphony.xwork2.Action;

import java.util.ArrayList;

import com.akto.action.UserAction;
import com.akto.dao.SSOConfigsDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
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
}
