package com.akto.dto;

public class UserAccountEntry {
    private int accountId;
    private String name;
    private boolean isDefault = false;

    public UserAccountEntry() {}

    public UserAccountEntry(int accountId) {
        this.accountId = accountId;
    }

    public UserAccountEntry(int accountId, String name) {
        this.accountId = accountId;
        this.name = name;
    }

    public int getAccountId() {
        return accountId;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
