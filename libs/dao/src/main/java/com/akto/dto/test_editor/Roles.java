package com.akto.dto.test_editor;

public class Roles {
    
    private String role;

    private String authKey;

    private String authToken;

    private String access;

    public Roles(String role, String authKey, String authToken, String access) {
        this.role = role;
        this.authKey = authKey;
        this.authToken = authToken;
        this.access = access;
    }

    public Roles() { }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getAuthKey() {
        return authKey;
    }

    public void setAuthKey(String authKey) {
        this.authKey = authKey;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public String getAccess() {
        return access;
    }

    public void setAccess(String access) {
        this.access = access;
    }
    
}
