package com.akto.dto;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bson.codecs.pojo.annotations.BsonIgnore;

import com.akto.dao.CustomRoleDao;
import com.akto.dao.context.Context;
import com.akto.dto.rbac.AdminRoleStrategy;
import com.akto.dto.rbac.DeveloperRoleStrategy;
import com.akto.dto.rbac.GuestRoleStrategy;
import com.akto.dto.rbac.MemberRoleStrategy;
import com.akto.dto.rbac.RoleStrategy;
import com.akto.util.Pair;
import com.mongodb.BasicDBObject;
import com.akto.dto.rbac.RbacEnums.Feature;
import com.akto.dto.rbac.RbacEnums.ReadWriteAccess;

public class Role {

    public final static Role ADMIN = new Role("ADMIN", "ADMIN");
    public final static Role MEMBER = new Role("SECURITY ENGINEER", "MEMBER");
    public final static Role DEVELOPER = new Role("DEVELOPER", "DEVELOPER");
    public final static Role GUEST = new Role("GUEST", "GUEST");

    private static final ConcurrentHashMap<Integer, Pair<List<Role>, Integer>> accountCustomRoleMap = new ConcurrentHashMap<>();
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

    public Role() {
    }

    private Role(String name, String baseRole) {
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
        this.name = name;
        this.baseRole = baseRole;
    }

    public Role(String name, String baseRole, List<Integer> apiCollectionsId, boolean defaultInviteRole) {
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

    @BsonIgnore
    public Role[] getRoleHierarchy() {
        return getRoleStrategy().getRoleHierarchy();
    }

    @BsonIgnore
    public ReadWriteAccess getReadWriteAccessForFeature(Feature feature) {
        return getRoleStrategy().getFeatureAccessMap().getOrDefault(feature, ReadWriteAccess.READ);
    }

    public String getName() {
        return name;
    }

    @BsonIgnore
    public RoleStrategy getRoleStrategy() {
        switch (this.baseRole) {
            case "ADMIN":
                return new AdminRoleStrategy();
            case "MEMBER":
                return new MemberRoleStrategy();
            case "DEVELOPER":
                return new DeveloperRoleStrategy();
            case "GUEST":
                return new GuestRoleStrategy();
            default:
                return new GuestRoleStrategy();
        }
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
    public static Set<Role> getBaseRoles() {
        Set<Role> roles = new HashSet<>();
        roles.add(ADMIN);
        roles.add(MEMBER);
        roles.add(DEVELOPER);
        roles.add(GUEST);
        return roles;
    }

    @BsonIgnore
    public static Set<String> getBaseRolesName() {
        Set<String> roles = new HashSet<>();
        roles.add(ADMIN.getName());
        roles.add(MEMBER.getName());
        roles.add(DEVELOPER.getName());
        roles.add(GUEST.getName());
        return roles;
    }

    @BsonIgnore
    public static List<Role> getCustomRolesForAccount(int accountId) {
        List<Role> customRoles = new ArrayList<>();

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

    public static Role valueOf(String role) throws Exception {

        switch (role) {
            case "ADMIN":
                return ADMIN;
            case "MEMBER":
                return MEMBER;
            case "DEVELOPER":
                return DEVELOPER;
            case "GUEST":
                return GUEST;
            default:
                break;
        }

        int accountId = Context.accountId.get();
        List<Role> customRoles = getCustomRolesForAccount(accountId);

        for (Role customRole : customRoles) {
            if (role.equals(customRole.getName())) {
                return customRole;
            }
        }

        throw new Exception("Role not found");
    }

    boolean defaultInviteRole;

    public boolean getDefaultInviteRole() {
        return defaultInviteRole;
    }

    public void setDefaultInviteRole(boolean defaultInviteRole) {
        this.defaultInviteRole = defaultInviteRole;
    }

}