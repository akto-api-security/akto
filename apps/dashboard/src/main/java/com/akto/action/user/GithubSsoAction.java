package com.akto.action.user;

import com.akto.action.UserAction;
import com.akto.action.testing.StartTestAction;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.ConfigsDao;
import com.akto.dao.RBACDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.akto.dto.Config;
import com.akto.dto.User;
import com.akto.utils.sso.SsoUtils;
import com.akto.github.GithubUtils;
import com.akto.log.LoggerMaker;
import com.akto.util.DashboardMode;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import java.util.ArrayList;

import static com.akto.dao.AccountSettingsDao.generateFilter;

public class GithubSsoAction extends UserAction {
    private static final LoggerMaker loggerMaker = new LoggerMaker(StartTestAction.class);

    public String deleteGithubSso() {

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

        DeleteResult result = ConfigsDao.instance.deleteAll(Filters.eq("_id", "GITHUB-ankush"));

        if (result.getDeletedCount() > 0) {
            for (Object obj : UsersDao.instance.getAllUsersInfoForTheAccount(Context.accountId.get())) {
                BasicDBObject detailsObj = (BasicDBObject) obj;
                UsersDao.instance.updateOne("login", detailsObj.getString(User.LOGIN), Updates.set("refreshTokens", new ArrayList<>()));
                UsersDao.instance.updateOne("login", detailsObj.getString(User.LOGIN), Updates.unset("signupInfoMap.GITHUB"));
            }
        }

        return SUCCESS.toUpperCase();
    }

    public String fetchGithubAppId() {
        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(generateFilter());
        githubAppId = accountSettings.getGithubAppId();
        return SUCCESS.toUpperCase();
    }

    public String deleteGithubAppSecretKey() {
        if(!DashboardMode.isOnPremDeployment()){
            addActionError("This feature is only available in on-prem deployment");
            return ERROR.toUpperCase();
        }

        User user = getSUser();
        boolean isAdmin = RBACDao.instance.isAdmin(user.getId(), Context.accountId.get());
        if (!isAdmin) {
            addActionError("Only admin can delete github app credentials");
            return ERROR.toUpperCase();
        }

        AccountSettingsDao.instance.updateOne(generateFilter(), Updates.combine(
                Updates.unset(AccountSettings.GITHUB_APP_ID),
                Updates.unset(AccountSettings.GITHUB_APP_SECRET_KEY)));
        addActionMessage("Deleted github app ID and secret key");
        return SUCCESS.toUpperCase();
    }

    public String addGithubAppSecretKey() {
        if(!DashboardMode.isOnPremDeployment()){
            addActionError("This feature is only available in on-prem deployment");
            return ERROR.toUpperCase();
        }

        User user = getSUser();
        boolean isAdmin = RBACDao.instance.isAdmin(user.getId(), Context.accountId.get());
        if (!isAdmin) {
            addActionError("Only admin can delete github app credentials");
            return ERROR.toUpperCase();
        }
        githubAppSecretKey = githubAppSecretKey.replace("-----BEGIN RSA PRIVATE KEY-----","");
        githubAppSecretKey = githubAppSecretKey.replace("-----END RSA PRIVATE KEY-----","");
        githubAppSecretKey = githubAppSecretKey.replace("\n","");

        try {
            String jwtToken = GithubUtils.createJWT(githubAppId,githubAppSecretKey, 10 * 60 * 1000);
            GitHub gitHub = new GitHubBuilder().withJwtToken(jwtToken).build();
            gitHub.getApp();
        } catch (Exception e) {
            addActionError("invalid github app Id and secret key");
            return ERROR.toUpperCase();
        }
        AccountSettingsDao.instance.updateOne(generateFilter(), Updates.combine(Updates.set(AccountSettings.GITHUB_APP_SECRET_KEY, githubAppSecretKey),
                Updates.set(AccountSettings.GITHUB_APP_ID, githubAppId)));
        return SUCCESS.toUpperCase();
    }
    private String githubClientId;
    private String githubClientSecret;
    private String githubAppSecretKey;
    private String githubAppId;
    private String testingRunSummaryHexId;
    public String addGithubSso() {

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

        Config.GithubConfig ghConfig = new Config.GithubConfig();
        ghConfig.setClientId(githubClientId);
        ghConfig.setClientSecret(githubClientSecret);

        ConfigsDao.instance.insertOne(ghConfig);

        return SUCCESS.toUpperCase();
    }

    @Override
    public String execute() throws Exception {

        if(!DashboardMode.isOnPremDeployment()){
            addActionError("This feature is only available in on-prem deployment");
            return ERROR.toUpperCase();
        }

        Config.GithubConfig githubConfig = (Config.GithubConfig) ConfigsDao.instance.findOne("_id", "GITHUB-ankush");
        if (SsoUtils.isAnySsoActive() && githubConfig == null) {
            addActionError("A different SSO Integration already exists.");
            return ERROR.toUpperCase();
        }

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

    public String getGithubAppSecretKey() {
        return githubAppSecretKey;
    }

    public void setGithubAppSecretKey(String githubAppSecretKey) {
        this.githubAppSecretKey = githubAppSecretKey;
    }

    public String getTestingRunSummaryHexId() {
        return testingRunSummaryHexId;
    }

    public void setTestingRunSummaryHexId(String testingRunSummaryHexId) {
        this.testingRunSummaryHexId = testingRunSummaryHexId;
    }

    public String getGithubAppId() {
        return githubAppId;
    }

    public void setGithubAppId(String githubAppId) {
        this.githubAppId = githubAppId;
    }
}
