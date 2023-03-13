package com.akto.action.observe;

import com.akto.action.UserAction;
import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.testing.EndpointDataFilterCondition;
import com.akto.dto.testing.EndpointDataQuery;
import com.akto.dto.testing.EndpointDataSortCondition;
import com.akto.dto.testing.SingleTypeInfoView;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods.Method;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.mongodb.BasicDBList;
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
import org.bson.conversions.Bson;

import java.net.URI;
import java.util.*;

public class InventoryAction extends UserAction {

    int apiCollectionId = -1;
    int fetchEndpointsLimit = 50;

    BasicDBObject response;

    // public String fetchAPICollection() {
    //     List<SingleTypeInfo> list = SingleTypeInfoDao.instance.findAll(Filters.eq("apiCollectionId", apiCollectionId));
    //     response = new BasicDBObject();
    //     response.put("data", new BasicDBObject("name", "Main application").append("endpoints", list));

    //     return Action.SUCCESS.toUpperCase();
    // }

    public final static int DELTA_PERIOD_VALUE = 60 * 24 * 60 * 60;
    private static final LoggerMaker loggerMaker = new LoggerMaker(InventoryAction.class);

    private String subType;
    public List<SingleTypeInfo> fetchSensitiveParams() {
        Bson filterStandardSensitiveParams = SingleTypeInfoDao.instance.filterForSensitiveParamsExcludingUserMarkedSensitive(apiCollectionId, url, method, subType, null);

        List<SingleTypeInfo> list = SingleTypeInfoDao.instance.findAll(filterStandardSensitiveParams, 0, 1_000, null, Projections.exclude("values"));
        return list;        
    }
    
    public List<SingleTypeInfo> fetchSensitiveParamsForUrls(List<ApiInfoKey> apiInfoList) {

        List<String> urls = new ArrayList<>();
        Set<ApiInfoKey> apiInfoKeySet = new HashSet<ApiInfoKey>();

        for (ApiInfoKey apiInfoKey: apiInfoList) {
            urls.add(apiInfoKey.getUrl());
            apiInfoKeySet.add(new ApiInfoKey(apiInfoKey.getApiCollectionId(), apiInfoKey.getUrl(), apiInfoKey.getMethod()));
        }

        Bson filterStandardSensitiveParams = SingleTypeInfoDao.instance.filterForSensitiveParamsExcludingUserMarkedSensitive(apiCollectionId, url, method, subType, urls);

        List<SingleTypeInfo> list = SingleTypeInfoDao.instance.findAll(filterStandardSensitiveParams, 0, 1_000, null, Projections.exclude("values"));

        List<SingleTypeInfo> filteredList = new ArrayList<>();

        for (SingleTypeInfo singleTypeInfo: list) {
            ApiInfoKey apiInfoKey = new ApiInfoKey(singleTypeInfo.getApiCollectionId(), singleTypeInfo.getUrl(), Method.fromString(singleTypeInfo.getMethod()));
            if (apiInfoKeySet.contains(apiInfoKey)) {
                filteredList.add(singleTypeInfo);
            }
        }

        return list;        
    }

    private int startTimestamp = 0; 
    private int endTimestamp = 0;

    public List<BasicDBObject> fetchRecentEndpoints(int startTimestamp, int endTimestamp) {
        List<BasicDBObject> endpoints = new ArrayList<>();

        Bson hostFilterQ = SingleTypeInfoDao.filterForHostHeader(0, false);
        Bson filterQWithTs = Filters.and(Filters.gte("timestamp", startTimestamp), Filters.lte("timestamp", endTimestamp), hostFilterQ);
        List<SingleTypeInfo> latestHosts = SingleTypeInfoDao.instance.findAll(filterQWithTs, 0, 1_000, Sorts.descending("timestamp"), Projections.exclude("values"));
        for(SingleTypeInfo sti: latestHosts) {
            BasicDBObject id = 
                new BasicDBObject("apiCollectionId", sti.getApiCollectionId())
                .append("url", sti.getUrl())
                .append("method", sti.getMethod());
            BasicDBObject endpoint = 
                new BasicDBObject("_id", id).append("startTs", sti.getTimestamp()).append("count", 1);
            endpoints.add(endpoint);
        }
        
        List <Integer> nonHostApiCollectionIds = ApiCollectionsDao.instance.fetchNonTrafficApiCollectionsIds();

        if (nonHostApiCollectionIds != null && nonHostApiCollectionIds.size() > 0){
            List<Bson> pipeline = new ArrayList<>();

            pipeline.add(Aggregates.match(Filters.in("apiCollectionId", nonHostApiCollectionIds)));
            BasicDBObject groupedId = 
                new BasicDBObject("apiCollectionId", "$apiCollectionId")
                .append("url", "$url")
                .append("method", "$method");
            pipeline.add(Aggregates.group(groupedId, Accumulators.min("startTs", "$timestamp"),Accumulators.sum("countTs",1)));
            pipeline.add(Aggregates.match(Filters.gte("startTs", startTimestamp)));
            pipeline.add(Aggregates.match(Filters.lte("startTs", endTimestamp)));
            pipeline.add(Aggregates.sort(Sorts.descending("startTs")));
            MongoCursor<BasicDBObject> endpointsCursor = SingleTypeInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
            while(endpointsCursor.hasNext()) {
                endpoints.add(endpointsCursor.next());
            }
        }

        return endpoints;
    }

    public List<SingleTypeInfoView> executeEndpointDataQuery(EndpointDataQuery endpointDataQuery) {

        ArrayList<Bson> filterList = new ArrayList<>();
        List<Bson> sorts = new ArrayList<>();
        String key;
        ArrayList<Object> values;
        int sortOrder;
        for (EndpointDataFilterCondition endpointDataFilterCondition: endpointDataQuery.getFilterConditions()) {
            key = endpointDataFilterCondition.getKey();
            values = endpointDataFilterCondition.getValues();

            if (key.equals("apiCollectionId")) {
                if (values.size() > 1) {
                    filterList.add((Filters.in("_id." + key, values)));
                } else if (values.size() == 1) {
                    filterList.add((Filters.eq("_id." + key, ((Long) values.get(0)).intValue())));
                }
            }

            if (key.equals("method")) {
                if (values.size() > 1) {
                    filterList.add((Filters.in("_id." + key, values)));
                } else if (values.size() == 1) {
                    filterList.add((Filters.eq("_id." + key, values.get(0))));
                }
            }

            if (key.equals("url")) {
                filterList.add(Filters.regex("_id." + key, ".*"+values.get(0)+".*"));
            }

            if (key.equals("accessTypes") || key.equals("authTypes") || key.equals("sensitiveParams")) {
                filterList.add((Filters.in(key, values)));
            }

            if (key.equals( "lastSeenTs") || key.equals("discoveredTs")) {
                filterList.add(Filters.eq(key, (int) values.get(0)));
            }
        }

        for (EndpointDataSortCondition endpointDataSortCondition: endpointDataQuery.getSortConditions()) {
            key = endpointDataSortCondition.getKey();
            sortOrder = endpointDataSortCondition.getSortOrder();

            if (key.equals("apiCollectionId") || key.equals("method") || key.equals("url")) {
                key = "_id." + key;
            }

            if (sortOrder == 1) {
                sorts.add(Sorts.ascending(key));
            } else {
                sorts.add(Sorts.descending(key));
            }
        }
        
        Bson sort = Sorts.orderBy(sorts);
        List<SingleTypeInfoView> data = SingleTypeInfoViewDao.instance.findAll(Filters.and(filterList), collectionPage * fetchEndpointsLimit, fetchEndpointsLimit, sort);
        return data;
    }

    public static final int LIMIT = 2000;
    public List<BasicDBObject> fetchEndpointsInCollection(int apiCollectionId) {
        List<Bson> pipeline = new ArrayList<>();
        BasicDBObject groupedId = 
            new BasicDBObject("apiCollectionId", "$apiCollectionId")
            .append("url", "$url")
            .append("method", "$method");
            
        pipeline.add(Aggregates.match(Filters.eq("apiCollectionId", apiCollectionId)));

        int recentEpoch = Context.now() - DELTA_PERIOD_VALUE;

        Bson projections = Projections.fields(
            Projections.include("timestamp", "apiCollectionId", "url", "method"),
            Projections.computed("dayOfYearFloat", new BasicDBObject("$divide", new Object[]{"$timestamp", recentEpoch})),
            Projections.computed("dayOfYear", new BasicDBObject("$trunc", new Object[]{"$dayOfYearFloat", 0}))
        );

        pipeline.add(Aggregates.project(projections));
        pipeline.add(Aggregates.group(groupedId, Accumulators.min("startTs", "$timestamp"), Accumulators.sum("changesCount", 1)));
        pipeline.add(Aggregates.skip(skip));
        pipeline.add(Aggregates.limit(LIMIT));
        pipeline.add(Aggregates.sort(Sorts.descending("startTs")));
        
        MongoCursor<BasicDBObject> endpointsCursor = SingleTypeInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();

        List<BasicDBObject> endpoints = new ArrayList<>();
        while(endpointsCursor.hasNext()) {
            endpoints.add(endpointsCursor.next());
        }

        return endpoints;
    }

    public List<BasicDBObject> fetchEndpointsInCollectionUsingHost(int apiCollectionId) {

        ApiCollection apiCollection = ApiCollectionsDao.instance.getMeta(apiCollectionId);
        
        if (apiCollection.getHostName() == null || apiCollection.getHostName().length() == 0 ) {
            return fetchEndpointsInCollection(apiCollectionId);
        } else {
            List<SingleTypeInfo> allUrlsInCollection = fetchHostSTI(apiCollectionId, skip);

            List<BasicDBObject> endpoints = new ArrayList<>();
            for(SingleTypeInfo singleTypeInfo: allUrlsInCollection) {
                BasicDBObject groupId = new BasicDBObject("apiCollectionId", singleTypeInfo.getApiCollectionId())
                    .append("url", singleTypeInfo.getUrl())
                    .append("method", singleTypeInfo.getMethod());
                endpoints.add(new BasicDBObject("startTs", singleTypeInfo.getTimestamp()).append("_id", groupId));
            }
    
            return endpoints;
        }
    }

    public List<BasicDBObject> fetchEndpoints(int apiCollectionId, int page) {
        Bson filters = Filters.eq("_id.apiCollectionId", apiCollectionId);
        List<SingleTypeInfoView> singleTypeInfos = SingleTypeInfoViewDao.instance.findAll(filters, page * fetchEndpointsLimit, fetchEndpointsLimit, null);

        List<BasicDBObject> endpoints = new ArrayList<>();
        
        for(SingleTypeInfoView singleTypeInfo: singleTypeInfos) {
            BasicDBObject groupId = new BasicDBObject("apiCollectionId", apiCollectionId)
                .append("url", singleTypeInfo.getApiInfoKey().getUrl())
                .append("method", singleTypeInfo.getApiInfoKey().getMethod());
            endpoints.add(new BasicDBObject("startTs", singleTypeInfo.getDiscoveredTs()).append("_id", groupId));

        }
        return endpoints;
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

        List<SingleTypeInfo> singleTypeInfos = fetchHostSTI(apiCollection.getId(), skip);
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
        List<BasicDBObject> list = null;
        if (apiCollectionId > -1) {
            list = fetchEndpointsInCollectionUsingHost(apiCollectionId);
        }
        if (list != null && !list.isEmpty()) {
            list.forEach(element -> {
                BasicDBObject item = (BasicDBObject) element.get(Constants.ID);
                    if (item == null) {
                        return;
                    }
                ApiInfoKey apiInfoKey = new ApiInfoKey(
                        apiCollectionId,
                        item.getString(ApiInfoKey.URL),
                        Method.fromString(item.getString(ApiInfoKey.METHOD)));
                listOfEndpointsInCollection.add(apiInfoKey);
            });
        }
        return SUCCESS.toUpperCase();
    }


    public static List<SingleTypeInfo> fetchHostSTI(int apiCollectionId, int skip) {
        Bson filterQ = SingleTypeInfoDao.filterForHostHeader(apiCollectionId, true);
        return SingleTypeInfoDao.instance.findAll(filterQ, skip,10_000, null);
    }


    private void attachTagsInAPIList(List<BasicDBObject> list) {
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

    private void attachAPIInfoListInResponse(List<BasicDBObject> list, int apiCollectionId) {
        response = new BasicDBObject();
        List<ApiInfo> apiInfoList = new ArrayList<>();

        Set<ApiInfoKey> apiInfoKeys = new HashSet<ApiInfoKey>();
        for (BasicDBObject singleTypeInfo: list) {
            singleTypeInfo = (BasicDBObject) (singleTypeInfo.getOrDefault("_id", new BasicDBObject()));
            apiInfoKeys.add(new ApiInfoKey(singleTypeInfo.getInt("apiCollectionId"),singleTypeInfo.getString("url"), Method.fromString(singleTypeInfo.getString("method"))));
        }

        BasicDBObject query = new BasicDBObject();
        if (apiCollectionId > -1) {
            query.append("_id.apiCollectionId", apiCollectionId);
        }

        int counter = 0;
        int batchSize = 100;

        List<String> urlsToSearch = new ArrayList<>();
        
        for(ApiInfoKey apiInfoKey: apiInfoKeys) {
            urlsToSearch.add(apiInfoKey.getUrl());
            counter++;
            if (counter % batchSize == 0 || counter == apiInfoKeys.size()) {
                query.append("_id.url", new BasicDBObject("$in", urlsToSearch));
                List<ApiInfo> fromDb = ApiInfoDao.instance.findAll(query);
                for (ApiInfo a: fromDb) {
                    if (apiInfoKeys.contains(a.getId())) {
                        a.calculateActualAuth();
                        apiInfoList.add(a);
                    }
                }
                urlsToSearch.clear();
            } 
        }


        response.put("data", new BasicDBObject("endpoints", list).append("apiInfoList", apiInfoList));
    }

    public static String retrievePath(String url) {
        URI uri = URI.create(url);
        String prependPath = uri.getPath();
        if (!prependPath.startsWith("/")) prependPath = "/" + prependPath;
        if (prependPath.endsWith("/")) prependPath = prependPath.substring(0, prependPath.length()-1);
        return prependPath;
    }

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
                    if ((Objects.equals(r[i], "STRING") || Objects.equals(r[i], "INTEGER")) && q[i].contains("{")) continue;

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

    public String fetchAPICollection() {
        List<BasicDBObject> list = fetchEndpoints(apiCollectionId, collectionPage);

        APISpec apiSpec = APISpecDao.instance.findById(apiCollectionId);
        Set<String> unused = null;
        try {
            if (apiSpec != null) {
                SwaggerParseResult result = new OpenAPIParser().readContents(apiSpec.getContent(), null, null);
                OpenAPI openAPI = result.getOpenAPI();
                unused = fetchSwaggerData(list, openAPI);
            }
        } catch (Exception e) {
            ;
        }

        attachTagsInAPIList(list);
        attachAPIInfoListInResponse(list, apiCollectionId);

        if (unused == null) {
            unused = new HashSet<>();
        }
        response.put("unusedEndpoints", unused);

        return Action.SUCCESS.toUpperCase();
    }

    public String fetchEndpointData() {

        endpointDataResponse = executeEndpointDataQuery(endpointDataQuery);
        return Action.SUCCESS.toUpperCase();
    }

    private List<String> urls;
    public String fetchSensitiveParamsForEndpoints() {

        int batchSize = 500;
        List<SingleTypeInfo> list = new ArrayList<>();
        for (int i = 0; i < urls.size(); i += batchSize) {
            List<String> slice = urls.subList(i, Math.min(i+batchSize, urls.size()));
            Bson sensitiveFilters = SingleTypeInfoDao.instance.filterForSensitiveParamsExcludingUserMarkedSensitive(null, null, null, null, null);
            Bson sensitiveFiltersWithUrls = 
                Filters.and(Filters.in("url", slice), sensitiveFilters);
            List<SingleTypeInfo> sensitiveSTIs = SingleTypeInfoDao.instance.findAll(sensitiveFiltersWithUrls, 0, 2000, null, Projections.exclude("values"));
            list.addAll(sensitiveSTIs);
        }

        response = new BasicDBObject();
        response.put("data", new BasicDBObject("endpoints", list));

        return Action.SUCCESS.toUpperCase();
    }

    public String loadRecentEndpoints() {
        List<BasicDBObject> list = fetchRecentEndpoints(startTimestamp, endTimestamp);
        attachTagsInAPIList(list);
        attachAPIInfoListInResponse(list, -1);
        return Action.SUCCESS.toUpperCase();
    }

    public String loadSensitiveParameters() {

        List list = fetchSensitiveParams();
        List<Bson> filterCustomSensitiveParams = new ArrayList<>();

        if (subType == null) {
            filterCustomSensitiveParams.add(Filters.eq("sensitive", true));
            
            if (apiCollectionId != -1) {
                Bson apiCollectionIdFilter = Filters.eq("apiCollectionId", apiCollectionId);
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

    public String fetchSensitiveParameters() {

        List list = fetchSensitiveParamsForUrls(apiInfoList);
        List<Bson> filterCustomSensitiveParams = new ArrayList<>();

        if (subType == null) {
            filterCustomSensitiveParams.add(Filters.eq("sensitive", true));
            
            if (apiCollectionId != -1) {
                Bson apiCollectionIdFilter = Filters.eq("apiCollectionId", apiCollectionId);
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

    public String fetchNewParametersTrend() {
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.limit(100_000));
        pipeline.add(Aggregates.match(Filters.gte("timestamp", startTimestamp)));
        pipeline.add(Aggregates.match(Filters.lte("timestamp", endTimestamp)));
        pipeline.add(Aggregates.project(Projections.computed("dayOfYearFloat", new BasicDBObject("$divide", new Object[]{"$timestamp", 86400}))));
        pipeline.add(Aggregates.project(Projections.computed("dayOfYear", new BasicDBObject("$trunc", new Object[]{"$dayOfYearFloat", 0}))));
        pipeline.add(Aggregates.group("$dayOfYear", Accumulators.sum("count", 1)));

        MongoCursor<BasicDBObject> endpointsCursor = SingleTypeInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();

        List<BasicDBObject> endpoints = new ArrayList<>();
        while(endpointsCursor.hasNext()) {
            endpoints.add(endpointsCursor.next());
        }

        response = new BasicDBObject();
        response.put("data", new BasicDBObject("endpoints", endpoints));

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
    private boolean request;
    private int collectionPage;
    private List<ApiInfoKey> apiInfoList;
    private EndpointDataQuery endpointDataQuery;
    private List<SingleTypeInfoView> endpointDataResponse;

    private Bson prepareFilters() {
        ArrayList<Bson> filterList = new ArrayList<>();
        filterList.add(Filters.gt("timestamp", startTimestamp));
        filterList.add(Filters.lt("timestamp", endTimestamp));

        if (sensitive) {
            Bson sensitveSubTypeFilter;
            if (request) {
                List<String> sensitiveInRequest = SingleTypeInfoDao.instance.sensitiveSubTypeInRequestNames();
                sensitiveInRequest.addAll(SingleTypeInfoDao.instance.sensitiveSubTypeNames());
                sensitveSubTypeFilter = Filters.and(
                        Filters.in("subType",sensitiveInRequest),
                        Filters.eq("responseCode", -1)
                );
            } else {
                List<String> sensitiveInResponse = SingleTypeInfoDao.instance.sensitiveSubTypeInResponseNames();
                sensitiveInResponse.addAll(SingleTypeInfoDao.instance.sensitiveSubTypeNames());
                sensitveSubTypeFilter = Filters.and(
                    Filters.in("subType",sensitiveInResponse),
                    Filters.gt("responseCode", -1)
                );
            }

            filterList.add(sensitveSubTypeFilter);
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

        loggerMaker.infoAndAddToDb(filterList.toString(), LogDb.DASHBOARD);
        return Filters.and(filterList);

    }


    public String url;
    public String method;
    public String loadParamsOfEndpoint() {
        Bson filters = Filters.and(
            Filters.eq("apiCollectionId", apiCollectionId),
            Filters.eq("url", url),  
            Filters.eq("method", method)
        );

        List<SingleTypeInfo> list = SingleTypeInfoDao.instance.findAll(filters);

        response = new BasicDBObject();
        response.put("data", new BasicDBObject("params", list));
        return Action.SUCCESS.toUpperCase();
    }

    private List<SingleTypeInfo> getMongoResults() {

        List<String> sortFields = new ArrayList<>();
        sortFields.add(sortKey);

        Bson sort = sortOrder == 1 ? Sorts.ascending(sortFields) : Sorts.descending(sortFields);

        loggerMaker.infoAndAddToDb(String.format("skip: %s, limit: %s, sort: %s", skip, limit, sort), LogDb.DASHBOARD);
        List<SingleTypeInfo> list = SingleTypeInfoDao.instance.findAll(Filters.and(prepareFilters()), skip, limit, sort);
        return list;        
    }

    private long getTotalParams() {
        return SingleTypeInfoDao.instance.getMCollection().countDocuments(prepareFilters());
    }

    public String fetchChanges() {
        response = new BasicDBObject();

        long totalParams = getTotalParams();
        loggerMaker.infoAndAddToDb("Total params: " + totalParams, LogDb.DASHBOARD);

        List<SingleTypeInfo> singleTypeInfos = getMongoResults();
        loggerMaker.infoAndAddToDb("STI count: " + singleTypeInfos.size(), LogDb.DASHBOARD);

        response.put("data", new BasicDBObject("endpoints", singleTypeInfos ).append("total", totalParams));

        return Action.SUCCESS.toUpperCase();
    }

    public String fetchSubTypeCountMap() {
        Map<String,Map<String, Integer>> subTypeCountMap = SingleTypeInfoDao.instance.buildSubTypeCountMap(startTimestamp, endTimestamp);
        response = new BasicDBObject();
        response.put("subTypeCountMap", subTypeCountMap);

        return Action.SUCCESS.toUpperCase();
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

    public void setRequest(boolean request) {
        this.request = request;
    }

    
    public void setSubType(String subType) {
        this.subType = subType;
    }

    public int getCollectionPage() {
        return this.collectionPage;
    }

    public void setCollectionPage(int collectionPage) {
        this.collectionPage = collectionPage;
    }

    public List<ApiInfoKey> getApiInfoList() {
        return this.apiInfoList;
    }

    public void setApiInfoList(List<ApiInfoKey> apiInfoList) {
        this.apiInfoList = apiInfoList;
    }

    public EndpointDataQuery getEndpointDataQuery() {
        return this.endpointDataQuery;
    }

    public void setEndpointDataQuery(EndpointDataQuery endpointDataQuery) {
        this.endpointDataQuery = endpointDataQuery;
    }

    public List<SingleTypeInfoView> getEndpointDataResponse() {
        return this.endpointDataResponse;
    }

    public void setEndpointDataResponse(List<SingleTypeInfoView> endpointDataResponse) {
        this.endpointDataResponse = endpointDataResponse;
    }

}
