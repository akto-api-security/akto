package com.akto.dto.rbac;
import java.util.HashMap;
import java.util.Map;

import com.akto.dto.rbac.RbacEnums.AccessGroups;
import com.akto.dto.rbac.RbacEnums.Feature;
import com.akto.dto.rbac.RbacEnums.ReadWriteAccess;
import com.akto.dto.RBAC.Role;

public class DeveloperRoleStrategy implements RoleStrategy{
    @Override
    public Role[] getRoleHierarchy() {
        return new Role[]{Role.DEVELOPER, Role.GUEST};
    }

    @Override
    public Map<Feature, ReadWriteAccess> getFeatureAccessMap() {
        Map<Feature, ReadWriteAccess> accessMap = new HashMap<>();
        for (AccessGroups group : AccessGroups.getAccessGroups()) {
            ReadWriteAccess access = ReadWriteAccess.READ ;
            if(group == AccessGroups.SETTINGS ){
                access = ReadWriteAccess.READ_WRITE;
            }else if(group == AccessGroups.PII_DATA){
                access = ReadWriteAccess.NO_ACCESS;
            }
            for (Feature feature : Feature.getFeaturesForAccessGroup(group)) {
                accessMap.put(feature, access);
            }
        }
        RbacEnums.mergeUserFeaturesAccess(accessMap);
        return accessMap;
    }
}
