package com.akto.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.bson.codecs.pojo.annotations.BsonIgnore;

import com.akto.dao.CustomRoleDao;
import com.akto.dao.context.Context;
import com.akto.util.Pair;
import com.mongodb.BasicDBObject;

public class CustomRole {

    private static final ConcurrentHashMap<Integer, Pair<List<CustomRole>, Integer>> accountCustomRoleMap = new ConcurrentHashMap<>();
    private static final int EXPIRY_TIME = 15 * 60; // 15 minute

    public final static String BASE_ROLE = "baseRole";
    public final static String _NAME = "name";
    private String name;

    public void setName(String name) {
        this.name = name;
    }

    private String baseRole;

    public String getBaseRole() {
        return baseRole;
    }

    public void setBaseRole(String baseRole) {
        this.baseRole = baseRole;
    }

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

    private List<Integer> apiCollectionsId;
    public static final String API_COLLECTIONS_ID = "apiCollectionsId";
    public static final String DEFAULT_INVITE_ROLE = "defaultInviteRole";

    public List<Integer> getApiCollectionsId() {
        return apiCollectionsId;
    }

    public void setApiCollectionsId(List<Integer> apiCollectionsId) {
        this.apiCollectionsId = apiCollectionsId;
    }


    @BsonIgnore
    public static List<CustomRole> getCustomRolesForAccount(int accountId) {
        List<CustomRole> customRoles = new ArrayList<>();

        if (accountCustomRoleMap.containsKey(accountId)
                && accountCustomRoleMap.get(accountId).getSecond() < EXPIRY_TIME) {
            customRoles = accountCustomRoleMap.get(accountId).getFirst();
        } else {
            customRoles = CustomRoleDao.instance.findAll(new BasicDBObject());
            accountCustomRoleMap.put(accountId, new Pair<>(customRoles, Context.now()));
        }
        return customRoles;
    }

    public static void deleteCustomRoleCache(int accountId) {
        accountCustomRoleMap.remove(accountId);
    }

    boolean defaultInviteRole;

    public boolean getDefaultInviteRole() {
        return defaultInviteRole;
    }

    public void setDefaultInviteRole(boolean defaultInviteRole) {
        this.defaultInviteRole = defaultInviteRole;
    }

}