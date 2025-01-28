package com.akto.dto;

import java.util.List;

public class CustomRole {

    public final static String _NAME = "name";
    public final static String BASE_ROLE = "baseRole";
    public static final String API_COLLECTIONS_ID = "apiCollectionsId";
    public static final String DEFAULT_INVITE_ROLE = "defaultInviteRole";
    private String name;
    private String baseRole;
    private List<Integer> apiCollectionsId;
    boolean defaultInviteRole;

    public CustomRole() {
    }

    public CustomRole(String name, String baseRole, List<Integer> apiCollectionsId, boolean defaultInviteRole) {
        switch (baseRole) {
            case "ADMIN":
            case "DEVELOPER":
            case "MEMBER":
            case "GUEST":
                break;
            default:
                baseRole = "GUEST";
                break;
        }
        this.baseRole = baseRole;
        this.name = name;
        this.apiCollectionsId = apiCollectionsId;
        this.defaultInviteRole = defaultInviteRole;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBaseRole() {
        return baseRole;
    }

    public void setBaseRole(String baseRole) {
        this.baseRole = baseRole;
    }

    public List<Integer> getApiCollectionsId() {
        return apiCollectionsId;
    }

    public void setApiCollectionsId(List<Integer> apiCollectionsId) {
        this.apiCollectionsId = apiCollectionsId;
    }

    public boolean getDefaultInviteRole() {
        return defaultInviteRole;
    }

    public void setDefaultInviteRole(boolean defaultInviteRole) {
        this.defaultInviteRole = defaultInviteRole;
    }

}