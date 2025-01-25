package com.akto.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

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
    public final static Role ADMIN = new Role("ADMIN", new AdminRoleStrategy());
    public final static Role MEMBER = new Role("SECURITY ENGINEER", new MemberRoleStrategy());
    public final static Role DEVELOPER = new Role("DEVELOPER", new DeveloperRoleStrategy());
    public final static Role GUEST = new Role("GUEST", new GuestRoleStrategy());

    private static final ConcurrentHashMap<Integer, Pair<List<Role>, Integer>> accountRoleMap = new ConcurrentHashMap<>();
    private static final int EXPIRY_TIME = 15 * 60; // 15 minute

    private final RoleStrategy roleStrategy;
    private String name;

    Role(String name, RoleStrategy roleStrategy) {
        this.roleStrategy = roleStrategy;
        this.name = name;
    }

    public Role[] getRoleHierarchy() {
        return roleStrategy.getRoleHierarchy();
    }

    public ReadWriteAccess getReadWriteAccessForFeature(Feature feature) {
        return roleStrategy.getFeatureAccessMap().getOrDefault(feature, ReadWriteAccess.READ);
    }

    public String getName() {
        return name;
    }

    private List<Integer> apiCollectionsId;
    public static final String API_COLLECTIONS_ID = "apiCollectionsId";

    public List<Integer> getApiCollectionsId() {
        return apiCollectionsId;
    }
    public void setApiCollectionsId(List<Integer> apiCollectionsId) {
        this.apiCollectionsId = apiCollectionsId;
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
        List<Role> customRoles = new ArrayList<>();

        if (accountRoleMap.containsKey(accountId) && accountRoleMap.get(accountId).getSecond() < EXPIRY_TIME) {
            customRoles = accountRoleMap.get(accountId).getFirst();
        } else {
            customRoles = CustomRoleDao.instance.findAll(new BasicDBObject());
            accountRoleMap.put(accountId, new Pair<>(customRoles, Context.now()));
        }

        for (Role customRole : customRoles) {
            if (role.equals(customRole.getName())) {
                return customRole;
            }
        }

        throw new Exception("Role not found");
    }

}