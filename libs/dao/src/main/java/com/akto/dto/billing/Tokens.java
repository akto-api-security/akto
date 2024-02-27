package com.akto.dto.billing;

import com.akto.dao.context.Context;

public class Tokens {
    
    private String token;

    private String orgId;

    private int accountId;

    private int createdAt;

    private int updatedAt;

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
        return Context.now() > (this.createdAt + 14400);
    }

}
