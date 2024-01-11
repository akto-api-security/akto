package com.akto.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.conversions.Bson;

import com.akto.billing.UsageMetricCalculator;
import com.akto.billing.UsageMetricHandler;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.MCollection;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.ApiCollectionUsers.CollectionType;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.usage.MetricTypes;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public class EndpointUtil {

    public static void calcAndDeleteEndpoints() {
        // check if overage happened and delete over the limit data
        int accountId = Context.accountId.get();
        FeatureAccess featureAccess = UsageMetricHandler.calcAndFetchFeatureAccess(MetricTypes.ACTIVE_ENDPOINTS, accountId);

        if (featureAccess.checkOverageAfterGrace()) {

            int usageLimit = featureAccess.getUsageLimit();
            int measureEpoch = featureAccess.getMeasureEpoch();

            /*
             * delete all data related to endpoints after the
             * specified limit for the current measureEpoch.
             */
            deleteEndpoints(usageLimit, measureEpoch);
        }
    }

    public static void calcAndDeleteEndpointsFromList(Set<ApiInfoKey> apiSet) {

        int accountId = Context.accountId.get();
        FeatureAccess featureAccess = UsageMetricHandler.calcAndFetchFeatureAccess(MetricTypes.ACTIVE_ENDPOINTS, accountId);

        if (featureAccess.checkOverageAfterGrace()) {

            int usageLimit = featureAccess.getUsageLimit();
            int usage = featureAccess.getUsage();

            List<ApiInfoKey> apiList = new ArrayList<>(apiSet);

            int overUse = Math.abs(usage - usageLimit);
            int size = apiList.size();
            int subListSize = Math.min(size, overUse);
            apiList = apiList.subList(0, subListSize);
            deleteForApis(apiList);
        }
    }

    final static int delta = 60 * 20; // 20 minutes

    private static void deleteEndpoints(int skip, int timestamp) {

        Bson filters = Filters.and(
                Filters.gt(SingleTypeInfo._TIMESTAMP, timestamp),
                UsageMetricCalculator.excludeDemos(SingleTypeInfo._API_COLLECTION_ID));

        boolean hasMore = false;

        do {
            hasMore = false;
            /*
             * we query up to 100 endpoints at a time
             * Using the delta epoch to bring the latest traffic only.
             */
            int now = Context.now();
            int deltaEpoch = now - delta;
            List<ApiInfoKey> apis = SingleTypeInfoDao.instance.getEndpointsAfterOverage(filters, skip, deltaEpoch);
            hasMore = deleteForApis(apis);

        } while (hasMore);
    }

    private static boolean deleteForApis(List<ApiInfoKey> apis) {
        // This contains all collections related to endpoints
        Map<CollectionType, MCollection<?>[]> collectionsMap = ApiCollectionUsers.COLLECTIONS_WITH_API_COLLECTION_ID;

        for (Map.Entry<CollectionType, MCollection<?>[]> collections : collectionsMap.entrySet()) {
            deleteInManyCollections(collections.getValue(), createFilters(collections.getKey(), apis));
        }

        // we need to update the api collection with the new list of urls
        Map<Integer, List<String>> urls = new HashMap<>();

        for (ApiInfoKey api : apis) {
            List<String> urlList = urls.get(api.getApiCollectionId());
            if (urlList == null) {
                urlList = new ArrayList<>();
            }
            urlList.add(api.getUrl() + " " + api.getMethod().toString());
            urls.put(api.getApiCollectionId(), urlList);
        }

        for (Map.Entry<Integer, List<String>> entry : urls.entrySet()) {
            ApiCollectionsDao.instance.updateOne(Filters.eq(Constants.ID, entry.getKey()),
                    Updates.pullAll(ApiCollection._URLS, entry.getValue()));
        }

        if (apis != null && !apis.isEmpty()) {
            return true;
        }

        return false;
    }

    private static void deleteInManyCollections(MCollection<?>[] collections, Bson filter) {
        for (MCollection<?> collection : collections) {
            collection.deleteAll(filter);
        }
    }

    private static String getFilterPrefix(CollectionType type) {
        String prefix = "";
        switch (type) {
            case Id_ApiCollectionId:
                prefix = "_id.";
                break;

            case Id_ApiInfoKey_ApiCollectionId:
                prefix = "_id.apiInfoKey.";
                break;

            case ApiCollectionId:
            default:
                break;
        }
        return prefix;
    }

    private static Bson createApiFilters(CollectionType type, ApiInfoKey api) {

        String prefix = getFilterPrefix(type);

        return Filters.and(
                Filters.eq(prefix + SingleTypeInfo._URL, api.getUrl()),
                Filters.eq(prefix + SingleTypeInfo._METHOD, api.getMethod().toString()),
                Filters.in(SingleTypeInfo._COLLECTION_IDS, api.getApiCollectionId()));

    }

    private static Bson createFilters(CollectionType type, List<ApiInfoKey> apiList) {
        Set<ApiInfoKey> apiSet = new HashSet<>(apiList);
        List<Bson> apiFilters = new ArrayList<>();
        if (apiSet != null && !apiSet.isEmpty()) {
            for (ApiInfoKey api : apiSet) {
                apiFilters.add(createApiFilters(type, api));
            }
            return Filters.or(apiFilters);
        }

        return Filters.nor(new BasicDBObject());
    }

}
