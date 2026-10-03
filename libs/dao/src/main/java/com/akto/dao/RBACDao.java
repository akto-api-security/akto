package com.akto.dao;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;

import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.CustomRole;
import com.akto.dto.RBAC;
import com.akto.dto.RBAC.Role;
import com.akto.dto.rbac.CollectionRule;
import com.akto.dto.rbac.RbacEnums.Feature;
import com.akto.dto.rbac.RbacEnums.ReadWriteAccess;
import com.akto.dto.traffic.CollectionTags;
import com.akto.util.Pair;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RBACDao extends CommonContextDao<RBAC> {
    public static final RBACDao instance = new RBACDao();

    private static final Logger logger = LoggerFactory.getLogger(RBACDao.class);
    private static final ConcurrentHashMap<Pair<Integer, Integer>, Pair<RBAC, Integer>> rbacEntryCache = new ConcurrentHashMap<>();
    private static final int EXPIRY_TIME = 2 * 60; // 2 minute
    public void createIndicesIfAbsent() {

        boolean exists = false;
        for (String col: clients[0].getDatabase(Context.accountId.get()+"").listCollectionNames()){
            if (getCollName().equalsIgnoreCase(col)){
                exists = true;
                break;
            }
        };

        if (!exists) {
            clients[0].getDatabase(Context.accountId.get()+"").createCollection(getCollName());
        }

        String[] fieldNames = {RBAC.USER_ID, RBAC.ACCOUNT_ID};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);
    }

    public void deleteUserEntryFromCache(Pair<Integer, Integer> key) {
        rbacEntryCache.remove(key);
    }

    /** Drops every cached RBAC entry and rule match of the account (see RbacCacheVersionDao). */
    public static void clearAccountCache(int accountId) {
        rbacEntryCache.keySet().removeIf(key -> key.getSecond() != null && key.getSecond() == accountId);
        String accountPrefix = accountId + "|";
        ruleCollectionsCache.keySet().removeIf(key -> key.startsWith(accountPrefix));
    }

    /*
     * Base roles whose threat access is decided by the role itself. Admin and the threat
     * roles always keep it, guest never gets it, and a custom role built on any of them
     * cannot override that either way.
     */
    private static final Set<Role> FIXED_THREAT_ACCESS_ROLES = new HashSet<>(java.util.Arrays.asList(
            Role.ADMIN, Role.GUEST, Role.THREAT_ENGINEER, Role.THREAT_VIEWER));

    /** The user's current custom role, or null for built-in roles. */
    public static CustomRole currentCustomRole(int userId, int accountId) {
        RBAC rbac = getCurrentRBACForUser(userId, accountId);
        if (rbac == null) {
            return null;
        }
        String currentRole = instance.fetchRole(rbac);
        /*
         * Custom role names cannot collide with built-in ones (RoleAction rejects
         * reserved keywords), so a built-in role can skip the lookup entirely.
         */
        if (currentRole == null || currentRole.isEmpty() || Role.fromName(currentRole) != null) {
            return null;
        }
        return CustomRoleDao.instance.findRoleByNameCached(currentRole);
    }

    /*
     * Access to a feature for the user's current role: the base role's access, then for threat
     * features the custom role's threat toggle, then any per-feature override on the custom role.
     * The toggle can only ever add access, never remove what the base role already gives.
     */
    public static ReadWriteAccess resolveFeatureAccess(int userId, int accountId, Feature feature, ReadWriteAccess baseRoleAccess) {
        try {
            return accessFor(currentCustomRole(userId, accountId), feature, baseRoleAccess);
        } catch (Exception e) {
            return baseRoleAccess;
        }
    }

    /** Access a custom role gives to a feature, given its base role's access (null role: the base role's access). */
    public static ReadWriteAccess accessFor(CustomRole customRole, Feature feature, ReadWriteAccess baseRoleAccess) {
        if (customRole == null || customRole.getBaseRole() == null) {
            return baseRoleAccess;
        }

        ReadWriteAccess override = customRole.overrideFor(feature);
        if (override == null && feature == Feature.THREAT_SETTINGS) {
            // threat settings follow threat protection unless changed on their own
            override = customRole.overrideFor(Feature.THREAT_PROTECTION);
        }
        if (override != null) {
            return override;
        }

        boolean threatFeature = feature == Feature.THREAT_PROTECTION || feature == Feature.THREAT_SETTINGS;
        // the base role decides on its own; the toggle is not consulted
        if (!threatFeature || FIXED_THREAT_ACCESS_ROLES.contains(Role.fromName(customRole.getBaseRole()))) {
            return baseRoleAccess;
        }

        return Boolean.TRUE.equals(customRole.getThreatProtectionEnabled())
                ? ReadWriteAccess.READ_WRITE : baseRoleAccess;
    }

    /** Access a built-in or custom role gives to a feature; unknown roles give none. */
    public static ReadWriteAccess accessFor(String roleName, Feature feature, boolean fresh) {
        Role role = Role.fromName(roleName);
        if (role != null) {
            return role.getReadWriteAccessForFeature(feature);
        }
        CustomRole customRole = fresh ? CustomRoleDao.instance.findRoleByName(roleName) : CustomRoleDao.instance.findRoleByNameCached(roleName);
        Role baseRole = customRole == null ? null : Role.fromName(customRole.getBaseRole());
        return baseRole == null ? ReadWriteAccess.NO_ACCESS : accessFor(customRole, feature, baseRole.getReadWriteAccessForFeature(feature));
    }

    public static Role getCurrentRoleForUser(int userId, int accountId){
        RBAC userRbac = getCurrentRBACForUser(userId, accountId);
        Role actualRole = Role.MEMBER;
        String currentRole = null;
        if (userRbac != null) {
            currentRole = instance.fetchRole(userRbac);
            if(currentRole == null){
                return Role.MEMBER;
            }
            Role resolvedRole = Role.fromName(currentRole);
            if (resolvedRole == null) {
                CustomRole customRole = CustomRoleDao.instance.findRoleByNameCached(currentRole);
                resolvedRole = customRole == null ? null : Role.fromName(customRole.getBaseRole());
            }
            if (resolvedRole == null) {
                // unknown or deleted role: no access instead of an exception (which callers treated as full access)
                logger.error(String.format("Unknown role %s for userId: %d accountId: %d", currentRole, userId, accountId));
                resolvedRole = Role.NO_ACCESS;
            }
            actualRole = resolvedRole;
        }
        return actualRole;
    }

    /** True when the user's role in the current product is a custom role that no longer exists (e.g. deleted while still in an SSO mapping). */
    public static boolean hasMissingRole(int userId, int accountId) {
        RBAC rbac = getCurrentRBACForUser(userId, accountId);
        if (rbac == null || rbac.hasAccessExpired()) {
            return false;
        }
        String currentRole = instance.fetchRole(rbac);
        return currentRole != null && !currentRole.isEmpty() && Role.fromName(currentRole) == null
                && CustomRoleDao.instance.findRoleByNameCached(currentRole) == null;
    }

    public String fetchRole (RBAC userRbac) {
        // time-bound access has ended: no access in any product until someone extends it
        if (userRbac.hasAccessExpired()) {
            return Role.NO_ACCESS.getName();
        }

        String currentRole = null;
        if (userRbac.getScopeRoleMapping() != null && !userRbac.getScopeRoleMapping().isEmpty()) {
            try {
                CONTEXT_SOURCE contextSourceObj = Context.contextSource.get();
                if (contextSourceObj == null) {
                    contextSourceObj = CONTEXT_SOURCE.API;
                }
                String currentScope = contextSourceObj.name();
                String scopeRole = userRbac.getScopeRoleMapping().get(currentScope);
                if (scopeRole != null && !scopeRole.isEmpty()) {
                    currentRole = scopeRole;
                } else {
                    // as we remove complete scope role mapping, we need to return NO_ACCESS for all users for which scope role mapping is not present
                    return Role.NO_ACCESS.getName();
                }
            } catch (Exception e) {
            }
        } else {
            currentRole = userRbac.getRole();
        }
        return currentRole;
    }

    
    public List<Integer> getUserCollectionsById(int userId, int accountId) {
        return getUserCollectionsById(userId, accountId, true);
    }

    /*
     * includeRules=false gives only the explicit grants (role and user collection ids), for screens that
     * edit and save those grants back; rule matches must never be saved as fixed per-user grants.
     */
    private List<Integer> getUserCollectionsById(int userId, int accountId, boolean includeRules) {
        RBAC rbac = getCurrentRBACForUser(userId, accountId);

        if (rbac == null) {
            logger.debug(String.format("Rbac not found userId: %d accountId: %d", userId, accountId));
            return new ArrayList<>();
        }

        // the role in the current product decides; the older single role field is used by fetchRole only when there is no per-product mapping
        String currentRole = fetchRole(rbac);
        if (currentRole != null && Role.fromName(currentRole) == Role.ADMIN) {
            logger.debug(String.format("Rbac is admin userId: %d accountId: %d", userId, accountId));
            return null;
        }

        /*
         * For API collectionIds, we need to merge
         * collections from the custom role and the user role.
         */

        if(currentRole!= null && currentRole.isEmpty()){
            currentRole = rbac.getRole();
        }

        CustomRole customRole = CustomRoleDao.instance.findRoleByNameCached(currentRole);
        Set<Integer> apiCollectionsId = new HashSet<>();
        boolean hasRules = includeRules && customRole != null && customRole.getCollectionRules() != null && !customRole.getCollectionRules().isEmpty();
        if (customRole != null) {
            if (customRole.getApiCollectionsId() != null) {
                apiCollectionsId.addAll(customRole.getApiCollectionsId());
            }
            if (hasRules) {
                apiCollectionsId.addAll(ruleCollectionIds(accountId, customRole.getCollectionRules()));
            }
        }

        if (rbac.getApiCollectionsId() == null) {
            logger.debug(String.format("Rbac collections not found userId: %d accountId: %d", userId, accountId));
        } else {
            logger.debug(String.format("Rbac found userId: %d accountId: %d", userId, accountId));
            apiCollectionsId.addAll(rbac.getApiCollectionsId());
        }

        // an empty list means all collections, so a role limited by rules that match nothing yet must still see nothing,
        // and so must a custom role that no longer exists (e.g. deleted while still named in an SSO group mapping)
        boolean unknownRole = customRole == null && currentRole != null && Role.fromName(currentRole) == null;
        if ((hasRules || unknownRole) && apiCollectionsId.isEmpty()) {
            apiCollectionsId.add(NO_COLLECTION_ID);
        }

        return new ArrayList<>(apiCollectionsId);
    }

    /** Collection id that matches no collection; keeps a limited user limited when nothing matches. */
    public static final int NO_COLLECTION_ID = Integer.MIN_VALUE;

    private static final ConcurrentHashMap<String, Pair<Set<Integer>, Integer>> ruleCollectionsCache = new ConcurrentHashMap<>();
    private static final int RULE_CACHE_EXPIRY_TIME = 2 * 60;

    /** Collections matching a role's host / tag rules, cached per account and rule set. */
    public static Set<Integer> ruleCollectionIds(int accountId, List<CollectionRule> rules) {
        String key = accountId + "|" + rules;
        Pair<Set<Integer>, Integer> cached = ruleCollectionsCache.get(key);
        if (cached != null && Context.now() - cached.getSecond() <= RULE_CACHE_EXPIRY_TIME) {
            return cached.getFirst();
        }
        List<Bson> filters = new ArrayList<>();
        for (CollectionRule rule : rules) {
            if (rule == null || rule.validate() != null) {
                continue;
            }
            if (rule.getHostRegex() != null && !rule.getHostRegex().trim().isEmpty()) {
                // collections without a host (created by hand or from a file) are matched by their name instead
                Bson noHost = Filters.or(Filters.exists(ApiCollection.HOST_NAME, false), Filters.eq(ApiCollection.HOST_NAME, null), Filters.eq(ApiCollection.HOST_NAME, ""));
                filters.add(Filters.or(
                        Filters.regex(ApiCollection.HOST_NAME, rule.getHostRegex()),
                        Filters.and(noHost, Filters.regex(ApiCollection.NAME, rule.getHostRegex()))));
            } else {
                filters.add(Filters.elemMatch(ApiCollection.TAGS_STRING, Filters.and(
                        Filters.eq(CollectionTags.KEY_NAME, rule.getTagKey()),
                        Filters.eq(CollectionTags.VALUE, rule.getTagValue()))));
            }
        }
        Set<Integer> ids = new HashSet<>();
        if (!filters.isEmpty()) {
            try {
                // raw query: the RBAC-filtered DAO methods resolve the user's collections through this method
                for (ApiCollection collection : ApiCollectionsDao.instance.getMCollection()
                        .find(Filters.or(filters)).projection(Projections.include(ApiCollection.ID))) {
                    ids.add(collection.getId());
                }
            } catch (Exception e) {
                // e.g. a pattern Mongo rejects: match nothing, so the user stays limited instead of seeing everything
                logger.error("Error resolving collection rules " + rules + ": " + e.getMessage());
                ids.clear();
            }
        }
        if (ruleCollectionsCache.size() > 1000) {
            ruleCollectionsCache.clear(); // keeps the cache bounded when rules change often
        }
        ruleCollectionsCache.put(key, new Pair<>(ids, Context.now()));
        return ids;
    }

    public HashMap<Integer, List<Integer>> getAllUsersCollections(int accountId) {
        HashMap<Integer, List<Integer>> collectionList = new HashMap<>();

        List<Integer> userList = UsersDao.instance.getAllUsersIdsForTheAccount(accountId);

        for (int userId : userList) {
            collectionList.put(userId, getUserCollectionsById(userId, accountId, false));
        }

        return collectionList;
    }

    public static void updateApiCollectionAccess(int userId, int accountId, Set<Integer> apiCollectionList) {
        RBACDao.instance.updateOne(Filters.and(eq(RBAC.USER_ID, userId), eq(RBAC.ACCOUNT_ID, accountId)),
                set(RBAC.API_COLLECTIONS_ID, apiCollectionList));
    }

    
    public static RBAC getCurrentRBACForUser(int userId, int accountId) {
        Pair<Integer, Integer> key = new Pair<>(userId, accountId);
        Pair<RBAC, Integer> cachedEntry = rbacEntryCache.get(key);
        RBAC rbacEntry;

        // Check if cache exists and is still valid
        if (cachedEntry != null && (Context.now() - cachedEntry.getSecond() <= EXPIRY_TIME)) {
            return cachedEntry.getFirst();
        }

        // Fetch from database if cache miss or expired
        Bson filterRbac = Filters.and(
                Filters.eq(RBAC.USER_ID, userId),
                Filters.eq(RBAC.ACCOUNT_ID, accountId));

        rbacEntry = RBACDao.instance.findOne(filterRbac);

        if(rbacEntry == null){
            // old cases where rbac entry is not present in the database
            rbacEntry = new RBAC();
            rbacEntry.setUserId(userId);
            rbacEntry.setAccountId(accountId);
            rbacEntry.setRole(Role.MEMBER.name());
            rbacEntry.setScopeRoleMapping(new HashMap<>());
        }

        // Cache the result (even if null)
        if (rbacEntry != null || cachedEntry == null) {
            rbacEntryCache.put(key, new Pair<>(rbacEntry, Context.now()));
        }

        return rbacEntry;
    }


    @Override
    public String getCollName() {
        return "rbac";
    }

    @Override
    public Class<RBAC> getClassT() {
        return RBAC.class;
    }
}
