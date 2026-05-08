package com.akto.action.observe;

import com.akto.action.UserAction;
import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dao.filter.MergedUrlsDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.*;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.CodeAnalysisApiInfo.CodeAnalysisApiInfoKey;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.testing.custom_groups.AllAPIsGroup;
import com.akto.dto.filter.MergedUrls;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.*;
import com.akto.dto.type.URLMethods.Method;
import com.akto.interceptor.CollectionInterceptor;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.usage.UsageMetricCalculator;
import com.akto.listener.InitializerListener;
import com.akto.listener.RuntimeListener;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.APICatalogSync;
import com.akto.runtime.Main;
import com.akto.util.Constants;
import com.akto.util.GroupByTimeRange;
import com.akto.utils.AccountHTTPCallParserAktoPolicyInfo;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.*;
import com.opensymphony.xwork2.Action;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import lombok.Getter;
import lombok.Setter;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

public class InventoryAction extends UserAction {

    int apiCollectionId = -1;

    BasicDBObject response;

    // public String fetchAPICollection() {
    //     List<SingleTypeInfo> list = SingleTypeInfoDao.instance.findAll(Filters.eq("apiCollectionId", apiCollectionId));
    //     response = new BasicDBObject();
    //     response.put("data", new BasicDBObject("name", "Main application").append("endpoints", list));

    //     return Action.SUCCESS.toUpperCase();
    // }

    @Getter
    int notTestedEndpointsCount;

    @Getter
    int onlyOnceTestedEndpointsCount;

    @Setter
    private boolean showUrls;

    @Getter
    private List<ApiInfo> notTestedEndpointsApiInfo = new ArrayList<>();
    @Setter
    private boolean showApiInfo;

    @Getter
    private List<ApiInfo> onlyOnceTestedEndpointsApiInfo = new ArrayList<>();


    private static final LoggerMaker loggerMaker = new LoggerMaker(InventoryAction.class, LogDb.DASHBOARD);

    private String subType;
    private static final int LIMIT = 10_000;
    public List<SingleTypeInfo> fetchSensitiveParams() {
        Bson filterStandardSensitiveParams = SingleTypeInfoDao.instance.filterForSensitiveParamsExcludingUserMarkedSensitive(apiCollectionId, url, method, subType);

        List<SingleTypeInfo> list = SingleTypeInfoDao.instance.findAll(filterStandardSensitiveParams, 0, LIMIT, null, Projections.exclude("values"));
        return list;
    }

    private int startTimestamp = 0;
    private int endTimestamp = 0;

    Set<Integer> deactivatedCollections = UsageMetricCalculator.getDeactivated();

    public List<BasicDBObject> fetchRecentEndpoints(int startTimestamp, int endTimestamp) {
        List<BasicDBObject> recentEndpoints = SingleTypeInfoDao.instance.fetchRecentEndpoints(startTimestamp, endTimestamp, deactivatedCollections);
        return recentEndpoints;
    }

    long newCount = 0;
    long oldCount = 0;
    public String fetchEndpointsCount() {
        if (endTimestamp == 0) endTimestamp = Context.now();

        Set<Integer> demoCollections = new HashSet<>();
        demoCollections.addAll(deactivatedCollections);
        demoCollections.add(RuntimeListener.LLM_API_COLLECTION_ID);
        demoCollections.add(RuntimeListener.VULNERABLE_API_COLLECTION_ID);

        ApiCollection juiceshopCollection = ApiCollectionsDao.instance.findByName("juice_shop_demo");
        if (juiceshopCollection != null) demoCollections.add(juiceshopCollection.getId());

        newCount = SingleTypeInfoDao.instance.fetchEndpointsCount(0, endTimestamp, demoCollections);
        oldCount = SingleTypeInfoDao.instance.fetchEndpointsCount(0, startTimestamp, demoCollections);
        return SUCCESS.toUpperCase();
    }

    public long getNewCount() {
        return newCount;
    }

    public long getOldCount() {
        return oldCount;
    }

    private String hostName;
    private List<BasicDBObject> endpoints;
    public String fetchEndpointsBasedOnHostName() {
        endpoints = new ArrayList<>();
        if (hostName == null) {
            addActionError("Host cannot be null");
            return ERROR.toUpperCase();
        }

        ApiCollection apiCollection = ApiCollectionsDao.instance.findByHost(hostName);

        if (apiCollection == null) {
            addActionError("Invalid host");
            return ERROR.toUpperCase();
        }

        if (deactivatedCollections.contains(apiCollection.getId())) {
            addActionError(CollectionInterceptor.errorMessage);
            return ERROR.toUpperCase();
        }

        List<SingleTypeInfo> singleTypeInfos = Utils.fetchHostSTI(apiCollection.getId(), skip);
        for (SingleTypeInfo singleTypeInfo: singleTypeInfos) {
            BasicDBObject value = new BasicDBObject();
            value.put("url", singleTypeInfo.getUrl());
            value.put("method", singleTypeInfo.getMethod());
            endpoints.add(value);
        }

        return Action.SUCCESS.toUpperCase();
    }

    private Set<ApiInfoKey> listOfEndpointsInCollection;

    public String fetchCollectionWiseApiEndpoints() {
        listOfEndpointsInCollection = new HashSet<>();
        List<BasicDBObject> list = new ArrayList<>();
        if (apiCollectionId == -1) {
            addActionError("API Collection ID cannot be -1");
            return Action.ERROR.toUpperCase();
        }
        ApiCollection apiCollection = ApiCollectionsDao.instance.findOne(
                Filters.eq(Constants.ID, apiCollectionId),
                Projections.include(ApiCollection.HOST_NAME)
        );
        if(apiCollection == null) {
            addActionError("No such collection exists");
            return Action.ERROR.toUpperCase();
        }
        if(apiCollection.getHostName() == null || apiCollection.getHostName().isEmpty()) {
            list = ApiCollectionsDao.fetchEndpointsInCollection(apiCollectionId, skip, Utils.LIMIT, Utils.DELTA_PERIOD_VALUE);
        }else{
            list = ApiCollectionsDao.fetchEndpointsInCollectionUsingHost(apiCollectionId, skip, false);
        }

        if (list != null && !list.isEmpty()) {
            list.forEach(element -> {
                BasicDBObject item = (BasicDBObject) element.get(Constants.ID);
                if (item == null) {
                    return;
                }
                ApiInfoKey apiInfoKey = new ApiInfoKey(
                        item.getInt(ApiInfoKey.API_COLLECTION_ID),
                        item.getString(ApiInfoKey.URL),
                        Method.fromString(item.getString(ApiInfoKey.METHOD)));
                listOfEndpointsInCollection.add(apiInfoKey);
            });
        }
        return SUCCESS.toUpperCase();
    }

    public void attachTagsInAPIList(List<BasicDBObject> list) {
        List<TagConfig> tagConfigs = TagConfigsDao.instance.findAll(new BasicDBObject("active", true));
        for (BasicDBObject singleTypeInfo: list) {
            singleTypeInfo = (BasicDBObject) (singleTypeInfo.getOrDefault("_id", new BasicDBObject()));
            String url = singleTypeInfo.getString("url");
            List<String> tags = new ArrayList<>();
            for(TagConfig tagConfig: tagConfigs) {
                if (tagConfig.getKeyConditions().validate(url)) {
                    tags.add(tagConfig.getName());
                }
            }
            singleTypeInfo.put("tags", tags);
        }
    }

    public void attachAPIInfoListInResponse(List<BasicDBObject> list, int apiCollectionId) {
        response = new BasicDBObject();
        List<ApiInfo> apiInfoList = ApiInfoDao.getApiInfosFromList(list, apiCollectionId);

        response.put("data", new BasicDBObject("endpoints", list).append("apiInfoList", apiInfoList));

        if(apiCollectionId != -1){
            ApiCollection apiCollection = ApiCollectionsDao.instance.findOne(Filters.eq(Constants.ID, apiCollectionId));
            response.put("redacted", apiCollection.getRedact());
        }
    }

    public static String retrievePath(String url) {
        URI uri = URI.create(url);
        String prependPath = uri.getPath();
        if (!prependPath.startsWith("/")) prependPath = "/" + prependPath;
        if (prependPath.endsWith("/")) prependPath = prependPath.substring(0, prependPath.length()-1);
        return prependPath;
    }

    // todo: handle null
    public static Set<String> fetchSwaggerData(List<BasicDBObject> endpoints, OpenAPI openAPI) {
        List<Server> servers = openAPI.getServers();

        List<String> prependPaths = new ArrayList<>();
        if (servers == null) {
            servers = new ArrayList<>();
            servers.add(new Server().url("/"));
        }

        for (Server server: servers) {
            String url = server.getUrl();
            String prependPath = retrievePath(url);
            prependPaths.add(prependPath);
        }

        Paths paths = openAPI.getPaths();

        Set<String> strictPathsSet = new HashSet<>();
        Map<String, PathItem> templatePathsMap = new HashMap<>();
        Set<String> unused = new HashSet<>();

        for(String path: paths.keySet()) {
            PathItem pathItem = paths.get(path);
            if (pathItem == null) continue;

            if (path.contains("{")) {
                for (String prependPath: prependPaths) {
                    String finalUrl = prependPath + path;
                    templatePathsMap.put(finalUrl, pathItem);
                    for (PathItem.HttpMethod pathMethod: pathItem.readOperationsMap().keySet()) {
                        unused.add(finalUrl + " " + pathMethod);
                    }
                }
                continue;
            }

            for (PathItem.HttpMethod operationType: pathItem.readOperationsMap().keySet()) {
                String method = operationType.toString().toUpperCase();
                for (String prependPath: prependPaths) {
                    String finalUrl = prependPath + path;
                    strictPathsSet.add(finalUrl + " " + method);
                    unused.add(finalUrl + " " + method);
                }
            }
        }

        for (BasicDBObject endpoint: endpoints) {
            String endpointUrl = (String) ((BasicDBObject) endpoint.get("_id")).get("url");
            // clean endpoint
            String path = retrievePath(endpointUrl);
            String method = (String) ((BasicDBObject) endpoint.get("_id")).get("method");
            if (!path.startsWith("/")) path = "/" + path;
            // match with strict
            String endpointKey =  path + " " + method;
            if (strictPathsSet.contains(endpointKey)) {
                unused.remove(endpointKey);
                continue;
            }

            String[] r = path.split("/");
            boolean matched = false;
            // if not then loop over templates
            for (String p: templatePathsMap.keySet()) {
                if (matched) break;
                // check if method exists
                Operation operation = templatePathsMap.get(p).readOperationsMap().get(PathItem.HttpMethod.valueOf(method));
                if (operation == null) continue;

                // check if same length
                String[] q = p.split("/");
                if (q.length != r.length) continue;

                // loop over
                boolean flag = true;
                for (int i =0; i < q.length; i ++) {
                    if (Objects.equals(q[i], r[i]) ) continue;
                    if (APICatalog.isTemplateUrl(r[i]) && q[i].contains("{")) continue;

                    flag = false;
                    break;
                }

                if (flag) {
                    unused.remove(p + " " + method);
                    matched = true;
                }

            }

            if (!matched) {
                endpoint.append("shadow",true);
            }
        }


        return unused;
    }

    private void attachCodeAnalysisInResponse(BasicDBObject response){
        BasicDBObject codeAnalysisCollectionInfo = new BasicDBObject();
        ApiCollection apiCollection = ApiCollectionsDao.instance.getMeta(apiCollectionId);
        CodeAnalysisCollection codeAnalysisCollection = null;
        if (apiCollection != null) {
            codeAnalysisCollection = CodeAnalysisCollectionDao.instance.findOne(
                    Filters.eq("name", apiCollection.getName())
            );
        }
        codeAnalysisCollectionInfo.put("codeAnalysisCollection", codeAnalysisCollection);

        // Fetch code analysis endpoints
        Map<String, CodeAnalysisApiInfo> codeAnalysisApisMap = new HashMap<>();
        if (codeAnalysisCollection != null) {
            List<CodeAnalysisApiInfo> codeAnalysisApiInfoList = CodeAnalysisApiInfoDao.instance.findAll(
                    Filters.eq("_id.codeAnalysisCollectionId", codeAnalysisCollection.getId()
                    )
            );

            for(CodeAnalysisApiInfo codeAnalysisApiInfo: codeAnalysisApiInfoList) {
                CodeAnalysisApiInfoKey codeAnalysisApiInfoKey = codeAnalysisApiInfo.getId();
                codeAnalysisApisMap.put(codeAnalysisApiInfoKey.getMethod() + " " + codeAnalysisApiInfoKey.getEndpoint(), codeAnalysisApiInfo);
            }
        }
        codeAnalysisCollectionInfo.put("codeAnalysisApisMap", codeAnalysisApisMap);
        response.put("codeAnalysisCollectionInfo", codeAnalysisCollectionInfo);
    }

    public String fetchCodeAnalysisApiInfos(){
        response = new BasicDBObject();
        attachCodeAnalysisInResponse(response);
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchApiInfosFromSTIs(){
        ApiCollection collection = ApiCollectionsDao.instance.findOne(
                Filters.in(Constants.ID, apiCollectionId),
                Projections.include(ApiCollection.HOST_NAME)
        );
        if(collection == null){
            addActionError("No such collection exists");
            return Action.ERROR.toUpperCase();
        }
        List<BasicDBObject> list = new ArrayList<>();
        if((collection.getHostName() == null || collection.getHostName().isEmpty()) && collection.getId() != AllAPIsGroup.ALL_APIS_GROUP_ID){
            Bson filter = Filters.and(Filters.in(SingleTypeInfo._COLLECTION_IDS, apiCollectionId),
                            Filters.nin(SingleTypeInfo._API_COLLECTION_ID, deactivatedCollections));
            if (collection.getType() != null && !collection.getType().equals(ApiCollection.Type.API_GROUP)) {
                filter = Filters.and(SingleTypeInfoDao.filterForHostHeader(0, false), filter);
            }
            list = ApiCollectionsDao.fetchEndpointsInCollection(filter, 0, -1, Utils.DELTA_PERIOD_VALUE);
        }else{
            list = ApiCollectionsDao.fetchEndpointsInCollectionUsingHost(apiCollectionId, 0, collection.getId() == AllAPIsGroup.ALL_APIS_GROUP_ID);
        }

        response = new BasicDBObject();
        response.put("list", list);
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchApiInfosForCollection(){
        List<ApiInfo> apiInfos = ApiInfoDao.instance.findAll(
                Filters.and(
                        Filters.in(SingleTypeInfo._COLLECTION_IDS, apiCollectionId),
                        Filters.nin(ApiInfo.ID_API_COLLECTION_ID, deactivatedCollections)
                ));
        for(ApiInfo apiInfo: apiInfos){
            apiInfo.calculateActualAuth();
        }
        response = new BasicDBObject();
        response.put("apiInfoList", apiInfos);
        if(apiCollectionId != -1){
            ApiCollection apiCollection = ApiCollectionsDao.instance.findOne(Filters.eq(Constants.ID, apiCollectionId));
            if (apiCollection != null) {
                response.put("redacted", apiCollection.getRedact());
            }
        }
        return Action.SUCCESS.toUpperCase();
    }

    private List<String> urls;
    public String fetchSensitiveParamsForEndpoints() {

        if (urls == null || urls.isEmpty()){
            return Action.SUCCESS.toUpperCase();
        }

        int batchSize = 500;
        List<SingleTypeInfo> list = new ArrayList<>();
        for (int i = 0; i < urls.size(); i += batchSize) {
            List<String> slice = urls.subList(i, Math.min(i+batchSize, urls.size()));
            Bson sensitiveFilters = SingleTypeInfoDao.instance.filterForSensitiveParamsExcludingUserMarkedSensitive(null, null, null, null);
            Bson sensitiveFiltersWithUrls =
                    Filters.and(Filters.in("url", slice), sensitiveFilters);
            List<SingleTypeInfo> sensitiveSTIs = SingleTypeInfoDao.instance.findAll(sensitiveFiltersWithUrls, 0, 2000, null, Projections.exclude("values"));
            list.addAll(sensitiveSTIs);
        }

        response = new BasicDBObject();
        response.put("data", new BasicDBObject("endpoints", list));

        return Action.SUCCESS.toUpperCase();
    }

    public String getAccessTypes() {
        response = new BasicDBObject();
        if (urls == null || urls.size() == 0 ){
            return Action.SUCCESS.toUpperCase();
        }
        Bson filter = Filters.in(ApiInfo.ID_URL, urls);
        List<ApiInfo> apiInfos = ApiInfoDao.instance.findAll(filter, 0, 2000, null, Projections.include(ApiInfo.API_ACCESS_TYPES, ApiInfo.ID_URL));
        response.put("apiInfos", apiInfos);
        return Action.SUCCESS.toUpperCase();
    }

    public String getSummaryInfoForChanges(){
        long countEndpoints = SingleTypeInfoDao.instance.fetchEndpointsCount(startTimestamp, endTimestamp, deactivatedCollections);
        int countSensitiveApis = SingleTypeInfoDao.instance.getSensitiveApisCount(new ArrayList<>(), false, (
                Filters.and(
                        Filters.and(
                                Filters.gte(SingleTypeInfo._TIMESTAMP, startTimestamp),
                                Filters.lte(SingleTypeInfo._TIMESTAMP, endTimestamp)
                        ),
                        Filters.nin(SingleTypeInfo._API_COLLECTION_ID, deactivatedCollections)
                )
        ));

        response = new BasicDBObject();
        response.put("newEndpointsCount", countEndpoints);
        response.put("sensitiveEndpointsCount", countSensitiveApis);

        return Action.SUCCESS.toUpperCase();
    }

    public String loadRecentEndpoints() {
        String regexPattern = getRegexPattern();
        Bson searchFilter = Filters.empty();
        if(!regexPattern.isEmpty() && regexPattern.length() > 0){
            searchFilter = Filters.or(
                    Filters.regex(ApiInfo.ID_URL, regexPattern, "i"),
                    Filters.regex(ApiInfo.ID_METHOD, regexPattern, "i")
            );
        }
        if(skip < 0){
            skip *= -1;
        }

        if(limit < 0){
            limit *= -1;
        }

        int pageLimit = Math.min(limit == 0 ? 50 : limit, 200);
        Bson filter = Filters.and(prepareFilters("API_INFO"), searchFilter);
        try {
            List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            if(collectionIds != null) {
                filter = Filters.and(filter, Filters.in(ApiInfo.COLLECTION_IDS, collectionIds));
            }
        } catch(Exception e){
        }
        List<ApiInfo> list = ApiInfoDao.instance.findAll(filter, skip, pageLimit, Sorts.descending(ApiInfo.DISCOVERED_TIMESTAMP));
        for(ApiInfo apiInfo: list){
            apiInfo.calculateActualAuth();
        }
        long countDocuments = ApiInfoDao.instance.count(filter);
        response = new BasicDBObject();
        response.put("endpoints", list);
        response.put("totalCount", countDocuments);
        return Action.SUCCESS.toUpperCase();
    }

    public String loadSensitiveParameters() {

        List list = fetchSensitiveParams();
        List<Bson> filterCustomSensitiveParams = new ArrayList<>();

        if (subType == null) {
            filterCustomSensitiveParams.add(Filters.eq("sensitive", true));

            if (apiCollectionId != -1) {
                Bson apiCollectionIdFilter = Filters.and(
                        Filters.in(SingleTypeInfo._COLLECTION_IDS, apiCollectionId),
                        Filters.nin(SingleTypeInfo._API_COLLECTION_ID, deactivatedCollections)
                );
                filterCustomSensitiveParams.add(apiCollectionIdFilter);
            }

            if (url != null) {
                Bson urlFilter = Filters.eq("url", url);
                filterCustomSensitiveParams.add(urlFilter);
            }

            if (method != null) {
                Bson methodFilter = Filters.eq("method", method);
                filterCustomSensitiveParams.add(methodFilter);
            }

            List<SensitiveParamInfo> customSensitiveList = SensitiveParamInfoDao.instance.findAll(Filters.and(filterCustomSensitiveParams));
            list.addAll(customSensitiveList);
        }

        response = new BasicDBObject();
        response.put("data", new BasicDBObject("endpoints", list));

        return Action.SUCCESS.toUpperCase();
    }

    private void getResponseForTrendApis(List<Bson> pipeline){
        MongoCursor<BasicDBObject> endpointsCursor = SingleTypeInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();

        List<BasicDBObject> endpoints = new ArrayList<>();
        while(endpointsCursor.hasNext()) {
            endpoints.add(endpointsCursor.next());
        }

        response = new BasicDBObject();
        response.put("data", new BasicDBObject("endpoints", endpoints));
    }

    public String fetchNewEndpointsTrendForHostCollections(){
        List <Integer> nonHostApiCollectionIds = ApiCollectionsDao.instance.fetchNonTrafficApiCollectionsIds();
        nonHostApiCollectionIds.addAll(deactivatedCollections);

        Bson filterQWithTs = SingleTypeInfoDao.instance.getFilterForHostApis(startTimestamp, endTimestamp, deactivatedCollections, nonHostApiCollectionIds);

        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.sort(Sorts.descending(SingleTypeInfo._TIMESTAMP)));
        pipeline.add(Aggregates.match(filterQWithTs));
        try {
            List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            if(collectionIds != null) {
                pipeline.add(Aggregates.match(Filters.in(SingleTypeInfo._COLLECTION_IDS, collectionIds)));
            }
        } catch(Exception e){
        }
        pipeline.addAll(SingleTypeInfoDao.instance.buildPipelineForTrend(InitializerListener.isNotKubernetes()));
        getResponseForTrendApis(pipeline);

        return SUCCESS.toUpperCase();
    }

    public String fetchNewEndpointsTrendForNonHostCollections(){
        List <Integer> nonHostApiCollectionIds = ApiCollectionsDao.instance.fetchNonTrafficApiCollectionsIds();
        Bson nonHostFilterWithTs = Filters.and(
                Filters.in(SingleTypeInfo._API_COLLECTION_ID, nonHostApiCollectionIds),
                Filters.nin(SingleTypeInfo._API_COLLECTION_ID, deactivatedCollections),
                Filters.gte(SingleTypeInfo._TIMESTAMP, startTimestamp),
                Filters.lte(SingleTypeInfo._TIMESTAMP, endTimestamp)
        );
        List<Bson> pipeline = new ArrayList<>();

        BasicDBObject _id =
                new BasicDBObject(SingleTypeInfo._API_COLLECTION_ID, "$" + SingleTypeInfo._API_COLLECTION_ID)
                        .append(SingleTypeInfo._URL, "$" + SingleTypeInfo._URL)
                        .append(SingleTypeInfo._METHOD, "$" +  SingleTypeInfo._METHOD);

        pipeline.add(Aggregates.sort(Sorts.descending(SingleTypeInfo._TIMESTAMP)));
        pipeline.add(Aggregates.match(nonHostFilterWithTs));
        try {
            List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            if(collectionIds != null) {
                pipeline.add(Aggregates.match(Filters.in(SingleTypeInfo._COLLECTION_IDS, collectionIds)));
            }
        } catch(Exception e){
        }

        pipeline.add(Aggregates.group(_id, Accumulators.last(SingleTypeInfo._TIMESTAMP, "$" + SingleTypeInfo._TIMESTAMP)));
        pipeline.addAll(SingleTypeInfoDao.instance.buildPipelineForTrend(InitializerListener.isNotKubernetes()));
        getResponseForTrendApis(pipeline);
        return SUCCESS.toUpperCase();
    }

    public String fetchNewParametersTrend() {
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.sort(Sorts.descending(SingleTypeInfo._TIMESTAMP)));
        pipeline.add(Aggregates.match(Filters.gte("timestamp", startTimestamp)));
        pipeline.add(Aggregates.match(Filters.lte("timestamp", endTimestamp)));
        pipeline.add(Aggregates.match(Filters.nin(SingleTypeInfo._API_COLLECTION_ID, deactivatedCollections)));
        try {
            List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            if(collectionIds != null) {
                pipeline.add(Aggregates.match(Filters.in(SingleTypeInfo._COLLECTION_IDS, collectionIds)));
            }
        } catch(Exception e){
        }
        pipeline.add(Aggregates.limit(100_000));
        pipeline.addAll(SingleTypeInfoDao.instance.buildPipelineForTrend(InitializerListener.isNotKubernetes()));

        getResponseForTrendApis(pipeline);

        return Action.SUCCESS.toUpperCase();

    }

    public List<SingleTypeInfo> fetchAllNewParams(int startTimestamp,int endTimestamp){
        Bson filterNewParams = SingleTypeInfoDao.instance.filterForAllNewParams(startTimestamp,endTimestamp);

        List<SingleTypeInfo> list = SingleTypeInfoDao.instance.findAll(filterNewParams);

        return list;
    }

    public String fetchAllUrlsAndMethods() {
        response = new BasicDBObject();
        BasicDBObject ret = new BasicDBObject();

        APISpec apiSpec = APISpecDao.instance.findById(apiCollectionId);
        if (apiSpec != null) {
            SwaggerParseResult result = new OpenAPIParser().readContents(apiSpec.getContent(), null, null);
            OpenAPI openAPI = result.getOpenAPI();
            Paths paths = openAPI.getPaths();
            for(String path: paths.keySet()) {
                ret.append(path, paths.get(path).readOperationsMap().keySet());
            }
        }

        response.put("data", ret);

        return Action.SUCCESS.toUpperCase();
    }

    private String sortKey;
    private int sortOrder;
    private int limit;
    private int skip;
    private Map<String, List> filters;
    private Map<String, String> filterOperators;
    private boolean sensitive;
    private List<String> request;

    private Bson prepareFilters(String collection) {
        ArrayList<Bson> filterList = new ArrayList<>();
        if(collection.equalsIgnoreCase("STI")){
            filterList.add(Filters.gt("timestamp", startTimestamp));
            filterList.add(Filters.lt("timestamp", endTimestamp));
        }else{
            filterList.add(Filters.gt(ApiInfo.DISCOVERED_TIMESTAMP, startTimestamp));
            filterList.add(Filters.lt(ApiInfo.DISCOVERED_TIMESTAMP, endTimestamp));
        }

        if (sensitive) {
            List<Bson> sensitiveFilters = new ArrayList<>();
            for (String val: request) {
                if (val.equalsIgnoreCase("request")) {
                    List<String> sensitiveInRequest = SingleTypeInfoDao.instance.sensitiveSubTypeInRequestNames();
                    sensitiveInRequest.addAll(SingleTypeInfoDao.instance.sensitiveSubTypeNames());
                    sensitiveFilters.add(Filters.and(
                            Filters.in("subType",sensitiveInRequest),
                            Filters.eq("responseCode", -1)
                    ));
                } else if (val.equalsIgnoreCase("response")) {
                    List<String> sensitiveInResponse = SingleTypeInfoDao.instance.sensitiveSubTypeInResponseNames();
                    sensitiveInResponse.addAll(SingleTypeInfoDao.instance.sensitiveSubTypeNames());
                    sensitiveFilters.add(Filters.and(
                            Filters.in("subType",sensitiveInResponse),
                            Filters.gt("responseCode", -1)
                    ));
                }
            }
            // If any filters were added, combine them with OR
            if (!sensitiveFilters.isEmpty()) {
                filterList.add(Filters.or(sensitiveFilters));
            }
        }

        for(Map.Entry<String, List> entry: filters.entrySet()) {
            String key = entry.getKey();
            List value = entry.getValue();

            if (value.size() == 0) continue;
            String operator = filterOperators.get(key);

            switch (key) {
                case "color": continue;
                case "url":
                case "param":
                    switch (operator) {
                        case "OR":
                        case "AND":
                            filterList.add(Filters.regex(key, ".*"+value.get(0)+".*", "i"));
                            break;
                        case "NOT":
                            filterList.add(Filters.not(Filters.regex(key, ".*"+value.get(0)+".*", "i")));
                            break;
                    }

                    break;
                case "timestamp":
                    List<Long> ll = value;
                    filterList.add(Filters.lte(key, (long) (Context.now()) - ll.get(0) * 86400L));
                    filterList.add(Filters.gte(key, (long) (Context.now()) - ll.get(1) * 86400L));
                    break;
                case "location":
                    boolean isHeader = value.contains("header");
                    boolean isUrlParam = value.contains("urlParam");
                    boolean isPayload = value.contains("payload");
                    boolean isQueryParam = value.contains("queryParam");
                    ArrayList<Bson> locationFilters = new ArrayList<>();
                    if (isHeader) {
                        locationFilters.add(Filters.eq(SingleTypeInfo._IS_HEADER, true));
                    }
                    if (isUrlParam) {
                        locationFilters.add(Filters.eq(SingleTypeInfo._IS_URL_PARAM, true));
                    }
                    if (isPayload) {
                        locationFilters.add(Filters.and(
                                Filters.eq(SingleTypeInfo._IS_HEADER, false),
                                Filters.or(
                                        Filters.exists(SingleTypeInfo._IS_URL_PARAM, false),
                                        Filters.eq(SingleTypeInfo._IS_URL_PARAM, false))));
                    }
                    if (isQueryParam) {
                        locationFilters.add(Filters.eq("isQueryParam", true));
                    }
                    filterList.add(Filters.or(locationFilters));
                    break;
                case "method":
                case "apiCollectionId":
                    String keyForFilter = key;
                    if(collection.equalsIgnoreCase("API_INFO")){
                        keyForFilter = Constants.ID + "." + key;
                    }
                    filterList.add(Filters.in(keyForFilter, value));
                    break;
                case "responseCodes":
                    List<Long> temp = value;
                    if(temp.isEmpty()){
                        break;
                    }
                    long startVal = temp.get(0);
                    long endVal = temp.get(0);

                    for(long t: temp){
                        if(t > endVal){
                            endVal = t;
                        }else if(t < startVal){
                            startVal = t;
                        }
                    }

                    Document query = new Document(ApiInfo.RESPONSE_CODES,
                            new Document("$elemMatch",
                                    new Document("$gte", startVal)
                                            .append("$lte", endVal + 99)
                            )
                    );

                    filterList.add(query);
                    break;
                case "accessType":
                    Document typeQ = new Document(ApiInfo.API_ACCESS_TYPES,
                            new Document("$elemMatch",
                                    new Document("$in", value)
                            )
                    );
                    filterList.add(typeQ);
                    break;
                default:
                    switch (operator) {
                        case "OR":
                        case "AND":
                            filterList.add(Filters.in(key, value));
                            break;

                        case "NOT":
                            filterList.add(Filters.nin(key, value));
                            break;
                    }

            }
        }
        if(collection.equalsIgnoreCase("STI")){
            filterList.add(Filters.nin(SingleTypeInfo._API_COLLECTION_ID, deactivatedCollections));
        }else{
            filterList.add(Filters.nin(ApiInfo.ID_API_COLLECTION_ID, deactivatedCollections));
        }

        try {
            List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            if (collectionIds != null) {
                filterList.add(Filters.in(SingleTypeInfo._COLLECTION_IDS, collectionIds));
            }
        } catch (Exception e) {
        }

        loggerMaker.debugAndAddToDb(filterList.toString(), LogDb.DASHBOARD);
        return Filters.and(filterList);

    }


    public String url;
    public String method;
    public String loadParamsOfEndpoint() {
        Bson filters = Filters.and(
                Filters.in(SingleTypeInfo._COLLECTION_IDS, apiCollectionId),
                Filters.eq("url", url),
                Filters.eq("method", method)
        );

        List<SingleTypeInfo> list = SingleTypeInfoDao.instance.findAll(filters);


        Bson filtersForCodeAnalysisSTIs = Filters.and(
                Filters.eq(SingleTypeInfo._API_COLLECTION_ID, apiCollectionId),
                Filters.eq(SingleTypeInfo._URL, url),
                Filters.eq(SingleTypeInfo._METHOD, method)
        );
        List<SingleTypeInfo> codeAnalysisSTIs = CodeAnalysisSingleTypeInfoDao.instance.findAll(filtersForCodeAnalysisSTIs);
        if (!codeAnalysisSTIs.isEmpty()) {
            list.addAll(codeAnalysisSTIs);
        }

        response = new BasicDBObject();
        response.put("data", new BasicDBObject("params", list));
        return Action.SUCCESS.toUpperCase();
    }

    private String getRegexPattern(){
        if(this.searchString == null || this.searchString.length() < 3){
            return "";
        }
        String escapedPrefix = Utils.escapeSpecialCharacters(this.searchString);
        String regexPattern = ".*" + escapedPrefix + ".*";
        return regexPattern;
    }

    private Bson getSearchFilters(String searchKey){
        String regexPattern = getRegexPattern();
        if(regexPattern == null || regexPattern.isEmpty()){
            return Filters.empty();
        }
        Bson filter = Filters.regex(searchKey, regexPattern, "i");
        return filter;
    }

    private String searchString;
    private List<SingleTypeInfo> getMongoResults(String searchKey) {

        List<String> sortFields = new ArrayList<>();
        sortFields.add(sortKey);

        Bson sort = sortOrder == 1 ? Sorts.ascending(sortFields) : Sorts.descending(sortFields);

        loggerMaker.debugAndAddToDb(String.format("skip: %s, limit: %s, sort: %s", skip, limit, sort), LogDb.DASHBOARD);
        if(skip < 0){
            skip *= -1;
        }

        if(limit < 0){
            limit *= -1;
        }

        int pageLimit = Math.min(limit == 0 ? 50 : limit, 200);

        List<SingleTypeInfo> list = SingleTypeInfoDao.instance.findAll(Filters.and(prepareFilters("STI"), getSearchFilters(searchKey)), skip,pageLimit, sort);
        return list;
    }

    private long getTotalParams(String searchKey) {
        return SingleTypeInfoDao.instance.getMCollection().countDocuments(Filters.and(prepareFilters("STI"), getSearchFilters(searchKey)));
    }

    public String fetchChanges() {
        response = new BasicDBObject();

        long totalParams = getTotalParams(SingleTypeInfo._URL);
        loggerMaker.debugAndAddToDb("Total params: " + totalParams, LogDb.DASHBOARD);

        List<SingleTypeInfo> singleTypeInfos = getMongoResults(SingleTypeInfo._URL);
        loggerMaker.debugAndAddToDb("STI count: " + singleTypeInfos.size(), LogDb.DASHBOARD);

        response.put("data", new BasicDBObject("endpoints", singleTypeInfos ).append("total", totalParams));

        return Action.SUCCESS.toUpperCase();
    }
    public String fetchRecentParams (){

        // ignore time for new params detection {15 days}
        Bson apiInfoFilter = Filters.gte(ApiInfo.DISCOVERED_TIMESTAMP, this.startTimestamp - (Utils.DELTA_PERIOD_VALUE/4));

        long totalCount = ApiInfoDao.instance.estimatedDocumentCount();
        long countApiInfosInvalid = ApiInfoDao.instance.count(apiInfoFilter);

        Bson useFilter = totalCount >= (2 * countApiInfosInvalid) ? apiInfoFilter : Filters.lt(ApiInfo.DISCOVERED_TIMESTAMP, this.startTimestamp - (Utils.DELTA_PERIOD_VALUE/4));
        List<ApiInfo> apiInfos = ApiInfoDao.instance.findAll(useFilter, 0, 2000, Sorts.descending(ApiInfo.DISCOVERED_TIMESTAMP), Projections.include(Constants.ID));
        Set<Integer> uniqueApiCollections = new HashSet<>();
        for(ApiInfo info: apiInfos){
            uniqueApiCollections.add(info.getId().getApiCollectionId());
        }
        Set<String> apiInfosHash = new HashSet<>();
        for(ApiInfo apiInfo: apiInfos){
            apiInfosHash.add(apiInfo.getId().toString());
        }

        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.sort(Sorts.descending(SingleTypeInfo._TIMESTAMP)));
        pipeline.add(Aggregates.match(
                Filters.and(
                        Filters.in(SingleTypeInfo._API_COLLECTION_ID, uniqueApiCollections),
                        Filters.nin(SingleTypeInfo._API_COLLECTION_ID, deactivatedCollections),
                        Filters.gte(SingleTypeInfo._TIMESTAMP, this.startTimestamp),
                        Filters.lte(SingleTypeInfo._TIMESTAMP, this.endTimestamp)
                )
        ));

        try {
            List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            if(collectionIds != null) {
                pipeline.add(Aggregates.match(Filters.in(SingleTypeInfo._COLLECTION_IDS, collectionIds)));
            }
        } catch(Exception e){
        }

        pipeline.add(Aggregates.project(Projections.exclude(SingleTypeInfo._VALUES)));
        pipeline.add(Aggregates.limit(20_000));
        List<SingleTypeInfo> singleTypeInfos = new ArrayList<>();

        MongoCursor<SingleTypeInfo> cursor = SingleTypeInfoDao.instance.getMCollection().aggregate(pipeline, SingleTypeInfo.class).cursor();
        while (cursor.hasNext()) {
            SingleTypeInfo sti = cursor.next();
            ApiInfoKey apiInfoKey = new ApiInfoKey(sti.getApiCollectionId(), sti.getUrl(), Method.fromString(sti.getMethod()));
            if(totalCount >= (2 * countApiInfosInvalid)){
                if(!apiInfosHash.contains(apiInfoKey.toString())){
                    singleTypeInfos.add(sti);
                }
            }else{
                if(apiInfosHash.contains(apiInfoKey.toString())){
                    singleTypeInfos.add(sti);
                }
            }
        }
        response = new BasicDBObject();
        response.put("data", new BasicDBObject("endpoints", singleTypeInfos ));
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchSubTypeCountMap() {
        Map<String,Map<String, Integer>> subTypeCountMap = buildSubTypeCountMap(startTimestamp, endTimestamp);
        response = new BasicDBObject();
        response.put("subTypeCountMap", subTypeCountMap);

        return Action.SUCCESS.toUpperCase();
    }

    public Map<String,Map<String, Integer>> buildSubTypeCountMap(int startTimestamp, int endTimestamp) {

        ArrayList<Bson> filterList = new ArrayList<>();
        filterList.add(Filters.gt("timestamp", startTimestamp));
        filterList.add(Filters.lt("timestamp", endTimestamp));
        filterList.add(Filters.nin(SingleTypeInfo._API_COLLECTION_ID, UsageMetricCalculator.getDeactivated()));

        List<String> sensitiveInRequest = SingleTypeInfoDao.instance.sensitiveSubTypeInRequestNames();
        sensitiveInRequest.addAll(SingleTypeInfoDao.instance.sensitiveSubTypeNames());
        Bson sensitveSubTypeFilterRequest = Filters.in("subType",sensitiveInRequest);
        List<Bson> requestFilterList = new ArrayList<>();
        requestFilterList.add(sensitveSubTypeFilterRequest);
        requestFilterList.addAll(filterList);
        requestFilterList.add(Filters.eq("responseCode", -1));

        List<String> sensitiveInResponse = SingleTypeInfoDao.instance.sensitiveSubTypeInResponseNames();
        sensitiveInResponse.addAll(SingleTypeInfoDao.instance.sensitiveSubTypeNames());
        Bson sensitveSubTypeFilterResponse = Filters.in("subType",sensitiveInResponse);
        List<Bson> responseFilterList = new ArrayList<>();
        responseFilterList.add(sensitveSubTypeFilterResponse);
        responseFilterList.addAll(filterList);
        responseFilterList.add(Filters.gt("responseCode", -1));

        Map<String, Integer> requestResult = SingleTypeInfoDao.instance.execute(requestFilterList);
        Map<String, Integer> responseResult = SingleTypeInfoDao.instance.execute(responseFilterList);

        Map<String, Map<String, Integer>> resultMap = new HashMap<>();
        resultMap.put("REQUEST", requestResult);
        resultMap.put("RESPONSE", responseResult);

        return resultMap;
    }

    public String deMergeApi() {
        if (this.url == null || this.url.length() == 0) {
            addActionError("URL can't be null or empty");
            return ERROR.toUpperCase();
        }

        if (this.method == null || this.method.length() == 0) {
            addActionError("Method can't be null or empty");
            return ERROR.toUpperCase();
        }

        Method urlMethod = null;
        try {
            urlMethod = Method.valueOf(method);
        } catch (Exception e) {
            addActionError("Invalid Method");
            return ERROR.toUpperCase();
        }

        if (!APICatalog.isTemplateUrl(this.url)) {
            addActionError("Only merged URLs can be de-merged");
            return ERROR.toUpperCase();
        }

        try {
            MergedUrlsDao.instance.updateOne(Filters.and(
                    Filters.eq(MergedUrls.URL, url),
                    Filters.eq(MergedUrls.METHOD, method),
                    Filters.eq(MergedUrls.API_COLLECTION_ID, apiCollectionId)
            ), Updates.combine(
                    Updates.set(MergedUrls.URL, url),
                    Updates.set(MergedUrls.METHOD, method),
                    Updates.set(MergedUrls.API_COLLECTION_ID, apiCollectionId)
            ));
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while saving merged url in DB: " + e.getMessage(), LogDb.DASHBOARD);
        }


        SampleData sampleData = SampleDataDao.instance.fetchSampleDataForApi(apiCollectionId, url, urlMethod);
        List<String> samples = sampleData.getSamples();
        loggerMaker.debugAndAddToDb("Found " + samples.size() + " samples for API: " + apiCollectionId + " " + url + method, LogDb.DASHBOARD);

        Bson stiFilter = SingleTypeInfoDao.filterForSTIUsingURL(apiCollectionId, url, urlMethod);
        SingleTypeInfoDao.instance.deleteAll(stiFilter);

        Bson sampleDataFilter = SampleDataDao.filterForSampleData(apiCollectionId, url, urlMethod);
        ApiInfoDao.instance.deleteAll(sampleDataFilter);
        SampleDataDao.instance.deleteAll(sampleDataFilter);
        SensitiveSampleDataDao.instance.deleteAll(sampleDataFilter);
        TrafficInfoDao.instance.deleteAll(sampleDataFilter);

        loggerMaker.debugAndAddToDb("Cleanup done", LogDb.DASHBOARD);

        List<HttpResponseParams> responses = new ArrayList<>();
        for (String sample : samples) {
            try {
                HttpResponseParams httpResponseParams = HttpCallParser.parseKafkaMessage(sample);
                httpResponseParams.requestParams.setApiCollectionId(apiCollectionId);
                responses.add(httpResponseParams);
            } catch (Exception e) {
                loggerMaker.debugAndAddToDb("Error while processing sample message while de-merging : " + e.getMessage(), LogDb.DASHBOARD);
            }
        }

        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        if (accountSettings != null) {
            Map<String, Map<Pattern, String>> apiCollectioNameMapper = accountSettings.convertApiCollectionNameMapperToRegex();
            Main.changeTargetCollection(apiCollectioNameMapper, responses);
        }

        int accountId = Context.accountId.get();

        AccountHTTPCallParserAktoPolicyInfo info = RuntimeListener.accountHTTPParserMap.get(accountId);
        if (info == null) { // account created after docker run
            info = new AccountHTTPCallParserAktoPolicyInfo();
            HttpCallParser callParser = new HttpCallParser("userIdentifier", 1, 1, 1, false);
            info.setHttpCallParser(callParser);
            RuntimeListener.accountHTTPParserMap.put(accountId, info);
        }

        // because changes were made to db and apiCatalogSync doesn't have latest data
        info.getHttpCallParser().apiCatalogSync.buildFromDB(false, false);

        try {
            URLTemplate urlTemplate = APICatalogSync.createUrlTemplate(url, URLMethods.Method.GET);
            if (info.getHttpCallParser().apiCatalogSync.getDbState(apiCollectionId) != null) {
                RequestTemplate requestTemplate = info.getHttpCallParser().apiCatalogSync.getDbState(apiCollectionId).getTemplateURLToMethods().get(urlTemplate);
                loggerMaker.debugAndAddToDb("Request template exists for url " + urlTemplate.getTemplateString() + " : " + ( requestTemplate != null), LogDb.DASHBOARD);
            } else {
                loggerMaker.debugAndAddToDb("Clean dbState for apiCollectionId: " + apiCollectionId,LogDb.DASHBOARD);
            }
        } catch (Exception e) {
            e.printStackTrace();
            loggerMaker.errorAndAddToDb("Error while checking requestTemplate: " + e.getMessage(), LogDb.DASHBOARD);
        }

        loggerMaker.debugAndAddToDb("Processing " + responses.size() + " httpResponseParams for API: " + apiCollectionId + " " + url + method, LogDb.DASHBOARD);

        responses = com.akto.runtime.Main.filterBasedOnHeaders(responses, AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter()));
        loggerMaker.debugAndAddToDb("After filter: Processing " + responses.size() + " httpResponseParams for API: " + apiCollectionId + " " + url + method, LogDb.DASHBOARD);
        try {
            info.getHttpCallParser().syncFunction(responses, true, false, accountSettings);
        } catch (Exception e) {
            addActionError("Error in httpCallParser : " + e.getMessage());
            return ERROR.toUpperCase();
        }

        return SUCCESS.toUpperCase();
    }

    Map<ApiInfoKey, Map<String, Integer>> severityMapForCollection;

    public String getSeveritiesCountPerCollection(){
        if(apiCollectionId == -1){
            return ERROR.toUpperCase();
        }

        if(deactivatedCollections.contains(apiCollectionId)){
            return SUCCESS.toUpperCase();
        }

        Bson filter = Filters.in(SingleTypeInfo._COLLECTION_IDS, apiCollectionId);
        this.severityMapForCollection = TestingRunIssuesDao.instance.getSeveritiesMapForApiInfoKeys(filter, false);
        return SUCCESS.toUpperCase();
    }

    private String description;
    public String saveEndpointDescription() {
        if(description == null) {
            addActionError("No description provided");
            return Action.ERROR.toUpperCase();
        }

        ApiInfoDao.instance.updateOneNoUpsert(Filters.and(
                Filters.eq(ApiInfo.ID_API_COLLECTION_ID, apiCollectionId),
                Filters.eq(ApiInfo.ID_METHOD, method),
                Filters.eq(ApiInfo.ID_URL, url)
        ), Updates.set(ApiInfo.DESCRIPTION, description));

        return SUCCESS.toUpperCase();
    }

    private Set<MergedUrls> mergedUrls;
    public String getDeMergedApis() {
        mergedUrls = MergedUrlsDao.instance.getMergedUrls();
        return SUCCESS.toUpperCase();
    }

    public String undoDemergedApis() {
        for(MergedUrls mergedUrl : mergedUrls) {
            Bson filters = Filters.and(
                    Filters.eq(MergedUrls.API_COLLECTION_ID, mergedUrl.getApiCollectionId()),
                    Filters.eq(MergedUrls.URL, mergedUrl.getUrl()),
                    Filters.eq(MergedUrls.METHOD, mergedUrl.getMethod())
            );
            MergedUrlsDao.instance.getMCollection().deleteOne(filters);
        }
        return SUCCESS.toUpperCase();
    }

    public String fetchNotTestedAPICount() {
        Bson filterQ = UsageMetricCalculator.excludeDemosAndDeactivated(ApiInfo.ID_API_COLLECTION_ID);

        Bson filter = Filters.and(
                filterQ,
                Filters.exists(ApiInfo.LAST_TESTED, false)
        );

        if (this.showApiInfo) {
            this.notTestedEndpointsApiInfo = ApiInfoDao.instance.findAll(filter);
            this.notTestedEndpointsCount = this.notTestedEndpointsApiInfo.size();
        } else {
            this.notTestedEndpointsCount = (int) ApiInfoDao.instance.count(filter);
        }

        return Action.SUCCESS.toUpperCase();
    }


    public String fetchOnlyOnceTestedAPICount() {

        Bson filterQ = UsageMetricCalculator.excludeDemosAndDeactivated(ApiInfo.ID_API_COLLECTION_ID);

        Bson filter = Filters.and(
                filterQ,
                Filters.exists(ApiInfo.LAST_TESTED, true),
                Filters.eq(ApiInfo.TOTAL_TESTED_COUNT, 1)
        );

        if (this.showApiInfo) {
            this.onlyOnceTestedEndpointsApiInfo = ApiInfoDao.instance.findAll(filter);
            this.onlyOnceTestedEndpointsCount = this.onlyOnceTestedEndpointsApiInfo.size();
        } else {
            this.onlyOnceTestedEndpointsCount = (int) ApiInfoDao.instance.count(filter);
        }

        return Action.SUCCESS.toUpperCase();
    }

    public String fetchTestedApisRanges(){
        response = new BasicDBObject();
         try {
            List<Bson> pipeLine = new ArrayList<>();
            pipeLine.add(Aggregates.sort(
                Sorts.descending(ApiInfo.LAST_TESTED)
            ));
            pipeLine.add(
                Aggregates.match(Filters.gt(ApiInfo.LAST_TESTED, 0))
            );

            try {
                List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
                if(collectionIds != null) {
                    pipeLine.add(Aggregates.match(Filters.in(SingleTypeInfo._COLLECTION_IDS, collectionIds)));
                }
            } catch(Exception e){
            }

            GroupByTimeRange.groupByWeek(pipeLine, ApiInfo.LAST_TESTED, "totalApisTested", new BasicDBObject());
            MongoCursor<BasicDBObject> cursor = ApiInfoDao.instance.getMCollection().aggregate(pipeLine, BasicDBObject.class).cursor();
            while (cursor.hasNext()) {
                BasicDBObject document = cursor.next();
                if(document.isEmpty()) continue;
                BasicDBObject id = (BasicDBObject) document.get("_id");
                String key = id.getInt("year") + "_" + id.getInt("weekOfYear");
                response.put(key, document.getInt("totalApisTested"));
            }
            cursor.close();
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        }


        return SUCCESS.toUpperCase();
    }

    public String getSortKey() {
        return this.sortKey;
    }

    public void setSortKey(String sortKey) {
        this.sortKey = sortKey;
    }

    public int getSortOrder() {
        return this.sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public int getLimit() {
        return this.limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getSkip() {
        return this.skip;
    }

    public void setSkip(int skip) {
        this.skip = skip;
    }

    public int getApiCollectionId() {
        return this.apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public BasicDBObject getResponse() {
        return this.response;
    }

    public void setResponse(BasicDBObject response) {
        this.response = response;
    }

    public Map<String,List> getFilters() {
        return this.filters;
    }

    public void setFilters(Map<String,List> filters) {
        this.filters = filters;
    }

    public Map<String,String> getFilterOperators() {
        return this.filterOperators;
    }

    public void setFilterOperators(Map<String,String> filterOperators) {
        this.filterOperators = filterOperators;
    }

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMethod() {
        return this.method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public void setStartTimestamp(int startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public void setEndTimestamp(int endTimestamp) {
        this.endTimestamp = endTimestamp;
    }


    public void setHostName(String hostName) {
        this.hostName = hostName;
    }


    public List<BasicDBObject> getEndpoints() {
        return endpoints;
    }

    public Set<ApiInfoKey> getListOfEndpointsInCollection() {
        return listOfEndpointsInCollection;
    }

    public void setListOfEndpointsInCollection(Set<ApiInfoKey> listOfEndpointsInCollection) {
        this.listOfEndpointsInCollection = listOfEndpointsInCollection;
    }

    public List<String> getUrls() {
        return this.urls;
    }

    public void setUrls(List<String> urls) {
        this.urls = urls;
    }

    public void setSensitive(boolean sensitive) {
        this.sensitive = sensitive;
    }

    public void setRequest(List<String> request) {
        this.request = request;
    }


    public void setSubType(String subType) {
        this.subType = subType;
    }

    public void setSearchString(String searchString) {
        this.searchString = searchString;
    }

    public Map<ApiInfoKey, Map<String, Integer>> getSeverityMapForCollection() {
        return severityMapForCollection;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }


    public Set<MergedUrls> getMergedUrls() {
        return mergedUrls;
    }

    public void setMergedUrls(Set<MergedUrls> mergedUrls) {
        this.mergedUrls = mergedUrls;
    }
}
