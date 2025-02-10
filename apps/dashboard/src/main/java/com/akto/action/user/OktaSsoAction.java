package com.akto.action.user;

import java.util.ArrayList;

import org.bson.conversions.Bson;

import com.akto.action.UserAction;
import com.akto.dao.ConfigsDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.akto.dto.User;
import com.akto.dto.Config.OktaConfig;
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
