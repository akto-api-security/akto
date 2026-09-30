package com.akto.utils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.akto.dao.CustomRoleDao;
import com.akto.dao.RBACDao;
import com.akto.dto.CustomRole;
import com.akto.dto.RBAC;
import com.akto.dto.RBAC.Role;

/*
 * Who may give which role, for invites and role changes in every product.
 * Users limited to specific collections (team admins) may only give the custom roles their own role
 * lists as assignable, so they can never hand out access beyond their team.
 * Everyone else follows the role hierarchy, as before.
 */
public class RoleAssignment {

    /** Roles a collection-limited caller may give, or null when the caller is not limited (the role hierarchy applies). */
    public static Set<String> limitedAssignableRoles(int callerId, int accountId) {
        List<Integer> callerCollections = RBACDao.instance.getUserCollectionsById(callerId, accountId);
        if (callerCollections == null || callerCollections.isEmpty()) {
            return null;
        }
        CustomRole callerRole = RBACDao.currentCustomRole(callerId, accountId);
        if (callerRole == null || callerRole.getAssignableRoles() == null) {
            return Collections.emptySet();
        }
        return new HashSet<>(callerRole.getAssignableRoles());
    }

    /** True if the caller may give this role (built-in or custom role name). No access can always be given. */
    public static boolean canAssign(int callerId, int accountId, String roleName) {
        if (roleName == null) {
            return false;
        }
        if (Role.NO_ACCESS.name().equals(roleName)) {
            return true;
        }
        Set<String> limited = limitedAssignableRoles(callerId, accountId);
        if (limited != null) {
            return limited.contains(roleName);
        }
        Role baseRole = baseRoleOf(roleName);
        return baseRole != null && Arrays.asList(RBACDao.getCurrentRoleForUser(callerId, accountId).getRoleHierarchy()).contains(baseRole);
    }

    /** True if the caller may change a user's roles: every role the user holds must be one the caller may give. */
    public static boolean canManage(int callerId, int accountId, RBAC target) {
        if (target == null) {
            return true;
        }
        Collection<String> targetRoles = (target.getScopeRoleMapping() != null && !target.getScopeRoleMapping().isEmpty())
                ? target.getScopeRoleMapping().values() : Collections.singletonList(target.getRole());
        for (String targetRole : targetRoles) {
            if (targetRole != null && !canAssign(callerId, accountId, targetRole)) {
                return false;
            }
        }
        return true;
    }

    private static Role baseRoleOf(String roleName) {
        Role role = Role.fromName(roleName);
        if (role != null) {
            return role;
        }
        CustomRole customRole = CustomRoleDao.instance.findRoleByName(roleName);
        return customRole == null ? null : Role.fromName(customRole.getBaseRole());
    }
}
