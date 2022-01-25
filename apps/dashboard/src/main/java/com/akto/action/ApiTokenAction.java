package com.akto.action;

import com.akto.dao.ApiTokensDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiToken;
import com.akto.utils.RandomString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.DeleteResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ApiTokenAction extends UserAction{
    private static final int keyLength = 40;
    private static final RandomString randomString = new RandomString(keyLength);

    public String addBurpToken() {
        String username = getSUser().getLogin();
        String apiKey = randomString.nextString();
        if (apiKey == null || apiKey.length() != keyLength) return ERROR.toUpperCase();

        ApiToken apiToken = new ApiToken(Context.now(),Context.accountId.get(),"burp_key",apiKey, Context.now(), username, ApiToken.Utility.BURP,
                Collections.singletonList("/api/uploadHar"));
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
    public String fetchBurpTokens() {
        String username = getSUser().getLogin();
        apiTokenList = ApiTokensDao.instance.findAll(
                Filters.and(
                        Filters.eq(ApiToken.USER_NAME, username),
                        Filters.eq(ApiToken.UTILITY, ApiToken.Utility.BURP)
                )
        );
        return SUCCESS.toUpperCase();
    }

    public List<ApiToken> getApiTokenList() {
        return apiTokenList;
    }

    public void setApiTokenId(int apiTokenId) {
        this.apiTokenId = apiTokenId;
    }

    public boolean isApiTokenDeleted() {
        return apiTokenDeleted;
    }
}
