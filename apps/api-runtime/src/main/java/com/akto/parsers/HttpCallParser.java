package com.akto.parsers;

import java.io.*;
import java.net.URLDecoder;
import java.util.*;

import com.akto.DaoInit;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.runtime.APICatalogSync;
import com.akto.runtime.URLAggregator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.ConnectionString;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpCallParser {
    
    private static final String AKTO_REQUEST = "##AKTO_REQUEST##";
    private static final String AKTO_RESPONSE = "##AKTO_RESPONSE##";
    public static final String JSON_CONTENT_TYPE = "application/json";
    public static final String FORM_URL_ENCODED_CONTENT_TYPE = "application/x-www-form-urlencoded";
    private final static ObjectMapper mapper = new ObjectMapper();
    private final int sync_threshold_count;
    private final int sync_threshold_time;
    private int sync_count = 0;
    private int last_synced;
    private static final Logger logger = LoggerFactory.getLogger(HttpCallParser.class);

    private Map<String, Integer> hostNameToIdMap = new HashMap<>();

    APICatalogSync apiCatalogSync;
    public HttpCallParser(String userIdentifier, int thresh, int sync_threshold_count, int sync_threshold_time) {
        last_synced = 0;
        this.sync_threshold_count = sync_threshold_count;
        this.sync_threshold_time = sync_threshold_time;
        apiCatalogSync = new APICatalogSync(userIdentifier,thresh);
        apiCatalogSync.buildFromDB(false);
    }
    
    public static HttpResponseParams parseKafkaMessage(String message) throws Exception {
        Gson gson = new Gson();

        //convert java object to JSON format
        Map<String, Object> json = gson.fromJson(message, Map.class);

        String method = (String) json.get("method");
        String url = (String) json.get("path");
        String type = (String) json.get("type");
        Map<String,List<String>> requestHeaders = getHeaders(gson, json, "requestHeaders");

        String requestPayload = (String) json.get("requestPayload");
        requestPayload = requestPayload.trim();
        String acceptableContentType = getAcceptableContentType(requestHeaders);
        if (acceptableContentType != null && requestPayload.length() > 0) {
            // only if request payload is of FORM_URL_ENCODED_CONTENT_TYPE we convert it to json
            if (acceptableContentType.equals(FORM_URL_ENCODED_CONTENT_TYPE)) {
                String myStringDecoded = URLDecoder.decode((String) json.get("requestPayload"), "UTF-8");
                String[] parts = myStringDecoded.split("&");
                Map<String,String> valueMap = new HashMap<>();

                for(String part: parts){
                    String[] keyVal = part.split("="); // The equal separates key and values
                    if (keyVal.length == 2) {
                        valueMap.put(keyVal[0], keyVal[1]);
                    }
                }
                requestPayload = mapper.writeValueAsString(valueMap);
            }
        }

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
        Map<String,List<String>> responseHeaders = getHeaders(gson, json, "responseHeaders");
        String payload = (String) json.get("responsePayload");
        int time = Integer.parseInt(json.get("time").toString());
        String accountId = (String) json.get("akto_account_id");

        String isPendingStr = (String) json.getOrDefault("is_pending", "false");
        boolean isPending = !isPendingStr.toLowerCase().equals("false");
        String sourceStr = (String) json.getOrDefault("source", HttpResponseParams.Source.OTHER.name());
        HttpResponseParams.Source source = HttpResponseParams.Source.valueOf(sourceStr);
        
        return new HttpResponseParams(
                type,statusCode, status, responseHeaders, payload, requestParams, time, accountId, isPending, source, message
        );

    }

    public static Map<String,List<String>> getHeaders(Gson gson, Map json, String key) {
        Map headersFromRequest = gson.fromJson((String) json.get(key),Map.class);
        Map<String,List<String>> headers = new HashMap<>();
        for (Object k: headersFromRequest.keySet()) {
            List<String> values = headers.getOrDefault(k,new ArrayList<>());
            values.add(headersFromRequest.get(k).toString());
            headers.put(k.toString().toLowerCase(),values);
        }
        return headers;
    }

    public static String getHostName(Map<String,List<String>> headers) {
        if (headers == null) return null;
        for (String k: headers.keySet()) {
            if (k.equalsIgnoreCase("host")) {
                List<String> hosts = headers.get(k);
                if (hosts.size() > 0) return hosts.get(0);
                return null;
            }
        }
        return null;
    }

    public int createCollectionSimple(int vxlanId) {
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

        return vxlanId;
    }


    public int createCollectionBasedOnHostName(int id, String host, int vxlanId)  throws Exception {
        UpdateOptions updateOptions = new UpdateOptions();
        updateOptions.upsert(true);
        // 3 cases
        // 1. If 2 threads are trying to insert same host simultaneously then both will succeed with upsert true
        // 2. If we are trying to insert different host but same id (hashCode collision) then it will fail,
        //    so we loop 20 times till we succeed
        boolean flag = false;
        for (int i=0;i < 100; i++) {
            id += i;
            try {
                ApiCollectionsDao.instance.getMCollection().updateOne(
                        Filters.and(
                                Filters.eq(ApiCollection.HOST_NAME, host),
                                Filters.eq(ApiCollection.VXLAN_ID, vxlanId)
                        ),
                        Updates.combine(
                                Updates.setOnInsert("_id", id),
                                Updates.setOnInsert("startTs", Context.now()),
                                Updates.setOnInsert("urls", new HashSet<>())
                        ),
                        updateOptions
                );
                flag = true;
                break;
            } catch (Exception e) {
                logger.error("Error while inserting apiCollection, trying again " + i + " " + e.getMessage());
            }
        }
        if (flag) { // flag tells if we were successfully able to insert collection
            return id;
        } else {
            throw new Exception("Not able to insert");
        }
    }

    public static String getAcceptableContentType(Map<String,List<String>> headers) {
        List<String> acceptableContentTypes = Arrays.asList(JSON_CONTENT_TYPE, FORM_URL_ENCODED_CONTENT_TYPE);
        List<String> contentTypeValues = new ArrayList<>();
        for (String k: headers.keySet()) {
            if (k.equalsIgnoreCase("content-type")) {
                contentTypeValues = headers.get(k);
                for (String value: contentTypeValues) {
                    for (String acceptableContentType: acceptableContentTypes) {
                        if (value.contains(acceptableContentType)) {
                            return acceptableContentType;
                        }
                    }
                }
            }
        }
        return null;
    }

    int numberOfSyncs = 0;

    public void syncFunction(List<HttpResponseParams> responseParams)  {
        // USE ONLY filteredResponseParams and not responseParams
        List<HttpResponseParams> filteredResponseParams = filterHttpResponseParams(responseParams);
        boolean isHarOrPcap = aggregate(filteredResponseParams);

        for (int apiCollectionId: aggregatorMap.keySet()) {
            URLAggregator aggregator = aggregatorMap.get(apiCollectionId);
            apiCatalogSync.computeDelta(aggregator, false, apiCollectionId);
        }

        this.sync_count += filteredResponseParams.size();
        int syncThresh = numberOfSyncs < 10 ? 10000 : sync_threshold_count;
        if (this.sync_count >= syncThresh || (Context.now() - this.last_synced) > this.sync_threshold_time || isHarOrPcap) {
            numberOfSyncs++;
            apiCatalogSync.syncWithDB();
            this.last_synced = Context.now();
            this.sync_count = 0;
        }
    }

    public List<HttpResponseParams> filterHttpResponseParams(List<HttpResponseParams> httpResponseParamsList) {
        List<HttpResponseParams> filteredResponseParams = new ArrayList<>();
        for (HttpResponseParams httpResponseParam: httpResponseParamsList) {
            boolean cond = httpResponseParam.statusCode >= 200 && httpResponseParam.statusCode < 300;
            if (!cond) continue;

            String hostName = getHostName(httpResponseParam.getRequestParams().getHeaders());
            int vxlanId = httpResponseParam.requestParams.getApiCollectionId();
            int apiCollectionId ;

            if (hostName != null && ApiCollection.useHost) {
                hostName = hostName.toLowerCase();
                hostName = hostName.trim();

                String key = hostName + " " + vxlanId;

                if (hostNameToIdMap.containsKey(key)) {
                    apiCollectionId = hostNameToIdMap.get(key);
                } else {
                    int id = hostName.hashCode() + vxlanId;
                    try {
                        apiCollectionId = createCollectionBasedOnHostName(id, hostName, vxlanId);
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

                if (!hostNameToIdMap.containsKey(key)) {
                    createCollectionSimple(vxlanId);
                    hostNameToIdMap.put(key, vxlanId);
                }

                apiCollectionId = vxlanId;
            }

            httpResponseParam.requestParams.setApiCollectionId(apiCollectionId);
            filteredResponseParams.add(httpResponseParam);
        }

        return filteredResponseParams;
    }

    Map<Integer, URLAggregator> aggregatorMap = new HashMap<>();
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
