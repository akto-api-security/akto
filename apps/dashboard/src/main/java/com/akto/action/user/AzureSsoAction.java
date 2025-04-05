package com.akto.action.user;

import com.opensymphony.xwork2.Action;

import java.util.ArrayList;

import com.akto.action.UserAction;
import com.akto.dao.ConfigsDao;
import com.akto.dao.RBACDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.akto.dto.User;
import com.akto.util.DashboardMode;
import com.akto.utils.sso.SsoUtils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;

public class AzureSsoAction extends UserAction{
    
    private String x509Certificate ;
    private String azureEntityId ;
    private String loginUrl ;
    private String acsUrl ;
    private String applicationIdentifier;

    public String addAzureSsoInfo(){

        if(!DashboardMode.isOnPremDeployment()){
            addActionError("This feature is only available in on-prem deployment");
            return ERROR.toUpperCase();
        }

        User user = getSUser();
        if (user == null) return ERROR.toUpperCase();
        boolean isAdmin = RBACDao.instance.isAdmin(user.getId(), Context.accountId.get());
        if (!isAdmin) {
            addActionError("Only admin can add SSO");
            return Action.ERROR.toUpperCase();
        }

        if (SsoUtils.isAnySsoActive()) {
            addActionError("A SSO Integration already exists.");
            return ERROR.toUpperCase();
        }

        Config.AzureConfig azureConfig = new Config.AzureConfig();
        azureConfig.setX509Certificate(x509Certificate);
        azureConfig.setAzureEntityId(azureEntityId);
        azureConfig.setAcsUrl(acsUrl);
        azureConfig.setLoginUrl(loginUrl);
        azureConfig.setApplicationIdentifier(applicationIdentifier);

        ConfigsDao.instance.insertOne(azureConfig);

        return Action.SUCCESS.toUpperCase();
    }

    public String deleteAzureSso(){

        if(!DashboardMode.isOnPremDeployment()){
            addActionError("This feature is only available in on-prem deployment");
            return ERROR.toUpperCase();
        }

        User user = getSUser();
        if (user == null) return ERROR.toUpperCase();
        boolean isAdmin = RBACDao.instance.isAdmin(user.getId(), Context.accountId.get());
        if (!isAdmin) {
            addActionError("Only admin can delete SSO");
            return Action.ERROR.toUpperCase();
        }

        DeleteResult result = ConfigsDao.instance.deleteAll(Filters.eq("_id", "AZURE-ankush"));

        if (result.getDeletedCount() > 0) {
            for (Object obj : UsersDao.instance.getAllUsersInfoForTheAccount(Context.accountId.get())) {
                BasicDBObject detailsObj = (BasicDBObject) obj;
                UsersDao.instance.updateOne("login", detailsObj.getString(User.LOGIN), Updates.set("refreshTokens", new ArrayList<>()));
                UsersDao.instance.updateOne("login", detailsObj.getString(User.LOGIN), Updates.unset("signupInfoMap.AZURE"));
            }
        }

        return Action.SUCCESS.toUpperCase();
    }

    @Override
    public String execute() throws Exception {

        if(!DashboardMode.isOnPremDeployment()){
            addActionError("This feature is only available in on-prem deployment");
            return ERROR.toUpperCase();
        }
        
        Config.AzureConfig azureConfig = (Config.AzureConfig) ConfigsDao.instance.findOne("_id", "AZURE-ankush");
        if (SsoUtils.isAnySsoActive() && azureConfig == null) {
            addActionError("A different SSO Integration already exists.");
            return ERROR.toUpperCase();
        }

        if (azureConfig != null) {
            this.loginUrl = azureConfig.getLoginUrl();
            this.azureEntityId = azureConfig.getAzureEntityId();
        }

        return SUCCESS.toUpperCase();
    }
    public void setX509Certificate(String x509Certificate) {
        this.x509Certificate = x509Certificate;
    }

    public String getAzureEntityId() {
        return azureEntityId;
    }

    public void setAzureEntityId(String azureEntityId) {
        this.azureEntityId = azureEntityId;
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
}
