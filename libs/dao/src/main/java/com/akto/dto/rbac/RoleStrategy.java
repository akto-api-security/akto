package com.akto.dto.rbac;

import java.util.Map;

import com.akto.dto.Role;
import com.akto.dto.rbac.RbacEnums.Feature;
import com.akto.dto.rbac.RbacEnums.ReadWriteAccess;

public interface RoleStrategy {
    Role[] getRoleHierarchy();
    Map<Feature, ReadWriteAccess> getFeatureAccessMap();
}