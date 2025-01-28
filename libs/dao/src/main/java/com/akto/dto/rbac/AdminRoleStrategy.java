package com.akto.dto.rbac;

import java.util.HashMap;
import java.util.Map;

import com.akto.dto.RBAC.Role;
import com.akto.dto.rbac.RbacEnums.AccessGroups;
import com.akto.dto.rbac.RbacEnums.Feature;
import com.akto.dto.rbac.RbacEnums.ReadWriteAccess;

public class AdminRoleStrategy implements RoleStrategy {
    @Override
    public CustomRole[] getRoleHierarchy() {
        return new CustomRole[]{CustomRole.ADMIN, CustomRole.MEMBER, CustomRole.DEVELOPER, CustomRole.GUEST};
    }

    @Override
    public Map<Feature, ReadWriteAccess> getFeatureAccessMap() {
        return createAccessMap(AccessGroups.getAccessGroups(), ReadWriteAccess.READ_WRITE);
    }

    private Map<Feature, ReadWriteAccess> createAccessMap(AccessGroups[] groups, ReadWriteAccess access) {
        Map<Feature, ReadWriteAccess> accessMap = new HashMap<>();
        for (AccessGroups group : groups) {
            for (Feature feature : Feature.getFeaturesForAccessGroup(group)) {
                accessMap.put(feature, access);
            }
        }
        return accessMap;
    }
}