package com.akto.utils;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.http.HttpServletRequest;

import com.akto.audit_logs_util.AuditLogsUtil;
import com.akto.dao.CustomRoleDao;
import com.akto.dao.RBACDao;
import com.akto.dao.RbacCacheVersionDao;
import com.akto.dao.UsersDao;
import com.akto.dao.audit_logs.ApiAuditLogsDao;
import com.akto.dao.context.Context;
import com.akto.dto.CustomRole;
import com.akto.dto.RBAC;
import com.akto.dto.RBAC.Role;
import com.akto.dto.User;
import com.akto.dto.audit_logs.ApiAuditLogs;
import com.akto.dto.audit_logs.Operation;
import com.akto.dto.audit_logs.Resource;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Pair;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

/*
 * Roles from SSO groups, the same for every SSO provider (Azure AD / JumpCloud SAML, Okta, and any added later).
 * A provider only supplies the user's groups and its group -> role mapping; this decides the roles,
 * never changes existing admins, and writes an audit log when the roles change.
 */
public class SsoRoleMapping {

    private static final LoggerMaker loggerMaker = new LoggerMaker(SsoRoleMapping.class, LogDb.DASHBOARD);

    // Most privileged first, following the role hierarchies
    private static final List<Role> ROLE_PRIORITY = Arrays.asList(
            Role.ADMIN, Role.THREAT_ENGINEER, Role.THREAT_VIEWER, Role.MEMBER, Role.DEVELOPER, Role.GUEST);

    /*
     * Roles to set at this login, or null to leave the user's roles as they are.
     * The most privileged mapped role applies to every licensed product. When the user is in none of the
     * mapped groups, roles stay as they are unless removeAccessWithoutGroup is on and the IdP sent the full
     * group list (groupsComplete), in which case the user gets no access.
     */
    public static Map<String, String> rolesForLogin(String userEmail, int accountId, Map<String, String> groupRoleMapping,
                                                    Collection<String> groups, boolean removeAccessWithoutGroup, boolean groupsComplete) {
        if (groupRoleMapping == null || groupRoleMapping.isEmpty() || isExistingAdmin(userEmail, accountId)) {
            return null;
        }
        // custom roles are stored in the account's db
        Context.accountId.set(accountId);
        String role = highestPriorityRole(groupRoleMapping, groups);
        if (role != null) {
            return sameRoleEverywhere(accountId, role);
        }
        return removeAccessWithoutGroup && groupsComplete ? sameRoleEverywhere(accountId, Role.NO_ACCESS.name()) : null;
    }

    /*
     * The most privileged role mapped to any of the groups (built-in or custom role name), or null.
     * Same base role: a role with access to all collections wins over one limited to some, then the name decides,
     * so the result never depends on the order the IdP sends groups in.
     */
    public static String highestPriorityRole(Map<String, String> groupRoleMapping, Collection<String> groups) {
        if (groupRoleMapping == null || groups == null) {
            return null;
        }
        String bestRole = null;
        int bestRank = Integer.MAX_VALUE;
        for (String group : groups) {
            String mappedRole = groupRoleMapping.get(group);
            if (mappedRole == null) continue;
            Role baseRole = Role.fromName(mappedRole);
            boolean limited = false;
            if (baseRole == null) {
                CustomRole customRole = CustomRoleDao.instance.findRoleByName(mappedRole);
                baseRole = customRole == null ? null : Role.fromName(customRole.getBaseRole());
                limited = customRole != null && ((customRole.getApiCollectionsId() != null && !customRole.getApiCollectionsId().isEmpty())
                        || (customRole.getCollectionRules() != null && !customRole.getCollectionRules().isEmpty()));
            }
            int priority = ROLE_PRIORITY.indexOf(baseRole);
            if (priority < 0) continue;
            int rank = priority * 2 + (limited ? 1 : 0);
            if (rank < bestRank || (rank == bestRank && mappedRole.compareTo(bestRole) < 0)) {
                bestRank = rank;
                bestRole = mappedRole;
            }
        }
        return bestRole;
    }

    private static Map<String, String> sameRoleEverywhere(int accountId, String role) {
        Map<String, String> scopeRoleMapping = new HashMap<>();
        for (String scope : RBAC.getEnabledScopesForAccount(accountId)) {
            scopeRoleMapping.put(scope, role);
        }
        return scopeRoleMapping;
    }

    private static RBAC findRbac(String userEmail, int accountId) {
        User user = UsersDao.instance.findOne(Filters.eq(User.LOGIN, userEmail));
        return user == null ? null : RBACDao.instance.findOne(Filters.and(Filters.eq(RBAC.USER_ID, user.getId()), Filters.eq(RBAC.ACCOUNT_ID, accountId)));
    }

    // Existing Admins are never changed by the SSO group mapping, so a wrong mapping cannot lock the account out
    public static boolean isExistingAdmin(String userEmail, int accountId) {
        RBAC rbac = findRbac(userEmail, accountId);
        if (rbac == null) {
            return false;
        }
        if (rbac.getScopeRoleMapping() != null && !rbac.getScopeRoleMapping().isEmpty()) {
            return rbac.getScopeRoleMapping().containsValue(Role.ADMIN.name());
        }
        return Role.ADMIN.name().equals(rbac.getRole());
    }

    /** Audit log for roles set from SSO groups at login; nothing is written when the roles do not change. Returns true if they change. */
    public static boolean auditRoleChange(String userEmail, int accountId, Map<String, String> newScopeRoleMapping, String loginEndpoint, HttpServletRequest request) {
        try {
            RBAC rbac = findRbac(userEmail, accountId);
            Map<String, String> before = rbac == null || rbac.getScopeRoleMapping() == null ? null : new TreeMap<>(rbac.getScopeRoleMapping());
            if (new TreeMap<>(newScopeRoleMapping).equals(before)) {
                return false;
            }
            Context.accountId.set(accountId);
            List<String> ipAddresses = AuditLogsUtil.getClientIpAddresses(request);
            BasicDBObject metadata = new BasicDBObject("auditAccessChange", "user=" + userEmail + " before="
                    + (rbac == null ? "none" : rbac.accessSummary()) + " requested=" + new TreeMap<>(newScopeRoleMapping));
            ApiAuditLogsDao.instance.insertOne(new ApiAuditLogs(Context.now(), loginEndpoint,
                    "SSO login set the user's product roles from the group mapping", userEmail, "SSO",
                    ipAddresses.isEmpty() ? null : ipAddresses.get(0), ipAddresses, Resource.USER_ACCESS, Operation.UPDATE, metadata));
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error writing SSO role change audit log: " + e.getMessage());
        }
        return true;
    }

    public static void clearUserCache(String userEmail, int accountId) {
        User user = UsersDao.instance.findOne(Filters.eq(User.LOGIN, userEmail));
        if (user != null) {
            RBACDao.instance.deleteUserEntryFromCache(new Pair<>(user.getId(), accountId));
            UsersCollectionsList.deleteCollectionIdsFromCache(user.getId(), accountId);
        }
        RbacCacheVersionDao.accessChanged(accountId);
    }
}
