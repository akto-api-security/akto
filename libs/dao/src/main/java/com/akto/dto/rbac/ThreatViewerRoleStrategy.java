package com.akto.dto.rbac;

import java.util.HashMap;
import java.util.Map;

import com.akto.dto.rbac.RbacEnums.Feature;
import com.akto.dto.rbac.RbacEnums.ReadWriteAccess;
import com.akto.dto.RBAC.Role;

public class ThreatViewerRoleStrategy implements RoleStrategy {

    @Override
    public Role[] getRoleHierarchy() {
        return new Role[]{Role.THREAT_VIEWER};
    }

    @Override
    public Map<Feature, ReadWriteAccess> getFeatureAccessMap() {
        //Threat viewer is superset of security engineer with threat protection read access added
        Map<Feature, ReadWriteAccess> accessMap = new HashMap<>();
        for (RbacEnums.AccessGroups group : RbacEnums.AccessGroups.getAccessGroups()) {
            ReadWriteAccess access = ReadWriteAccess.READ ;
            if(group != RbacEnums.AccessGroups.SETTINGS && group != RbacEnums.AccessGroups.ADMIN){
                access = ReadWriteAccess.READ_WRITE;
            }
            for (Feature feature : Feature.getFeaturesForAccessGroup(group)) {
                accessMap.put(feature, access);
            }
        }

        accessMap.put(Feature.API_TOKENS, ReadWriteAccess.READ_WRITE);
        accessMap.put(Feature.THREAT_PROTECTION, ReadWriteAccess.READ);

        RbacEnums.mergeUserFeaturesAccess(accessMap);
        return accessMap;
    }
}
