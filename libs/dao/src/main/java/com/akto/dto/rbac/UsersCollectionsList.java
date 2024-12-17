package com.akto.dto.rbac;

import com.akto.dao.RBACDao;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dto.billing.Organization;
import com.akto.util.Pair;
import com.mongodb.client.model.Filters;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class UsersCollectionsList {
    private static final ConcurrentHashMap<Pair<Integer, Integer>, Pair<List<Integer>, Integer>> usersCollectionMap = new ConcurrentHashMap<>();
    private static final int EXPIRY_TIME = 15 * 60;

    public static void deleteCollectionIdsFromCache(int userId, int accountId) {
        Pair<Integer, Integer> key = new Pair<>(userId, accountId);
        usersCollectionMap.remove(key);
    }

    public static final String RBAC_FEATURE = "RBAC_FEATURE";

    /*
     * Cases:
     * 1. For admin we save the list as null and
     * do not add any collection filter in db queries if the list comes to be null.
     * 2. For roles other than admin, we save the list as empty if no collections
     * are there and add the empty filter, so no data comes out.
     * 3. If no rbac is found, we treat it as least privilege account (empty array).
     * 4. If rbac feature not available, then, full access.
     */
    public static List<Integer> getCollectionsIdForUser(int userId, int accountId) {
        Pair<Integer, Integer> key = new Pair<>(userId, accountId);
        Pair<List<Integer>, Integer> collectionIdEntry = usersCollectionMap.get(key);
        List<Integer> collectionList = new ArrayList<>();
        
        if(collectionIdEntry == null || (Context.now() - collectionIdEntry.getSecond() > EXPIRY_TIME)) {
            Organization organization = OrganizationsDao.instance.findOne(
                Filters.in(Organization.ACCOUNTS, accountId));

            if (organization != null &&
                    organization.getFeatureWiseAllowed() != null &&
                    !organization.getFeatureWiseAllowed().isEmpty() &&
                    organization.getFeatureWiseAllowed().containsKey(RBAC_FEATURE) &&
                    organization.getFeatureWiseAllowed().get(RBAC_FEATURE).getIsGranted()) {
                collectionList = RBACDao.instance.getUserCollectionsById(userId, accountId);
            } else {
                collectionList = null;
            }

            usersCollectionMap.put(key, new Pair<>(collectionList, Context.now()));
        } else {
            collectionList = collectionIdEntry.getFirst();
        }

        return collectionList;
    }

}
