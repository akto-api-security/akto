package com.akto.parsers;

import java.io.*;
import java.net.URLDecoder;
import java.util.*;

import com.akto.DaoInit;
import com.akto.dao.context.Context;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.runtime.APICatalogSync;
import com.akto.runtime.URLAggregator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.ConnectionString;

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

    public void syncFunction(List<HttpResponseParams> responseParams)  {
        // USE ONLY filteredResponseParams and not responseParams
        List<HttpResponseParams> filteredResponseParams = filterHttpResponseParams(responseParams);
        boolean isHarOrPcap = aggregate(filteredResponseParams);

        for (int apiCollectionId: aggregatorMap.keySet()) {
            URLAggregator aggregator = aggregatorMap.get(apiCollectionId);
            apiCatalogSync.computeDelta(aggregator, false, apiCollectionId);
        }

        this.sync_count += filteredResponseParams.size();
        if (this.sync_count >= sync_threshold_count || (Context.now() - this.last_synced) > this.sync_threshold_time || isHarOrPcap) {
            apiCatalogSync.syncWithDB();
            this.last_synced = Context.now();
            this.sync_count = 0;
        }
    }

    public static List<HttpResponseParams> filterHttpResponseParams(List<HttpResponseParams> httpResponseParamsList) {
        List<HttpResponseParams> filteredResponseParams = new ArrayList<>();
        for (HttpResponseParams httpResponseParam: httpResponseParamsList) {
            boolean cond = httpResponseParam.statusCode >= 200 && httpResponseParam.statusCode < 300;
            if (cond) {
                filteredResponseParams.add(httpResponseParam);
            }
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
}
