package com.akto.dto;

import com.akto.dao.context.Context;
import com.akto.dto.messaging.Message;
import com.mongodb.BasicDBObject;

import java.util.HashMap;
import java.util.Map;

public class User {
    private String name;
    private String login;
    private int id;

    private Map<String, UserAccountEntry> accounts;

    private Message.Mode preferredChannel;
    private Map<String, SignupInfo> signupInfoMap;

    public User() {}

    public User(String name, String login, Map<String, UserAccountEntry> accounts, Map<String, SignupInfo> signupInfoMap,
                Message.Mode preferredChannel) {
        this.name = name;
        this.login = login;
        this.id = Context.getId();
        this.accounts = accounts;
        this.signupInfoMap = signupInfoMap;
        this.preferredChannel = preferredChannel;
    }

    public static User create(String name, String login, SignupInfo info, Map<String, UserAccountEntry> accountEntryMap) {
        Map<String, SignupInfo> infoMap = new HashMap<>();
        infoMap.put(info.getKey(), info);
        return new User(name, login, accountEntryMap, infoMap, Message.Mode.EMAIL);
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

    public Message.Mode getPreferredChannel() {
        return Message.Mode.SLACK;
    }

    public void setPreferredChannel(Message.Mode preferredChannel) {
        this.preferredChannel = preferredChannel;
    }

    public Map<String, SignupInfo> getSignupInfoMap() {
        return signupInfoMap;
    }

    public void setSignupInfoMap(Map<String, SignupInfo> signupInfoMap) {
        this.signupInfoMap = signupInfoMap;
    }
}
