package com.akto.action.user;

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

public class OktaSsoAction extends UserAction {
    
    private String clientId;
    private String clientSecret;
    private String authorisationServerId;
    private String oktaDomain;
    private String redirectUri;

    public String addOktaSso() {

        if(!DashboardMode.isOnPremDeployment()){
            addActionError("This feature is only available in on-prem deployment");
            return ERROR.toUpperCase();
        }

        User user = getSUser();
        if (user == null) return ERROR.toUpperCase();
        boolean isAdmin = RBACDao.instance.isAdmin(user.getId(), Context.accountId.get());
        if (!isAdmin) {
            addActionError("Only admin can add SSO");
            return ERROR.toUpperCase();
        }

        if (SsoUtils.isAnySsoActive()) {
            addActionError("A SSO Integration already exists.");
            return ERROR.toUpperCase();
        }

        Config.OktaConfig oktaConfig = new Config.OktaConfig();
        oktaConfig.setClientId(clientId);
        oktaConfig.setClientSecret(clientSecret);
        oktaConfig.setAuthorisationServerId(authorisationServerId);
        oktaConfig.setOktaDomainUrl(oktaDomain);
        oktaConfig.setRedirectUri(redirectUri);

        ConfigsDao.instance.insertOne(oktaConfig);

        return SUCCESS.toUpperCase();
    }

    public String deleteOktaSso() {
        if(!DashboardMode.isOnPremDeployment()){
            addActionError("This feature is only available in on-prem deployment");
            return ERROR.toUpperCase();
        }

        User user = getSUser();
        if (user == null) return ERROR.toUpperCase();
        boolean isAdmin = RBACDao.instance.isAdmin(user.getId(), Context.accountId.get());
        if (!isAdmin) {
            addActionError("Only admin can delete SSO");
            return ERROR.toUpperCase();
        }

        DeleteResult result = ConfigsDao.instance.deleteAll(Filters.eq("_id", "OKTA-ankush"));

        if (result.getDeletedCount() > 0) {
            for (Object obj : UsersDao.instance.getAllUsersInfoForTheAccount(Context.accountId.get())) {
                BasicDBObject detailsObj = (BasicDBObject) obj;
                UsersDao.instance.updateOne("login", detailsObj.getString(User.LOGIN), Updates.set("refreshTokens", new ArrayList<>()));
                UsersDao.instance.updateOne("login", detailsObj.getString(User.LOGIN), Updates.unset("signupInfoMap.OKTA"));
            }
        }

        return SUCCESS.toUpperCase();
    }

    @Override
    public String execute() throws Exception {

        if(!DashboardMode.isOnPremDeployment()){
            addActionError("This feature is only available in on-prem deployment");
            return ERROR.toUpperCase();
        }

        Config.OktaConfig oktaConfig = (Config.OktaConfig) ConfigsDao.instance.findOne("_id", "OKTA-ankush");
        if (SsoUtils.isAnySsoActive() && oktaConfig == null) {
            addActionError("A different SSO Integration already exists.");
            return ERROR.toUpperCase();
        }

        if (oktaConfig != null) {
            this.clientId = oktaConfig.getClientId();
            this.oktaDomain = oktaConfig.getOktaDomainUrl();
            this.authorisationServerId = oktaConfig.getAuthorisationServerId();
            this.redirectUri = oktaConfig.getRedirectUri();
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

}
