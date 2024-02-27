package com.akto.dto.billing;

import com.akto.dao.context.Context;

public class Tokens {
    
    private String token;
    public static final String TOKEN = "token";
    private String orgId;
    public static final String ORG_ID = "orgId";
    private int accountId;
    public static final String ACCOUNT_ID = "accountId";
    private int createdAt;
    public static final String CREATED_AT = "createdAt";
    private int updatedAt;
    public static final String UPDATED_AT = "updatedAt";

    private static final int TOKEN_ENPIRY_PERIOD_IN_SECONDS = 5 * 60 * 60;

    public Tokens(String token, String orgId, int accountId, int createdAt, int updatedAt) {
        this.token = token;
        this.orgId = orgId;
        this.accountId = accountId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Tokens() {
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public int getAccountId() {
        return accountId;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }

    public int getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(int createdAt) {
        this.createdAt = createdAt;
    }

    public int getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(int updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isOldToken() {
        return Context.now() > (this.updatedAt + TOKEN_ENPIRY_PERIOD_IN_SECONDS);
    }

}
