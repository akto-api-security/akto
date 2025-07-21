package com.akto.action;

import com.akto.dao.ApiTokensDao;
import com.akto.dao.context.Context;
import com.akto.dao.notifications.SlackWebhooksDao;
import com.akto.dto.ApiToken;
import com.akto.dto.ApiToken.Utility;
import com.akto.dto.notifications.SlackWebhook;
import com.akto.dto.type.KeyTypes;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.utils.RandomString;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.DeleteResult;
import com.opensymphony.xwork2.Action;

import org.apache.struts2.interceptor.ServletRequestAware;

import javax.servlet.http.HttpServletRequest;

import static com.akto.utils.Utils.createDashboardUrlFromRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ApiTokenAction extends UserAction implements ServletRequestAware {
    private static final int keyLength = 40;
    private static final RandomString randomString = new RandomString(keyLength);
    private ApiToken.Utility tokenUtility;

    public String addApiToken() {
        String username = getSUser().getLogin();
        String apiKey = randomString.nextString();
        if (apiKey == null || apiKey.length() != keyLength) return ERROR.toUpperCase();

        ApiToken apiToken = new ApiToken();
        switch (tokenUtility){
            case BURP: 
                apiToken = new ApiToken(Context.now(),Context.accountId.get(),tokenUtility.toString().toLowerCase(), apiKey, Context.now(), username, ApiToken.Utility.BURP);
                break;
            case CICD:
                apiToken = new ApiToken(Context.now(),Context.accountId.get(), tokenUtility.toString().toLowerCase(), apiKey, Context.now(), username, ApiToken.Utility.CICD);
                break;
            default:
            apiToken = new ApiToken(Context.now(),Context.accountId.get(),tokenUtility.toString().toLowerCase(), apiKey, Context.now(), username, ApiToken.Utility.EXTERNAL_API);
        }
        ApiTokensDao.instance.insertOne(apiToken);
        apiTokenList = new ArrayList<>();
        apiTokenList.add(apiToken);
        return SUCCESS.toUpperCase();
    }

    private int apiTokenId;
    private boolean apiTokenDeleted;
    public String deleteApiToken() {
        String username = getSUser().getLogin();
        DeleteResult deleteResult = ApiTokensDao.instance.getMCollection().deleteOne(
                Filters.and(
                        Filters.eq("_id", apiTokenId),
                        Filters.eq(ApiToken.USER_NAME, username)
                )
        );

        long c = deleteResult.getDeletedCount();
        this.apiTokenDeleted = c >= 1;

        return SUCCESS.toUpperCase();
    }

    List<ApiToken> apiTokenList;
    public String fetchApiTokens() {
        String username = getSUser().getLogin();
        apiTokenList = ApiTokensDao.instance.findAll(
                Filters.and(
                        Filters.eq(ApiToken.USER_NAME, username),
                        Filters.eq(ApiToken.ACCOUNT_ID, Context.accountId.get())

                )
        );

        List<SlackWebhook> slackWebhooks = SlackWebhooksDao.instance.findAll(new BasicDBObject());
        for(SlackWebhook sw: slackWebhooks) {
            ApiToken slackToken = 
                new ApiToken(sw.getId(), Context.accountId.get(), sw.getSlackWebhookName(), sw.getWebhook(),
                sw.getId(), sw.getUserEmail(), Utility.SLACK);
                
            apiTokenList.add(slackToken);
        }

        return SUCCESS.toUpperCase();
    }

    public String fetchSlackWebhooks() {
        apiTokenList = new ArrayList<>();
        List<SlackWebhook> slackWebhooks = SlackWebhooksDao.instance.findAll(new BasicDBObject());
        for(SlackWebhook sw: slackWebhooks) {
            ApiToken slackToken = 
                new ApiToken(sw.getId(), Context.accountId.get(), sw.getSlackWebhookName(), sw.getWebhook(), 
                sw.getId(), sw.getUserEmail(), Utility.SLACK);
                
            apiTokenList.add(slackToken);
        }

        return SUCCESS.toUpperCase();
    }

    private String error;
    private String webhookUrl;
    private String webhookName;
    private String dashboardUrl;
    private int frequencyInSeconds;
    public int getFrequencyInSeconds() {
        return frequencyInSeconds;
    }

    public void setFrequencyInSeconds(int frequencyInSeconds) {
        this.frequencyInSeconds = frequencyInSeconds;
    }

    public String addSlackWebhook() {

        boolean isUrl = KeyTypes.patternToSubType.get(SingleTypeInfo.URL).matcher(webhookUrl).matches();
        boolean isSlackUrl = isUrl && webhookUrl.contains("hooks.slack.com");
        boolean alreadyExists = SlackWebhooksDao.instance.findOne(new BasicDBObject("webhook", webhookUrl)) != null;

        if (!isSlackUrl) {
            this.error = "Please enter a valid Slack url";
        } else if (alreadyExists) {
            this.error = "This webhook url already exists";
        } else {
            int now = Context.now();

            setFrequencyInSeconds(24*60*60); // set initially to one day

            SlackWebhook newWebhook = new SlackWebhook(now, webhookUrl, 1, 1, now, getSUser().getLogin(), dashboardUrl,now,frequencyInSeconds, webhookName);
            this.apiTokenId = SlackWebhooksDao.instance.insertOne(newWebhook).getInsertedId().asInt32().getValue();
        }

        return Action.SUCCESS.toUpperCase();
    }

    public String deleteSlackWebhook() {
        SlackWebhooksDao.instance.deleteAll(new BasicDBObject("_id", apiTokenId));
        return Action.SUCCESS.toUpperCase();
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getError() {
        return this.error;
    }

    public String getWebhookUrl() {
        return this.webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public List<ApiToken> getApiTokenList() {
        return apiTokenList;
    }

    public int getApiTokenId() {
        return this.apiTokenId;
    }

    public void setApiTokenId(int apiTokenId) {
        this.apiTokenId = apiTokenId;
    }

    public boolean isApiTokenDeleted() {
        return apiTokenDeleted;
    }

    public ApiToken.Utility getTokenUtility() {
        return tokenUtility;
    }

    public void setTokenUtility(ApiToken.Utility tokenUtility) {
        this.tokenUtility = tokenUtility;
    }

    public String getWebhookName() {
        return webhookName;
    }

    public void setWebhookName(String webhookName) {
        this.webhookName = webhookName;
    }

    @Override
    public void setServletRequest(HttpServletRequest request) {
        this.dashboardUrl = createDashboardUrlFromRequest(request);
    }
}
