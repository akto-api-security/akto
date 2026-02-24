package com.akto.hybrid_parsers;

import com.akto.RuntimeMode;
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
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.traffic_metrics.TrafficMetrics;
import com.akto.graphql.GraphQLUtils;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.runtime.parser.SampleParser;
import com.akto.hybrid_runtime.APICatalogSync;
import com.akto.hybrid_runtime.Main;
import com.akto.hybrid_runtime.MergeLogicLocal;
import com.akto.hybrid_runtime.URLAggregator;
import com.akto.util.JSONUtils;
import com.akto.util.Constants;
import com.akto.util.HttpRequestResponseUtils;
import com.akto.util.http_util.CoreHTTPClient;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.*;
import okhttp3.*;
import org.apache.commons.lang3.math.NumberUtils;
import org.bson.conversions.Bson;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
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
    private int tagsLastUpdated = 0;
    private static final int TAGS_UPDATE_INTERVAL = 15 * 60; // 15 minutes
    private static final String NON_HOSTNAME_KEY = "null" + " "; // used for collections created without hostnames
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
        boolean useMap = !(Main.isOnprem || RuntimeMode.isHybridDeployment());
        apiCollectionsMap = new HashMap<>();
        List<ApiCollection> apiCollections = dataActor.fetchAllApiCollectionsMeta();
        for (ApiCollection apiCollection: apiCollections) {
            apiCollectionsMap.put(apiCollection.getId(), apiCollection);
        }
        this.dependencyAnalyser = new DependencyAnalyser(apiCatalogSync.dbState, Main.isOnprem, RuntimeMode.isHybridDeployment(), apiCollectionsMap);
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

    public int createCollectionSimpleForVpc(int vxlanId, String vpcId, List<CollectionTags> tags) {
        dataActor.createCollectionSimpleForVpc(vxlanId, vpcId, tags, null);
        return vxlanId;
    }


    public int createCollectionBasedOnHostName(int id, String host, List<CollectionTags> tags)  throws Exception {
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
                dataActor.createCollectionForHostAndVpc(host, id, vpcId, tags, null);
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
        filteredResponseParams = filterHttpResponseParams(filteredResponseParams);
        boolean isHarOrPcap = aggregate(filteredResponseParams, aggregatorMap);
        apiCatalogSync.setMergeUrlsOnVersions(accountSettings.isAllowMergingOnVersions());

        for (int apiCollectionId: aggregatorMap.keySet()) {
            URLAggregator aggregator = aggregatorMap.get(apiCollectionId);
            apiCatalogSync.computeDelta(aggregator, false, apiCollectionId);
        }

         for (HttpResponseParams responseParam: filteredResponseParams) {
             dependencyAnalyser.analyse(responseParam.getOrig(), responseParam.requestParams.getApiCollectionId());
         }

        this.sync_count += filteredResponseParams.size();
        int syncThresh = numberOfSyncs < 10 ? 10000 : sync_threshold_count;
        if (syncImmediately || this.sync_count >= syncThresh || (Context.now() - this.last_synced) > this.sync_threshold_time || isHarOrPcap) {
            numberOfSyncs++;
            List<ApiCollection> apiCollections = dataActor.fetchAllApiCollectionsMeta();
            for (ApiCollection apiCollection: apiCollections) {
                apiCollectionsMap.put(apiCollection.getId(), apiCollection);
            }
            apiCatalogSync.syncWithDB(syncImmediately, fetchAllSTI);
            dependencyAnalyser.dbState = apiCatalogSync.dbState;
            dependencyAnalyser.syncWithDb();
            syncTrafficMetricsWithDB();
            this.last_synced = Context.now();
            this.sync_count = 0;
            /*
             * submit a job only if it is not running.
             */
            loggerMaker.infoAndAddToDb("Current pg merging status " + pgMerging);
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
/**
     * Updates API collection tags if any new tags are detected.
     * 
     * @param hostNameMapKey     key for the host name map, which is used to create
     *                           collections based on host names.
     * @param httpResponseParams
     */
    public void updateApiCollectionTags(String hostNameMapKey, HttpResponseParams httpResponseParams) {
        if (Context.now() - this.tagsLastUpdated < TAGS_UPDATE_INTERVAL) {
            // Avoid updating tags too frequently
            return;
        }
        this.tagsLastUpdated = Context.now();
        int apiCollectionId = httpResponseParams.requestParams.getApiCollectionId();
        ApiCollection apiCollection = apiCollectionsMap.get(apiCollectionId);

        List<CollectionTags> tagsList = CollectionTags.calculateTagsDiff(apiCollection.getTagsList(), httpResponseParams.getTags());

        if (tagsList == null || tagsList.isEmpty()) {
            return;
        }
        
        // Detects if collections were created based on hostName
        if (hostNameMapKey.contains(NON_HOSTNAME_KEY)) {
            String vpcId = System.getenv("VPC_ID");
            createCollectionSimpleForVpc(
                    httpResponseParams.requestParams.getApiCollectionId(), vpcId, tagsList);
        } else {
            try {
                createCollectionBasedOnHostName(hostNameMapKey.hashCode(), hostNameMapKey,
                        tagsList);
            } catch (Exception e) {
                loggerMaker.error(
                        "Error while updating api collection tags for host: " + hostNameMapKey + " " + e.getMessage());
                e.printStackTrace();
            }
        }

        loggerMaker.debug("Updated tags for apiCollectionId: " + apiCollectionId + " with tags: " + tagsList);
    }

    public List<HttpResponseParams> filterHttpResponseParams(List<HttpResponseParams> httpResponseParamsList) {
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

            if (httpResponseParam.getRequestParams().getURL().toLowerCase().contains("/health")) {
                continue;
            }

            String hostName = getHeaderValue(httpResponseParam.getRequestParams().getHeaders(), "host");


            int vxlanId = httpResponseParam.requestParams.getApiCollectionId();
            int apiCollectionId;

            List<CollectionTags> tagsList = CollectionTags.convertTagsFormat(httpResponseParam.getTags());



            if (useHostCondition(hostName, httpResponseParam.getSource())) {
                hostName = hostName.toLowerCase();
                hostName = hostName.trim();

                String key = hostName;

                if (hostNameToIdMap.containsKey(key)) {
                    apiCollectionId = hostNameToIdMap.get(key);
                    updateApiCollectionTags(key, httpResponseParam);

                } else {
                    int id = hostName.hashCode();
                    try {

                        apiCollectionId = createCollectionBasedOnHostName(id, hostName, tagsList);

                        hostNameToIdMap.put(key, apiCollectionId);
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb("Failed to create collection for host : " + hostName, LogDb.RUNTIME);
                        createCollectionSimple(vxlanId);
                        hostNameToIdMap.put(NON_HOSTNAME_KEY + vxlanId, vxlanId);
                        apiCollectionId = httpResponseParam.requestParams.getApiCollectionId();
                    }
                }

            } else {
                String key = NON_HOSTNAME_KEY + vxlanId;
                if (!hostNameToIdMap.containsKey(key)) {
                    createCollectionSimple(vxlanId);
                    hostNameToIdMap.put(key, vxlanId);
                }else {
                    updateApiCollectionTags(key, httpResponseParam);
                }

                apiCollectionId = vxlanId;
            }

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
