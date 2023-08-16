package com.akto.dto;

import com.akto.dao.context.Context;
import com.mongodb.BasicDBObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class User {
    private String name;
    private String login;
    public static final String LOGIN = "login";
    private int id;
    private List<String> refreshTokens;
    public static final String LAST_LOGIN_TS = "lastLoginTs";
    private int lastLoginTs;

    private Map<String, UserAccountEntry> accounts;
    public static final String ACCOUNTS = "accounts";

    private Map<String, SignupInfo> signupInfoMap;
    public static final String SIGNUP_INFO_MAP = "signupInfoMap";

    public User() {}

    public User(String name, String login, Map<String, UserAccountEntry> accounts, Map<String, SignupInfo> signupInfoMap) {
        this.name = name;
        this.login = login;
        this.id = Context.getId();
        this.accounts = accounts;
        this.signupInfoMap = signupInfoMap;
        this.refreshTokens = new ArrayList<>();
    }

    public static User create(String name, String login, SignupInfo info, Map<String, UserAccountEntry> accountEntryMap) {
        Map<String, SignupInfo> infoMap = new HashMap<>();
        infoMap.put(info.getKey(), info);
        return new User(name, login, accountEntryMap, infoMap);
    }

    public String findAnyAccountId() {
        if (this.accounts == null || this.accounts.isEmpty()) return null;

        for (String acc: accounts.keySet()) {
            return acc;
        }

        return null;
    }

    public static BasicDBObject convertUserToUserDetails(User user) {
        BasicDBObject userDetails = new BasicDBObject();
        userDetails.put("id",user.getId());
        userDetails.put("login",user.getLogin());
        userDetails.put("name",user.getName());

        return userDetails;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    @Override
    public String toString() {
        return String.format("User (name=%s, login=%s, id=%s)", name, login, id+"");
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Map<String, UserAccountEntry> getAccounts() {
        return accounts;
    }

    public void setAccounts(Map<String, UserAccountEntry> accounts) {
        this.accounts = accounts;
    }

    public Map<String, SignupInfo> getSignupInfoMap() {
        return signupInfoMap;
    }

    public void setSignupInfoMap(Map<String, SignupInfo> signupInfoMap) {
        this.signupInfoMap = signupInfoMap;
    }

    public List<String> getRefreshTokens() {
        return refreshTokens;
    }

    public void setRefreshTokens(List<String> refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    public int getLastLoginTs() {
        return lastLoginTs;
    }

    public void setLastLoginTs(int lastLoginTs) {
        this.lastLoginTs = lastLoginTs;
    }
}
