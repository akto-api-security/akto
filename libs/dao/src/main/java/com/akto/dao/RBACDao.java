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
    }

    /*
     * This method should be used everywhere to access user role.
     * Because we update the userRole from the custom roles here.
     * There is no context of custom roles anywhere else.
     */
    public static Role getCurrentRoleForUser(int userId, int accountId){
        Pair<Integer, Integer> key = new Pair<>(userId, accountId);
        Pair<Role, Integer> userRoleEntry = userRolesMap.get(key);
        String currentRole;
        Role actualRole = Role.MEMBER;
        if (userRoleEntry == null || (Context.now() - userRoleEntry.getSecond() > EXPIRY_TIME)) {
            Bson filterRbac = Filters.and(
                    Filters.eq(RBAC.USER_ID, userId),
                    Filters.eq(RBAC.ACCOUNT_ID, accountId));

            RBAC userRbac = RBACDao.instance.findOne(filterRbac);
            if (userRbac != null) {
                currentRole = userRbac.getRole();
            } else {
                currentRole = Role.MEMBER.name();
            }
            
            CustomRole customRole = CustomRoleDao.instance.findRoleByName(currentRole);
            if (customRole != null) {
                actualRole = Role.valueOf(customRole.getBaseRole());
            } else {
                actualRole = Role.valueOf(currentRole);
            }

            userRolesMap.put(key, new Pair<>(actualRole, Context.now()));
        } else {
            actualRole = userRoleEntry.getFirst();
        }
        return actualRole;
    }

    public static boolean hasAccessToFeature(int userId, int accountId, String featureLabel) {
        Pair<Integer, Integer> key = new Pair<>(userId, accountId);
        RBAC.Role userRoleRecord = RBACDao.getCurrentRoleForUser(userId, accountId);
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

    @Override
    public String getCollName() {
        return "rbac";
    }

    @Override
    public Class<RBAC> getClassT() {
        return RBAC.class;
    }
}
