package com.akto.dto;


import org.bson.types.ObjectId;

import com.akto.dto.rbac.*;

import com.akto.dto.rbac.RbacEnums.Feature;
import com.akto.dto.rbac.RbacEnums.ReadWriteAccess;

import java.util.ArrayList;
import java.util.List;

public class RBAC {

    private ObjectId id;
    private int userId;

    public static final String USER_ID = "userId";
    private Role role;
    public static final String ROLE = "role";
    private int accountId;
    public static final String ACCOUNT_ID = "accountId";
    private List<Integer> apiCollectionsId;
    public static final String API_COLLECTIONS_ID = "apiCollectionsId";

    public enum Role {
        ADMIN("ADMIN",new AdminRoleStrategy()),
        MEMBER("SECURITY ENGINEER", new MemberRoleStrategy()),
        DEVELOPER("DEVELOPER", new DeveloperRoleStrategy()),
        GUEST("GUEST", new GuestRoleStrategy());

        private final RoleStrategy roleStrategy;
        private String name;

        Role(String name ,RoleStrategy roleStrategy) {
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
    }

    public RBAC(int userId, Role role) {
        this.userId = userId;
        this.role = role;
        this.apiCollectionsId = new ArrayList<>();
    }

    public RBAC(int userId, Role role, int accountId) {
        this.userId = userId;
        this.role = role;
        this.accountId = accountId;
        this.apiCollectionsId = new ArrayList<>();
    }

    public RBAC() {
    }

    public int getAccountId() {
        return accountId;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }


    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public List<Integer> getApiCollectionsId() {
        return apiCollectionsId;
    }
    public void setApiCollectionsId(List<Integer> apiCollectionsId) {
        this.apiCollectionsId = apiCollectionsId;
    }
}
