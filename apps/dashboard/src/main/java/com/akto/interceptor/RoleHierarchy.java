package com.akto.interceptor;

import java.util.HashMap;
import java.util.Map;

public class RoleHierarchy {
    private static final Map<String, String[]> hierarchy = new HashMap<>();

    static {
        hierarchy.put("ADMIN", new String[]{"ADMIN", "MEMBER"});
        hierarchy.put("MEMBER", new String[]{"MEMBER"});
    }

    public static boolean hasRole(String userRole, String requiredRole) {
        String[] roles = hierarchy.get(userRole);
        if (roles == null) {
            return false;
        }
        for (String role : roles) {
            if (role.equalsIgnoreCase(requiredRole)) {
                return true;
            }
        }
        return false;
    }
}
