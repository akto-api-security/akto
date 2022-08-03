package com.akto.action.observe;

import java.net.URI;
import java.util.*;

import com.akto.action.UserAction;
import com.akto.dao.APISpecDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.SensitiveParamInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.TagConfigsDao;
import com.akto.dao.context.Context;
import com.akto.dto.APISpec;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.SensitiveParamInfo;
import com.akto.dto.TagConfig;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods.Method;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.opensymphony.xwork2.Action;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.servers.Server;
import org.bson.conversions.Bson;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

public class InventoryAction extends UserAction {

    int apiCollectionId = -1;

    BasicDBObject response;

    // public String fetchAPICollection() {
    //     List<SingleTypeInfo> list = SingleTypeInfoDao.instance.findAll(Filters.eq("apiCollectionId", apiCollectionId));
    //     response = new BasicDBObject();
    //     response.put("data", new BasicDBObject("name", "Main application").append("endpoints", list));

    //     return Action.SUCCESS.toUpperCase();
    // }

    public final static int DELTA_PERIOD_VALUE = 60 * 24 * 60 * 60;

    public List<SingleTypeInfo> fetchSensitiveParams() {
        Bson filterStandardSensitiveParams = SingleTypeInfoDao.instance.filterForSensitiveParamsExcludingUserMarkedSensitive(apiCollectionId, url, method);

        List<SingleTypeInfo> list = SingleTypeInfoDao.instance.findAll(filterStandardSensitiveParams);

        return list;        
    }

    private int startTimestamp = 0; 
    private int endTimestamp = 0;

    public List<BasicDBObject> fetchRecentEndpoints(int startTimestamp, int endTimestamp) {
        List<Bson> pipeline = new ArrayList<>();
        BasicDBObject groupedId = 
            new BasicDBObject("apiCollectionId", "$apiCollectionId")
            .append("url", "$url")
            .append("method", "$method");
        pipeline.add(Aggregates.group(groupedId, Accumulators.min("startTs", "$timestamp")));
        pipeline.add(Aggregates.match(Filters.gte("startTs", startTimestamp)));
        pipeline.add(Aggregates.match(Filters.lte("startTs", endTimestamp)));
        pipeline.add(Aggregates.sort(Sorts.descending("startTs")));
        MongoCursor<BasicDBObject> endpointsCursor = SingleTypeInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();

        List<BasicDBObject> endpoints = new ArrayList<>();
        while(endpointsCursor.hasNext()) {
            endpoints.add(endpointsCursor.next());
        }

        return endpoints;
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

    private boolean useHost;
    public List<BasicDBObject> fetchEndpointsInCollectionUsingHost(int apiCollectionId) {

        ApiCollection apiCollection = ApiCollectionsDao.instance.findAll(Filters.eq("_id", apiCollectionId), Projections.exclude("urls")).get(0);

        if (apiCollection.getHostName() == null || apiCollection.getHostName().length() == 0 || !useHost) {
            return fetchEndpointsInCollection(apiCollectionId);
        } else {
            Bson filterQ = Filters.and(Filters.eq("param", "host"), Filters.eq("apiCollectionId", apiCollectionId));
            List<SingleTypeInfo> allUrlsInCollection = SingleTypeInfoDao.instance.findAll(filterQ, skip, LIMIT, null);

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
            apiInfoKeys.add(new ApiInfoKey(singleTypeInfo.getInt("apiCollectionId"),singleTypeInfo.getString("url"), Method.valueOf(singleTypeInfo.getString("method"))));
        }

        BasicDBObject query = new BasicDBObject();
        if (apiCollectionId > -1) {
            query.append("_id.apiCollectionId", apiCollectionId);
        }

        int counter = 0;
        int batchSize = 4;

        List<String> urlsToSearch = new ArrayList<>();
        
        for(ApiInfoKey apiInfoKey: apiInfoKeys) {
            urlsToSearch.add(apiInfoKey.getUrl());
            counter++;
            if (counter % batchSize == 0 || counter == apiInfoKeys.size()) {
                query.append("_id.url", new BasicDBObject("$in", urlsToSearch));
                List<ApiInfo> fromDb = ApiInfoDao.instance.findAll(query);
                for (ApiInfo a: fromDb) {
                    System.out.println("ApiInfo a: " + a);
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
        List<BasicDBObject> list = fetchEndpointsInCollectionUsingHost(apiCollectionId);

        APISpec apiSpec = APISpecDao.instance.findById(apiCollectionId);
        Set<String> unused = null;
        try {
            if (apiSpec != null) {
                SwaggerParseResult result = new OpenAPIParser().readContents(apiSpec.getContent(), null, null);
                OpenAPI openAPI = result.getOpenAPI();
                unused = fetchSwaggerData(list, openAPI);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        attachTagsInAPIList(list);
        attachAPIInfoListInResponse(list, apiCollectionId);

        if (unused == null) {
            unused = new HashSet<>();
        }
        response.put("unusedEndpoints", unused);

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

        filterCustomSensitiveParams.add(Filters.eq("sensitive", true));
        
        if (apiCollectionId >= 0) {
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

        response = new BasicDBObject();
        response.put("data", new BasicDBObject("endpoints", list));

        return Action.SUCCESS.toUpperCase();
    }

    public String fetchNewParametersTrend() {
        List<Bson> pipeline = new ArrayList<>();
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

    private Bson prepareFilters() {
        ArrayList<Bson> filterList = new ArrayList<>();
        filterList.add(Filters.gt("timestamp", startTimestamp));
        filterList.add(Filters.lt("timestamp", endTimestamp));
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

        List<SingleTypeInfo> list = SingleTypeInfoDao.instance.findAll(Filters.and(prepareFilters()), skip, limit, sort);
        return list;        
    }

    private long getTotalParams() {
        return SingleTypeInfoDao.instance.getMCollection().countDocuments(prepareFilters());
    }

    public String fetchChanges() {
        response = new BasicDBObject();
        response.put("data", new BasicDBObject("endpoints", getMongoResults()).append("total", getTotalParams()));

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

    public void setUseHost(boolean useHost) {
        this.useHost = useHost;
    }
}
