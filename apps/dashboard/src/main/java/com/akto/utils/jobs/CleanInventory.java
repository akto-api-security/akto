package com.akto.utils.jobs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

import org.bson.conversions.Bson;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.SensitiveSampleDataDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.FilterYamlTemplateDao;
import com.akto.dto.Account;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.monitoring.FilterConfig.FILTER_TYPE;
import com.akto.dto.test_editor.ExecutorNode;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLMethods.Method;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.parsers.HttpCallParser;
import com.akto.test_editor.execution.ParseAndExecute;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.AccountTask;
import com.akto.util.Constants;
import com.akto.util.DashboardMode;
import com.akto.util.Pair;
import com.akto.utils.Utils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;

import static com.akto.utils.Utils.deleteApis;
import static com.akto.runtime.utils.Utils.createRegexPatternFromList;

public class CleanInventory {

    private static final LoggerMaker logger = new LoggerMaker(CleanInventory.class, LogDb.DASHBOARD);
    private static final int limit = 500;
    private static final Bson sort = Sorts.ascending(ApiInfo.ID_API_COLLECTION_ID, ApiInfo.ID_URL, ApiInfo.ID_METHOD);

    final static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static void cleanInventoryJobRunner() {

        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {

                int now = Context.now();
                logger.debug("Starting cleanInventoryJob for all accounts at " + now);

                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        try {
                            cleanInventoryJob();
                        } catch (Exception e) {
                            logger.errorAndAddToDb(e, "Error in cleanInventoryJob");
                        }
                    }
                }, "clean-inventory-job");

                int now2 = Context.now();
                int diffNow = now2-now;
                logger.debug(String.format("Completed cleanInventoryJob for all accounts at %d , time taken : %d", now2, diffNow));
            }
        }, 0, 5, TimeUnit.HOURS);

    }

    private static Set<String> methodSet = new HashSet<>();

    private static Set<String> getMethodSet() {

        if (!methodSet.isEmpty()) {
            return methodSet;
        }

        List<String> lowerCaseMethods = Arrays.asList(URLMethods.Method.getValuesArray()).stream()
                .map(s -> s.name().toLowerCase()).collect(Collectors.toList());
        List<String> upperCaseMethods = Arrays.asList(URLMethods.Method.getValuesArray()).stream()
                .map(s -> s.name().toUpperCase()).collect(Collectors.toList());
        methodSet.addAll(upperCaseMethods);
        methodSet.addAll(lowerCaseMethods);
        return methodSet;
    }

    private static void cleanInventoryJob() {

        int now = Context.now();
        SingleTypeInfoDao.instance.deleteAll(Filters.nin(SingleTypeInfo._METHOD, getMethodSet()));
        SensitiveSampleDataDao.instance.deleteAll(Filters.nin("_id.method", getMethodSet()));
        /*
         * The above collections implement method as String, thus cleaning them.
         * Rest of the collections implement method as an ENUM,
         * thus they will not have any non-standard method.
         * Any non-standard method will be in the form of "OTHER". Thus ignoring them.
         */

        int now2 = Context.now();
        int diff = now2 - now;

        if (diff >= 2) {
            logger.infoAndAddToDb(String.format("cleanInventoryJob finished, time taken: %d ", diff));
        }

    }

    public static void moveApisFromSampleData(List<Key> sampleDataIds, boolean skipMergingOnKnownStaticURLsForVersionedApis) {
        if (sampleDataIds.isEmpty()) return;
        
        List<SampleData> allSamples = SampleDataDao.instance.findAll(Filters.or(SampleDataDao.filterForMultipleSampleData(sampleDataIds)));
        List<String> messages = new ArrayList<>();
        for(SampleData sampleData: allSamples) {
            messages.addAll(sampleData.getSamples());
        }

        if (allSamples.isEmpty() || messages.isEmpty()) return;
        

            try {
                Utils.pushDataToKafka(allSamples.get(0).getId().getApiCollectionId(), "", messages, new ArrayList<>(), true, false, false, skipMergingOnKnownStaticURLsForVersionedApis);
                logger.infoAndAddToDb("Successfully moved APIs.");
            } catch (Exception e) {
                logger.errorAndAddToDb("Error during move APIs: " + e.getMessage());
                e.printStackTrace();
            }
    }
    
    public static void cleanFilteredSampleDataFromAdvancedFilters(List<ApiCollection> apiCollections, List<YamlTemplate> yamlTemplates, List<String> redundantUrlList, String filePath, boolean shouldDeleteRequest, boolean saveLogsToDB) throws IOException{

        Map<Integer, ApiCollection> apiCollectionMap = apiCollections.stream().collect(Collectors.toMap(ApiCollection::getId, Function.identity()));
        // BufferedWriter writer = new BufferedWriter(new FileWriter(new File(filePath)));
        List<SampleData> sampleDataList = new ArrayList<>();
        Bson filters = Filters.empty();
        int skip = 0;
        Map<Integer,Integer> collectionWiseDeletionCountMap = new HashMap<>();

        Map<String,FilterConfig> filterMap = FilterYamlTemplateDao.fetchFilterConfig(false, yamlTemplates, true);
        Pattern pattern = createRegexPatternFromList(redundantUrlList);
        do {
            sampleDataList = SampleDataDao.instance.findAll(filters, skip, limit, sort);
            skip += limit;
            List<Key> toBeDeleted = new ArrayList<>();
            List<Key> toMove = new ArrayList<>();
            List<String> remainingSamples = new ArrayList<>();
            for(SampleData sampleData: sampleDataList) {
                try {
                    List<String> samples = sampleData.getSamples();
                    remainingSamples.clear();
                    if (samples == null || samples.isEmpty()) {
                        logger.debug("[BadApisRemover] No samples found for : " + sampleData.getId());
                        continue;
                    }

                    ApiCollection apiCollection = apiCollectionMap.get(sampleData.getId().getApiCollectionId());
                    if (apiCollection == null) {
                        if(!DashboardMode.isMetered()){
                            logger.debug("[BadApisRemover] No apiCollection found for : " + sampleData.getId());
                        }
                        continue;
                    }

                    
                    boolean isRedundant = false;
                    boolean isNetsparkerPresent = false;
                    boolean movingApi = false;
                    for (String sample : samples) {
                        HttpResponseParams httpResponseParams = HttpCallParser.parseKafkaMessage(sample);
                        isNetsparkerPresent |= sample.toLowerCase().contains("netsparker");
                        if(httpResponseParams != null){
                            isRedundant =  HttpCallParser.isRedundantEndpoint(httpResponseParams.getRequestParams().getURL(), pattern);
                            if(!isRedundant){
                                Map<String, List<ExecutorNode>> executorNodesMap = ParseAndExecute.createExecutorNodeMap(filterMap);
                                Pair<HttpResponseParams,FILTER_TYPE> temp = HttpCallParser.applyAdvancedFilters(httpResponseParams, executorNodesMap, filterMap);
                                HttpResponseParams param = temp.getFirst();
                                FILTER_TYPE filterType = temp.getSecond();

                                if(param != null){
                                    // comes when Filter_Block is not valid {Remaining => Unchanged, Modified, Allowed}
                                    if(filterType.equals(FILTER_TYPE.MODIFIED)){
                                        // filter passed and modified
                                        movingApi = true;
                                        remainingSamples.add(sample);
                                        break;
                                    }else if(filterType.equals(FILTER_TYPE.ALLOWED)){
                                        // filter passed and not modified
                                        remainingSamples.add(sample);
                                    }else if(filterMap.size() == 1){
                                        // filter failed and id was default_delete
                                        String key = filterMap.entrySet().iterator().next().getKey();
                                        if(!key.equals("DEFAULT_ALLOW_FILTER")){
                                            remainingSamples.add(sample);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if(movingApi){
                        // any 1 of the sample is modifiable, we print this block
                        toMove.add(sampleData.getId());
                        if(saveLogsToDB){
                            logger.infoAndAddToDb("Filter passed, modify sample data of API: " + sampleData.getId(), LogDb.DASHBOARD);
                        }else{
                            logger.debug("[BadApisUpdater] Updating bad from template API: " + sampleData.getId(), LogDb.DASHBOARD);
                        }
                    } else if (isRedundant || (remainingSamples.size() != samples.size())) {
                        if (remainingSamples.isEmpty()) {
                            // writer.write(sampleData.toString());
                            // if api falls under redundant url and if block filter is passed or none of the filter from any of the filters is passed, we print this block
                            int initialCount = collectionWiseDeletionCountMap.getOrDefault(sampleData.getId().getApiCollectionId(), 0);
                            collectionWiseDeletionCountMap.put(sampleData.getId().getApiCollectionId(),initialCount + 1);
                            toBeDeleted.add(sampleData.getId());
                            if(saveLogsToDB){
                                logger.infoAndAddToDb(
                                        "Filter passed, deleting bad api found from filter: " + sampleData.getId(), LogDb.DASHBOARD
                                );
                            }else{
                                logger.debug("[BadApisRemover] " + isNetsparkerPresent + " Deleting bad samples from template: " + sampleData.getId(), LogDb.DASHBOARD);
                            }
                        } else {
                            if (remainingSamples.size() != samples.size()) {

                                if (shouldDeleteRequest) {
                                    SampleDataDao.instance.updateOneNoUpsert(Filters.eq("_id",sampleData.getId()), Updates.set(SampleData.SAMPLES,remainingSamples));
                                }
                                if(saveLogsToDB){
                                    logger.infoAndAddToDb(
                                            "Deleting bad samples from sample data " + sampleData.getId(), LogDb.DASHBOARD
                                    );
                                }else{
                                    logger.debug("[BadApisRemover] " + isNetsparkerPresent + " Deleting bad API from template: " + sampleData.getId(), LogDb.DASHBOARD);
                                }
                            }
                        }
                    } else {
                        // other cases like: => filter from advanced filter is passed || filter from block filter fails
                        if(saveLogsToDB){
                            logger.infoAndAddToDb(
                                "Filter did not pass, keeping api found from filter: " + sampleData.getId(), LogDb.DASHBOARD
                            );
                        }else{
                            logger.debug("[BadApisRemover] " + isNetsparkerPresent + " Keeping API from template: " + sampleData.getId(), LogDb.DASHBOARD);
                        } 
                        
                    }
                } catch (Exception e) {
                    logger.errorAndAddToDb("[BadApisRemover] Couldn't delete an api for default payload: " + sampleData.getId() + e.getMessage(), LogDb.DASHBOARD);
                }
            }
            if (shouldDeleteRequest) {
                logger.debug("starting deletion of apis");
                deleteApis(toBeDeleted);
            }

            if (shouldDeleteRequest && toMove.size() > 0) {
                logger.debug("starting moving APIs");
                moveApisFromSampleData(toMove, false);
            }

            // String shouldMove = System.getenv("MOVE_REDUNDANT_APIS");

        } while (!sampleDataList.isEmpty());

        for(Map.Entry<Integer,Integer> iterator: collectionWiseDeletionCountMap.entrySet()){
            int collId = iterator.getKey();
            int deletionCount = iterator.getValue();
            String name = apiCollectionMap.get(collId).getDisplayName();

            if(saveLogsToDB){
                logger.infoAndAddToDb("Total apis deleted from collection: " + name + " are: " + deletionCount, LogDb.DASHBOARD);
            }
        }

        // writer.flush();
        // writer.close();
    }

    public static void removeUnnecessaryEndpoints(List<ApiCollection> apiCollections,  boolean shouldDeleteRequest){
        try {
            for (ApiCollection apiCollection: apiCollections) {
                List<Key> toBeDeleted = new ArrayList<>();
                if (apiCollection.getHostName() == null) {
                    continue;
                }
                List<BasicDBObject> endpoints = ApiCollectionsDao.fetchEndpointsInCollectionUsingHost(apiCollection.getId(), 0, false);

                if (endpoints == null || endpoints.isEmpty()) {
                    continue;
                }

                logger.debug("[BadApisRemover] Starting for APICollection: " + apiCollection.getId(), LogDb.DASHBOARD);
                for (BasicDBObject singleTypeInfo: endpoints) {
                    singleTypeInfo = (BasicDBObject) (singleTypeInfo.getOrDefault("_id", new BasicDBObject()));
                    int apiCollectionId = singleTypeInfo.getInt("apiCollectionId");
                    String url = singleTypeInfo.getString("url");
                    String method = singleTypeInfo.getString("method");

                    Key key = new Key(apiCollectionId, url, Method.fromString(method), -1, 0, 0);

                    if (method.equalsIgnoreCase("options")) {
                        logger.debug("[BadApisRemover] OPTIONS Deleting bad API: " + key, LogDb.DASHBOARD);
                        toBeDeleted.add(key);
                        continue;
                    }

                    if (!method.equalsIgnoreCase("get")) {
                        logger.debug("[BadApisRemover] Non-get Deleting bad API: " + key, LogDb.DASHBOARD);
                        continue;
                    }

                    Bson filter = ApiInfoDao.getFilter(url, method, apiCollectionId);
        
                    SampleData sampleData = SampleDataDao.instance.findOne(filter);
                    if (sampleData == null || sampleData.getSamples() == null || sampleData.getSamples().isEmpty()) {
                        Bson stiFilterReq = Filters.and(
                            Filters.eq("url", url),
                            Filters.eq("method", method),
                            Filters.in("responseCode", new Integer[]{-1, 200, 201, 204, 302}),
                            Filters.eq("isHeader", false),
                            Filters.or(Filters.eq("isUrlParam", false), Filters.exists("isUrlParam", false)), 
                            Filters.eq("apiCollectionId", apiCollectionId)
                        );
                        SingleTypeInfo singleTypeInfoForApi = SingleTypeInfoDao.instance.findOne(stiFilterReq);
                        if (singleTypeInfoForApi == null) {
                            logger.debug("[BadApisRemover] no-sample Deleting bad API: " + key, LogDb.DASHBOARD);
                            toBeDeleted.add(key);    
                        } else {
                            logger.debug("[BadApisRemover] yes-sti Deleting bad API: " + key + " " + singleTypeInfoForApi.composeKey(), LogDb.DASHBOARD);
                        }
                    } else {
                        logger.debug("[BadApisRemover] yes-sample Deleting bad API: " + key, LogDb.DASHBOARD);
                    }
                }

                
                if (shouldDeleteRequest) {
                    logger.debug("starting deletion of apis");
                    deleteApis(toBeDeleted);
                }
            }

        } catch (Exception e) {
            logger.errorAndAddToDb("Couldn't complete scan for APIs remover: " + e.getMessage(), LogDb.DASHBOARD);
            e.printStackTrace();
        }
    }

    public static void deleteOptionsAPIs(List<ApiCollection> apiCollections){
        for (ApiCollection apiCollection: apiCollections) {
            List<Key> toBeDeleted = new ArrayList<>();
            if (apiCollection.getHostName() == null) {
                continue;
            }
            List<BasicDBObject> endpoints = ApiCollectionsDao.fetchEndpointsInCollectionUsingHost(apiCollection.getId(), 0, false);

            if (endpoints == null || endpoints.isEmpty()) {
                continue;
            }
            for (BasicDBObject singleTypeInfo: endpoints) {
                singleTypeInfo = (BasicDBObject) (singleTypeInfo.getOrDefault("_id", new BasicDBObject()));
                int apiCollectionId = singleTypeInfo.getInt("apiCollectionId");
                String url = singleTypeInfo.getString("url");
                String method = singleTypeInfo.getString("method");

                Key key = new Key(apiCollectionId, url, Method.fromString(method), -1, 0, 0);

                if (method.equalsIgnoreCase("options")) {
                    toBeDeleted.add(key);
                    continue;
                }
            }
            deleteApis(toBeDeleted);
        }
    }

    public static int deleteApiInfosForMissingSTIs(boolean deleteAPIsInstantly){
        try {
            Bson filterInfo = UsageMetricCalculator.excludeDemosAndDeactivated(ApiInfo.ID_API_COLLECTION_ID);
            Bson filterQ = Filters.and(UsageMetricCalculator.excludeDemosAndDeactivated(Constants.ID));
            Map<Integer, ApiCollection> apiCollectionMap = ApiCollectionsDao.instance.findAll(filterQ, Projections.include(ApiCollection.ID, ApiCollection.HOST_NAME)).stream().collect(Collectors.toMap(ApiCollection::getId, Function.identity()));
            ExecutorService executor = Executors.newFixedThreadPool(20);
            List<Future<Void>> futures = new ArrayList<>();
            int accountId = Context.accountId.get();

            AtomicInteger counter = new AtomicInteger(0);
            ArrayList<WriteModel<ApiInfo>> bulkUpdate = new ArrayList<>();
            
            for (Integer apiCollectionId : apiCollectionMap.keySet()) {
                ApiCollection apiCollection = apiCollectionMap.get(apiCollectionId);
                if (apiCollection == null || apiCollection.getHostName() == null || apiCollection.getHostName().isEmpty()) {
                    logger.info("Skipping apiCollectionId: " + apiCollectionId + " as it has no hostName");
                    continue;
                }
                futures.add(executor.submit(() -> {
                    Context.accountId.set(accountId);
                    try {
                        List<ApiInfo> actualApiInfosInColl = ApiInfoDao.instance.findAll(
                            Filters.eq(ApiInfo.ID_API_COLLECTION_ID, apiCollectionId),
                            Projections.include(Constants.ID)
                        );

                        logger.info("Found " + actualApiInfosInColl.size() + " apiInfos in collection: " + apiCollectionId);
                        if(actualApiInfosInColl.isEmpty()){
                            return null;
                        }

                        List<BasicDBObject> stisApis = ApiCollectionsDao.fetchEndpointsInCollectionUsingHost(apiCollectionId, 0, false);
                        Set<ApiInfoKey> apiInfoKeys = stisApis.stream()
                            .map(sti -> {
                                BasicDBObject idObj = (BasicDBObject) sti.get(Constants.ID);
                                if (idObj == null) return null;
                                int collId = idObj.getInt(ApiInfoKey.API_COLLECTION_ID);
                                String url = idObj.getString(ApiInfoKey.URL);
                                String method = idObj.getString(ApiInfoKey.METHOD);
                                return new ApiInfoKey(collId, url, Method.fromString(method));
                            })
                            .filter(java.util.Objects::nonNull)
                            .collect(Collectors.toSet());

                        logger.info("Found " + apiInfoKeys.size() + " STIs in collection: " + apiCollectionId);
                        for (ApiInfo apiInfo : actualApiInfosInColl) {
                            if (!apiInfoKeys.contains(apiInfo.getId())) {
                                // delete this apiInfo
                                counter.incrementAndGet();
                                Bson filter = ApiInfoDao.getFilter(apiInfo.getId());
                                logger.info("Deleting apiInfo for missing STI host: " + apiInfo.getId());
                                if(deleteAPIsInstantly){
                                    bulkUpdate.add(new DeleteOneModel<>(filter));
                                }
                                
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    return null;
                }));
            }

            executor.shutdown();
            int shutdownTime = deleteAPIsInstantly ? 5 : 1;
            try {
                
                if (!executor.awaitTermination(shutdownTime, TimeUnit.MINUTES)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (!bulkUpdate.isEmpty() && deleteAPIsInstantly) {
                ApiInfoDao.instance.getMCollection().bulkWrite(bulkUpdate);
            }

            counter.addAndGet((int) ApiInfoDao.instance.count(
                Filters.and(
                    Filters.nin(ApiInfo.ID_API_COLLECTION_ID, apiCollectionMap.keySet()),
                    filterInfo
                )
            ));

            if (deleteAPIsInstantly) {
                ApiInfoDao.instance.deleteAll(
                Filters.and(
                    Filters.nin(ApiInfo.ID_API_COLLECTION_ID, apiCollectionMap.keySet()),
                    filterInfo
                ));
                
            } else {
                logger.info("Found " + counter.get() + " apiInfos for missing STIs, but not deleted as deleteAPIsInstantly is false.");
            }

            return counter.get();
        } catch (Exception e) {
            e.printStackTrace();
            // TODO: handle exception
        }

        return 0;
    }
}