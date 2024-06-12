package com.akto.dto.rbac;
import java.util.HashMap;
import java.util.Map;

import com.akto.dto.rbac.RbacEnums.AccessGroups;
import com.akto.dto.rbac.RbacEnums.Feature;
import com.akto.dto.rbac.RbacEnums.ReadWriteAccess;
import com.akto.dto.RBAC.Role;

public class MemberRoleStrategy implements RoleStrategy{
    @Override
    public Role[] getRoleHierarchy() {
        return new Role[]{Role.MEMBER, Role.DEVELOPER, Role.GUEST};
    }

    @Override
    public Map<Feature, ReadWriteAccess> getFeatureAccessMap() {
        Map<Feature, ReadWriteAccess> accessMap = new HashMap<>();
        for (AccessGroups group : AccessGroups.getAccessGroups()) {
            ReadWriteAccess access = group == AccessGroups.SETTINGS ? ReadWriteAccess.READ : ReadWriteAccess.READ_WRITE;
            for (Feature feature : Feature.getFeaturesForAccessGroup(group)) {
                accessMap.put(feature, access);
            }
        }
        return accessMap;
    }
}
