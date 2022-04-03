package com.akto.runtime.policies;

import com.akto.DaoInit;
import com.akto.dao.*;
import com.akto.dto.*;
import com.akto.dao.context.Context;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.akto.dto.type.APICatalog;
import com.akto.dto.type.URLStatic;
import com.akto.dto.type.URLTemplate;
import com.akto.runtime.APICatalogSync;
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
    private Map<Integer, ApiInfoCatalog> apiInfoCatalogMap = new HashMap<>();
    private Map<ApiInfo.ApiInfoKey, ApiInfo> reserveApiInfoMap = new HashMap<>();
    private Map<FilterSampleData.FilterKey, FilterSampleData> reserveFilterSampleDataMap = new HashMap<>();

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

    public AktoPolicy(APICatalogSync apiCatalogSync) {
        buildFromDb(apiCatalogSync.delta);
    }

    public void buildFromDb(Map<Integer, APICatalog> delta) {
        fetchFilters();

        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(new BasicDBObject());
        if (accountSettings != null) {
            List<String> cidrList = accountSettings.getPrivateCidrList();
            if ( cidrList != null && !cidrList.isEmpty()) {
                // logger.info("Found cidr from db");
                apiAccessTypePolicy.setPrivateCidrList(cidrList);
            }
        }

        apiInfoCatalogMap = new HashMap<>();
        for (Integer collectionId: delta.keySet()) {
            ApiInfoCatalog apiInfoCatalog = new ApiInfoCatalog(new HashMap<>(), new HashMap<>(), new ArrayList<>());
            apiInfoCatalogMap.put(collectionId, apiInfoCatalog);
            APICatalog apiCatalog = delta.get(collectionId);
            for (URLStatic urlStatic: apiCatalog.getStrictURLToMethods().keySet()) {
                apiInfoCatalog.getStrictURLToMethods().put(urlStatic, new PolicyCatalog(null, new HashMap<>()));
            }
            for (URLTemplate urlTemplate: apiCatalog.getTemplateURLToMethods().keySet()) {
                apiInfoCatalog.getTemplateURLToMethods().put(urlTemplate,new PolicyCatalog(null, new HashMap<>()));
            }
        }

        List<ApiInfo> apiInfoList =  ApiInfoDao.instance.findAll(new BasicDBObject());
        for (ApiInfo apiInfo: apiInfoList) {
            fillApiInfoInCatalog(apiInfo, true);
        }

        for (ApiInfo apiInfo: reserveApiInfoMap.values()) {
            fillApiInfoInCatalog(apiInfo, false);
        }

        List<FilterSampleData> filterSampleDataList = FilterSampleDataDao.instance.findAll(new BasicDBObject());
        for (FilterSampleData filterSampleData: filterSampleDataList) {
            fillFilterSampleDataInCatalog(filterSampleData);
        }

        for (FilterSampleData filterSampleData: reserveFilterSampleDataMap.values()) {
            fillFilterSampleDataInCatalog(filterSampleData);
        }

        reserveApiInfoMap = new HashMap<>();
        reserveFilterSampleDataMap = new HashMap<>();
    }

    public void syncWithDb(boolean initialising, Map<Integer, APICatalog> delta) {
        // logger.info("Syncing with db");
        if (!initialising) {
            UpdateReturn updateReturn = AktoPolicy.getUpdates(apiInfoCatalogMap);
            List<WriteModel<ApiInfo>> writesForApiInfo = updateReturn.updatesForApiInfo;
            List<WriteModel<FilterSampleData>> writesForSampleData = updateReturn.updatesForSampleData;
            logger.info("Writing to db: " + "writesForApiInfoSize="+writesForApiInfo.size() + " writesForSampleData="+ writesForSampleData.size());
            try {
                if (writesForApiInfo.size() > 0) ApiInfoDao.instance.getMCollection().bulkWrite(writesForApiInfo);
                if (writesForSampleData.size() > 0) FilterSampleDataDao.instance.getMCollection().bulkWrite(writesForSampleData);
            } catch (Exception e) {
                logger.error(e.toString());
            }
        }

        buildFromDb(delta);
    }

    public void fillFilterSampleDataInCatalog(FilterSampleData filterSampleData) {
        ApiInfo.ApiInfoKey apiInfoKey = filterSampleData.getId().getApiInfoKey();
        ApiInfoCatalog apiInfoCatalog = apiInfoCatalogMap.get(apiInfoKey.getApiCollectionId());
        if (apiInfoCatalog == null) {
            // TODO: should delete from db??
            return;
        }
        Map<URLStatic, PolicyCatalog> strictURLToMethods = apiInfoCatalog.getStrictURLToMethods();
        Map<URLTemplate, PolicyCatalog> templateURLToMethods = apiInfoCatalog.getTemplateURLToMethods();
        URLStatic urlStatic = new URLStatic(apiInfoKey.getUrl(), apiInfoKey.getMethod());
        if (strictURLToMethods.containsKey(urlStatic)) {
            strictURLToMethods.get(urlStatic).getFilterSampleDataMap().put(filterSampleData.getId().getFilterId(), filterSampleData);
            return;
        }

        for (URLTemplate urlTemplate: templateURLToMethods.keySet()) {
            if (urlTemplate.match(urlStatic)) {
                filterSampleData.getId().getApiInfoKey().setUrl(urlTemplate.getTemplateString());
                // merge with existing apiInfo if present
                FilterSampleData filterSampleDataFromMap = templateURLToMethods.get(urlTemplate).getFilterSampleDataMap().get(filterSampleData.getId().getFilterId());
                if (filterSampleDataFromMap != null) {
                    filterSampleData.merge(filterSampleDataFromMap);
                }
                templateURLToMethods.get(urlTemplate).getFilterSampleDataMap().put(filterSampleData.getId().getFilterId(), filterSampleData);
                return;
            }
        }
    }

    public void fillApiInfoInCatalog(ApiInfo apiInfo, boolean shouldDeleteFromDb) {
        ApiInfo.ApiInfoKey apiInfoKey = apiInfo.getId();
        ApiInfoCatalog apiInfoCatalog = apiInfoCatalogMap.get(apiInfoKey.getApiCollectionId());
        if (apiInfoCatalog == null) {
            // TODO: should delete from db??
            return;
        }

        Map<URLStatic, PolicyCatalog> strictURLToMethods = apiInfoCatalog.getStrictURLToMethods();
        Map<URLTemplate, PolicyCatalog> templateURLToMethods = apiInfoCatalog.getTemplateURLToMethods();
        URLStatic urlStatic = new URLStatic(apiInfoKey.getUrl(), apiInfoKey.getMethod());
        if (strictURLToMethods.containsKey(urlStatic)) {
            strictURLToMethods.get(urlStatic).setApiInfo(apiInfo);
            return;
        }

        for (URLTemplate urlTemplate: templateURLToMethods.keySet()) {
            if (urlTemplate.match(urlStatic)) {
                // need to delete duplicate from db. For example in db if url is api/books/1 now it becomes api/books/INTEGER so we need to delete api/books/1
                if (!urlTemplate.getTemplateString().equals(urlStatic.getUrl()) && shouldDeleteFromDb) {
                    // created new object because apiInfo id is altered below
                    apiInfoCatalog.getDeletedInfo().add(new ApiInfo.ApiInfoKey(apiInfo.getId().getApiCollectionId(), apiInfo.getId().getUrl(), apiInfo.getId().getMethod()));
                }
                // change api info url to template url
                apiInfo.getId().setUrl(urlTemplate.getTemplateString());
                // merge with existing apiInfo if present
                ApiInfo apiInfoFromMap = templateURLToMethods.get(urlTemplate).getApiInfo();
                if (apiInfoFromMap != null) {
                    apiInfo.merge(apiInfoFromMap);
                }
                templateURLToMethods.get(urlTemplate).setApiInfo(apiInfo);
                return;
            }
        }

        if (shouldDeleteFromDb) {
            apiInfoCatalog.getDeletedInfo().add(apiInfo.getId());
        }

    }

    public void main(List<HttpResponseParams> httpResponseParamsList, APICatalogSync apiCatalogSync) throws Exception {
        boolean syncImmediately = false;

        for (HttpResponseParams httpResponseParams: httpResponseParamsList) {
            try {
                process(httpResponseParams);
            } catch (Exception e) {
                logger.error(e.toString());
                e.printStackTrace();
            }
            if (httpResponseParams.getSource().equals(HttpResponseParams.Source.HAR) || httpResponseParams.getSource().equals(HttpResponseParams.Source.PCAP)) {
                syncImmediately = true;
            }
            currentBatchSize += 1;
        }

        if (apiCatalogSync != null) {
            this.currentBatchSize = 0;
            this.timeSinceLastSync = Context.now();
            syncWithDb(false, apiCatalogSync.delta);
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

        ApiInfo.ApiInfoKey apiInfoKey = ApiInfo.ApiInfoKey.generateFromHttpResponseParams(httpResponseParams);
        PolicyCatalog policyCatalog = getApiInfoFromMap(apiInfoKey);
        boolean addToReserve = false;
        if (policyCatalog == null) {
            addToReserve = true;
            policyCatalog = new PolicyCatalog(new ApiInfo(apiInfoKey), new HashMap<>());
        }
        ApiInfo apiInfo = policyCatalog.getApiInfo();
        Map<Integer, FilterSampleData> filterSampleDataMap = policyCatalog.getFilterSampleDataMap();

        int statusCode = httpResponseParams.getStatusCode();
        addToReserve = addToReserve && statusCode >= 200 && statusCode < 300;

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
                FilterSampleData filterSampleData = filterSampleDataMap.get(filter.getId());
                if (filterSampleData == null) {
                    filterSampleData = new FilterSampleData(apiInfo.getId(), filter.getId());
                    filterSampleDataMap.put(filter.getId(), filterSampleData);
                }
                filterSampleData.getSamples().add(httpResponseParams.getOrig());
            }
        }

        apiInfo.setLastSeen(Context.now());

        if (addToReserve) {
            ApiInfo reserveApiInfo = reserveApiInfoMap.get(apiInfo.getId());
            if (reserveApiInfo != null) apiInfo.merge(reserveApiInfo);
            reserveApiInfoMap.put(apiInfo.getId(), apiInfo);

            for (Integer filterId: filterSampleDataMap.keySet()) {
                FilterSampleData.FilterKey filterKey = new FilterSampleData.FilterKey(apiInfo.getId(), filterId);
                FilterSampleData filterSampleData = filterSampleDataMap.get(filterId);
                FilterSampleData reserveFilterSampleData = reserveFilterSampleDataMap.get(filterKey);
                if (reserveFilterSampleData != null) {
                    filterSampleData.merge(reserveFilterSampleData);
                }

                reserveFilterSampleDataMap.put(filterKey, filterSampleData);
            }
        }

    }

    public PolicyCatalog getApiInfoFromMap(ApiInfo.ApiInfoKey apiInfoKey) {
        ApiInfoCatalog apiInfoCatalog = apiInfoCatalogMap.get(apiInfoKey.getApiCollectionId());
        if (apiInfoCatalog == null) {
            ApiInfoCatalog apiInfoCatalog1 = new ApiInfoCatalog(new HashMap<>(), new HashMap<>(), new ArrayList<>());
            apiInfoCatalogMap.put(apiInfoKey.getApiCollectionId(), apiInfoCatalog1);
            return null;
        }

        Map<URLStatic, PolicyCatalog> strictURLToMethods = apiInfoCatalog.getStrictURLToMethods();
        Map<URLTemplate, PolicyCatalog> templateURLToMethods = apiInfoCatalog.getTemplateURLToMethods();
        URLStatic urlStatic = new URLStatic(apiInfoKey.getUrl(), apiInfoKey.getMethod());
        if (strictURLToMethods.containsKey(urlStatic)) {
            ApiInfo a = strictURLToMethods.get(urlStatic).getApiInfo();
            if (a == null) {
                a = new ApiInfo(apiInfoKey);
                strictURLToMethods.get(urlStatic).setApiInfo(a);
            }
            return strictURLToMethods.get(urlStatic);
        }

        for (URLTemplate urlTemplate: templateURLToMethods.keySet()) {
            if (urlTemplate.match(urlStatic)) {
                ApiInfo a = templateURLToMethods.get(urlTemplate).getApiInfo();
                if (a == null) {
                    a = new ApiInfo(apiInfoKey.getApiCollectionId(), urlTemplate.getTemplateString(), apiInfoKey.getMethod());
                    templateURLToMethods.get(urlTemplate).setApiInfo(a);
                }
                return templateURLToMethods.get(urlTemplate);
            }
        }

        return null;
    }

    public static UpdateReturn getUpdates(Map<Integer, ApiInfoCatalog> apiInfoCatalogMap) {
        List<ApiInfo> apiInfoList = new ArrayList<>();
        List<FilterSampleData> filterSampleDataList = new ArrayList<>();
        List<ApiInfo.ApiInfoKey> deletedApiInfoKeys = new ArrayList<>();
        for (ApiInfoCatalog apiInfoCatalog: apiInfoCatalogMap.values()) {

            for (PolicyCatalog policyCatalog: apiInfoCatalog.getStrictURLToMethods().values()) {
                ApiInfo apiInfo = policyCatalog.getApiInfo();
                if (apiInfo != null) {
                    apiInfoList.add(apiInfo);
                }
                filterSampleDataList.addAll(policyCatalog.getFilterSampleDataMap().values());
            }

            for (PolicyCatalog policyCatalog: apiInfoCatalog.getTemplateURLToMethods().values()) {
                ApiInfo apiInfo = policyCatalog.getApiInfo();
                if (apiInfo != null) {
                    apiInfoList.add(apiInfo);
                }
                filterSampleDataList.addAll(policyCatalog.getFilterSampleDataMap().values());
            }

            deletedApiInfoKeys.addAll(apiInfoCatalog.getDeletedInfo());
        }

        List<WriteModel<ApiInfo>> updatesForApiInfo = getUpdatesForApiInfo(apiInfoList, deletedApiInfoKeys);
        List<WriteModel<FilterSampleData>> updatesForSampleData = getUpdatesForSampleData(filterSampleDataList,deletedApiInfoKeys);

        return new UpdateReturn(updatesForApiInfo, updatesForSampleData);
    }

    public static class UpdateReturn {
        public List<WriteModel<ApiInfo>> updatesForApiInfo;
        public List<WriteModel<FilterSampleData>> updatesForSampleData;

        public UpdateReturn(List<WriteModel<ApiInfo>> updatesForApiInfo, List<WriteModel<FilterSampleData>> updatesForSampleData) {
            this.updatesForApiInfo = updatesForApiInfo;
            this.updatesForSampleData = updatesForSampleData;
        }
    }

    public static List<WriteModel<ApiInfo>> getUpdatesForApiInfo(List<ApiInfo> apiInfoList, List<ApiInfo.ApiInfoKey> deletedApiInfoList) {

        List<WriteModel<ApiInfo>> updates = new ArrayList<>();
        for (ApiInfo apiInfo: apiInfoList) {

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

        for (ApiInfo.ApiInfoKey apiInfoKey: deletedApiInfoList) {
            updates.add(
                    new DeleteOneModel<>(
                            ApiInfoDao.getFilter(apiInfoKey)
                    )
            );
        }


        return updates;
    }

    public static List<WriteModel<FilterSampleData>> getUpdatesForSampleData(List<FilterSampleData> filterSampleDataList, List<ApiInfo.ApiInfoKey> apiInfoRemoveList) {
        ArrayList<WriteModel<FilterSampleData>> bulkUpdates = new ArrayList<>();

        for (FilterSampleData filterSampleData: filterSampleDataList) {
            List<String> sampleData = filterSampleData.getSamples().get();
            Bson bson = Updates.pushEach(FilterSampleData.SAMPLES+".elements", sampleData, new PushOptions().slice(-1 * FilterSampleData.cap));
            bulkUpdates.add(
                    new UpdateOneModel<>(
                            FilterSampleDataDao.getFilter(filterSampleData.getId().getApiInfoKey(), filterSampleData.getId().getFilterId()),
                            bson,
                            new UpdateOptions().upsert(true)
                    )
            );
        }

        for (ApiInfo.ApiInfoKey apiInfoKey: apiInfoRemoveList) {
            bulkUpdates.add(
                    new DeleteManyModel<>(FilterSampleDataDao.getFilterForApiInfoKey(apiInfoKey))
            );
        }

        return bulkUpdates;
    }

    public List<RuntimeFilter> getFilters() {
        return filters;
    }

    public void setFilters(List<RuntimeFilter> filters) {
        this.filters = filters;
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


    public Map<ApiInfo.ApiInfoKey, ApiInfo> getReserveApiInfoMap() {
        return reserveApiInfoMap;
    }

    public void setReserveApiInfoMap(Map<ApiInfo.ApiInfoKey, ApiInfo> reserveApiInfoMap) {
        this.reserveApiInfoMap = reserveApiInfoMap;
    }

    public Map<FilterSampleData.FilterKey, FilterSampleData> getReserveFilterSampleDataMap() {
        return reserveFilterSampleDataMap;
    }

    public void setReserveFilterSampleDataMap(Map<FilterSampleData.FilterKey, FilterSampleData> reserveFilterSampleDataMap) {
        this.reserveFilterSampleDataMap = reserveFilterSampleDataMap;
    }

    public Map<Integer, ApiInfoCatalog> getApiInfoCatalogMap() {
        return apiInfoCatalogMap;
    }

    public void setApiInfoCatalogMap(Map<Integer, ApiInfoCatalog> apiInfoCatalogMap) {
        this.apiInfoCatalogMap = apiInfoCatalogMap;
    }
}
