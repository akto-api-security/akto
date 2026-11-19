package com.akto.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
import com.akto.dto.rbac.RbacEnums.Feature;
import com.akto.dto.rbac.RbacEnums.ReadWriteAccess;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.client.model.Filters;

/*
 * Who may give which role, checked in the product the role is given for:
 * - users limited to collections (team admins) give only the roles their custom role lists;
 * - everyone else follows the role hierarchy, and an admin manages every product, as before;
 * - nobody gives a custom role with more access to a feature than they have.
 */
public class RoleAssignment {

    /** Roles a user limited to collections may give in the current product (empty: none), or null when the role hierarchy applies. */
    public static Set<String> limitedAssignableRoles(int callerId, int accountId) {
        // collection limits apply only with the RBAC feature, the same as everywhere else
        if (!UsageMetricCalculator.isRbacFeatureAvailable(accountId)) {
            return null;
        }
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

    /** A team admin may only give custom roles that are limited to collections and not based on Admin. */
    public static boolean isGivableByTeamAdmin(CustomRole role) {
        if (role == null || Role.fromName(role.getBaseRole()) == Role.ADMIN) {
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
        if (Role.fromName(roleName) == Role.NO_ACCESS) {
            return true;
        }
        boolean requestScopeAdmin = isUnlimitedAdmin(callerId, accountId);
        return inScope(scope, () -> {
            Role callerRole = RBACDao.getCurrentRoleForUser(callerId, accountId);
            if (callerRole == Role.NO_ACCESS && requestScopeAdmin) {
                // no role in this product: an admin of the request's product manages it, as before
                return true;
            }
            Set<String> limited = limitedAssignableRoles(callerId, accountId);
            if (limited != null) {
                // re-checked here too: the role may have changed since it was listed
                CustomRole role = CustomRoleDao.instance.findRoleByName(roleName);
                return limited.contains(roleName) && isGivableByTeamAdmin(role) && !exceedsCaller(callerId, accountId, callerRole, role);
            }
            if (!Arrays.asList(callerRole.getRoleHierarchy()).contains(baseRoleOf(roleName))) {
                return false;
            }
            return Role.fromName(roleName) != null || !exceedsCaller(callerId, accountId, callerRole, CustomRoleDao.instance.findRoleByName(roleName));
        });
    }

    /** True if the caller may change a user's roles: every role the user holds must be one the caller may give in that product. */
    public static boolean canManage(int callerId, int accountId, RBAC target) {
        return target == null || canManage(callerId, accountId, target.getScopeRoleMapping(), target.getRole());
    }

    /** Same as above for roles held elsewhere, e.g. a pending invite: per-product roles, or the older single role (held in every product). */
    public static boolean canManage(int callerId, int accountId, Map<String, String> scopeRoleMapping, String singleRole) {
        if (scopeRoleMapping == null || scopeRoleMapping.isEmpty()) {
            if (singleRole == null) {
                return true;
            }
            if (!isExistingRole(singleRole)) {
                return true;
            }
            for (String scope : productScopes(accountId)) {
                if (!canAssign(callerId, accountId, scope, singleRole)) {
                    return false;
                }
            }
            return true;
        }
        for (Map.Entry<String, String> entry : scopeRoleMapping.entrySet()) {
            // a role that no longer exists gives no access, so anyone who can change access may replace it
            if (entry.getValue() != null && isExistingRole(entry.getValue()) && !canAssign(callerId, accountId, entry.getKey(), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    /** True for a built-in role or a custom role that exists. */
    public static boolean isExistingRole(String roleName) {
        return Role.fromName(roleName) != null || CustomRoleDao.instance.findRoleByName(roleName) != null;
    }

    /** True if the caller's role in the current product allows changing other users' access (the "Invite users" permission). */
    public static boolean canChangeOthersAccess(int callerId, int accountId) {
        Role callerRole = RBACDao.getCurrentRoleForUser(callerId, accountId);
        ReadWriteAccess access = RBACDao.resolveFeatureAccess(callerId, accountId, Feature.INVITE_MEMBERS,
                callerRole.getReadWriteAccessForFeature(Feature.INVITE_MEMBERS));
        return access == ReadWriteAccess.READ_WRITE;
    }

    /** True for an admin of the current product who is not limited to specific collections (built-in Admin, or a custom role on it with none). */
    public static boolean isUnlimitedAdmin(int userId, int accountId) {
        if (RBACDao.getCurrentRoleForUser(userId, accountId) != Role.ADMIN) {
            return false;
        }
        List<Integer> collections = RBACDao.instance.getUserCollectionsById(userId, accountId);
        return collections == null || collections.isEmpty();
    }

    /** Built-in roles in their stored form (e.g. "admin" -> "ADMIN"); custom role names as they are. */
    public static String normalizeRoleName(String roleName) {
        Role role = Role.fromName(roleName);
        return role != null ? role.name() : roleName;
    }

    /*
     * Products that would be left without an admin if the user's access became newScopeRoleMapping with
     * newExpiresAt (null mapping: the user is removed). Only products that have an admin whose access does
     * not expire today are protected, so an account already in that state is never blocked.
     */
    public static List<String> productsLosingLastAdmin(int accountId, int targetUserId, Map<String, String> newScopeRoleMapping, int newExpiresAt) {
        List<RBAC> rbacs = RBACDao.instance.findAll(Filters.eq(RBAC.ACCOUNT_ID, accountId));
        // only products the account still has: an admin of a product that is gone must stay removable
        Set<String> scopes = new LinkedHashSet<>(productScopes(accountId));
        List<String> losing = new ArrayList<>();
        for (String scope : scopes) {
            boolean hasPermanentAdmin = false;
            boolean keepsPermanentAdmin = false;
            for (RBAC rbac : rbacs) {
                if (!isPermanentAdmin(rbac.getScopeRoleMapping(), rbac.getRole(), rbac.getAccessExpiresAt(), scope)) {
                    continue;
                }
                hasPermanentAdmin = true;
                if (rbac.getUserId() != targetUserId) {
                    keepsPermanentAdmin = true;
                }
            }
            boolean targetStaysAdmin = newScopeRoleMapping != null && isPermanentAdmin(newScopeRoleMapping, null, newExpiresAt, scope);
            if (hasPermanentAdmin && !keepsPermanentAdmin && !targetStaysAdmin) {
                losing.add(scope);
            }
        }
        return losing;
    }

    private static boolean isPermanentAdmin(Map<String, String> scopeRoleMapping, String singleRole, int expiresAt, String scope) {
        if (expiresAt > 0) {
            return false;
        }
        String role = (scopeRoleMapping != null && !scopeRoleMapping.isEmpty()) ? scopeRoleMapping.get(scope) : singleRole;
        if (role == null) {
            return false;
        }
        if (Role.fromName(role) != null) {
            return Role.fromName(role) == Role.ADMIN;
        }
        // a custom role based on Admin and not limited to collections is an admin too
        CustomRole customRole = CustomRoleDao.instance.findRoleByNameCached(role);
        return customRole != null && Role.fromName(customRole.getBaseRole()) == Role.ADMIN
                && (customRole.getApiCollectionsId() == null || customRole.getApiCollectionsId().isEmpty())
                && (customRole.getCollectionRules() == null || customRole.getCollectionRules().isEmpty());
    }

    /** Products this account is licensed for. */
    public static Set<String> productScopes(int accountId) {
        try {
            Set<String> scopes = UsageMetricCalculator.getAccessibleProductScopes(accountId);
            if (scopes != null && !scopes.isEmpty()) {
                return scopes;
            }
        } catch (Exception ignored) {
        }
        return Collections.singleton(CONTEXT_SOURCE.API.name());
    }

    /*
     * True if a custom role gives more access to some feature than the caller has. Only features the role
     * changes from its base role are compared; the role hierarchy already covers the base role itself.
     */
    private static boolean exceedsCaller(int callerId, int accountId, Role callerRole, CustomRole role) {
        if (role == null) {
            return true;
        }
        // the built-in Admin has everything; a custom role on Admin may have had some of it taken away
        if (callerRole == Role.ADMIN && RBACDao.currentCustomRole(callerId, accountId) == null) {
            return false;
        }
        Role baseRole = Role.fromName(role.getBaseRole());
        if (baseRole == null) {
            return true;
        }
        for (Feature feature : Feature.values()) {
            ReadWriteAccess baseAccess = baseRole.getReadWriteAccessForFeature(feature);
            ReadWriteAccess given = RBACDao.accessFor(role, feature, baseAccess);
            if (rank(given) <= rank(baseAccess)) {
                continue;
            }
            ReadWriteAccess callerAccess = RBACDao.resolveFeatureAccess(callerId, accountId, feature, callerRole.getReadWriteAccessForFeature(feature));
            if (rank(given) > rank(callerAccess)) {
                return true;
            }
        }
        return false;
    }

    private static int rank(ReadWriteAccess access) {
        if (access == ReadWriteAccess.READ_WRITE) return 2;
        if (access == ReadWriteAccess.READ) return 1;
        return 0;
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

    // unknown or deleted roles count as no access, the same as when resolving a user's role.
    // Role changes are rare writes, so they read roles fresh instead of from the per-request cache.
    private static Role baseRoleOf(String roleName) {
        Role role = Role.fromName(roleName);
        if (role != null) {
            return role;
        }
        CustomRole customRole = CustomRoleDao.instance.findRoleByName(roleName);
        Role baseRole = customRole == null ? null : Role.fromName(customRole.getBaseRole());
        return baseRole == null ? Role.NO_ACCESS : baseRole;
    }
}
