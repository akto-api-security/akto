package com.akto.trafficFilter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dto.ApiCollection;
import com.mongodb.client.model.Filters;

public class HostFilter {

    private static final String K8S_DEFAULT_HOST = "kubernetes.default.svc";

    private static Map<Integer, Set<Integer>> collectionSet = new HashMap<>();

    public static Set<Integer> getCollectionSet(int accountId) {

        Set<Integer> ignoreCollectionSet = new HashSet<>();

        if (collectionSet.containsKey(accountId)) {
            return collectionSet.get(accountId);
        }

        ApiCollection collection = ApiCollectionsDao.instance.findOne(
                Filters.eq(ApiCollection.HOST_NAME, K8S_DEFAULT_HOST));

        if (collection != null) {
            ignoreCollectionSet.add(collection.getId());
        }
        ignoreCollectionSet.add(0);
        collectionSet.put(accountId, ignoreCollectionSet);
        return collectionSet.get(accountId);
    }
}