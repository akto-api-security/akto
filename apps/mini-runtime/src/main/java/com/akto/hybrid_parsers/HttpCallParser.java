package com.akto.hybrid_parsers;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dao.traffic_metrics.TrafficMetricsDao;
import com.akto.hybrid_dependency.DependencyAnalyser;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.*;
import com.akto.dto.billing.Organization;
import com.akto.dto.bulk_updates.BulkUpdates;
import com.akto.dto.bulk_updates.UpdatePayload;
import com.akto.dto.settings.DefaultPayload;
import com.akto.dto.traffic_metrics.TrafficMetrics;
import com.akto.graphql.GraphQLUtils;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.hybrid_runtime.APICatalogSync;
import com.akto.hybrid_runtime.Main;
import com.akto.hybrid_runtime.URLAggregator;
import com.akto.runtime.RuntimeUtil;
import com.akto.util.JSONUtils;
import com.akto.util.Constants;
import com.akto.util.HttpRequestResponseUtils;
import com.akto.util.http_util.CoreHTTPClient;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.*;
import okhttp3.*;
import org.apache.commons.lang3.math.NumberUtils;
import org.bson.conversions.Bson;
import com.alibaba.fastjson2.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.akto.runtime.RuntimeUtil.matchesDefaultPayload;

public class HttpCallParser {
    private final int sync_threshold_count;
    private final int sync_threshold_time;
    private int sync_count = 0;
    private int last_synced;
    private static final LoggerMaker loggerMaker = new LoggerMaker(HttpCallParser.class);
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

    private static final ConcurrentLinkedQueue<BasicDBObject> queue = new ConcurrentLinkedQueue<>();
    private DataActor dataActor = DataActorFactory.fetchInstance();

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
        this.dependencyAnalyser = new DependencyAnalyser(apiCatalogSync.dbState, !Main.isOnprem);
    }

    public HttpCallParser(int sync_threshold_count, int sync_threshold_time) {
        this.sync_threshold_count = sync_threshold_count;
        this.sync_threshold_time = sync_threshold_time;
    }
    
    public static HttpResponseParams parseKafkaMessage(String message) throws Exception {

        //convert java object to JSON format
        JSONObject jsonObject = JSON.parseObject(message);

        String method = jsonObject.getString("method");
        String url = jsonObject.getString("path");
        String type = jsonObject.getString("type");
        Map<String,List<String>> requestHeaders = OriginalHttpRequest.buildHeadersMap(jsonObject, "requestHeaders");

        String rawRequestPayload = jsonObject.getString("requestPayload");
        String requestPayload = HttpRequestResponseUtils.rawToJsonString(rawRequestPayload,requestHeaders);



        String apiCollectionIdStr = jsonObject.getOrDefault("akto_vxlan_id", "0").toString();
        int apiCollectionId = 0;
        if (NumberUtils.isDigits(apiCollectionIdStr)) {
            apiCollectionId = NumberUtils.toInt(apiCollectionIdStr, 0);
        }

        HttpRequestParams requestParams = new HttpRequestParams(
                method,url,type, requestHeaders, requestPayload, apiCollectionId
        );

        int statusCode = jsonObject.getInteger("statusCode");
        String status = jsonObject.getString("status");
        Map<String,List<String>> responseHeaders = OriginalHttpRequest.buildHeadersMap(jsonObject, "responseHeaders");
        String payload = jsonObject.getString("responsePayload");
        payload = HttpRequestResponseUtils.rawToJsonString(payload, responseHeaders);
        payload = JSONUtils.parseIfJsonP(payload);
        int time = jsonObject.getInteger("time");
        String accountId = jsonObject.getString("akto_account_id");
        String sourceIP = jsonObject.getString("ip");
        String destIP = jsonObject.getString("destIp");
        String direction = jsonObject.getString("direction");

        String isPendingStr = (String) jsonObject.getOrDefault("is_pending", "false");
        boolean isPending = !isPendingStr.toLowerCase().equals("false");
        String sourceStr = (String) jsonObject.getOrDefault("source", HttpResponseParams.Source.OTHER.name());
        HttpResponseParams.Source source = HttpResponseParams.Source.valueOf(sourceStr);
        
        return new HttpResponseParams(
                type,statusCode, status, responseHeaders, payload, requestParams, time, accountId, isPending, source, message, sourceIP, destIP, direction
        );
    }

    private static final Gson gson = new Gson();

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


    public int createCollectionBasedOnHostName(int id, String host)  throws Exception {
        FindOneAndUpdateOptions updateOptions = new FindOneAndUpdateOptions();
        updateOptions.upsert(true);
        // 3 cases
        // 1. If 2 threads are trying to insert same host simultaneously then both will succeed with upsert true
        // 2. If we are trying to insert different host but same id (hashCode collision) then it will fail,
        //    so we loop 20 times till we succeed
        boolean flag = false;
        for (int i=0;i < 100; i++) {
            id += i;
            try {
                dataActor.createCollectionForHost(host, id);
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
            apiCatalogSync.syncWithDB(syncImmediately, fetchAllSTI);
            // dependencyAnalyser.dbState = apiCatalogSync.dbState;
            // dependencyAnalyser.syncWithDb();
            syncTrafficMetricsWithDB();
            this.last_synced = Context.now();
            this.sync_count = 0;
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

            if (organization != null && Main.isOnprem) {
                String metricKey = key.getName().name() + "|" + key.getIp() + "|" + key.getHost() + "|" + key.getVxlanID() + "|" + organization.getId() + "|" + accountId;
                count += (int) metricsData.getOrDefault(metricKey, 0);
                metricsData.put(metricKey, count);
            }

            bulkUpdates.add(new BulkUpdates(filterMap, individualUpdates));

        }

        if (bulkUpdates.size() > 0) {
            if (metricsData.size() != 0 && Main.isOnprem) {
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
        boolean hostNameCondition;
        if (hostName == null) {
            hostNameCondition = false;
        } else {
            hostNameCondition = ! ( hostName.toLowerCase().equals(hostName.toUpperCase()) );
        }
        return hostNameCondition && ApiCollection.useHost;
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

    public int createApiCollectionId(HttpResponseParams httpResponseParam){
        int apiCollectionId;
        String hostName = getHeaderValue(httpResponseParam.getRequestParams().getHeaders(), "host");

        if (hostName != null && !hostNameToIdMap.containsKey(hostName) && RuntimeUtil.hasSpecialCharacters(hostName)) {
            hostName = "Special_Char_Host";
        }

        int vxlanId = httpResponseParam.requestParams.getApiCollectionId();
        boolean useHostConditionResult = useHostCondition(hostName, httpResponseParam.getSource());

        if (useHostConditionResult) {
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
                    createCollectionSimple(vxlanId);
                    hostNameToIdMap.put("null " + vxlanId, vxlanId);
                    apiCollectionId = httpResponseParam.requestParams.getApiCollectionId();
                }
            }

        } else {
            String key = "null" + " " + vxlanId;
            if (!hostNameToIdMap.containsKey(key)) {
                createCollectionSimple(vxlanId);
                hostNameToIdMap.put(key, vxlanId);
            }

            apiCollectionId = vxlanId;
        }

        return apiCollectionId;
    }

    public static final String CONTENT_TYPE = "CONTENT-TYPE";

    public boolean isRedundantEndpoint(String url, List<String> discardedUrlList){
        StringJoiner joiner = new StringJoiner("|", ".*\\.(", ")(\\?.*)?");
        for (String extension : discardedUrlList) {
            if(extension.startsWith(CONTENT_TYPE)){
                continue;
            }
            joiner.add(extension);
        }
        String regex = joiner.toString();

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(url);
        return matcher.matches();
    }

    private boolean isInvalidContentType(String contentType){
        boolean res = false;
        if(contentType == null || contentType.length() == 0) return res;

        res = contentType.contains("javascript") || contentType.contains("png");
        return res;
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

            // check for garbage points here
            if(accountSettings != null && accountSettings.getAllowRedundantEndpointsList() != null){
                if(isRedundantEndpoint(httpResponseParam.getRequestParams().getURL(), accountSettings.getAllowRedundantEndpointsList())){
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