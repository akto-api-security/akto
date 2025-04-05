package com.akto.hybrid_parsers;

import com.akto.RuntimeMode;
import com.akto.billing.UsageMetricUtils;
import com.akto.dao.context.Context;
import com.akto.dao.traffic_metrics.TrafficMetricsDao;
import com.akto.hybrid_dependency.DependencyAnalyser;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.*;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.billing.Organization;
import com.akto.dto.billing.SyncLimit;
import com.akto.dto.bulk_updates.BulkUpdates;
import com.akto.dto.bulk_updates.UpdatePayload;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.monitoring.FilterConfig.FILTER_TYPE;
import com.akto.dto.settings.DefaultPayload;
import com.akto.dto.test_editor.ExecutorNode;
import com.akto.dto.traffic_metrics.TrafficMetrics;
import com.akto.dto.type.URLMethods.Method;
import com.akto.dto.usage.MetricTypes;
import com.akto.graphql.GraphQLUtils;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.runtime.RuntimeUtil;
import com.akto.runtime.parser.SampleParser;
import com.akto.runtime.utils.Utils;
import com.akto.test_editor.execution.ParseAndExecute;
import com.akto.test_editor.execution.VariableResolver;
import com.akto.test_editor.filter.data_operands_impl.ValidationResult;
import com.akto.hybrid_runtime.APICatalogSync;
import com.akto.hybrid_runtime.Main;
import com.akto.hybrid_runtime.MergeLogicLocal;
import com.akto.hybrid_runtime.URLAggregator;
import com.akto.util.Pair;
import com.akto.util.Constants;
import com.akto.util.http_util.CoreHTTPClient;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.*;
import okhttp3.*;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
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

    // Using default timeouts [10 seconds], as this is a slow API.
    private static final OkHttpClient client = CoreHTTPClient.client.newBuilder().build();
    
    private static final ExecutorService service = Executors.newFixedThreadPool(1);
    private static boolean pgMerging = false;

    private static final ConcurrentLinkedQueue<BasicDBObject> queue = new ConcurrentLinkedQueue<>();
    private DataActor dataActor = DataActorFactory.fetchInstance();
    private Map<Integer, ApiCollection> apiCollectionsMap = new HashMap<>();

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
    public HttpCallParser(String userIdentifier, int thresh, int sync_threshold_count, int sync_threshold_time, boolean fetchAllSTI) {
        last_synced = 0;
        this.sync_threshold_count = sync_threshold_count;
        this.sync_threshold_time = sync_threshold_time;
        apiCatalogSync = new APICatalogSync(userIdentifier, thresh, fetchAllSTI);
        apiCatalogSync.buildFromDB(false, fetchAllSTI);
        apiCollectionsMap = new HashMap<>();
        List<ApiCollection> apiCollections = dataActor.fetchAllApiCollectionsMeta();
        for (ApiCollection apiCollection: apiCollections) {
            apiCollectionsMap.put(apiCollection.getId(), apiCollection);
        }
        //this.dependencyAnalyser = new DependencyAnalyser(apiCatalogSync.dbState, Main.isOnprem, RuntimeMode.isHybridDeployment(), apiCollectionsMap);
    }
    
    public static HttpResponseParams parseKafkaMessage(String message) throws Exception {
        return SampleParser.parseSampleMessage(message);
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

    public int createCollectionSimple(int vxlanId) {
        dataActor.createCollectionSimple(vxlanId);
        return vxlanId;
    }

    public int createCollectionSimpleForVpc(int vxlanId, String vpcId) {
        dataActor.createCollectionSimpleForVpc(vxlanId, vpcId);
        return vxlanId;
    }


    public int createCollectionBasedOnHostName(int id, String host)  throws Exception {
        FindOneAndUpdateOptions updateOptions = new FindOneAndUpdateOptions();
        updateOptions.upsert(true);
        String vpcId = System.getenv("VPC_ID");
        // 3 cases
        // 1. If 2 threads are trying to insert same host simultaneously then both will succeed with upsert true
        // 2. If we are trying to insert different host but same id (hashCode collision) then it will fail,
        //    so we loop 20 times till we succeed
        boolean flag = false;
        for (int i=0;i < 100; i++) {
            id += i;
            try {
                dataActor.createCollectionForHostAndVpc(host, id, vpcId);
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


    int numberOfSyncs = 0;

    private static int lastSyncLimitFetch = 0;
    private static final int REFRESH_INTERVAL = 60 * 10; // 10 minutes.
    private static SyncLimit syncLimit = FeatureAccess.fullAccess.fetchSyncLimit();
    /*
     * This is the epoch for August 27, 2024 7:15:31 AM GMT .
     * Enabling this feature for all users after this timestamp.
     */
    private static final int DAY_0_EPOCH = 1724742931;

    private SyncLimit fetchSyncLimit() {
        try {
            if ((lastSyncLimitFetch + REFRESH_INTERVAL) >= Context.now()) {
                return syncLimit;
            }

            AccountSettings accountSettings = dataActor.fetchAccountSettings();
            int accountId = Context.accountId.get();
            if (accountSettings != null) {
                accountId = accountSettings.getId();
            }

            /*
             * If a user is using on-prem mini-runtime, no limits would apply there.
             */
            if(accountId < DAY_0_EPOCH){
                return syncLimit;
            }

            Organization organization = dataActor.fetchOrganization(accountId);
            FeatureAccess featureAccess = UsageMetricUtils.getFeatureAccess(organization, MetricTypes.ACTIVE_ENDPOINTS);
            syncLimit = featureAccess.fetchSyncLimit();
            lastSyncLimitFetch = Context.now();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error in fetching sync limit from feature access " + e.toString());
        }
        return syncLimit;
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
        // USE ONLY filteredResponseParams and not responseParams
        List<HttpResponseParams> filteredResponseParams = responseParams;
        if (accountSettings != null && accountSettings.getDefaultPayloads() != null) {
            filteredResponseParams = filterDefaultPayloads(filteredResponseParams, accountSettings.getDefaultPayloads());
        }
        filteredResponseParams = filterHttpResponseParams(filteredResponseParams, accountSettings);
        boolean isHarOrPcap = aggregate(filteredResponseParams, aggregatorMap);

        for (int apiCollectionId: aggregatorMap.keySet()) {
            URLAggregator aggregator = aggregatorMap.get(apiCollectionId);
            apiCatalogSync.computeDelta(aggregator, false, apiCollectionId);
        }

        //  for (HttpResponseParams responseParam: filteredResponseParams) {
        //      dependencyAnalyser.analyse(responseParam.getOrig(), responseParam.requestParams.getApiCollectionId());
        //  }

        this.sync_count += filteredResponseParams.size();
        int syncThresh = numberOfSyncs < 10 ? 10000 : sync_threshold_count;
        if (syncImmediately || this.sync_count >= syncThresh || (Context.now() - this.last_synced) > this.sync_threshold_time || isHarOrPcap) {
            numberOfSyncs++;
            List<ApiCollection> apiCollections = dataActor.fetchAllApiCollectionsMeta();
            for (ApiCollection apiCollection: apiCollections) {
                apiCollectionsMap.put(apiCollection.getId(), apiCollection);
            }
            SyncLimit syncLimit = fetchSyncLimit();
            apiCatalogSync.syncWithDB(syncImmediately, fetchAllSTI, syncLimit);
            // dependencyAnalyser.dbState = apiCatalogSync.dbState;
            // dependencyAnalyser.syncWithDb();
            syncTrafficMetricsWithDB();
            this.last_synced = Context.now();
            this.sync_count = 0;

            if (accountSettings != null && accountSettings.isRedactPayload()) {
                loggerMaker.infoAndAddToDb("Current pg merging status " + pgMerging);
                /*
                 * submit a job only if it is not running.
                 */
                if (!pgMerging) {
                    int accountId = Context.accountId.get();
                    pgMerging = true;
                    service.submit(() -> {
                        Context.accountId.set(accountId);
                        try {
                            loggerMaker.infoAndAddToDb("Running merging job for sql");
                            MergeLogicLocal.mergingJob(apiCatalogSync.dbState);
                            loggerMaker.infoAndAddToDb("completed merging job for sql");
                        } catch (Exception e) {
                            loggerMaker.errorAndAddToDb(e, "error in sql merge job");
                        } finally {
                            pgMerging = false;
                        }
                    });
                }
            }
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
        List<BulkUpdates> bulkUpdates = new ArrayList<>();
        BasicDBObject metricsData = new BasicDBObject();
        int accountId = Context.accountId.get();
        Organization organization = dataActor.fetchOrganization(accountId);
        for (TrafficMetrics trafficMetrics: trafficMetricsMap.values()) {
            TrafficMetrics.Key key = trafficMetrics.getId();
            Map<String, Long> countMap = trafficMetrics.getCountMap();

            if (countMap == null || countMap.isEmpty()) continue;

            Map<String, Object> filterMap = TrafficMetricsDao.getFiltersMap(key);

            int count = 0;
            ArrayList<String> individualUpdates = new ArrayList<>();
            for (String ts: countMap.keySet()) {
                UpdatePayload updatePayload = new UpdatePayload("countMap." + ts, countMap.get(ts), "inc");
                individualUpdates.add(updatePayload.toString());
                count += countMap.get(ts);
            }

            if (organization != null && (Main.isOnprem || RuntimeMode.isHybridDeployment())) {
                String metricKey = key.getName().name() + "|" + key.getIp() + "|" + key.getHost() + "|" + key.getVxlanID() + "|" + organization.getId() + "|" + accountId;
                count += (int) metricsData.getOrDefault(metricKey, 0);
                metricsData.put(metricKey, count);
            }

            bulkUpdates.add(new BulkUpdates(filterMap, individualUpdates));

        }

        if (bulkUpdates.size() > 0) {
            if (metricsData.size() != 0 && (Main.isOnprem || RuntimeMode.isHybridDeployment())) {
                queue.add(metricsData);
            }
            List<Object> writesForTraffic = new ArrayList<>();
            writesForTraffic.addAll(bulkUpdates);
            dataActor.bulkWriteTrafficMetrics(writesForTraffic);
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
        List<HttpResponseParams.Source> whiteListSource = Arrays.asList(HttpResponseParams.Source.MIRRORING);
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

    public boolean isRedundantEndpoint(String url, Pattern pattern){
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
        String hostName = getHeaderValue(httpResponseParam.getRequestParams().getHeaders(), "host");

        if (hostName != null && !hostNameToIdMap.containsKey(hostName) && RuntimeUtil.hasSpecialCharacters(hostName)) {
            hostName = "Special_Char_Host";
        }

        int vxlanId = httpResponseParam.requestParams.getApiCollectionId();
        String vpcId = System.getenv("VPC_ID");

        if (useHostCondition(hostName, httpResponseParam.getSource())) {
            hostName = hostName.toLowerCase();
            hostName = hostName.trim();

            String key = hostName;

            if (hostNameToIdMap.containsKey(key)) {
                apiCollectionId = hostNameToIdMap.get(key);

            } else {
                int id = hostName.hashCode();
                try {

                    apiCollectionId = createCollectionBasedOnHostName(id, hostName);

                    hostNameToIdMap.put(key, apiCollectionId);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb("Failed to create collection for host : " + hostName, LogDb.RUNTIME);
                    createCollectionSimpleForVpc(vxlanId, vpcId);
                    hostNameToIdMap.put("null " + vxlanId, vxlanId);
                    apiCollectionId = httpResponseParam.requestParams.getApiCollectionId();
                }
            }

        } else {
            String key = "null" + " " + vxlanId;
            if (!hostNameToIdMap.containsKey(key)) {
                createCollectionSimpleForVpc(vxlanId, vpcId);
                hostNameToIdMap.put(key, vxlanId);
            }

            apiCollectionId = vxlanId;
        }
        return apiCollectionId;
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

    public List<HttpResponseParams> filterHttpResponseParams(List<HttpResponseParams> httpResponseParamsList, AccountSettings accountSettings) {
        List<HttpResponseParams> filteredResponseParams = new ArrayList<>();
        int originalSize = httpResponseParamsList.size();

        List<String> redundantList = new ArrayList<>();
        if(accountSettings !=null && !accountSettings.getAllowRedundantEndpointsList().isEmpty()){
            redundantList = accountSettings.getAllowRedundantEndpointsList();
        }
        Pattern regexPattern = Utils.createRegexPatternFromList(redundantList);
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

            if (httpResponseParam.getRequestParams().getURL().toLowerCase().contains("/health")) {
                continue;
            }

            // check for garbage points here
            if(!redundantList.isEmpty()){
                if(isRedundantEndpoint(httpResponseParam.getRequestParams().getURL(),regexPattern)){
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
                    String method = httpResponseParam.getRequestParams().getMethod();
                    String responseBody = httpResponseParam.getPayload();
                    boolean ignore = false;
                    for (String extension : accountSettings.getAllowRedundantEndpointsList()) {
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

            Pair<HttpResponseParams,FILTER_TYPE> temp = applyAdvancedFilters(httpResponseParam, executorNodesMap, apiCatalogSync.advancedFilterMap);
            HttpResponseParams param = temp.getFirst();
            if(param == null || temp.getSecond().equals(FILTER_TYPE.UNCHANGED)){
                continue;
            }else{
                httpResponseParam = param;
            }
            
            int apiCollectionId = createApiCollectionId(httpResponseParam);

            httpResponseParam.requestParams.setApiCollectionId(apiCollectionId);

            List<HttpResponseParams> responseParamsList = GraphQLUtils.getUtils().parseGraphqlResponseParam(httpResponseParam);
            if (responseParamsList.isEmpty()) {
                filteredResponseParams.add(httpResponseParam);
            } else {
                filteredResponseParams.addAll(responseParamsList);
                loggerMaker.infoAndAddToDb("Adding " + responseParamsList.size() + "new graphql endpoints in invetory",LogDb.RUNTIME);
            }

            if (httpResponseParam.getSource().equals(HttpResponseParams.Source.MIRRORING)) {
                TrafficMetrics.Key processedRequestsKey = getTrafficMetricsKey(httpResponseParam, TrafficMetrics.Name.FILTERED_REQUESTS_RUNTIME);
                incTrafficMetrics(processedRequestsKey,1);
            }

        }
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
}
