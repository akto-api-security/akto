package com.akto.action.user;

import com.akto.DaoInit;
import com.akto.action.UserAction;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.ConfigsDao;
import com.akto.dao.RBACDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.akto.dto.Config;
import com.akto.dto.User;
import com.akto.utils.DashboardMode;
import com.akto.utils.JWT;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import org.kohsuke.github.*;

import java.util.ArrayList;
import java.util.List;

import static com.akto.dao.AccountSettingsDao.generateFilter;

public class GithubSsoAction extends UserAction {

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

    public String fetchGithubAppSecretKey() {
        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(generateFilter());
        githubAppSecretKey = accountSettings.getGithubAppSecretKey();
        return SUCCESS.toUpperCase();
    }


    public String publishGithubComments() throws Exception {
        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(generateFilter());
        String privateKey = accountSettings.getGithubAppSecretKey();
        privateKey = privateKey.replace("\n","");
        String jwtToken = JWT.createJWT(String.valueOf(398685),privateKey, 10 * 60 * 1000);
        GitHub gitHub = new GitHubBuilder().withJwtToken(jwtToken).build();
        GHApp ghApp = gitHub.getApp();
        List<GHAppInstallation> appInstallations = ghApp.listInstallations().toList();
        GHAppInstallation appInstallation = appInstallations.get(0);
        GHAppCreateTokenBuilder builder = appInstallation.createToken();
        GHAppInstallationToken token = builder.create();
        token.getToken();
        GitHub gitHub1 =  new GitHubBuilder().withAppInstallationToken(token.getToken())
                .build();
        GHRepository repository = gitHub1.getInstallation().listRepositories().toList().get(4);
        List<GHPullRequest> pullRequests =  repository.getPullRequests(GHIssueState.OPEN);
        GHPullRequest pullRequest = pullRequests.get(0);
        GHIssue issue = repository.getIssue(pullRequest.getNumber());
        issue.comment("this is test message");
        GHPullRequestReviewBuilder ghPullRequestReviewBuilder =  pullRequest.createReview();
        System.out.println("token: "  + token.getToken());
        System.out.println("jwt token: "  + jwtToken);
        return SUCCESS.toUpperCase();
    }
    public String addGithubAppSecretKey() {
        githubAppSecretKey = githubAppSecretKey.replace("-----BEGIN RSA PRIVATE KEY-----","");
        githubAppSecretKey = githubAppSecretKey.replace("-----END RSA PRIVATE KEY-----","");

        AccountSettingsDao.instance.updateOne(generateFilter(), Updates.set(AccountSettings.GITHUB_APP_SECRET_KEY, githubAppSecretKey));
        return SUCCESS.toUpperCase();
    }
    private String githubClientId;
    private String githubClientSecret;
    private String githubAppSecretKey;
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

        if(!DashboardMode.isOnPremDeployment()){
            addActionError("This feature is only available in on-prem deployment");
            return ERROR.toUpperCase();
        }

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
}
