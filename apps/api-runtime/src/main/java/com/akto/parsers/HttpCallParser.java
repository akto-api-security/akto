package com.akto.parsers;

import com.akto.billing.UsageMetricUtils;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dao.traffic_metrics.TrafficMetricsDao;
import com.akto.data_actor.DbLayer;
import com.akto.dependency.DependencyAnalyser;
import com.akto.dto.*;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.SyncLimit;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.monitoring.FilterConfig.FILTER_TYPE;
import com.akto.dto.billing.Organization;
import com.akto.dto.settings.DefaultPayload;
import com.akto.dto.test_editor.ExecutorNode;
import com.akto.dto.testing.custom_groups.AllAPIsGroup;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.traffic.CollectionTags.TagSource;
import com.akto.dto.traffic_metrics.TrafficMetrics;
import com.akto.dto.type.URLMethods.Method;
import com.akto.dto.usage.MetricTypes;
import com.akto.graphql.GraphQLUtils;
import com.akto.imperva.ImpervaUtils;
import com.akto.jsonrpc.JsonRpcUtils;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.mcp.McpRequestResponseUtils;
import com.akto.rest.RestMethodUtils;
import com.akto.runtime.APICatalogSync;
import com.akto.runtime.Main;
import com.akto.runtime.RuntimeUtil;
import com.akto.runtime.URLAggregator;
import com.akto.runtime.utils.Utils;
import com.akto.test_editor.execution.ParseAndExecute;
import com.akto.test_editor.execution.VariableResolver;
import com.akto.test_editor.filter.data_operands_impl.ValidationResult;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.DbMode;
import com.akto.util.Pair;
import com.akto.util.http_util.CoreHTTPClient;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.*;
import okhttp3.*;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.akto.runtime.RuntimeUtil.matchesDefaultPayload;
import static com.akto.testing.Utils.validateFilter;

public class HttpCallParser {
    private final int sync_threshold_count;
    private final int sync_threshold_time;
    private int sync_count = 0;
    private int last_synced;
    private static final LoggerMaker loggerMaker = new LoggerMaker(HttpCallParser.class, LogDb.RUNTIME);
    public APICatalogSync apiCatalogSync;
    public DependencyAnalyser dependencyAnalyser;
    private Map<String, Integer> hostNameToIdMap = new HashMap<>();
    private Map<TrafficMetrics.Key, TrafficMetrics> trafficMetricsMap = new HashMap<>();
    public static final ScheduledExecutorService trafficMetricsExecutor = Executors.newScheduledThreadPool(1);
    private static final String trafficMetricsUrl = "https://logs.akto.io/traffic-metrics";
    private static final OkHttpClient client = CoreHTTPClient.client.newBuilder()
            .writeTimeout(1, TimeUnit.SECONDS)
            .readTimeout(1, TimeUnit.SECONDS)
            .callTimeout(1, TimeUnit.SECONDS)
            .build();

    private Map<Integer, ApiCollection> apiCollectionMap;

    private static final ConcurrentLinkedQueue<BasicDBObject> queue = new ConcurrentLinkedQueue<>();

    public static void init() {
        trafficMetricsExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                while(!queue.isEmpty()) {
                    BasicDBObject metrics = queue.poll();
                    try {
                        sendTrafficMetricsToTelemetry(metrics);
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb("Error while sending traffic_metrics data to prometheus", LogDb.RUNTIME);
                    }
                }
            }
        },0,5,TimeUnit.MINUTES);
    }

    public HttpCallParser(String userIdentifier, int thresh, int sync_threshold_count, int sync_threshold_time, boolean fetchAllSTI, boolean skipMergingOnKnownStaticURLsForVersionedApis){
        this(userIdentifier, thresh, sync_threshold_count, sync_threshold_time, fetchAllSTI);
        apiCatalogSync.setSkipMergingOnKnownStaticURLsForVersionedApis(skipMergingOnKnownStaticURLsForVersionedApis);
    }

    public HttpCallParser(String userIdentifier, int thresh, int sync_threshold_count, int sync_threshold_time, boolean fetchAllSTI) {
        last_synced = 0;
        this.sync_threshold_count = sync_threshold_count;
        this.sync_threshold_time = sync_threshold_time;
        apiCatalogSync = new APICatalogSync(userIdentifier, thresh, fetchAllSTI);
        apiCatalogSync.buildFromDB(false, fetchAllSTI);
        apiCollectionMap = new HashMap<>();
        DbLayer.fetchAllApiCollections()
            .forEach(apiCollection -> apiCollectionMap.put(apiCollection.getId(), apiCollection));

        this.dependencyAnalyser = new DependencyAnalyser(apiCatalogSync.dbState, !Main.isOnprem);
    }

    public static HttpResponseParams parseKafkaMessage(String message) throws Exception {
        try {
            return com.akto.runtime.utils.Utils.parseKafkaMessage(message);
        } catch (Exception e) {
            throw e;
        }
        
    }

    public static String getHeaderValue(Map<String,List<String>> headers, String headerKey) {
        if (headers == null) return null;
        for (String k: headers.keySet()) {
            if (k.equalsIgnoreCase(headerKey)) {
                List<String> hosts = headers.getOrDefault(k, new ArrayList<>());
                if (hosts.size() > 0) return hosts.get(0);
                return null;
            }
        }
        return null;
    }

    public int createCollectionSimple(int vxlanId, boolean isMcpRequest) {
        UpdateOptions updateOptions = new UpdateOptions();
        updateOptions.upsert(true);

        Bson updates = Updates.combine(
            Updates.set(ApiCollection.VXLAN_ID, vxlanId),
            Updates.setOnInsert("startTs", Context.now()),
            Updates.setOnInsert("urls", new HashSet<>())
        );

        updates = getUpdatesIfMcpCollection(isMcpRequest, updates);


        ApiCollectionsDao.instance.getMCollection().updateOne(
                Filters.eq("_id", vxlanId),
                updates,
                updateOptions
        );
        return vxlanId;
    }


    public int createCollectionBasedOnHostName(int id, String host, boolean isMcpRequest)  throws Exception {
        FindOneAndUpdateOptions updateOptions = new FindOneAndUpdateOptions();
        updateOptions.upsert(true);
        updateOptions.returnDocument(ReturnDocument.AFTER);
        // 3 cases
        // 1. If 2 threads are trying to insert same host simultaneously then both will succeed with upsert true
        // 2. If we are trying to insert different host but same id (hashCode collision) then it will fail,
        //    so we loop 20 times till we succeed
        boolean flag = false;
        for (int i=0;i < 100; i++) {
            id += i;
            try {
                Bson updates = Updates.combine(
                    Updates.setOnInsert("_id", id),
                    Updates.setOnInsert("startTs", Context.now()),
                    Updates.setOnInsert("urls", new HashSet<>())
                );

                updates = getUpdatesIfMcpCollection(isMcpRequest, updates);

                ApiCollection createdCollection = ApiCollectionsDao.instance.getMCollection()
                    .findOneAndUpdate(Filters.eq(ApiCollection.HOST_NAME, host), updates, updateOptions);
                if (createdCollection != null) {
                    apiCollectionMap.put(createdCollection.getId(), createdCollection);
                }

                flag = true;
                break;
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error while inserting apiCollection, trying again " + i + " " + e.getMessage(), LogDb.RUNTIME);
            }
        }
        if (flag) { // flag tells if we were successfully able to insert collection
            loggerMaker.infoAndAddToDb("Using collectionId=" + id + " for " + host, LogDb.RUNTIME);
            return id;
        } else {
            throw new Exception("Not able to insert");
        }
    }

    public static FILTER_TYPE isValidResponseParam(HttpResponseParams responseParam, Map<String, FilterConfig> filterMap, Map<String, List<ExecutorNode>> executorNodesMap){
        FILTER_TYPE filterType = FILTER_TYPE.UNCHANGED;
        String message = responseParam.getOrig();
        RawApi rawApi = RawApi.buildFromMessage(message);
        int apiCollectionId = responseParam.requestParams.getApiCollectionId();
        String url = responseParam.getRequestParams().getURL();
        Method method = Method.fromString(responseParam.getRequestParams().getMethod());
        ApiInfoKey apiInfoKey = new ApiInfoKey(apiCollectionId, url, method);
        for (Entry<String, FilterConfig> apiFilterEntry : filterMap.entrySet()) {
            try {
                FilterConfig apiFilter = apiFilterEntry.getValue();
                Map<String, Object> varMap = apiFilter.resolveVarMap();
                VariableResolver.resolveWordList(varMap, new HashMap<ApiInfoKey, List<String>>() {
                    {
                        put(apiInfoKey, Arrays.asList(message));
                    }
                }, apiInfoKey);
                String filterExecutionLogId = UUID.randomUUID().toString();
                ValidationResult res = validateFilter(apiFilter.getFilter().getNode(), rawApi,
                        apiInfoKey, varMap, filterExecutionLogId);
                if (res.getIsValid()) {
                    // handle custom filters here
                    if(apiFilter.getId().equals(FilterConfig.DEFAULT_BLOCK_FILTER)){
                        return FILTER_TYPE.BLOCKED;
                    }

                    // handle execute here
                    List<ExecutorNode> nodes = executorNodesMap.getOrDefault(apiFilter.getId(), new ArrayList<>());
                    if(!nodes.isEmpty()){
                        RawApi modifiedApi = new ParseAndExecute().execute(nodes, rawApi, apiInfoKey, varMap, filterExecutionLogId);
                        responseParam = Utils.convertRawApiToHttpResponseParams(modifiedApi, responseParam);
                        filterType = FILTER_TYPE.MODIFIED;
                    }else{
                        filterType = FILTER_TYPE.ALLOWED;
                    }
                    
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, String.format("Error in httpCallFilter %s", e.toString()));
                filterType = FILTER_TYPE.ERROR;
            }
        }
        return filterType;
    }

    int numberOfSyncs = 0;

    public static Pair<HttpResponseParams,FILTER_TYPE> applyAdvancedFilters(HttpResponseParams responseParams, Map<String, List<ExecutorNode>> executorNodesMap,  Map<String,FilterConfig> filterMap){
        if (filterMap != null && !filterMap.isEmpty()) {
            FILTER_TYPE filterType = isValidResponseParam(responseParams, filterMap, executorNodesMap);
            if(filterType.equals(FILTER_TYPE.BLOCKED)){
                return new Pair<HttpResponseParams,FilterConfig.FILTER_TYPE>(null, FILTER_TYPE.BLOCKED);
            }else{
                return new Pair<HttpResponseParams,FilterConfig.FILTER_TYPE>(responseParams, filterType);
            }
        }
        return new Pair<HttpResponseParams,FilterConfig.FILTER_TYPE>(responseParams, FILTER_TYPE.ERROR);
    }

    public void syncFunction(List<HttpResponseParams> responseParams, boolean syncImmediately, boolean fetchAllSTI, AccountSettings accountSettings)  {
        syncFunction(responseParams, syncImmediately, fetchAllSTI, accountSettings, false);
    }

    public void syncFunction(List<HttpResponseParams> responseParams, boolean syncImmediately, boolean fetchAllSTI, AccountSettings accountSettings, boolean skipAdvancedFilters)  {
        // USE ONLY filteredResponseParams and not responseParams
        List<HttpResponseParams> filteredResponseParams = responseParams;
        if (accountSettings != null && accountSettings.getDefaultPayloads() != null) {
            filteredResponseParams = filterDefaultPayloads(filteredResponseParams, accountSettings.getDefaultPayloads());
        }
        List<String> redundantList = new ArrayList<>();
        if(accountSettings !=null && !accountSettings.getAllowRedundantEndpointsList().isEmpty()){
            redundantList = accountSettings.getAllowRedundantEndpointsList();
        }
        Pattern regexPattern = Utils.createRegexPatternFromList(redundantList);
        boolean shouldIgnoreOptionsApi = true;
        if(accountSettings != null){
            shouldIgnoreOptionsApi = !accountSettings.getAllowOptionsAPIs();
        }
        filteredResponseParams = filterHttpResponseParams(filteredResponseParams, redundantList, regexPattern, shouldIgnoreOptionsApi, skipAdvancedFilters);
        
        boolean makeApisCaseInsensitive = false;
        if(accountSettings != null){
            makeApisCaseInsensitive = accountSettings.getHandleApisCaseInsensitive();
        }

        boolean isHarOrPcap = aggregate(filteredResponseParams, aggregatorMap);

        for (int apiCollectionId: aggregatorMap.keySet()) {
            URLAggregator aggregator = aggregatorMap.get(apiCollectionId);
            apiCatalogSync.computeDelta(aggregator, false, apiCollectionId, makeApisCaseInsensitive);
        }

        if (DbMode.dbType.equals(DbMode.DbType.MONGO_DB)) {
            for (HttpResponseParams responseParam: filteredResponseParams) {
                try{
                    dependencyAnalyser.analyse(responseParam.getOrig(), responseParam.requestParams.getApiCollectionId());
                } catch (Exception e){
                    loggerMaker.errorAndAddToDb(e, "error in analyzing dependency: " + e);
                }
            }
        }

        this.sync_count += filteredResponseParams.size();
        int syncThresh = numberOfSyncs < 10 ? 10000 : sync_threshold_count;
        if (syncImmediately || this.sync_count >= syncThresh || (Context.now() - this.last_synced) > this.sync_threshold_time || isHarOrPcap) {
            apiCollectionMap = new HashMap<>();
            DbLayer.fetchAllApiCollections()
                .forEach(apiCollection -> apiCollectionMap.put(apiCollection.getId(), apiCollection));

            FeatureAccess featureAccess = UsageMetricUtils.getFeatureAccess(Context.accountId.get(), MetricTypes.ACTIVE_ENDPOINTS);
            SyncLimit syncLimit = featureAccess.fetchSyncLimit();

            SyncLimit mcpAssetsSyncLimit = fetchSyncLimit(MetricTypes.MCP_ASSET_COUNT);
            SyncLimit aiAssetsSyncLimit = fetchSyncLimit(MetricTypes.AI_ASSET_COUNT);

            numberOfSyncs++;
            apiCatalogSync.syncWithDB(syncImmediately, fetchAllSTI, syncLimit, mcpAssetsSyncLimit, aiAssetsSyncLimit,
                responseParams.get(0).getSource());
            if (DbMode.dbType.equals(DbMode.DbType.MONGO_DB)) {
                dependencyAnalyser.dbState = apiCatalogSync.dbState;
                dependencyAnalyser.syncWithDb();
            }
            syncTrafficMetricsWithDB();
            this.last_synced = Context.now();
            this.sync_count = 0;
            ApiCollectionUsers.computeCollectionsForCollectionId(Arrays.asList(new AllAPIsGroup()),AllAPIsGroup.ALL_APIS_GROUP_ID);
        }

    }

    private List<HttpResponseParams> filterDefaultPayloads(List<HttpResponseParams> filteredResponseParams, Map<String, DefaultPayload> defaultPayloadMap) {
        List<HttpResponseParams> ret = new ArrayList<>();
        for(HttpResponseParams httpResponseParams: filteredResponseParams) {
            if (matchesDefaultPayload(httpResponseParams, defaultPayloadMap)) continue;

            ret.add(httpResponseParams);
        }

        return ret;
    }

    public void syncTrafficMetricsWithDB() {
        loggerMaker.infoAndAddToDb("Starting syncing traffic metrics", LogDb.RUNTIME);
        try {
            syncTrafficMetricsWithDBHelper();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while updating traffic metrics: " + e.getMessage(), LogDb.RUNTIME);
        } finally {
            trafficMetricsMap = new HashMap<>();
        }
        loggerMaker.infoAndAddToDb("Finished syncing traffic metrics", LogDb.RUNTIME);
    }

    public void syncTrafficMetricsWithDBHelper() {
        ArrayList<WriteModel<TrafficMetrics>> bulkUpdates = new ArrayList<>();
        BasicDBObject metricsData = new BasicDBObject();
        int accountId = Context.accountId.get();
        Organization organization = OrganizationsDao.instance.findOne(Filters.eq(Organization.ACCOUNTS, accountId));
        for (TrafficMetrics trafficMetrics: trafficMetricsMap.values()) {
            TrafficMetrics.Key key = trafficMetrics.getId();
            Map<String, Long> countMap = trafficMetrics.getCountMap();

            if (countMap == null || countMap.isEmpty()) continue;

            Bson updateKey = TrafficMetricsDao.filtersForUpdate(key);
            int count = 0;
            List<Bson> individualUpdates = new ArrayList<>();
            for (String ts: countMap.keySet()) {
                individualUpdates.add(Updates.inc("countMap." + ts, countMap.get(ts)));
                count += countMap.get(ts);
            }

            if (organization != null && Main.isOnprem) {
                String metricKey = key.getName().name() + "|" + key.getIp() + "|" + key.getHost() + "|" + key.getVxlanID() + "|" + organization.getId() + "|" + accountId;
                count += (int) metricsData.getOrDefault(metricKey, 0);
                metricsData.put(metricKey, count);
            }
            Bson update = Updates.combine(individualUpdates);

            UpdateOneModel<TrafficMetrics> updateOneModel = new UpdateOneModel<>(updateKey, update, new UpdateOptions().upsert(true));
            bulkUpdates.add(updateOneModel);
        }

        if (bulkUpdates.size() > 0) {
            if (metricsData.size() != 0 && Main.isOnprem) {
                queue.add(metricsData);
            }
            TrafficMetricsDao.instance.getMCollection().bulkWrite(bulkUpdates);
        }
    }

    private static void sendTrafficMetricsToTelemetry(BasicDBObject metricsData) {
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(new BasicDBObject("data", metricsData).toJson(), mediaType);
        Request request = new Request.Builder()
                .url(trafficMetricsUrl)
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .build();
        Response response = null;
        try {
            response =  client.newCall(request).execute();
        } catch (IOException e) {
            loggerMaker.errorAndAddToDb("Error while executing request " + request.url() + ": " + e.getMessage(), LogDb.RUNTIME);
        } finally {
            if (response != null) {
                response.close();
            }
        }
        if (response!= null && response.isSuccessful()) {
            loggerMaker.infoAndAddToDb("Updated traffic_metrics", LogDb.RUNTIME);
        } else {
            loggerMaker.infoAndAddToDb("Traffic_metrics not sent", LogDb.RUNTIME);
        }
    }

    public static boolean useHostCondition(String hostName, HttpResponseParams.Source source) {
        List<HttpResponseParams.Source> whiteListSource = Arrays.asList(HttpResponseParams.Source.MIRRORING, HttpResponseParams.Source.OTHER, HttpResponseParams.Source.SDK);
        boolean hostNameCondition;
        if (hostName == null) {
            hostNameCondition = false;
        } else {
            hostNameCondition = ! ( hostName.toLowerCase().equals(hostName.toUpperCase()) );
        }
        return whiteListSource.contains(source) &&  hostNameCondition && ApiCollection.useHost;
    }

    public static int getBucketStartEpoch() {
        return Context.now()/(3600*24);
    }

    public static int getBucketEndEpoch() {
        return Context.now()/(3600*24) + 1;
    }

    public static TrafficMetrics.Key getTrafficMetricsKey(HttpResponseParams httpResponseParam, TrafficMetrics.Name name) {
        int bucketStartEpoch = getBucketStartEpoch();
        int bucketEndEpoch = getBucketEndEpoch();

        String hostName = getHeaderValue(httpResponseParam.getRequestParams().getHeaders(), "host");

        if (hostName != null &&  hostName.toLowerCase().equals(hostName.toUpperCase()) ) {
            hostName = "ip-host";
        }

        return new TrafficMetrics.Key(
                httpResponseParam.getSourceIP(), hostName, httpResponseParam.requestParams.getApiCollectionId(),
                name, bucketStartEpoch, bucketEndEpoch
        );
    }

    public void incTrafficMetrics(TrafficMetrics.Key key, int value)  {
        if (trafficMetricsMap == null) trafficMetricsMap = new HashMap<>();
        TrafficMetrics trafficMetrics = trafficMetricsMap.get(key);
        if (trafficMetrics == null) {
            trafficMetrics = new TrafficMetrics(key, new HashMap<>());
            trafficMetricsMap.put(key, trafficMetrics);
        }

        trafficMetrics.inc(value);
    }

    public static final String CONTENT_TYPE = "CONTENT-TYPE";

    public static boolean isRedundantEndpoint(String url, Pattern pattern){
        Matcher matcher = pattern.matcher(url);
        return matcher.matches();
    }

    private boolean isInvalidContentType(String contentType){
        boolean res = false;
        if(contentType == null || contentType.length() == 0) return res;

        res = contentType.contains("javascript") || contentType.contains("png");
        return res;
    }

    public int createApiCollectionId(HttpResponseParams httpResponseParam){
        int apiCollectionId;
        Map<String, List<String>> reqHeaders = httpResponseParam.getRequestParams().getHeaders();
        String hostName = getHeaderValue(reqHeaders, "host");

        if (hostName != null && !hostNameToIdMap.containsKey(hostName) && RuntimeUtil.hasSpecialCharacters(hostName)) {
            hostName = "Special_Char_Host";
        }

        if (StringUtils.isEmpty(hostName)) {
            hostName = getHeaderValue(reqHeaders, "authority");
            if (StringUtils.isEmpty(hostName)) {
                hostName = getHeaderValue(reqHeaders, ":authority");
            }

            if (!StringUtils.isEmpty(hostName)) {
                reqHeaders.put("host", Collections.singletonList(hostName));
            }
        }

        int vxlanId = httpResponseParam.requestParams.getApiCollectionId();

        boolean isMcpRequest = McpRequestResponseUtils.isMcpRequest(httpResponseParam).getFirst();
        if (useHostCondition(hostName, httpResponseParam.getSource())) {
            hostName = hostName.toLowerCase();
            hostName = hostName.trim();

            String key = hostName;

            if (hostNameToIdMap.containsKey(key)) {
                apiCollectionId = hostNameToIdMap.get(key);
                updateMcpServerTag(apiCollectionId, isMcpRequest);
            } else {
                int id = hostName.hashCode();
                try {

                    apiCollectionId = createCollectionBasedOnHostName(id, hostName, isMcpRequest);

                    hostNameToIdMap.put(key, apiCollectionId);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb("Failed to create collection for host : " + hostName, LogDb.RUNTIME);
                    createCollectionSimple(vxlanId, isMcpRequest);
                    hostNameToIdMap.put("null " + vxlanId, vxlanId);
                    apiCollectionId = httpResponseParam.requestParams.getApiCollectionId();
                }
            }

        } else {
            String key = "null" + " " + vxlanId;
            if (!hostNameToIdMap.containsKey(key)) {
                createCollectionSimple(vxlanId, isMcpRequest);
                hostNameToIdMap.put(key, vxlanId);
            }

            apiCollectionId = vxlanId;
        }
        return apiCollectionId;
    }

    private void updateMcpServerTag(int apiCollectionId, boolean isMcpRequest) {
        if (!isMcpRequest) {
            return;
        }

        ApiCollection apiCollection = apiCollectionMap.get(apiCollectionId);
        if (apiCollection == null) {
            return;
        }

        List<CollectionTags> collectionTags = apiCollection.getTagsList();
        boolean isMcpTagPresent = !CollectionUtils.isEmpty(collectionTags) &&
            collectionTags.stream().anyMatch(tag -> Constants.AKTO_MCP_SERVER_TAG.equals(tag.getKeyName()));

        if (isMcpTagPresent) {
            return;
        }

        if (collectionTags == null) {
            collectionTags = new ArrayList<>();
        }

        collectionTags.add(getMcpServerTag());

        ApiCollectionsDao.instance.updateOne(
            Filters.eq(ApiCollection.ID, apiCollection.getId()),
            Updates.set(ApiCollection.TAGS_STRING, collectionTags)
        );
    }

    private boolean isBlankResponseBodyForGET(String method, String contentType, String matchContentType,
            String responseBody) {
        boolean res = true;
        if (contentType == null || contentType.length() == 0)
            return false;
        res &= contentType.contains(matchContentType);
        res &= "GET".equals(method.toUpperCase());

        /*
         * To be sure that the content type
         * header matches the actual payload.
         * 
         * We will need to add more type validation as needed.
         */
        if (matchContentType.contains("html")) {
            res &= responseBody.startsWith("<") && responseBody.endsWith(">");
        } else {
            res &= false;
        }
        return res;
    }

    public List<HttpResponseParams> filterHttpResponseParams(List<HttpResponseParams> httpResponseParamsList, List<String> redundantUrlsList, Pattern pattern, Boolean shouldIgnoreOptionsApi) {
        return filterHttpResponseParams(httpResponseParamsList, redundantUrlsList, pattern, shouldIgnoreOptionsApi,
                false);
    }

    public List<HttpResponseParams> filterHttpResponseParams(List<HttpResponseParams> httpResponseParamsList, List<String> redundantUrlsList, Pattern pattern, Boolean shouldIgnoreOptionsApi, boolean skipAdvancedFilters) {
        List<HttpResponseParams> filteredResponseParams = new ArrayList<>();
        int originalSize = httpResponseParamsList.size();

        // create executor nodes from filters
        Map<String, List<ExecutorNode>> executorNodesMap = ParseAndExecute.createExecutorNodeMap(apiCatalogSync.advancedFilterMap);

        for (HttpResponseParams httpResponseParam: httpResponseParamsList) {

            if (httpResponseParam.getSource().equals(HttpResponseParams.Source.MIRRORING)) {
                TrafficMetrics.Key totalRequestsKey = getTrafficMetricsKey(httpResponseParam, TrafficMetrics.Name.TOTAL_REQUESTS_RUNTIME);
                incTrafficMetrics(totalRequestsKey,1);
            }

            boolean cond = HttpResponseParams.validHttpResponseCode(httpResponseParam.getStatusCode());
            if (httpResponseParam.getSource().equals(HttpResponseParams.Source.POSTMAN) && httpResponseParam.getStatusCode() <= 0) {
                cond = true;
            }

            if (!cond) continue;
            
            String ignoreAktoFlag = getHeaderValue(httpResponseParam.getRequestParams().getHeaders(),Constants.AKTO_IGNORE_FLAG);
            if (ignoreAktoFlag != null) continue;
            
            String method = httpResponseParam.getRequestParams().getMethod();
            if(shouldIgnoreOptionsApi && method != null && method.equalsIgnoreCase("OPTIONS")){
                continue;
            }

            // check for garbage points here
            if(redundantUrlsList != null && !redundantUrlsList.isEmpty()){
                if(isRedundantEndpoint(httpResponseParam.getRequestParams().getURL(), pattern)){
                    continue;
                }
                List<String> contentTypeList = (List<String>) httpResponseParam.getRequestParams().getHeaders().getOrDefault("content-type", new ArrayList<>());
                String contentType = null;
                if(!contentTypeList.isEmpty()){
                    contentType = contentTypeList.get(0);
                }
                if(isInvalidContentType(contentType)){
                    continue;
                }

                try {
                    List<String> responseContentTypeList = (List<String>) httpResponseParam.getHeaders().getOrDefault("content-type", new ArrayList<>());
                    String allContentTypes = responseContentTypeList.toString();
                    
                    String responseBody = httpResponseParam.getPayload();
                    boolean ignore = false;
                    for (String extension : redundantUrlsList) {
                        if(extension.startsWith(CONTENT_TYPE)){
                            String matchContentType = extension.split(" ")[1];
                            if(isBlankResponseBodyForGET(method, allContentTypes, matchContentType, responseBody)){
                                ignore = true;
                                break;
                            }
                        }
                    }
                    if(ignore){
                        continue;
                    }
    
                } catch(Exception e){
                    loggerMaker.errorAndAddToDb(e, "Error while ignoring content-type redundant samples " + e.toString(), LogDb.RUNTIME);
                }

            }

            if (!skipAdvancedFilters) {
                Pair<HttpResponseParams, FILTER_TYPE> temp = applyAdvancedFilters(httpResponseParam, executorNodesMap, apiCatalogSync.advancedFilterMap);
                HttpResponseParams param = temp.getFirst();
                if (param == null || temp.getSecond().equals(FILTER_TYPE.UNCHANGED)) {
                    continue;
                } else {
                    httpResponseParam = param;
                }
            }

            int apiCollectionId = createApiCollectionId(httpResponseParam);

            httpResponseParam.requestParams.setApiCollectionId(apiCollectionId);

            //TODO("Parse JSON in one place for all the parser methods like Rest/GraphQL/JsonRpc")
            List<HttpResponseParams> responseParamsList = GraphQLUtils.getUtils().parseGraphqlResponseParam(httpResponseParam);
            if (responseParamsList.isEmpty()) {
                // Check for REST method payload structure (only for account 1758525547)
                if (Context.accountId.get() == 1758525547 || Context.accountId.get() == 1667235738) {
                    List<HttpResponseParams> restMethodResponseParams = RestMethodUtils.getUtils().parseRestMethodResponseParam(httpResponseParam);
                    if (!restMethodResponseParams.isEmpty()) {
                        filteredResponseParams.addAll(restMethodResponseParams);
                        loggerMaker.infoAndAddToDb("Adding " + restMethodResponseParams.size() + " new REST method endpoints in inventory", LogDb.RUNTIME);
                    } else {
                        HttpResponseParams jsonRpcResponse = JsonRpcUtils.parseJsonRpcResponse(httpResponseParam);
                        HttpResponseParams mcpResponseParams = McpRequestResponseUtils.parseMcpResponseParams(jsonRpcResponse);
                        if (mcpResponseParams != null) {
                            filteredResponseParams.add(mcpResponseParams);
                        } else {
                            List<HttpResponseParams> impervaResponseParamsList = ImpervaUtils.parseImpervaResponse(httpResponseParam);
                            if (impervaResponseParamsList.isEmpty()) {
                                filteredResponseParams.add(httpResponseParam);
                            } else {
                                filteredResponseParams.addAll(impervaResponseParamsList);
                                loggerMaker.infoAndAddToDb("Added " + impervaResponseParamsList.size() + " new imperva endpoints in inventory", LogDb.RUNTIME);
                            }
                        }
                    }
                } else {
                    if (JsonRpcUtils.isJsonRpcRequest(httpResponseParam)) {
                        HttpResponseParams jsonRpcResponse = JsonRpcUtils.parseJsonRpcResponse(httpResponseParam);
                        HttpResponseParams mcpResponseParams = McpRequestResponseUtils.parseMcpResponseParams(jsonRpcResponse);
                        if (mcpResponseParams != null) {
                            filteredResponseParams.add(mcpResponseParams);
                        }
                    } else {
                        List<HttpResponseParams> impervaResponseParamsList = ImpervaUtils.parseImpervaResponse(httpResponseParam);
                        if (impervaResponseParamsList.isEmpty()) {
                            filteredResponseParams.add(httpResponseParam);
                        } else {
                            filteredResponseParams.addAll(impervaResponseParamsList);
                            loggerMaker.infoAndAddToDb("Added " + impervaResponseParamsList.size() + " new imperva endpoints in inventory", LogDb.RUNTIME);
                        }
                    }
                }
            } else {
                filteredResponseParams.addAll(responseParamsList);
                loggerMaker.infoAndAddToDb("Adding " + responseParamsList.size() + " new graphql endpoints in inventory", LogDb.RUNTIME);
            }

            if (httpResponseParam.getSource().equals(HttpResponseParams.Source.MIRRORING)) {
                TrafficMetrics.Key processedRequestsKey = getTrafficMetricsKey(httpResponseParam, TrafficMetrics.Name.FILTERED_REQUESTS_RUNTIME);
                incTrafficMetrics(processedRequestsKey,1);
            }

        }

        Set<Integer> deactivatedCollections = UsageMetricCalculator.getDeactivated();
        filteredResponseParams.removeIf((temp) -> {
            int apiCollectionId = temp.getRequestParams().getApiCollectionId();
            return deactivatedCollections.contains(apiCollectionId);
        });

        int filteredSize = filteredResponseParams.size();
        loggerMaker.debugInfoAddToDb("Filtered " + (originalSize - filteredSize) + " responses", LogDb.RUNTIME);
        return filteredResponseParams;
    }

    private Map<Integer, URLAggregator> aggregatorMap = new HashMap<>();

    public void setAggregatorMap(Map<Integer, URLAggregator> aggregatorMap){
        this.aggregatorMap=aggregatorMap;
    }

    public Map<Integer, URLAggregator> getAggregatorMap(){
        return this.aggregatorMap;
    }

    public static boolean aggregate(List<HttpResponseParams> responses, Map<Integer, URLAggregator> aggregatorMap) {
        int count = 0;
        boolean ret = false;
        Set<String> urlSet= new HashSet<>();
        for (HttpResponseParams responseParams: responses) {
            if (responseParams.getSource() == HttpResponseParams.Source.HAR || responseParams.getSource() == HttpResponseParams.Source.PCAP) {
                ret = true;
            }

            HttpRequestParams requestParams = responseParams.requestParams;
            if (requestParams != null) {
                String path = requestParams.getMethod() + " " + requestParams.url;
                if (urlSet.size() < 50) {
                    urlSet.add(path);
                }
            }

            try {
                int collId = responseParams.getRequestParams().getApiCollectionId();
                URLAggregator aggregator = aggregatorMap.get(collId);
                if (aggregator == null) {
                    aggregator = new URLAggregator();
                    aggregatorMap.put(collId, aggregator);
                }

                aggregator.addURL(responseParams);
                count++;
            } catch (Exception  e) {
            }
        }

        loggerMaker.debugInfoAddToDb("URLs: " + urlSet.toString(), LogDb.RUNTIME);
        loggerMaker.infoAndAddToDb("added " + count + " urls", LogDb.RUNTIME);
        return ret;
    }

    public int getLastSyncTime() {
        return this.last_synced;
    }

    public int getSyncCount() {
        return this.sync_count;
    }

    public Map<String, Integer> getHostNameToIdMap() {
        return hostNameToIdMap;
    }

    public void setHostNameToIdMap(Map<String, Integer> hostNameToIdMap) {
        this.hostNameToIdMap = hostNameToIdMap;
    }

    public void setTrafficMetricsMap(Map<TrafficMetrics.Key, TrafficMetrics> trafficMetricsMap) {
        this.trafficMetricsMap = trafficMetricsMap;
    }

    private CollectionTags getMcpServerTag() {
        return new CollectionTags(Context.now(), Constants.AKTO_MCP_SERVER_TAG, "MCP Server", TagSource.KUBERNETES);
    }

    private Bson getUpdatesIfMcpCollection(boolean isMcpRequest, Bson updates) {
        if (!isMcpRequest) {
            return updates;
        }

        updates = Updates.combine(updates,
            Updates.set(ApiCollection.TAGS_STRING, Collections.singletonList(getMcpServerTag())));
        return updates;
    }

    private SyncLimit fetchSyncLimit(MetricTypes metricType) {
        FeatureAccess featureAccess = UsageMetricUtils.getFeatureAccess(Context.accountId.get(), metricType);
        if (featureAccess.equals(FeatureAccess.noAccess)) {
            featureAccess.setUsageLimit(0);
        }
        return featureAccess.fetchSyncLimit();
    }
}
