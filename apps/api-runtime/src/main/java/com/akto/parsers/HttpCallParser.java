package com.akto.parsers;

import java.net.URLDecoder;
import java.util.*;

import com.akto.DaoInit;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.runtime.APICatalogSync;
import com.akto.runtime.URLAggregator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.apache.commons.lang3.math.NumberUtils;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpCallParser {
    
    private static final String AKTO_REQUEST = "##AKTO_REQUEST##";
    private static final String AKTO_RESPONSE = "##AKTO_RESPONSE##";
    private final static ObjectMapper mapper = new ObjectMapper();
    private final int sync_threshold_count;
    private final int sync_threshold_time;
    private int sync_count = 0;
    private int last_synced;
    private static final Logger logger = LoggerFactory.getLogger(HttpCallParser.class);

    public APICatalogSync apiCatalogSync;
    private Map<String, Integer> hostNameToIdMap = new HashMap<>();

    public HttpCallParser(String userIdentifier, int thresh, int sync_threshold_count, int sync_threshold_time, boolean fetchAllSTI) {
        last_synced = 0;
        this.sync_threshold_count = sync_threshold_count;
        this.sync_threshold_time = sync_threshold_time;
        apiCatalogSync = new APICatalogSync(userIdentifier,thresh);
        apiCatalogSync.buildFromDB(false, fetchAllSTI);
    }
    
    public static HttpResponseParams parseKafkaMessage(String message) throws Exception {

        //convert java object to JSON format
        Map<String, Object> json = gson.fromJson(message, Map.class);

        String method = (String) json.get("method");
        String url = (String) json.get("path");
        String type = (String) json.get("type");
        Map<String,List<String>> requestHeaders = OriginalHttpRequest.buildHeadersMap(json, "requestHeaders");

        String rawRequestPayload = (String) json.get("requestPayload");
        String requestPayload = OriginalHttpRequest.rawToJsonString(rawRequestPayload,requestHeaders);

        String apiCollectionIdStr = json.getOrDefault("akto_vxlan_id", "0").toString();
        int apiCollectionId = 0;
        if (NumberUtils.isDigits(apiCollectionIdStr)) {
            apiCollectionId = NumberUtils.toInt(apiCollectionIdStr, 0);
        }

        HttpRequestParams requestParams = new HttpRequestParams(
                method,url,type, requestHeaders, requestPayload, apiCollectionId
        );

        int statusCode = Integer.parseInt(json.get("statusCode").toString());
        String status = (String) json.get("status");
        Map<String,List<String>> responseHeaders = OriginalHttpRequest.buildHeadersMap(json, "responseHeaders");
        String payload = (String) json.get("responsePayload");
        int time = Integer.parseInt(json.get("time").toString());
        String accountId = (String) json.get("akto_account_id");
        String sourceIP = (String) json.get("ip");

        String isPendingStr = (String) json.getOrDefault("is_pending", "false");
        boolean isPending = !isPendingStr.toLowerCase().equals("false");
        String sourceStr = (String) json.getOrDefault("source", HttpResponseParams.Source.OTHER.name());
        HttpResponseParams.Source source = HttpResponseParams.Source.valueOf(sourceStr);
        
        return new HttpResponseParams(
                type,statusCode, status, responseHeaders, payload, requestParams, time, accountId, isPending, source, message, sourceIP
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
        System.out.println("createCollectionSimple: " + vxlanId);
        UpdateOptions updateOptions = new UpdateOptions();
        updateOptions.upsert(true);

        ApiCollectionsDao.instance.getMCollection().updateOne(
                Filters.eq("_id", vxlanId),
                Updates.combine(
                        Updates.set(ApiCollection.VXLAN_ID, vxlanId),
                        Updates.setOnInsert("startTs", Context.now()),
                        Updates.setOnInsert("urls", new HashSet<>())
                ),
                updateOptions
        );
        System.out.println("created: " + vxlanId);

        return vxlanId;
    }


    public int createCollectionBasedOnHostName(int id, String host)  throws Exception {
        System.out.println("createCollectionBasedOnHostName: " + id + " " + host);
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
                Bson updates = Updates.combine(
                    Updates.setOnInsert(ApiCollection.HOST_NAME, host),
                    Updates.setOnInsert("startTs", Context.now()),
                    Updates.setOnInsert("urls", new HashSet<>())
                );

                ApiCollectionsDao.instance.getMCollection().findOneAndUpdate(Filters.eq("_id", id), updates, updateOptions);

                flag = true;
                break;
            } catch (Exception e) {
                logger.error("Error while inserting apiCollection, trying again " + i + " " + e.getMessage());
            }
        }
        if (flag) { // flag tells if we were successfully able to insert collection
            System.out.println("created CollectionBasedOnHostName: " + id);
            return id;
        } else {
            throw new Exception("Not able to insert");
        }
    }


    int numberOfSyncs = 0;

    public APICatalogSync syncFunction(List<HttpResponseParams> responseParams, boolean syncImmediately, boolean fetchAllSTI)  {
        // USE ONLY filteredResponseParams and not responseParams
        List<HttpResponseParams> filteredResponseParams = filterHttpResponseParams(responseParams);
        boolean isHarOrPcap = aggregate(filteredResponseParams);

        for (int apiCollectionId: aggregatorMap.keySet()) {
            URLAggregator aggregator = aggregatorMap.get(apiCollectionId);
            apiCatalogSync.computeDelta(aggregator, false, apiCollectionId);
        }

        this.sync_count += filteredResponseParams.size();
        int syncThresh = numberOfSyncs < 10 ? 10000 : sync_threshold_count;
        if (syncImmediately || this.sync_count >= syncThresh || (Context.now() - this.last_synced) > this.sync_threshold_time || isHarOrPcap) {
            numberOfSyncs++;
            apiCatalogSync.syncWithDB(syncImmediately, fetchAllSTI);
            this.last_synced = Context.now();
            this.sync_count = 0;
            return apiCatalogSync;
        }

        return null;
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


    public List<HttpResponseParams> filterHttpResponseParams(List<HttpResponseParams> httpResponseParamsList) {
        List<HttpResponseParams> filteredResponseParams = new ArrayList<>();
        System.out.println("HttpCallParser.filterHttpResponseParams()");
        for (HttpResponseParams httpResponseParam: httpResponseParamsList) {
            boolean cond = HttpResponseParams.validHttpResponseCode(httpResponseParam.getStatusCode());
            if (!cond) continue;
            
            String ignoreAktoFlag = getHeaderValue(httpResponseParam.getRequestParams().getHeaders(), AccountSettings.AKTO_IGNORE_FLAG);
            if (ignoreAktoFlag != null) continue;

            String hostName = getHeaderValue(httpResponseParam.getRequestParams().getHeaders(), "host");

            int vxlanId = httpResponseParam.requestParams.getApiCollectionId();
            int apiCollectionId ;

            System.out.println("host: " + hostName + " vxlanid" + vxlanId);


            if (useHostCondition(hostName, httpResponseParam.getSource())) {
                hostName = hostName.toLowerCase();
                hostName = hostName.trim();

                String key = hostName;
                System.out.println("key: " + key);

                if (hostNameToIdMap.containsKey(key)) {
                    apiCollectionId = hostNameToIdMap.get(key);
                    System.out.println("key found: " + apiCollectionId);

                } else {
                    int id = hostName.hashCode();
                    try {
                        System.out.println("creating collection: " + id +  " " + hostName);

                        apiCollectionId = createCollectionBasedOnHostName(id, hostName);
                        System.out.println("created collection: " + apiCollectionId);

                        hostNameToIdMap.put(key, apiCollectionId);
                    } catch (Exception e) {
                        logger.error("Failed to create collection for host : " + hostName);
                        createCollectionSimple(vxlanId);
                        hostNameToIdMap.put("null " + vxlanId, vxlanId);
                        apiCollectionId = httpResponseParam.requestParams.getApiCollectionId();
                    }
                }

            } else {
                String key = "null" + " " + vxlanId;
                System.out.println("use host false: " + key);
                if (!hostNameToIdMap.containsKey(key)) {
                    System.out.println("createCollectionSimple: " + vxlanId);
                    createCollectionSimple(vxlanId);
                    hostNameToIdMap.put(key, vxlanId);
                }

                apiCollectionId = vxlanId;
            }

            httpResponseParam.requestParams.setApiCollectionId(apiCollectionId);
            filteredResponseParams.add(httpResponseParam);
        }

        System.out.println("returning");
        return filteredResponseParams;
    }

    private Map<Integer, URLAggregator> aggregatorMap = new HashMap<>();

    public void setAggregatorMap(Map<Integer, URLAggregator> aggregatorMap){
        this.aggregatorMap=aggregatorMap;
    }

    public Map<Integer, URLAggregator> getAggregatorMap(){
        return this.aggregatorMap;
    }

    public boolean aggregate(List<HttpResponseParams> responses) {
        int count = 0;
        boolean ret = false;
        for (HttpResponseParams responseParams: responses) {
            if (responseParams.getSource() == HttpResponseParams.Source.HAR || responseParams.getSource() == HttpResponseParams.Source.PCAP) {
                ret = true;
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
        
        logger.info("added " + count + " urls");
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
}
