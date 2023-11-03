package com.akto.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.bson.conversions.Bson;
import java.util.Collections;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.dao.ApiCollectionsDao;
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
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.CollectionConditions.CollectionCondition;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;

public class ApiCollectionUsers {

    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private static final Logger logger = LoggerFactory.getLogger(ApiCollectionUsers.class);

    public enum CollectionType {
        ApiCollectionId, Id_ApiCollectionId, Id_ApiInfoKey_ApiCollectionId
    }

    public static final Map<CollectionType, MCollection<?>[]> COLLECTIONS_WITH_API_COLLECTION_ID = 
        Collections.unmodifiableMap(new HashMap<CollectionType, MCollection<?>[]>() {{
            put(CollectionType.ApiCollectionId, new MCollection[] {
                    SingleTypeInfoDao.instance,
                    SensitiveParamInfoDao.instance
            });
            put(CollectionType.Id_ApiCollectionId, new MCollection[] {
                    ApiInfoDao.instance,
                    TrafficInfoDao.instance,
                    SampleDataDao.instance,
                    SensitiveSampleDataDao.instance,
                    VulnerableRequestForTemplateDao.instance,
                    FilterSampleDataDao.instance
            });
            put(CollectionType.Id_ApiInfoKey_ApiCollectionId, new MCollection[] {
                    TestingRunIssuesDao.instance
            });
        }});

    public static void updateApiCollection(List<CollectionCondition> conditions, int id) {
        Set<ApiInfoKey> apis = new HashSet<>();
        conditions.forEach((condition) -> {
            apis.addAll(condition.returnApis());
        });

        Set<String> urls = new HashSet<>();
        apis.forEach((api) -> {
            urls.add(api.getUrl() + " " + api.getMethod());
        });

        ApiCollectionsDao.instance.updateOne(
                Filters.eq(Constants.ID, id),
                Updates.combine(
                    Updates.set(ApiCollection.CONDITIONS_STRING, conditions),
                    Updates.set(ApiCollection.URLS_STRING, urls)));
    }

    private static List<Bson> getFilters(List<CollectionCondition> conditions, CollectionType type){
        List<Bson> filters = new ArrayList<>();
        conditions.forEach((condition) -> {
            Bson conditionFilter = condition.returnFiltersMap().get(type);
            filters.add(conditionFilter);
        });
        return filters;
    }

    public static void addToCollectionsForCollectionId(List<CollectionCondition> conditions, int apiCollectionId) {
        Bson update = Updates.addToSet(SingleTypeInfo._COLLECTION_IDS, apiCollectionId);

        Map<CollectionType, Bson> filtersMap = new HashMap<>();
        Bson matchFilter = Filters.nin(SingleTypeInfo._COLLECTION_IDS, apiCollectionId);

        for (CollectionType type : CollectionType.values()) {
            List<Bson> conditionsFilters = getFilters(conditions, type);
            Bson orFilter = Filters.or(conditionsFilters);
            Bson finalFilter = Filters.and(matchFilter, orFilter);
            filtersMap.put(type, finalFilter);
        }

        updateCollectionsForCollectionId(filtersMap, update);
    }

    public static void removeFromCollectionsForCollectionId(List<CollectionCondition> conditions, int apiCollectionId) {
        Bson update = Updates.pull(SingleTypeInfo._COLLECTION_IDS, apiCollectionId);
        
        Map<CollectionType, Bson> filtersMap = new HashMap<>();
        Bson matchFilter = Filters.in(SingleTypeInfo._COLLECTION_IDS, apiCollectionId);

        for (CollectionType type : CollectionType.values()) {
            List<Bson> conditionsFilters = getFilters(conditions, type);
            Bson norFilter = Filters.nor(conditionsFilters);
            Bson finalFilter = Filters.and(matchFilter, norFilter);
            filtersMap.put(type, finalFilter);
        }

        updateCollectionsForCollectionId(filtersMap, update);
    }

    public static void computeCollectionsForCollectionId(List<CollectionCondition> conditions, int apiCollectionId) {
        addToCollectionsForCollectionId(conditions, apiCollectionId);
        removeFromCollectionsForCollectionId(conditions, apiCollectionId);
        updateApiCollection(conditions, apiCollectionId);
    }

    private static void updateCollectionsForCollectionId(Map<CollectionType, Bson> filtersMap, Bson update) {
        int accountId = Context.accountId.get();
        executorService.schedule(new Runnable() {
            public void run() {
                Context.accountId.set(accountId);

                Map<CollectionType, MCollection<?>[]> collectionsMap = COLLECTIONS_WITH_API_COLLECTION_ID;

                for (Map.Entry<CollectionType, MCollection<?>[]> collectionsEntry : collectionsMap.entrySet()) {

                    CollectionType type = collectionsEntry.getKey();
                    MCollection<?>[] collections = collectionsEntry.getValue();
                    Bson filter = filtersMap.get(type);

                    updateCollections(collections, filter, update);
                }

            }
        }, 0, TimeUnit.SECONDS);
    }

    public static void updateCollections(MCollection<?>[] collections, Bson filter, Bson update) {
        for (MCollection<?> collection : collections) {
            collection.getMCollection().updateMany(filter, update);
        }
    }

    public static void updateCollections(MCollection<?>[] collections, Bson filter, List<Bson> update) {
        for (MCollection<?> collection : collections) {
            collection.getMCollection().updateMany(filter, update);
        }
    }

    public static void updateCollectionsInBatches(MCollection<?>[] collections, Bson filter, List<Bson> update) {
        for (MCollection<?> collection : collections) {
            updateCollectionInBatches(collection, filter, update);
        }
    }

    static final int UPDATE_LIMIT = 50_000;

    // NOTE: This update only works with collections which have ObjectId as id.
    private static void updateCollectionInBatches(MCollection<?> collection, Bson filter, List<Bson> update) {
        boolean doUpdate = true;
        int c = 0;
        int time = Context.now();
        while (doUpdate) {

            List<Bson> pipeline = new ArrayList<>();
            pipeline.add(Aggregates.match(filter));
            pipeline.add(Aggregates.project(Projections.include(Constants.ID)));
            pipeline.add(Aggregates.limit(UPDATE_LIMIT));

            MongoCursor<BasicDBObject> cursor = collection.getMCollection()
                    .aggregate(pipeline, BasicDBObject.class).cursor();

            ArrayList<ObjectId> ret = new ArrayList<>();

            while (cursor.hasNext()) {
                BasicDBObject elem = cursor.next();
                ret.add((ObjectId) elem.get(Constants.ID));
            }

            UpdateResult res = collection.getMCollection().updateMany(
                    Filters.in(Constants.ID, ret), update);

            if (res.getMatchedCount() == 0) {
                doUpdate = false;
            }
            c += Math.min(UPDATE_LIMIT , res.getModifiedCount());
            logger.info("updated " + c + " " + collection.getCollName());
        }
        logger.info("Total time taken : " + (Context.now() - time));
    }

}
