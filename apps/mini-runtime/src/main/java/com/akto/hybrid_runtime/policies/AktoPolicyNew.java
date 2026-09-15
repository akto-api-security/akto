package com.akto.hybrid_runtime.policies;

import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.ApiCollection.ServiceGraphEdgeInfo;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.akto.dto.traffic.CollectionTags;
import com.akto.runtime.RuntimeUtil;
import com.akto.runtime.policies.*;
import com.akto.runtime.utils.Utils;
import com.akto.util.Constants;
import com.akto.dto.type.APICatalog;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLStatic;
import com.akto.dto.type.URLTemplate;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.hybrid_runtime.APICatalogSync;
import com.akto.hybrid_parsers.HttpCallParser;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;
import org.yaml.snakeyaml.scanner.Constant;

import java.util.*;

import static com.akto.hybrid_runtime.APICatalogSync.createUrlTemplate;

public class AktoPolicyNew {

    private List<RuntimeFilter> filters = new ArrayList<>();
    private Map<Integer, ApiInfoCatalog> apiInfoCatalogMap = new HashMap<>();
    boolean processCalledAtLeastOnce = false;
    ApiAccessTypePolicy apiAccessTypePolicy = new ApiAccessTypePolicy(null, null);
    boolean redact = false;

    boolean mergeUrlsOnVersions = false;

    /*
     * Outbound call graph: which APIs each service calls.
     *
     * On outbound traffic the mirroring daemonset resolves the labels of the pod that MADE the
     * call, so the request carries the caller's service while the URL/method/collection describe
     * the callee. process() already computes the merged (templatised) URL, so the pair is free to
     * capture here - raw URLs would make every path parameter a separate edge.
     *
     * Keyed by callee host because AgentDiscoverGraph registers a node only when the edge key
     * equals targetService; the endpoints themselves go in metadata.endpointUrl, which that
     * component already renders. Accumulated in memory, flushed on the existing sync cycle.
     *
     * callerService -> calleeHost -> set of "METHOD /merged/url"
     */
    private Map<String, Map<String, Set<String>>> outboundEdgesByService = new HashMap<>();

    // Guards against one pathological caller bloating a single document.
    private static final int MAX_CALLEES_PER_SERVICE = 500;
    private static final int MAX_ENDPOINTS_PER_CALLEE = 100;

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
        flushOutboundEdges();
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

        // Detect and set WebSocket connection string endpoints
        boolean isWebSocketConnectionString = Constants.WEBSOCKET_PROTOCOL.equals(httpResponseParams.getType()) 
            && httpResponseParams.getStatusCode() == 101 
            && (httpResponseParams.getPayload() == null || httpResponseParams.getPayload().isEmpty());
        if (isWebSocketConnectionString) {
            apiInfo.setIsConnectionString(true);
            loggerMaker.infoAndAddToDb("Detected WebSocket connection string for: " + apiInfo.getId().toString());
        }

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

        Map<String, String> tagsMap = HttpCallParser.parseTagsMap(httpResponseParams.getTags());

        recordOutboundCall(httpResponseParams, apiInfoKey, tagsMap);

        String contextSource = tagsMap != null ? tagsMap.get(Constants.AI_AGENT_TAG_SOURCE) : null;

        if (CONTEXT_SOURCE.ENDPOINT.name().equals(contextSource)){
            Map<String, List<String>> reqHeaders = httpResponseParams.getRequestParams().getHeaders();
            String skillAgentHeader = RuntimeUtil.getHeaderValue(reqHeaders, "skill-tags");
            if (skillAgentHeader != null) {
                for (String skillAgent: parseSkillAgents(skillAgentHeader)) {
                    addClassifiedTag(apiInfo, "skill-tags", skillAgent);
                }
            }
        }

        if (!CONTEXT_SOURCE.ENDPOINT.name().equals(contextSource) && !CONTEXT_SOURCE.AGENTIC.name().equals(contextSource)) {
            Map<String, List<String>> reqHeaders = httpResponseParams.getRequestParams().getHeaders();
            String ua = RuntimeUtil.getHeaderValue(reqHeaders, "user-agent");
            if (ua != null) {
                addClassifiedTag(apiInfo, "user-agent", UserAgentClassifier.classify(ua).name());
            }

            String referer = RuntimeUtil.getHeaderValue(reqHeaders, "referer");
            addClassifiedTag(apiInfo, "referer", UserAgentClassifier.extractRefererHost(referer));
        }

    }

    private static List<String> parseSkillAgents(String skillAgentHeader) {
        if (skillAgentHeader == null || skillAgentHeader.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> skillAgents = new ArrayList<>();
        for (String skillAgent: skillAgentHeader.split(",")) {
            skillAgent = skillAgent.trim();
            if (!skillAgent.isEmpty()) {
                skillAgents.add(skillAgent);
            }
        }
        return skillAgents;
    }

    private static void addClassifiedTag(ApiInfo apiInfo, String headerKey, String category) {
        if (category == null || category.isEmpty()) return;
        List<CollectionTags> existingTags = apiInfo.getTagsList();
        if (existingTags == null) {
            existingTags = new ArrayList<>();
            apiInfo.setTagsList(existingTags);
        }
        for (CollectionTags tag : existingTags) {
            if (Objects.equals(tag.getKeyName(), headerKey) && Objects.equals(tag.getValue(), category)) {
                return;
            }
        }
        // lastUpdatedTs=0 so the object is stable across runs — MongoDB addEachToSet deduplicates by full equality
        existingTags.add(new CollectionTags(0, headerKey, category, CollectionTags.TagSource.AKTO));
    }

    /**
     * Remembers that the calling service reached this endpoint. apiInfoKey is the CALLEE's
     * collection plus the merged URL, so the recorded endpoint matches a real ApiInfo row.
     */
    private void recordOutboundCall(HttpResponseParams httpResponseParams, ApiInfo.ApiInfoKey apiInfoKey,
            Map<String, String> tagsMap) {
        // Only outbound traffic carries the caller's identity; inbound labels describe the callee.
        if (!HttpCallParser.DIRECTION_OUTBOUND.equals(httpResponseParams.getDirection())) {
            return;
        }
        String callerService = tagsMap == null ? null : tagsMap.get(HttpCallParser.SERVICE_TAG_KEY);
        if (callerService == null || callerService.isEmpty()) {
            return;
        }
        String calleeHost = HttpCallParser.getHeaderValue(httpResponseParams.getRequestParams().getHeaders(), "host");
        if (calleeHost == null || calleeHost.isEmpty()) {
            return;
        }
        calleeHost = calleeHost.toLowerCase().trim();

        Map<String, Set<String>> callees =
                outboundEdgesByService.computeIfAbsent(callerService, k -> new HashMap<>());
        if (!callees.containsKey(calleeHost) && callees.size() >= MAX_CALLEES_PER_SERVICE) {
            return;
        }
        Set<String> endpoints = callees.computeIfAbsent(calleeHost, k -> new HashSet<>());
        if (endpoints.size() < MAX_ENDPOINTS_PER_CALLEE) {
            endpoints.add(apiInfoKey.getMethod().name() + " " + apiInfoKey.getUrl());
        }
    }

    /**
     * Writes the accumulated edges onto each CALLER's collection, once per sync cycle.
     *
     * updateServiceGraphEdges replaces the whole map and ServiceGraphBuilder keeps whichever edge
     * it already has, so neither would grow an endpoint list - the merge has to happen here.
     */
    private void flushOutboundEdges() {
        if (outboundEdgesByService.isEmpty()) {
            return;
        }
        Map<String, Map<String, Set<String>>> snapshot = outboundEdgesByService;
        outboundEdgesByService = new HashMap<>();

        for (Map.Entry<String, Map<String, Set<String>>> entry : snapshot.entrySet()) {
            String callerService = entry.getKey();
            try {
                int callerCollectionId = ApiCollection.generateServiceTagCollectionId(callerService);
                ApiCollection caller = dataActor.fetchApiCollectionMeta(callerCollectionId);
                if (caller == null) {
                    // Caller has no collection of its own - nothing calls it, so its inbound
                    // traffic never created one. Don't materialise an empty collection.
                    loggerMaker.infoAndAddToDb("outbound-graph: no collection for " + callerService
                            + ", skipping " + entry.getValue().size() + " edges");
                    continue;
                }

                Map<String, ServiceGraphEdgeInfo> merged = caller.getServiceGraphEdges() == null
                        ? new HashMap<>()
                        : new HashMap<>(caller.getServiceGraphEdges());

                for (Map.Entry<String, Set<String>> calleeEntry : entry.getValue().entrySet()) {
                    String calleeHost = calleeEntry.getKey();
                    Set<String> endpoints = new TreeSet<>(calleeEntry.getValue());

                    ServiceGraphEdgeInfo existing = merged.get(calleeHost);
                    if (existing != null && existing.getMetadata() != null) {
                        Object prior = existing.getMetadata().get("endpointUrl");
                        if (prior != null) {
                            for (String e : String.valueOf(prior).split(",")) {
                                if (!e.trim().isEmpty()) endpoints.add(e.trim());
                            }
                        }
                    }

                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("endpointUrl", String.join(", ", endpoints));
                    metadata.put("endpointCount", endpoints.size());
                    metadata.put("lastSeen", Context.now());
                    // key == targetService so AgentDiscoverGraph registers this as a node.
                    merged.put(calleeHost, new ServiceGraphEdgeInfo(callerService, calleeHost, metadata));
                }

                dataActor.updateServiceGraphEdges(callerCollectionId, merged);
                loggerMaker.infoAndAddToDb("outbound-graph: " + callerService + " calls "
                        + merged.size() + " hosts (collection " + callerCollectionId + ")");
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "outbound-graph: failed to write edges for " + callerService);
            }
        }
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

    public void removeApiInfo(int apiCollectionId, String url, URLMethods.Method method) {
        ApiInfoCatalog catalog = apiInfoCatalogMap.get(apiCollectionId);
        if (catalog == null) return;
        URLStatic urlStatic = new URLStatic(url, method);
        if (catalog.getStrictURLToMethods().remove(urlStatic) != null) return;
        catalog.getTemplateURLToMethods().keySet().removeIf(t -> t.match(urlStatic));
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

    public boolean isMergeUrlsOnVersions() {
        return mergeUrlsOnVersions;
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


