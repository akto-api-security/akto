package com.akto.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

import com.akto.dao.CustomRoleDao;
import com.akto.dao.RBACDao;
import com.akto.dao.context.Context;
import com.akto.dto.CustomRole;
import com.akto.dto.RBAC;
import com.akto.dto.RBAC.Role;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;

/*
 * Who may give which role, for invites and role changes in every product.
 * A team admin (a custom role limited to specific collections, with "roles this role can give" set)
 * may only give those roles, so it can never hand out access beyond its team.
 * Everyone else follows the role hierarchy, as before.
 * Checks run in the product the role is given for, not the product the request came from.
 */
public class RoleAssignment {

    /** Roles a team admin may give in the current product, or null when the role hierarchy applies. */
    public static Set<String> limitedAssignableRoles(int callerId, int accountId) {
        CustomRole callerRole = RBACDao.currentCustomRole(callerId, accountId);
        if (callerRole == null || callerRole.getAssignableRoles() == null || callerRole.getAssignableRoles().isEmpty()) {
            return null;
        }
        List<Integer> callerCollections = RBACDao.instance.getUserCollectionsById(callerId, accountId);
        if (callerCollections == null || callerCollections.isEmpty()) {
            return null;
        }
        return new HashSet<>(callerRole.getAssignableRoles());
    }

    /** A team admin may only give custom roles that are limited to collections and not based on Admin. */
    public static boolean isGivableByTeamAdmin(CustomRole role) {
        if (role == null || Role.ADMIN.name().equals(role.getBaseRole())) {
            return false;
        }
        return (role.getApiCollectionsId() != null && !role.getApiCollectionsId().isEmpty())
                || (role.getCollectionRules() != null && !role.getCollectionRules().isEmpty());
    }

    /** True if the caller may give this role (built-in or custom role name) in the given product scope. No access can always be given. */
    public static boolean canAssign(int callerId, int accountId, String scope, String roleName) {
        if (roleName == null) {
            return false;
        }
        if (Role.NO_ACCESS.name().equals(roleName)) {
            return true;
        }
        return inScope(scope, () -> {
            Set<String> limited = limitedAssignableRoles(callerId, accountId);
            if (limited != null) {
                // re-checked here too: the role may have changed since it was listed
                return limited.contains(roleName) && isGivableByTeamAdmin(CustomRoleDao.instance.findRoleByName(roleName));
            }
            return Arrays.asList(RBACDao.getCurrentRoleForUser(callerId, accountId).getRoleHierarchy()).contains(baseRoleOf(roleName));
        });
    }

    /** True if the caller may change a user's roles: every role the user holds must be one the caller may give in that product. */
    public static boolean canManage(int callerId, int accountId, RBAC target) {
        return target == null || canManage(callerId, accountId, target.getScopeRoleMapping(), target.getRole());
    }

    /** Same as above for roles held elsewhere, e.g. a pending invite: per-product roles, or the older single role. */
    public static boolean canManage(int callerId, int accountId, Map<String, String> scopeRoleMapping, String singleRole) {
        if (scopeRoleMapping == null || scopeRoleMapping.isEmpty()) {
            return singleRole == null || canAssign(callerId, accountId, null, singleRole);
        }
        for (Map.Entry<String, String> entry : scopeRoleMapping.entrySet()) {
            if (entry.getValue() != null && !canAssign(callerId, accountId, entry.getKey(), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    // runs the check with the given product scope (null keeps the request's scope)
    private static boolean inScope(String scope, BooleanSupplier check) {
        CONTEXT_SOURCE previous = Context.contextSource.get();
        try {
            if (scope != null) {
                Context.contextSource.set(CONTEXT_SOURCE.valueOf(scope));
            }
            return check.getAsBoolean();
        } catch (IllegalArgumentException invalidScope) {
            return false;
        } finally {
            Context.contextSource.set(previous);
        }
    }

    // unknown or deleted roles count as Guest, the same as when resolving a user's role.
    // Role changes are rare writes, so they read roles fresh instead of from the per-request cache.
    private static Role baseRoleOf(String roleName) {
        Role role = Role.fromName(roleName);
        if (role != null) {
            return role;
        }
        CustomRole customRole = CustomRoleDao.instance.findRoleByName(roleName);
        Role baseRole = customRole == null ? null : Role.fromName(customRole.getBaseRole());
        return baseRole == null ? Role.GUEST : baseRole;
    }
}
