package com.akto.runtime.policies;

import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.akto.dto.type.APICatalog;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLStatic;
import com.akto.dto.type.URLTemplate;
import com.akto.runtime.APICatalogSync;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.*;

import static com.akto.runtime.APICatalogSync.createUrlTemplate;

public class AktoPolicyNew {

    private List<RuntimeFilter> filters = new ArrayList<>();
    private Map<Integer, ApiInfoCatalog> apiInfoCatalogMap = new HashMap<>();
    boolean processCalledAtLeastOnce = false;
    ApiAccessTypePolicy apiAccessTypePolicy = new ApiAccessTypePolicy(null);
    boolean redact = false;

    private static final Logger logger = LoggerFactory.getLogger(AktoPolicyNew.class);

    public void fetchFilters() {
        this.filters = RuntimeFilterDao.instance.findAll(new BasicDBObject());
    }

    public AktoPolicyNew(boolean fetchAllSTI) {
        buildFromDb(fetchAllSTI);
    }

    public void buildFromDb(boolean fetchAllSTI) {
        fetchFilters();

        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(new BasicDBObject());
        if (accountSettings != null) {
            List<String> cidrList = accountSettings.getPrivateCidrList();
            if ( cidrList != null && !cidrList.isEmpty()) {
                apiAccessTypePolicy.setPrivateCidrList(cidrList);
            }
            redact = accountSettings.isRedactPayload();
        }

        apiInfoCatalogMap = new HashMap<>();

        List<ApiInfo> apiInfoList;
        if (fetchAllSTI) {
            apiInfoList = ApiInfoDao.instance.findAll(new BasicDBObject());
        } else {
            List<Integer> apiCollectionIds = ApiCollectionsDao.instance.fetchNonTrafficApiCollectionsIds();
            apiInfoList =  ApiInfoDao.instance.findAll(Filters.in("_id.apiCollectionId", apiCollectionIds));
        }

        List<FilterSampleData> filterSampleDataList = FilterSampleDataDao.instance.findAll(new BasicDBObject());

        Map<ApiInfo.ApiInfoKey, Map<Integer, FilterSampleData>> filterSampleDataMapToApiInfo = new HashMap<>();
        for (FilterSampleData filterSampleData: filterSampleDataList) {
            FilterSampleData.FilterKey filterKey = filterSampleData.getId();
            ApiInfo.ApiInfoKey apiInfoKey = filterKey.getApiInfoKey();

            Map<Integer, FilterSampleData> filterSampleDataMap = filterSampleDataMapToApiInfo.getOrDefault(apiInfoKey, new HashMap<>());
            filterSampleDataMap.put(filterKey.filterId, filterSampleData);
            filterSampleDataMapToApiInfo.put(apiInfoKey, filterSampleDataMap);
        }

        for (ApiInfo apiInfo: apiInfoList) {
            try {
                Map<Integer, FilterSampleData> filterSampleDataMap = filterSampleDataMapToApiInfo.get(apiInfo.getId());
                fillApiInfoInCatalog(apiInfo, filterSampleDataMap);
            } catch (Exception e) {
                logger.error(e.getMessage() + " " + e.getCause());
            }
        }

    }

    public void syncWithDb(boolean initialising, boolean fetchAllSTI) {
        // logger.info("Syncing with db");
        if (!initialising) {
            AktoPolicy.UpdateReturn updateReturn = AktoPolicy.getUpdates(apiInfoCatalogMap);
            List<WriteModel<ApiInfo>> writesForApiInfo = updateReturn.updatesForApiInfo;
            List<WriteModel<FilterSampleData>> writesForSampleData = updateReturn.updatesForSampleData;
            logger.info("Writing to db: " + "writesForApiInfoSize="+writesForApiInfo.size() + " writesForSampleData="+ writesForSampleData.size());
            try {
                if (writesForApiInfo.size() > 0) ApiInfoDao.instance.getMCollection().bulkWrite(writesForApiInfo);
                if (!redact && writesForSampleData.size() > 0) FilterSampleDataDao.instance.getMCollection().bulkWrite(writesForSampleData);
            } catch (Exception e) {
                logger.error(e.toString());
            }
        }

        buildFromDb(fetchAllSTI);
    }

    public void fillApiInfoInCatalog(ApiInfo apiInfo,  Map<Integer, FilterSampleData> filterSampleDataMap) {
        ApiInfo.ApiInfoKey apiInfoKey = apiInfo.getId();
        ApiInfoCatalog apiInfoCatalog = apiInfoCatalogMap.get(apiInfoKey.getApiCollectionId());
        if (apiInfoCatalog == null) {
            apiInfoCatalog = new ApiInfoCatalog(new HashMap<>(), new HashMap<>(), new ArrayList<>());
            apiInfoCatalogMap.put(apiInfoKey.getApiCollectionId(), apiInfoCatalog);
        }

        PolicyCatalog policyCatalog = new PolicyCatalog(apiInfo, filterSampleDataMap);

        if (APICatalog.isTemplateUrl(apiInfoKey.url)) {
            URLTemplate urlTemplate = createUrlTemplate(apiInfoKey.url, apiInfoKey.method);
            Map<URLTemplate, PolicyCatalog> templateURLToMethods = apiInfoCatalog.getTemplateURLToMethods();
            templateURLToMethods.putIfAbsent(urlTemplate, policyCatalog);
        } else {
            URLStatic urlStatic = new URLStatic(apiInfoKey.getUrl(), apiInfoKey.getMethod());
            Map<URLStatic, PolicyCatalog> strictURLToMethods = apiInfoCatalog.getStrictURLToMethods();
            strictURLToMethods.putIfAbsent(urlStatic, policyCatalog);
        }

    }

    public void main(List<HttpResponseParams> httpResponseParamsList, boolean syncNow, boolean fetchAllSTI) throws Exception {
        if (httpResponseParamsList == null) httpResponseParamsList = new ArrayList<>();
        for (HttpResponseParams httpResponseParams: httpResponseParamsList) {
            try {
                process(httpResponseParams);
            } catch (Exception e) {
                logger.error(e.toString());
                e.printStackTrace();
            }
        }

        if (syncNow) {
            syncWithDb(false, fetchAllSTI);
        }
    }

    public void process(HttpResponseParams httpResponseParams) throws Exception {
        List<CustomAuthType> customAuthTypes = SingleTypeInfo.activeCustomAuthTypes;
        ApiInfo.ApiInfoKey apiInfoKey = ApiInfo.ApiInfoKey.generateFromHttpResponseParams(httpResponseParams);
        PolicyCatalog policyCatalog = getApiInfoFromMap(apiInfoKey);
        ApiInfo apiInfo = policyCatalog.getApiInfo();

        Map<Integer, FilterSampleData> filterSampleDataMap = policyCatalog.getFilterSampleDataMap();
        if (filterSampleDataMap == null) {
            filterSampleDataMap = new HashMap<>();
            policyCatalog.setFilterSampleDataMap(filterSampleDataMap);
        }

        int statusCode = httpResponseParams.getStatusCode();
        if (!HttpResponseParams.validHttpResponseCode(statusCode)) return; //todo: why?

        for (RuntimeFilter filter: filters) {
            boolean filterResult = filter.process(httpResponseParams);
            if (!filterResult) continue;

            RuntimeFilter.UseCase useCase = filter.getUseCase();
            boolean saveSample = false;
            switch (useCase) {
                case AUTH_TYPE:
                    saveSample = AuthPolicy.findAuthType(httpResponseParams, apiInfo, filter, customAuthTypes);
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

    }

    public PolicyCatalog getApiInfoFromMap(ApiInfo.ApiInfoKey apiInfoKey) {
        ApiInfoCatalog apiInfoCatalog = apiInfoCatalogMap.get(apiInfoKey.getApiCollectionId());
        if (apiInfoCatalog == null) {
            apiInfoCatalog = new ApiInfoCatalog(new HashMap<>(), new HashMap<>(), new ArrayList<>());
            apiInfoCatalogMap.put(apiInfoKey.getApiCollectionId(), apiInfoCatalog);
        }

        Map<URLStatic, PolicyCatalog> strictURLToMethods = apiInfoCatalog.getStrictURLToMethods();
        if (strictURLToMethods == null) {
            strictURLToMethods = new HashMap<>();
            apiInfoCatalog.setStrictURLToMethods(strictURLToMethods);
        }

        Map<URLTemplate, PolicyCatalog> templateURLToMethods = apiInfoCatalog.getTemplateURLToMethods();
        if (templateURLToMethods == null) {
            templateURLToMethods = new HashMap<>();
            apiInfoCatalog.setTemplateURLToMethods(templateURLToMethods);
        }

        URLStatic urlStatic = new URLStatic(apiInfoKey.getUrl(), apiInfoKey.getMethod());
        PolicyCatalog policyCatalog = strictURLToMethods.get(urlStatic);
        if (policyCatalog != null) {
            return policyCatalog;
        }

        for (URLTemplate urlTemplate: templateURLToMethods.keySet()) {
            policyCatalog = templateURLToMethods.get(urlTemplate);
            if (policyCatalog == null) continue;
            if (urlTemplate.match(urlStatic)) {
                ApiInfo a = policyCatalog.getApiInfo();
                if (a == null) {
                    a = new ApiInfo(apiInfoKey.getApiCollectionId(), urlTemplate.getTemplateString(), apiInfoKey.getMethod());
                    policyCatalog.setApiInfo(a);
                }
                return policyCatalog;
            }
        }

        PolicyCatalog newPolicyCatalog = new PolicyCatalog(new ApiInfo(apiInfoKey), new HashMap<>());
        strictURLToMethods.put(urlStatic, newPolicyCatalog);

        return newPolicyCatalog;
    }

    public static AktoPolicy.UpdateReturn getUpdates(Map<Integer, ApiInfoCatalog> apiInfoCatalogMap) {
        List<ApiInfo> apiInfoList = new ArrayList<>();
        List<FilterSampleData> filterSampleDataList = new ArrayList<>();
        for (ApiInfoCatalog apiInfoCatalog: apiInfoCatalogMap.values()) {

            Map<URLStatic, PolicyCatalog> strictURLToMethods = apiInfoCatalog.getStrictURLToMethods();
            Map<URLTemplate, PolicyCatalog> templateURLToMethods = apiInfoCatalog.getTemplateURLToMethods();

            List<PolicyCatalog> policyCatalogList = new ArrayList<>();
            policyCatalogList.addAll(strictURLToMethods.values());
            policyCatalogList.addAll(templateURLToMethods.values());

            for (PolicyCatalog policyCatalog: policyCatalogList) {
                ApiInfo apiInfo = policyCatalog.getApiInfo();
                if (apiInfo != null) {
                    apiInfoList.add(apiInfo);
                }
                Map<Integer, FilterSampleData> filterSampleDataMap = policyCatalog.getFilterSampleDataMap();
                if (filterSampleDataMap != null) {
                    filterSampleDataList.addAll(filterSampleDataMap.values());
                }
            }
        }

        List<WriteModel<ApiInfo>> updatesForApiInfo = getUpdatesForApiInfo(apiInfoList);
        List<WriteModel<FilterSampleData>> updatesForSampleData = getUpdatesForSampleData(filterSampleDataList);

        return new AktoPolicy.UpdateReturn(updatesForApiInfo, updatesForSampleData);
    }

    public static class UpdateReturn {
        public List<WriteModel<ApiInfo>> updatesForApiInfo;
        public List<WriteModel<FilterSampleData>> updatesForSampleData;

        public UpdateReturn(List<WriteModel<ApiInfo>> updatesForApiInfo, List<WriteModel<FilterSampleData>> updatesForSampleData) {
            this.updatesForApiInfo = updatesForApiInfo;
            this.updatesForSampleData = updatesForSampleData;
        }
    }

    public static List<WriteModel<ApiInfo>> getUpdatesForApiInfo(List<ApiInfo> apiInfoList) {

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
            if (violationsMap == null || violationsMap.isEmpty()) {
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

        return updates;
    }

    public static List<WriteModel<FilterSampleData>> getUpdatesForSampleData(List<FilterSampleData> filterSampleDataList) {
        ArrayList<WriteModel<FilterSampleData>> bulkUpdates = new ArrayList<>();
        if (filterSampleDataList == null) filterSampleDataList = new ArrayList<>();

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


    public Map<Integer, ApiInfoCatalog> getApiInfoCatalogMap() {
        return apiInfoCatalogMap;
    }

    public void setApiInfoCatalogMap(Map<Integer, ApiInfoCatalog> apiInfoCatalogMap) {
        this.apiInfoCatalogMap = apiInfoCatalogMap;
    }
}


