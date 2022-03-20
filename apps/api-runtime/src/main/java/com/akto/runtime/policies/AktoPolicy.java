package com.akto.runtime.policies;

import com.akto.DaoInit;
import com.akto.dao.*;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiCollection;
import com.akto.dto.FilterSampleData;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.HttpResponseParams.Source;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.akto.dto.type.URLMethods;
import com.akto.types.CappedList;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class AktoPolicy {
    public static final String TEMPLATE = "template";
    public static final String STRICT = "strict";

    private List<RuntimeFilter> filters = new ArrayList<>();
    private Map<String, Map<ApiInfo.ApiInfoKey, ApiInfo>> apiInfoMap = new HashMap<>();
    private List<ApiInfo.ApiInfoKey> apiInfoRemoveList = new ArrayList<>();
    private List<ApiInfo.ApiInfoKey> sampleDataRemoveList = new ArrayList<>();
    Map<ApiInfo.ApiInfoKey,Map<Integer, CappedList<String>>> sampleMessages = new HashMap<>();
    boolean processCalledAtLeastOnce = false;
    ApiAccessTypePolicy apiAccessTypePolicy = new ApiAccessTypePolicy(null);

    private final int batchTimeThreshold = 120;
    private int timeSinceLastSync;
    private final int batchSizeThreshold = 10_000_000;
    private int currentBatchSize = 0;

    private static final Logger logger = LoggerFactory.getLogger(AktoPolicy.class);

    public void fetchFilters() {
        this.filters = RuntimeFilterDao.instance.findAll(new BasicDBObject());
    }

    public AktoPolicy(boolean shouldSyncWithDb) {
        if (shouldSyncWithDb) {
            syncWithDb(true);
        }
    }

    public void syncWithDb(boolean initialising) {
        // logger.info("Syncing with db");
        if (!initialising) {
            List<WriteModel<ApiInfo>> writesForApiInfo = AktoPolicy.getUpdates(apiInfoMap, apiInfoRemoveList);
            List<WriteModel<FilterSampleData>> writesForSampleData = getUpdatesForSampleData();
            logger.info("Writing to db: " + "writesForApiInfoSize="+writesForApiInfo.size() + " writesForSampleData="+ writesForSampleData.size());
            try {
                if (writesForApiInfo.size() > 0) ApiInfoDao.instance.getMCollection().bulkWrite(writesForApiInfo);
                if (writesForSampleData.size() > 0) FilterSampleDataDao.instance.getMCollection().bulkWrite(writesForSampleData);
            } catch (Exception e) {
                logger.error(e.toString());
            }
        }

        fetchFilters();

        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(new BasicDBObject());
        if (accountSettings != null) {
            List<String> cidrList = accountSettings.getPrivateCidrList();
            if ( cidrList != null && !cidrList.isEmpty()) {
                // logger.info("Found cidr from db");
                apiAccessTypePolicy.setPrivateCidrList(cidrList);
            }
        }

        apiInfoMap = new HashMap<>();
        apiInfoMap.put(TEMPLATE, new HashMap<>());
        apiInfoMap.put(STRICT, new HashMap<>());

        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject());
        apiInfoRemoveList = new ArrayList<>();
        for (ApiCollection apiCollection: apiCollections) {
            // logger.info("ApiCollection: " + apiCollection.getName());
            // logger.info("URLs : " + apiCollection.getUrls());
            for (String u: apiCollection.getUrls()) {
                String[] v = u.split(" ");
                ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(apiCollection.getId(), v[0], URLMethods.Method.valueOf(v[1]));
                if (isTemplateUrl(apiInfoKey.getUrl())) {
                    apiInfoMap.get(TEMPLATE).put(apiInfoKey, null);
                } else {
                    apiInfoMap.get(STRICT).put(apiInfoKey, null);
                }
            }
        }
        // logger.info("Total apiInfoMap keys " + apiInfoMap.keySet());
        // logger.info("Fetching apiInfoList");
        List<ApiInfo> apiInfoList =  ApiInfoDao.instance.findAll(new BasicDBObject());
        for (ApiInfo apiInfo: apiInfoList) {
            ApiInfo.ApiInfoKey apiInfoKey = apiInfo.getId();
            if (apiInfoMap.get(STRICT).containsKey(apiInfoKey)) {
                // logger.info("ADDING: " + apiInfo.key());
                apiInfoMap.get(STRICT).put(apiInfoKey, apiInfo);
            } else if (apiInfoMap.get(TEMPLATE).containsKey(apiInfoKey)) {
                // logger.info("ADDING: " + apiInfo.key());
                apiInfoMap.get(TEMPLATE).put(apiInfoKey, apiInfo);
            } else {
                // logger.info("DELETING: " + apiInfo.key());
                apiInfoRemoveList.add(apiInfoKey);
            }
        }

        sampleDataRemoveList = new ArrayList<>();
        List<ApiInfo.ApiInfoKey> filterSampleDataIdList = FilterSampleDataDao.instance.getApiInfoKeys();
        for (ApiInfo.ApiInfoKey apiInfoKey: filterSampleDataIdList) {
            if (apiInfoMap.get(STRICT).containsKey(apiInfoKey) || apiInfoMap.get(TEMPLATE).containsKey(apiInfoKey)) {
                sampleMessages.put(apiInfoKey, new HashMap<>());
            } else {
                sampleDataRemoveList.add(apiInfoKey);
            }
        }
    }

    public void main(List<HttpResponseParams> httpResponseParamsList) throws Exception {
        boolean syncImmediately = false;

        for (HttpResponseParams httpResponseParams: httpResponseParamsList) {
            try {
                process(httpResponseParams);
            } catch (Exception e) {
                logger.error(e.toString());
                e.printStackTrace();
            }
            if (httpResponseParams.getSource().equals(Source.HAR) || httpResponseParams.getSource().equals(Source.PCAP)) {
                syncImmediately = true;
            }
            currentBatchSize += 1;
        }

        if (syncImmediately || currentBatchSize >= batchSizeThreshold || (Context.now() -  timeSinceLastSync) >= batchTimeThreshold) {
            logger.info("Let's sync becoz threshold achieved: " + currentBatchSize + " " + (Context.now() -  timeSinceLastSync));
            this.currentBatchSize = 0;
            this.timeSinceLastSync = Context.now();
            syncWithDb(false);
        }
    }

    public static void main(String[] args) {
        DaoInit.init(new ConnectionString("mongodb://172.18.0.2:27017/admini"));
        Context.accountId.set(1_000_000);
        RuntimeFilterDao.instance.initialiseFilters();
//        RuntimeFilterDao.instance.initialiseFilters();
//        List<CustomFilter> customFilterList = new ArrayList<>();
//        customFilterList.add(new ResponseCodeRuntimeFilter(200,299));
//        customFilterList.add(new FieldExistsFilter("labelId"));
//        RuntimeFilter runtimeFilter = new RuntimeFilter(Context.now(),RuntimeFilter.UseCase.SET_CUSTOM_FIELD, "Check labelId", customFilterList, RuntimeFilter.Operator.AND, "check_label_id");
//        RuntimeFilterDao.instance.insertOne(runtimeFilter);
    }

    public void process(HttpResponseParams httpResponseParams) throws Exception {
        // logger.info("processing....");
        if (!this.processCalledAtLeastOnce) {
            logger.info("Calling first");
            syncWithDb(true);
            this.processCalledAtLeastOnce = true;
        }

        ApiInfo apiInfo = getApiInfoFromMap(httpResponseParams);
        if (apiInfo == null) return;

        for (RuntimeFilter filter: filters) {
            boolean filterResult = filter.process(httpResponseParams);
            if (!filterResult) continue;

            RuntimeFilter.UseCase useCase = filter.getUseCase();
            boolean saveSample = false;
            switch (useCase) {
                case AUTH_TYPE:
                    saveSample = AuthPolicy.findAuthType(httpResponseParams, apiInfo, filter);
                    break;
                case SET_CUSTOM_FIELD:
                    saveSample = SetFieldPolicy.setField(httpResponseParams, apiInfo, filter);
                    break;
                case DETERMINE_API_ACCESS_TYPE:
                    saveSample = apiAccessTypePolicy.findApiAccessType(httpResponseParams, apiInfo, filter);
                    break;
                default:
                    throw new Exception("Function for use case not defined");
            }

            // add sample data
            if (saveSample) {
                Map<Integer, CappedList<String>> sampleData = sampleMessages.get(apiInfo.getId());
                if (sampleData == null) {
                    sampleData = new HashMap<>();
                }
                CappedList<String> d = sampleData.getOrDefault(filter.getId(),new CappedList<String>(FilterSampleData.cap, true));
                d.add(httpResponseParams.getOrig());
                sampleData.put(filter.getId(), d);
                sampleMessages.put(apiInfo.getId(), sampleData);
            }
        }

        apiInfo.setLastSeen(Context.now());
        if (apiInfoMap.get(STRICT).containsKey(apiInfo.getId())) {
            apiInfoMap.get(STRICT).put(apiInfo.getId(), apiInfo);
        } else {
            apiInfoMap.get(TEMPLATE).put(apiInfo.getId(), apiInfo);
        }
        // logger.info("Done with process");
    }

    public ApiInfo getApiInfoFromMap(ApiInfo.ApiInfoKey apiInfoKey) throws NoSuchElementException{
        // strict check
        String apiInfoKeyUrl = apiInfoKey.getUrl();
        ApiInfo apiInfo = null;
        if (apiInfoMap.get(STRICT).containsKey(apiInfoKey)) {
            apiInfo = apiInfoMap.get(STRICT).get(apiInfoKey);
            if (apiInfo == null) {
                apiInfo = new ApiInfo(apiInfoKey);
            }
            return apiInfo;
        }

        // only adding leading slashes if not present
        if (!apiInfoKeyUrl.startsWith("/")) {
            apiInfoKeyUrl = "/" + apiInfoKeyUrl;
        }

        // template check
        for (ApiInfo.ApiInfoKey key: apiInfoMap.get(TEMPLATE).keySet()) {
            // 1. match collection id
            if (key.getApiCollectionId() != apiInfoKey.getApiCollectionId()) continue;
            // 2. match method
            if (!key.getMethod().equals(apiInfoKey.getMethod())) continue;
            // 3. match url
            String keyUrl = key.getUrl();
            // b. check if template url matches
            if (!keyUrl.startsWith("/")) {
                keyUrl = "/" + keyUrl;
            }
            String[] a = keyUrl.split("/");
            String[] b = apiInfoKeyUrl.split("/");
            if (a.length != b.length) continue;
            boolean flag = true;
            for (int i =0; i < a.length; i++) {
                if (!Objects.equals(a[i], b[i])) {
                    if (!(Objects.equals(a[i], "STRING") || Objects.equals(a[i], "INTEGER"))) {
                        flag = false;
                    }
                }
            }

            if (flag) {
                apiInfo = apiInfoMap.get(TEMPLATE).get(key);
                if (apiInfo == null) {
                    apiInfo = new ApiInfo(key);
                }
                return apiInfo;
            }

        }

        // else discard with log
        logger.info("FAILED to find in apiInfoMap: " + apiInfoKey.getUrl());
        return null;

    }

    public ApiInfo getApiInfoFromMap(HttpResponseParams httpResponseParams)  {
        ApiInfo.ApiInfoKey apiInfoKey = ApiInfo.ApiInfoKey.generateFromHttpResponseParams(httpResponseParams);

        return getApiInfoFromMap(apiInfoKey);

    }

    public static List<WriteModel<ApiInfo>> getUpdates(Map<String, Map<ApiInfo.ApiInfoKey, ApiInfo>> apiInfoMapNested , List<ApiInfo.ApiInfoKey> apiInfoRemoveList) {
        List<WriteModel<ApiInfo>> updates = new ArrayList<>();
        Map<ApiInfo.ApiInfoKey, ApiInfo> apiInfoMap = new HashMap<>();
        for (String k: apiInfoMapNested.keySet()) {
            Map<ApiInfo.ApiInfoKey, ApiInfo> m = apiInfoMapNested.get(k);
            for (ApiInfo.ApiInfoKey kk: m.keySet()) {
                apiInfoMap.put(kk, m.get(kk));
            }
        }

        for (ApiInfo.ApiInfoKey key: apiInfoMap.keySet()) {
            ApiInfo apiInfo = apiInfoMap.get(key);
            if (apiInfo == null) {
                // this case happens we apiInfo.Key is present in apiCollections collection in db,
                // but we didn't find any apiInfo class locally (i.e. no traffic came for that apiInfo.key)
                // So we discard till we get apiInfo from traffic
                continue;
            }

            List<Bson> subUpdates = new ArrayList<>();

            // allAuthTypesFound
            Set<Set<ApiInfo.AuthType>> allAuthTypesFound = apiInfo.getAllAuthTypesFound();
            if (allAuthTypesFound.isEmpty()) {
                // to make sure no field is null (so setting empty objects)
                subUpdates.add(Updates.setOnInsert(ApiInfo.ALL_AUTH_TYPES_FOUND, new HashSet<>()));
            } else {
                subUpdates.add(Updates.addEachToSet(ApiInfo.ALL_AUTH_TYPES_FOUND, Arrays.asList(allAuthTypesFound.toArray())));
            }

            // apiAccessType
            Set<ApiInfo.ApiAccessType> apiAccessTypes = apiInfo.getApiAccessTypes();
            if (apiAccessTypes.isEmpty()) {
                // to make sure no field is null (so setting empty objects)
                subUpdates.add(Updates.setOnInsert(ApiInfo.API_ACCESS_TYPES, new HashSet<>()));
            } else {
                subUpdates.add(Updates.addEachToSet(ApiInfo.API_ACCESS_TYPES, Arrays.asList(apiAccessTypes.toArray())));
            }

            // violations
            Map<String,Integer> violationsMap = apiInfo.getViolations();
            if (violationsMap.isEmpty()) {
                // to make sure no field is null (so setting empty objects)
                subUpdates.add(Updates.setOnInsert(ApiInfo.VIOLATIONS, new HashMap<>()));
            } else {
                for (String customKey: violationsMap.keySet()) {
                    subUpdates.add(Updates.set(ApiInfo.VIOLATIONS + "." + customKey, violationsMap.get(customKey)));
                }
            }

            // last seen
            subUpdates.add(Updates.set(ApiInfo.LAST_SEEN, apiInfo.getLastSeen()));

            updates.add(
                    new UpdateOneModel<>(
                            ApiInfoDao.getFilter(apiInfo.getId()),
                            Updates.combine(subUpdates),
                            new UpdateOptions().upsert(true)
                    )
            );

        }


        for (ApiInfo.ApiInfoKey apiInfoKey: apiInfoRemoveList) {
            updates.add(
                    new DeleteOneModel<>(
                            ApiInfoDao.getFilter(apiInfoKey)
                    )
            );
        }

        return updates;
    }

    public List<WriteModel<FilterSampleData>> getUpdatesForSampleData() {
        ArrayList<WriteModel<FilterSampleData>> bulkUpdates = new ArrayList<>();

        for (ApiInfo.ApiInfoKey apiInfoKey: sampleMessages.keySet()) {
            Map<Integer, CappedList<String>> filterSampleDataMap = sampleMessages.get(apiInfoKey);
            for (Integer filterId: filterSampleDataMap.keySet()) {
                List<String> sampleData = filterSampleDataMap.get(filterId).get();
                Bson bson = Updates.pushEach(FilterSampleData.SAMPLES, sampleData, new PushOptions().slice(-1 * FilterSampleData.cap));
                bulkUpdates.add(
                        new UpdateOneModel<>(
                                FilterSampleDataDao.getFilter(apiInfoKey, filterId),
                                bson,
                                new UpdateOptions().upsert(true)
                        )
                );
            }
        }

        for (ApiInfo.ApiInfoKey apiInfoKey: sampleDataRemoveList) {
            bulkUpdates.add(
                    new DeleteOneModel<>(FilterSampleDataDao.getFilterForApiInfoKey(apiInfoKey))
            );
        }

        return bulkUpdates;
    }

    public boolean isTemplateUrl(String url) {
        return url.contains("STRING") || url.contains("INTEGER") ;
    }

    public List<RuntimeFilter> getFilters() {
        return filters;
    }

    public void setFilters(List<RuntimeFilter> filters) {
        this.filters = filters;
    }

    public Map<String,Map<ApiInfo.ApiInfoKey, ApiInfo>> getApiInfoMap() {
        return apiInfoMap;
    }

    public void setApiInfoMap(Map<String,Map<ApiInfo.ApiInfoKey, ApiInfo>> apiInfoMap) {
        this.apiInfoMap = apiInfoMap;
    }

    public List<ApiInfo.ApiInfoKey> getApiInfoRemoveList() {
        return apiInfoRemoveList;
    }

    public void setApiInfoRemoveList(List<ApiInfo.ApiInfoKey> apiInfoRemoveList) {
        this.apiInfoRemoveList = apiInfoRemoveList;
    }

    public Map<ApiInfo.ApiInfoKey, Map<Integer, CappedList<String>>> getSampleMessages() {
        return sampleMessages;
    }

    public void setSampleMessages(Map<ApiInfo.ApiInfoKey, Map<Integer, CappedList<String>>> sampleMessages) {
        this.sampleMessages = sampleMessages;
    }

    public boolean isProcessCalledAtLeastOnce() {
        return processCalledAtLeastOnce;
    }

    public void setProcessCalledAtLeastOnce(boolean processCalledAtLeastOnce) {
        this.processCalledAtLeastOnce = processCalledAtLeastOnce;
    }

    public ApiAccessTypePolicy getApiAccessTypePolicy() {
        return apiAccessTypePolicy;
    }

    public void setApiAccessTypePolicy(ApiAccessTypePolicy apiAccessTypePolicy) {
        this.apiAccessTypePolicy = apiAccessTypePolicy;
    }

    public List<ApiInfo.ApiInfoKey> getSampleDataRemoveList() {
        return sampleDataRemoveList;
    }

    public void setSampleDataRemoveList(List<ApiInfo.ApiInfoKey> sampleDataRemoveList) {
        this.sampleDataRemoveList = sampleDataRemoveList;
    }
}
