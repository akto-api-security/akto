package com.akto.dto;


import org.bson.types.ObjectId;

import com.akto.dao.CustomRoleDao;
import com.akto.dto.rbac.*;

import com.akto.dto.rbac.RbacEnums.Feature;
import com.akto.dto.rbac.RbacEnums.ReadWriteAccess;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;

public class RBAC {

    private ObjectId id;
    private int userId;

    public static final String USER_ID = "userId";
    private String role;
    public static final String ROLE = "role";
    private int accountId;
    public static final String ACCOUNT_ID = "accountId";
    private List<Integer> apiCollectionsId;
    public static final String API_COLLECTIONS_ID = "apiCollectionsId";

    public static final String ALLOWED_FEATURES_FOR_USER = "allowedFeaturesForUser";

    @Getter
    @Setter
    private List<String> allowedFeaturesForUser;

    public static final String SCOPE_ROLE_MAPPING = "scopeRoleMapping";
    @Getter
    @Setter
    private Map<String, String> scopeRoleMapping;

    public enum Role {
        ADMIN("ADMIN",new AdminRoleStrategy()),
        MEMBER("SECURITY ENGINEER", new MemberRoleStrategy()),
        DEVELOPER("DEVELOPER", new DeveloperRoleStrategy()),
        GUEST("GUEST", new GuestRoleStrategy()),
        THREAT_ENGINEER("THREAT ENGINEER", new ThreatEngineerRoleStrategy()),
        THREAT_VIEWER("THREAT VIEWER", new ThreatViewerRoleStrategy()),
        NO_ACCESS("NO ACCESS", new NoAccessRoleStrategy());

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
            // change default for dev and and feature label to NO_ACCESS
            ReadWriteAccess defaultAccess = ReadWriteAccess.READ;
            if(this.name.equals(Role.DEVELOPER.name()) || this.name.equals(Role.GUEST.name())){
                if(feature.getAccessGroup().equals(RbacEnums.AccessGroups.PII_DATA)){
                    defaultAccess = ReadWriteAccess.NO_ACCESS;
                }
            }
            return roleStrategy.getFeatureAccessMap().getOrDefault(feature, defaultAccess);
        }

        public String getName() {
            return name;
        }
    }

    public RBAC(int userId, String role) {
        this.userId = userId;
        this.role = role;
        this.apiCollectionsId = new ArrayList<>();
    }

    public RBAC(int userId, String role, int accountId) {
        this.userId = userId;
        this.role = role;
        this.accountId = accountId;
        this.apiCollectionsId = new ArrayList<>();
        this.allowedFeaturesForUser = new ArrayList<>();
    }

    public RBAC(int userId, String role, int accountId, Map <String,String> scopeRoleMapping) {
        this.userId = userId;
        this.role = role;
        this.accountId = accountId;
        this.scopeRoleMapping = scopeRoleMapping;
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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
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

    /**
     * Initializes default scope-role mapping if empty.
     * If scopeRoleMapping is null or empty, creates a new HashMap with API scope mapped to the provided default role.
     * Otherwise, returns the existing scopeRoleMapping unchanged.
     *
     * @param scopeRoleMapping the current scope-role mapping (may be null/empty)
     * @param defaultRole the default role to use for API scope if mapping is empty
     * @return the initialized or existing scopeRoleMapping
     */
    public static Map<String, String> initializeScopeRoleMapping(Map<String, String> scopeRoleMapping, String defaultRole) {
        if (scopeRoleMapping == null || scopeRoleMapping.isEmpty()) {
            Map<String, String> initialized = new HashMap<>();
            initialized.put("API", defaultRole);
            return initialized;
        }
        return scopeRoleMapping;
    }


    public Role getRoleForScope(CONTEXT_SOURCE scope) {
        if (scope == null) {
            return null;
        }

        String scopeStr = scope.toString();
        if (this.scopeRoleMapping != null && !this.scopeRoleMapping.isEmpty()) {
            if (this.scopeRoleMapping.containsKey(scopeStr)) {
                String roleStr = this.scopeRoleMapping.get(scopeStr);
                return resolveRoleString(roleStr);
            }
            // User has scopeRoleMapping but this scope is NOT in it - NO ACCESS
            return Role.NO_ACCESS;
        }

        // Fall back to old role field ONLY if scopeRoleMapping is null/empty (backward compatibility)
        if (this.role != null) {
            return resolveRoleString(this.role);
        }

        return null;
    }

    private Role resolveRoleString(String roleStr) {
        if (roleStr == null) {
            return null;
        }

        try {
            return Role.valueOf(roleStr);
        } catch (IllegalArgumentException e) {
        }

        try {
            CustomRole customRole = CustomRoleDao.instance.findRoleByName(roleStr);
            if (customRole != null && customRole.getBaseRole() != null) {
                try {
                    return Role.valueOf(customRole.getBaseRole());
                } catch (IllegalArgumentException e) {
                    return Role.GUEST;
                }
            }
        } catch (Exception e) {
        }
        return Role.GUEST;
    }
}
