package com.akto.action.user;

import com.akto.action.UserAction;
import com.akto.dao.ConfigsDao;
import com.akto.dao.RBACDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.akto.dto.User;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;

import java.util.ArrayList;

public class GithubSsoAction extends UserAction {

    public String deleteGithubSso() {

        User user = getSUser();
        if (user == null) return ERROR.toUpperCase();
        boolean isAdmin = RBACDao.instance.isAdmin(user.getId(), Context.accountId.get());
        if (!isAdmin) {
            addActionError("Only admin can delete SSO");
            return ERROR.toUpperCase();
        }

        DeleteResult result = ConfigsDao.instance.deleteAll(Filters.eq("_id", "GITHUB-ankush"));

        if (result.getDeletedCount() > 0) {
            for (Object obj : UsersDao.instance.getAllUsersInfoForTheAccount(Context.accountId.get())) {
                BasicDBObject detailsObj = (BasicDBObject) obj;
                UsersDao.instance.updateOne("login", detailsObj.getString(User.LOGIN), Updates.set("refreshTokens", new ArrayList<>()));
            }
        }

        return SUCCESS.toUpperCase();
    }

    private String githubClientId;
    private String githubClientSecret;
    public String addGithubSso() {

        User user = getSUser();
        if (user == null) return ERROR.toUpperCase();
        boolean isAdmin = RBACDao.instance.isAdmin(user.getId(), Context.accountId.get());
        if (!isAdmin) {
            addActionError("Only admin can add SSO");
            return ERROR.toUpperCase();
        }

        if (ConfigsDao.instance.findOne("_id", "GITHUB-ankush") != null) {
            addActionError("A Github SSO Integration already exists");
            return ERROR.toUpperCase();
        }

        Config.GithubConfig ghConfig = new Config.GithubConfig();
        ghConfig.setClientId(githubClientId);
        ghConfig.setClientSecret(githubClientSecret);

        ConfigsDao.instance.insertOne(ghConfig);

        return SUCCESS.toUpperCase();
    }

    @Override
    public String execute() throws Exception {
        Config.GithubConfig githubConfig = (Config.GithubConfig) ConfigsDao.instance.findOne("_id", "GITHUB-ankush");

        if (githubConfig != null) {
            this.githubClientId = githubConfig.getClientId();
        }

        return SUCCESS.toUpperCase();
    }

    public void setGithubClientId(String githubClientId) {
        this.githubClientId = githubClientId;
    }

    public String getGithubClientId() {
        return this.githubClientId;
    }
    
    public void setGithubClientSecret(String githubClientSecret) {
        this.githubClientSecret = githubClientSecret;
    }
}
