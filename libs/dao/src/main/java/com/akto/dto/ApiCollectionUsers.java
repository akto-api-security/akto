package com.akto.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.bson.conversions.Bson;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.FilterSampleDataDao;
import com.akto.dao.MCollection;
import com.akto.dao.SampleDataDao;
import com.akto.dao.SensitiveParamInfoDao;
import com.akto.dao.SensitiveSampleDataDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.TrafficInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.demo.VulnerableRequestForTemplateDao;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public class ApiCollectionUsers {

    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public enum Type {
        ApiCollectionIdInID, ApiCollectionIdNotInID
    }

    public static Map<Type, MCollection<?>[]> getCollectionsWithApiCollectionId() {
        Map<Type, MCollection<?>[]> ret = new HashMap<>();
        ret.put(Type.ApiCollectionIdNotInID, new MCollection[] {
                SingleTypeInfoDao.instance,
                SensitiveParamInfoDao.instance
        });
        ret.put(Type.ApiCollectionIdInID, new MCollection[] {
                ApiInfoDao.instance,
                TrafficInfoDao.instance,
                SampleDataDao.instance,
                SensitiveSampleDataDao.instance,
                VulnerableRequestForTemplateDao.instance,
                FilterSampleDataDao.instance
        });
        return ret;
    }

    public static Bson getApiFilterWithId(ApiInfoKey api) {
        final String ID = Constants.ID + Constants.DOT;
        return Filters.and(
                Filters.eq(ID + SingleTypeInfo._API_COLLECTION_ID, api.getApiCollectionId()),
                Filters.eq(ID + SingleTypeInfo._METHOD, api.getMethod().toString()),
                Filters.eq(ID + SingleTypeInfo._URL, api.getUrl()));
    }

    public static Bson getApiFilterWithoutId(ApiInfoKey api) {
        return Filters.and(
                Filters.eq(SingleTypeInfo._API_COLLECTION_ID, api.getApiCollectionId()),
                Filters.eq(SingleTypeInfo._METHOD, api.getMethod().toString()),
                Filters.eq(SingleTypeInfo._URL, api.getUrl()));
    }

    public static void addApisInCollectionsForCollectionId(Set<ApiInfoKey> apis, int apiCollectionId) {
        Bson update = Updates.addToSet(SingleTypeInfo._COLLECTION_IDS, apiCollectionId);
        updateCollectionsForCollectionId(apis, apiCollectionId, update);
    }

    public static void removeApisFromCollectionsForCollectionId(Set<ApiInfoKey> apis, int apiCollectionId) {
        Bson update = Updates.pull(SingleTypeInfo._COLLECTION_IDS, apiCollectionId);
        updateCollectionsForCollectionId(apis, apiCollectionId, update);
    }

    private static void updateCollectionsForCollectionId(Set<ApiInfoKey> apis, int apiCollectionId, Bson update) {
        int accountId = Context.accountId.get();
        executorService.schedule(new Runnable() {
            public void run() {
                Context.accountId.set(accountId);

                List<Bson> filtersWithId = new ArrayList<>();
                List<Bson> filtersWithoutId = new ArrayList<>();

                for (ApiInfoKey api : apis) {
                    filtersWithId.add(getApiFilterWithId(api));
                    filtersWithoutId.add(getApiFilterWithoutId(api));
                }

                Bson filterWithId = Filters.or(filtersWithId);
                Bson filterWithoutId = Filters.or(filtersWithoutId);

                Map<Type, MCollection<?>[]> collectionsMap = getCollectionsWithApiCollectionId();

                updateCollections(collectionsMap.get(Type.ApiCollectionIdInID), filterWithId, update);
                updateCollections(collectionsMap.get(Type.ApiCollectionIdNotInID), filterWithoutId, update);

            }
        }, 0, TimeUnit.SECONDS);
    }

    private static void updateCollections(MCollection<?>[] collections, Bson filter, Bson update) {
        for (MCollection<?> collection : collections) {
            collection.getMCollection().updateMany(filter, update);
        }
    }

}
