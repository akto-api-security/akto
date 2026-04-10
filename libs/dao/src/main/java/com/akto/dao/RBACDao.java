package com.akto.dao;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;

import com.akto.dao.context.Context;
import com.akto.dto.CustomRole;
import com.akto.dto.RBAC;
import com.akto.dto.RBAC.Role;
import com.akto.util.Pair;
import com.mongodb.client.model.Filters;
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
        rbacEntryCache.remove(key);
    }

    public static Role getCurrentRoleForUser(int userId, int accountId){
        RBAC userRbac = getCurrentRBACForUser(userId, accountId);
        Role actualRole = Role.MEMBER;
        String currentRole = null;
        if (userRbac != null) {
            if (userRbac.getScopeRoleMapping() != null && !userRbac.getScopeRoleMapping().isEmpty()) {
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
                }
            }
            else {
                currentRole = userRbac.getRole();
            }

            if(currentRole == null){
                return Role.MEMBER;
            }
            CustomRole customRole = CustomRoleDao.instance.findRoleByName(currentRole);
            if (customRole != null) {
                actualRole = Role.valueOf(customRole.getBaseRole());
            } else {
                actualRole = Role.valueOf(currentRole);
            }
        }
        return actualRole;
    }

    
    public List<Integer> getUserCollectionsById(int userId, int accountId) {
        RBAC rbac = getCurrentRBACForUser(userId, accountId);

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

        String currentRole = null;
        if (rbac.getScopeRoleMapping() != null && !rbac.getScopeRoleMapping().isEmpty()) {
            try {
                Object contextSourceObj = Context.contextSource.get();
                if (contextSourceObj != null) {
                    String currentScope = contextSourceObj.toString();
                    String scopeRole = rbac.getScopeRoleMapping().get(currentScope);
                    if (scopeRole != null && !scopeRole.isEmpty()) {
                        currentRole = scopeRole;
                    }
                }
            } catch (Exception e) {
            }
        }
        else {
            currentRole = rbac.getRole();
        }

        CustomRole customRole = CustomRoleDao.instance.findRoleByName(currentRole);
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
