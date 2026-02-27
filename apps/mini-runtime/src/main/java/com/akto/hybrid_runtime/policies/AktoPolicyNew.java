package com.akto.hybrid_runtime.policies;

import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.akto.runtime.policies.*;
import com.akto.runtime.utils.Utils;
import com.akto.dto.type.APICatalog;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLStatic;
import com.akto.dto.type.URLTemplate;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.hybrid_runtime.APICatalogSync;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;
import java.util.*;

import static com.akto.hybrid_runtime.APICatalogSync.createUrlTemplate;

public class AktoPolicyNew {

    private List<RuntimeFilter> filters = new ArrayList<>();
    private Map<Integer, ApiInfoCatalog> apiInfoCatalogMap = new HashMap<>();
    boolean processCalledAtLeastOnce = false;
    ApiAccessTypePolicy apiAccessTypePolicy = new ApiAccessTypePolicy(null, null);
    boolean redact = false;

    boolean mergeUrlsOnVersions = false;

    private DataActor dataActor = DataActorFactory.fetchInstance();

    private static final LoggerMaker loggerMaker = new LoggerMaker(AktoPolicyNew.class, LogDb.RUNTIME);

    public void fetchFilters() {
        this.filters = dataActor.fetchRuntimeFilters();
        loggerMaker.infoAndAddToDb("Fetched " + filters.size() + " filters from db");
    }

    public AktoPolicyNew() {
    }

    public void buildFromDb(boolean fetchAllSTI) {
        loggerMaker.infoAndAddToDb("AktoPolicyNew.buildFromDB(), fetchAllSti: " + fetchAllSTI);
        fetchFilters();

        AccountSettings accountSettings = dataActor.fetchAccountSettings();
        int accountId = Context.getActualAccountId();
        redact = accountId == 1718042191;
        if (accountSettings != null) {
            List<String> cidrList = accountSettings.getPrivateCidrList();
            if ( cidrList != null && !cidrList.isEmpty()) {
                apiAccessTypePolicy.setPrivateCidrList(cidrList);
            }
            List<String> partnerIpsList = new ArrayList<>();
            if (accountSettings.getPartnerIpList() != null) {
                partnerIpsList = accountSettings.getPartnerIpList();
            }
            apiAccessTypePolicy.setPartnerIpList(partnerIpsList);
            accountId = accountSettings.getId();
            Context.accountId.set(accountId);
            redact = accountId == 1718042191 || accountSettings.isRedactPayload();
            mergeUrlsOnVersions = accountSettings.isAllowMergingOnVersions();
        }

        apiInfoCatalogMap = new HashMap<>();

        List<ApiInfo> apiInfoList;
        if (fetchAllSTI) {
            apiInfoList = dataActor.fetchApiInfos();
        } else {
            apiInfoList = dataActor.fetchNonTrafficApiInfos();
        }

        List<FilterSampleData> filterSampleDataList = new ArrayList<>(); // FilterSampleDataDao.instance.findAll(new BasicDBObject());

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
                loggerMaker.errorAndAddToDb(e, "Error filling ApiInfo in catalog for apiInfo: " + apiInfo + ". Message: " + e.getMessage());
            }
        }
        loggerMaker.infoAndAddToDb("Built AktoPolicyNew");
    }

    public void syncWithDb() {
        loggerMaker.infoAndAddToDb("Syncing with db");
        List<ApiInfo> apiInfoList = getUpdates(apiInfoCatalogMap);
        loggerMaker.infoAndAddToDb("Writing to db: " + "writesForApiInfoSize="+ apiInfoList.size());
        try {
            if (apiInfoList.size() > 0) {
                loggerMaker.infoAndAddToDb("Writing to db: " + "writesForApiInfoSize="+apiInfoList.size());
                dataActor.bulkWriteApiInfo(apiInfoList);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error writing to db in syncWithDb: " + e.getMessage());
        }

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

    public static ApiInfoKey generateFromHttpResponseParams(HttpResponseParams httpResponseParams, boolean mergeUrlsOnVersions) {
        int apiCollectionId = httpResponseParams.getRequestParams().getApiCollectionId();
        String url = httpResponseParams.getRequestParams().getURL();
        url = url.split("\\?")[0];
        String methodStr = httpResponseParams.getRequestParams().getMethod();
        URLMethods.Method method = URLMethods.Method.fromString(methodStr);
        URLTemplate urlTemplate = APICatalogSync.tryParamteresingUrl(new URLStatic(url, method), mergeUrlsOnVersions);
        if (urlTemplate != null) {
            url = urlTemplate.getTemplateString();
        }
        
        return new ApiInfo.ApiInfoKey(apiCollectionId, url, method);
    }

    public void process(HttpResponseParams httpResponseParams) throws Exception {
        List<CustomAuthType> customAuthTypes = SingleTypeInfo.getCustomAuthType(Integer.parseInt(httpResponseParams.getAccountId()));
        ApiInfo.ApiInfoKey apiInfoKey = generateFromHttpResponseParams(httpResponseParams, mergeUrlsOnVersions);
        PolicyCatalog policyCatalog = getApiInfoFromMap(apiInfoKey);
        policyCatalog.setSeenEarlier(true);
        ApiInfo apiInfo = policyCatalog.getApiInfo();

        if (Utils.printDebugUrlLog(httpResponseParams.getRequestParams().getURL())) {
            loggerMaker.infoAndAddToDb("Found debug url in process " + httpResponseParams.getRequestParams().getURL()
                    + " apiInfoKey: " + apiInfo.getId().toString());
        }


        Map<Integer, FilterSampleData> filterSampleDataMap = policyCatalog.getFilterSampleDataMap();
        if (filterSampleDataMap == null) {
            filterSampleDataMap = new HashMap<>();
            policyCatalog.setFilterSampleDataMap(filterSampleDataMap);
        }

        int statusCode = httpResponseParams.getStatusCode();
        if (!HttpResponseParams.validHttpResponseCode(statusCode)) return; //todo: why?

        for (RuntimeFilter filter: filters) {

            RuntimeFilter.UseCase useCase = filter.getUseCase();
            boolean saveSample = false;
            switch (useCase) {
                case AUTH_TYPE:
                    try {
                        saveSample = AuthPolicy.findAuthType(httpResponseParams, apiInfo, filter, customAuthTypes);
                    } catch (Exception ignored) {}
                    break;
                case SET_CUSTOM_FIELD:
                    try {
                        saveSample = SetFieldPolicy.setField(httpResponseParams, apiInfo, filter);
                    } catch (Exception ignored) {}
                    break;
                case DETERMINE_API_ACCESS_TYPE:
                    try {
                        apiAccessTypePolicy.findApiAccessType(httpResponseParams, apiInfo);
                    } catch (Exception ignored) {}
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

        apiInfo.setLastSeen(httpResponseParams.getTimeOrNow());

        apiInfo.setParentMcpToolNames(httpResponseParams.getParentMcpToolNames());

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

    public static List<ApiInfo> getUpdates(Map<Integer, ApiInfoCatalog> apiInfoCatalogMap) {
        List<ApiInfo> apiInfoList = new ArrayList<>();
        List<FilterSampleData> filterSampleDataList = new ArrayList<>();
        for (ApiInfoCatalog apiInfoCatalog: apiInfoCatalogMap.values()) {

            Map<URLStatic, PolicyCatalog> strictURLToMethods = apiInfoCatalog.getStrictURLToMethods();
            Map<URLTemplate, PolicyCatalog> templateURLToMethods = apiInfoCatalog.getTemplateURLToMethods();

            List<PolicyCatalog> policyCatalogList = new ArrayList<>();
            policyCatalogList.addAll(strictURLToMethods.values());
            policyCatalogList.addAll(templateURLToMethods.values());

            for (PolicyCatalog policyCatalog: policyCatalogList) {
                if (!policyCatalog.isSeenEarlier()) continue;
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

        return apiInfoList;
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
            Set<Set<String>> allAuthTypesFound = apiInfo.getAllAuthTypesFound();
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

            subUpdates.add(Updates.setOnInsert(SingleTypeInfo._COLLECTION_IDS, Arrays.asList(apiInfo.getId().getApiCollectionId())));

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
//        if (filterSampleDataList == null) filterSampleDataList = new ArrayList<>();
//
//        for (FilterSampleData filterSampleData: filterSampleDataList) {
//            List<String> sampleData = filterSampleData.getSamples().get();
//            Bson bson = Updates.pushEach(FilterSampleData.SAMPLES+".elements", sampleData, new PushOptions().slice(-1 * FilterSampleData.cap));
//            bulkUpdates.add(
//                    new UpdateOneModel<>(
//                            FilterSampleDataDao.getFilter(filterSampleData.getId().getApiInfoKey(), filterSampleData.getId().getFilterId()),
//                            bson,
//                            new UpdateOptions().upsert(true)
//                    )
//            );
//        }

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


