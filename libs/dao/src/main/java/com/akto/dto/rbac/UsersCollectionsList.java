package com.akto.dto.rbac;

import com.akto.dao.RBACDao;
import com.akto.dao.context.Context;
import com.akto.util.Pair;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class UsersCollectionsList {
    private static final ConcurrentHashMap<Pair<Integer, Integer>, Pair<List<Integer>, Integer>> usersCollectionMap = new ConcurrentHashMap<>();
    private static final int EXPIRY_TIME = 30 * 60;

    public static void deleteCollectionIdsFromCache(int userId, int accountId) {
        Pair<Integer, Integer> key = new Pair<>(userId, accountId);
        usersCollectionMap.remove(key);
    }

    public static List<Integer> getCollectionsIdForUser(int userId, int accountId) {
        Pair<Integer, Integer> key = new Pair<>(userId, accountId);
        Pair<List<Integer>, Integer> collectionIdEntry = usersCollectionMap.get(key);
        List<Integer> collectionList;
        if(collectionIdEntry == null || (Context.now() - collectionIdEntry.getSecond() > EXPIRY_TIME)) {
            collectionList = RBACDao.instance.getUserCollectionsById(userId, accountId);
            usersCollectionMap.put(key, new Pair<>(collectionList, Context.now()));
        } else {
            collectionList = collectionIdEntry.getFirst();
        }

        return collectionList;
    }

}
