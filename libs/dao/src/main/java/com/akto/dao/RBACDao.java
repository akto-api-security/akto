package com.akto.dao;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;

import com.akto.dao.context.Context;
import com.akto.dto.CustomRole;
import com.akto.dto.RBAC;
import com.akto.dto.RBAC.Role;
import com.akto.util.Pair;
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

    //Caching for RBACDAO
    private static final ConcurrentHashMap<Pair<Integer, Integer>, Pair<Role, Integer>> userRolesMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Pair<Integer, Integer>, Pair<List<String>, Integer>> allowedFeaturesMapForUser = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Pair<Integer, Integer>, Pair<RBAC, Integer>> rbacEntryCache = new ConcurrentHashMap<>();
    private static final int EXPIRY_TIME = 15 * 60; // 15 minute
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
        userRolesMap.remove(key);
        allowedFeaturesMapForUser.remove(key);
        rbacEntryCache.remove(key);
    }

    /**
     * Refresh user role cache by deleting and immediately re-fetching from DB.
     * Use this when scopeRoleMapping is updated to ensure fresh data is cached.
     *
     * This method:
     * 1. Clears userRolesMap, allowedFeaturesMapForUser, and rbacEntryCache
     * 2. Immediately re-fetches role from DB via getCurrentRoleForUser()
     * 3. getCurrentRoleForUser internally calls getCurrentRBACForUser(),
     *    so both caches (userRolesMap + rbacEntryCache) get repopulated
     */
    public static void refreshUserRoleCache(int userId, int accountId) {
        Pair<Integer, Integer> key = new Pair<>(userId, accountId);
        // Delete stale cache entries
        RBACDao.instance.deleteUserEntryFromCache(key);
        // Re-fetch and populate ALL caches (getCurrentRoleForUser internally calls getCurrentRBACForUser)
        getCurrentRoleForUser(userId, accountId);
    }

    /*
     * This method should be used everywhere to access user role.
     * Because we update the userRole from the custom roles here.
     * There is no context of custom roles anywhere else.
     * Now also supports scope-based roles via scopeRoleMapping.
     *
     * IMPORTANT: Scope-based role caching strategy:
     * - Users WITH scopeRoleMapping: DO NOT cache in userRolesMap (keyed only by userId+accountId, no scope info)
     *   Instead, always fetch from rbacEntryCache and extract scope-specific role based on Context.contextSource
     * - Users WITHOUT scopeRoleMapping: Cache in userRolesMap for performance (backward compatibility)
     */
    public static Role getCurrentRoleForUser(int userId, int accountId){
        Pair<Integer, Integer> key = new Pair<>(userId, accountId);

        // Fetch RBAC object using cache (handles both old role field and new scopeRoleMapping)
        RBAC userRbac = getCurrentRBACForUser(userId, accountId);

        // Check if user has scope-based role mapping
        boolean hasScopeRoleMapping = userRbac != null && userRbac.getScopeRoleMapping() != null && !userRbac.getScopeRoleMapping().isEmpty();

        // If user has scope-based roles don't use userRolesMap cache
        // because userRolesMap is scope-agnostic and would cache wrong role for different scopes
        if (!hasScopeRoleMapping) {
            // For users with only primary role, try to use cached entry
            Pair<Role, Integer> userRoleEntry = userRolesMap.get(key);
            if (userRoleEntry != null && (Context.now() - userRoleEntry.getSecond() <= EXPIRY_TIME)) {
                return userRoleEntry.getFirst();
            }
        }

        // Fetch/calculate role
        String currentRole;
        Role actualRole = Role.MEMBER;

        if (userRbac != null) {
            currentRole = userRbac.getRole();

            // Check if user has scope-based role mapping
            if (hasScopeRoleMapping) {
                try {
                    Object contextSourceObj = Context.contextSource.get();
                    if (contextSourceObj != null) {
                        String currentScope = contextSourceObj.toString();
                        String scopeRole = userRbac.getScopeRoleMapping().get(currentScope);
                        if (scopeRole != null && !scopeRole.isEmpty()) {
                            currentRole = scopeRole;
                        }
                    }
                } catch (Exception e) {
                    // On any error, keep using the primary role
                }
            }
        } else {
            currentRole = Role.MEMBER.name();
        }

        if(currentRole == null){
            return null;
        }

        CustomRole customRole = CustomRoleDao.instance.findRoleByName(currentRole);
        if (customRole != null) {
            try {
                actualRole = Role.valueOf(customRole.getBaseRole());
            } catch (IllegalArgumentException e) {
                actualRole = Role.MEMBER; // Default to MEMBER if base role is invalid
            }
        } else {
            try {
                actualRole = Role.valueOf(currentRole);
            } catch (IllegalArgumentException e) {
                actualRole = Role.MEMBER; // Default to MEMBER if role is invalid
            }
        }

        // Only cache in userRolesMap if user does NOT have scope-based roles
        // (userRolesMap is scope-agnostic and would cause wrong role to be used when scope changes)
        if (!hasScopeRoleMapping) {
            userRolesMap.put(key, new Pair<>(actualRole, Context.now()));
        }

        return actualRole;
    }

    public static boolean hasAccessToFeature(int userId, int accountId, String featureLabel) {
        Pair<Integer, Integer> key = new Pair<>(userId, accountId);

        // Get role based on current scope context
        RBAC.Role userRoleRecord = getRoleForFeatureAccessInScope(userId, accountId);

        if (userRoleRecord == null) {
            userRoleRecord = RBAC.Role.MEMBER;
        }
        if (userRoleRecord.equals(RBAC.Role.ADMIN)) {
            return true; // Admin has access to all features
        }
        if(featureLabel == null || featureLabel.isEmpty() || !RBAC.SPECIAL_FEATURES_FOR_RBAC.contains(featureLabel)) {
            return true;
        }
        Pair<List<String>, Integer> allowedFeaturesEntry = allowedFeaturesMapForUser.get(key);
        if (allowedFeaturesEntry == null || allowedFeaturesEntry.getFirst() == null || (Context.now() - allowedFeaturesEntry.getSecond() > EXPIRY_TIME)) {
            List<String> allowedFeatures = instance.getAllowedFeaturesForRole(userId, accountId);
            allowedFeaturesMapForUser.put(key, new Pair<>(allowedFeatures, Context.now()));
            if(allowedFeatures != null && !allowedFeatures.isEmpty() && allowedFeatures.contains(featureLabel)) {
                return true;
            }
            return false;
        }
        if(allowedFeaturesEntry.getFirst() == null || allowedFeaturesEntry.getFirst().isEmpty()) {
            return false;
        }
        return allowedFeaturesEntry.getFirst().contains(featureLabel);
    }

    /**
     * Gets the user's role for feature access based on current scope context.
     * If user has scopeRoleMapping, returns the role for the current scope.
     * Otherwise, falls back to the old single role field.
     * This ensures scope-specific role access (new system),
     * while maintaining backward compatibility for users with only role field (old system).
     */
    private static RBAC.Role getRoleForFeatureAccessInScope(int userId, int accountId) {
        try {
            // Get current scope from context (set by RoleAccessInterceptor)
            Object contextSourceObj = Context.contextSource.get();
            if (contextSourceObj == null) {
                // No scope context, use old system
                return RBACDao.getCurrentRoleForUser(userId, accountId);
            }

            String currentScope = contextSourceObj.toString();

            // Fetch user's RBAC record using cache-aware method to check for scopeRoleMapping
            RBAC userRbac = RBACDao.getCurrentRBACForUser(userId, accountId);

            if (userRbac != null && userRbac.getScopeRoleMapping() != null && !userRbac.getScopeRoleMapping().isEmpty()) {
                // User has scope-based role mapping (new system)
                // Get the role for the current scope
                String scopeRole = userRbac.getScopeRoleMapping().get(currentScope);
                if (scopeRole != null && !scopeRole.isEmpty()) {
                    try {
                        return RBAC.Role.valueOf(scopeRole.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        // Invalid role string, try custom role lookup
                        CustomRole customRole = CustomRoleDao.instance.findRoleByName(scopeRole);
                        if (customRole != null && customRole.getBaseRole() != null) {
                            return RBAC.Role.valueOf(customRole.getBaseRole());
                        }
                        logger.warn("Invalid role in scopeRoleMapping for userId: " + userId + " scope: " + currentScope + " role: " + scopeRole);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error getting role for feature access check userId: " + userId + " accountId: " + accountId, e);
        }

        // Fallback to old single role field (backward compatibility)
        return RBACDao.getCurrentRoleForUser(userId, accountId);
    }

    public List<Integer> getUserCollectionsById(int userId, int accountId) {
        RBAC rbac = RBACDao.instance.findOne(
                Filters.and(
                        eq(RBAC.USER_ID, userId),
                        eq(RBAC.ACCOUNT_ID, accountId)),
                Projections.include(RBAC.API_COLLECTIONS_ID, RBAC.ROLE));

        if (rbac == null) {
            logger.debug(String.format("Rbac not found userId: %d accountId: %d", userId, accountId));
            return new ArrayList<>();
        }

        if (RBAC.Role.ADMIN.name().equals(rbac.getRole())) {
            logger.debug(String.format("Rbac is admin userId: %d accountId: %d", userId, accountId));
            return null;
        }

        /*
         * For API collectionIds, we need to merge
         * collections from the custom role and the user role.
         */

        String role = rbac.getRole();
        CustomRole customRole = CustomRoleDao.instance.findRoleByName(role);
        Set<Integer> apiCollectionsId = new HashSet<>();
        if (customRole != null) {
            apiCollectionsId.addAll(customRole.getApiCollectionsId());
        }

        if (rbac.getApiCollectionsId() == null) {
            logger.debug(String.format("Rbac collections not found userId: %d accountId: %d", userId, accountId));
        } else {
            logger.debug(String.format("Rbac found userId: %d accountId: %d", userId, accountId));
            apiCollectionsId.addAll(rbac.getApiCollectionsId());
        }

        return new ArrayList<>(apiCollectionsId);
    }

    public List<String> getAllowedFeaturesForRole(int userId, int accountId) {
        RBAC rbac = RBACDao.instance.findOne(
                Filters.and(
                        eq(RBAC.USER_ID, userId),
                        eq(RBAC.ACCOUNT_ID, accountId)),
                Projections.include(RBAC.ALLOWED_FEATURES_FOR_USER, RBAC.ROLE));

        if (RBAC.Role.ADMIN.name().equals(rbac.getRole())) {
            return RBAC.SPECIAL_FEATURES_FOR_RBAC;
        }

        String role = RBAC.Role.MEMBER.name();
        if(rbac != null){
            role = rbac.getRole();
        }
        CustomRole customRole = CustomRoleDao.instance.findRoleByName(role);
        Set<String> allowedFeatures = new HashSet<>();
        if (customRole != null && customRole.getAllowedFeaturesForUser() != null && !customRole.getAllowedFeaturesForUser().isEmpty()) {
            allowedFeatures.addAll(customRole.getAllowedFeaturesForUser());
        }
        if(rbac != null && rbac.getAllowedFeaturesForUser() != null) {
            allowedFeatures.addAll(rbac.getAllowedFeaturesForUser());
        }
        return new ArrayList<>(allowedFeatures);
    }

    public HashMap<Integer, List<Integer>> getAllUsersCollections(int accountId) {
        HashMap<Integer, List<Integer>> collectionList = new HashMap<>();

        List<Integer> userList = UsersDao.instance.getAllUsersIdsForTheAccount(accountId);

        for (int userId : userList) {
            collectionList.put(userId, getUserCollectionsById(userId, accountId));
        }

        return collectionList;
    }

    public static void updateApiCollectionAccess(int userId, int accountId, Set<Integer> apiCollectionList) {
        RBACDao.instance.updateOne(Filters.and(eq(RBAC.USER_ID, userId), eq(RBAC.ACCOUNT_ID, accountId)),
                set(RBAC.API_COLLECTIONS_ID, apiCollectionList));
    }

    /**
     * Get the full RBAC entry for a user with caching support.
     * This method caches the entire RBAC object to support scope-role mapping (n:n mapping).
     * Uses 15-minute cache expiry time. Falls back to backward-compatible single role if scopeRoleMapping is not set.
     *
     * @param userId the user ID
     * @param accountId the account ID
     * @return the cached or freshly fetched RBAC entry, or null if not found
     */
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
